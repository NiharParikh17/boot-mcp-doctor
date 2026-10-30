package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.util.ClassUtils;

/**
 * Logs, once the application is ready, whether boot-mcp-doctor supports the application's Spring Boot and Spring AI
 * versions, also when boot-mcp-doctor is turned off. It only uses classes that every Spring Boot version has and
 * looks the others up by name, so that it also runs, and says so, on versions that boot-mcp-doctor does not support.
 */
final class CompatibilityLog implements ApplicationListener<ApplicationReadyEvent>, Ordered {

    /**
     * The Spring Boot lines this release supports, oldest first, as in docs/compatibility.md. It is built with the
     * newest.
     */
    static final List<String> SPRING_BOOT = List.of("4.0", "4.1");

    /**
     * The Spring AI line this release supports, as in docs/compatibility.md.
     */
    static final String SPRING_AI = "2.0";

    static final String CHART = "https://github.com/NiharParikh17/boot-mcp-doctor/blob/main/docs/compatibility.md";

    static final String TURN_ON = "Set boot-mcp-doctor.enabled=true to turn it on.";

    // What BootMcpDoctorAutoConfiguration needs on the classpath.
    static final String ACTUATOR = "org.springframework.boot.actuate.endpoint.annotation.Endpoint";

    static final String MCP_SERVER = "io.modelcontextprotocol.server.McpServer";

    static final String MCP_ANNOTATIONS = "org.springframework.ai.mcp.annotation.McpTool";

    // Where Spring AI's MCP modules record their version, which is the same for all of them. Any one may be missing
    // when the application doesn't use the starter.
    static final List<String> SPRING_AI_POMS = List.of("spring-ai-mcp", "spring-ai-mcp-annotations",
            "spring-ai-autoconfigure-mcp-server-common")
        .stream()
        .map((module) -> "META-INF/maven/org.springframework.ai/" + module + "/pom.properties")
        .toList();

    private static final Log logger = LogFactory.getLog(CompatibilityLog.class);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ConfigurableApplicationContext context = event.getApplicationContext();
        ClassLoader classLoader = (context.getClassLoader() != null) ? context.getClassLoader()
                : ClassUtils.getDefaultClassLoader();
        Application application = new Application(SpringBootVersion.getVersion(), springAiVersion(classLoader),
                ClassUtils.isPresent(ACTUATOR, classLoader), ClassUtils.isPresent(MCP_SERVER, classLoader),
                ClassUtils.isPresent(MCP_ANNOTATIONS, classLoader),
                "true".equalsIgnoreCase(context.getEnvironment().getProperty("boot-mcp-doctor.enabled")),
                context.containsBean("bootMcpDoctorToolSpecifications"));
        Line line = line(CompatibilityLog.class.getPackage().getImplementationVersion(), application);
        if (line.warning()) {
            logger.warn(line.text());
        }
        else {
            logger.info(line.text());
        }
    }

    // Before the line that says where the tools are served.
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * @param classLoader the application's class loader
     * @return the Spring AI version on the classpath, or {@code null} when there is none or it can't be read
     */
    static String springAiVersion(ClassLoader classLoader) {
        for (String resource : SPRING_AI_POMS) {
            try (InputStream in = classLoader.getResourceAsStream(resource)) {
                if (in != null) {
                    Properties pom = new Properties();
                    pom.load(in);
                    return pom.getProperty("version");
                }
            }
            catch (IOException ex) {
                // Try the next module.
            }
        }
        return null;
    }

    /**
     * Describes how boot-mcp-doctor fits the application.
     * @param version boot-mcp-doctor's version, or {@code null} when it can't be read
     * @param application what the application has
     * @return the line to log
     */
    static Line line(String version, Application application) {
        String name = (version != null) ? "boot-mcp-doctor " + version : "boot-mcp-doctor";
        String springBoot = (application.springBoot() != null) ? application.springBoot() : "(unknown version)";
        String found = "Spring Boot " + springBoot + springAi(application);
        List<String> problems = problems(application, found);
        if (problems.isEmpty() && (application.springBoot() == null || application.springAi() == null)) {
            return new Line(false, name + " could not read this application's "
                    + ((application.springBoot() == null) ? "Spring Boot" : "Spring AI")
                    + " version, so it can't tell whether it supports it. It supports " + supported() + ". See "
                    + CHART + ".");
        }
        if (problems.isEmpty()) {
            return application.enabled() ? new Line(false, name + " supports this application: " + found + ".")
                    : new Line(false, name + " supports this application, " + found + ", but is turned off. "
                            + TURN_ON);
        }
        StringBuilder text = new StringBuilder(name).append(" does not support this application")
            .append(application.enabled() ? "" : ", and is turned off")
            .append(": ")
            .append(String.join("; ", problems))
            .append('.');
        if (application.enabled()) {
            text.append(application.started() ? " It started anyway, so some tools may not work."
                    : " It registered no tools.");
        }
        text.append(" See ").append(CHART).append(" for the supported versions.");
        return new Line(application.enabled(), text.toString());
    }

    private static String springAi(Application application) {
        if (application.springAi() != null) {
            return " with Spring AI " + application.springAi();
        }
        return isSpringAiUnread(application) ? " with Spring AI (unknown version)" : " without Spring AI's MCP server";
    }

    // Spring AI's MCP classes are there, but its version can't be read.
    private static boolean isSpringAiUnread(Application application) {
        return application.springAi() == null && application.mcpServer() && application.mcpAnnotations();
    }

    private static List<String> problems(Application application, String found) {
        List<String> problems = new ArrayList<>();
        boolean springBoot = application.springBoot() == null
                || SPRING_BOOT.stream().anyMatch((line) -> isLine(application.springBoot(), line));
        boolean springAi = application.springAi() != null && isLine(application.springAi(), SPRING_AI);
        if (!springBoot || !(springAi || isSpringAiUnread(application))) {
            problems.add("it needs " + supported() + ", and this application has " + found);
        }
        // With another Spring AI line, its MCP classes are expected to differ, so only the version is reported.
        List<String> missing = new ArrayList<>();
        if (!application.actuator()) {
            missing.add("Spring Boot Actuator");
        }
        if (springAi && !application.mcpServer()) {
            missing.add("the MCP server");
        }
        if (springAi && !application.mcpAnnotations()) {
            missing.add("Spring AI's MCP annotations");
        }
        if (!missing.isEmpty()) {
            problems.add("it needs " + join(missing) + " on the classpath");
        }
        return problems;
    }

    // Such as "Spring Boot 4.0 or 4.1 with Spring AI 2.0".
    private static String supported() {
        return "Spring Boot " + String.join(" or ", SPRING_BOOT) + " with Spring AI " + SPRING_AI;
    }

    // Whether the version, such as 4.1.2 or 4.1.3-SNAPSHOT, belongs to the line, such as 4.1.
    private static boolean isLine(String version, String line) {
        return version.equals(line) || version.startsWith(line + ".");
    }

    private static String join(List<String> items) {
        if (items.size() == 1) {
            return items.get(0);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    /**
     * What the application has.
     *
     * @param springBoot its Spring Boot version, or {@code null} when it can't be read
     * @param springAi its Spring AI version, or {@code null} when Spring AI is missing or its version can't be read
     * @param actuator whether Actuator is on the classpath
     * @param mcpServer whether the MCP server is on the classpath
     * @param mcpAnnotations whether Spring AI's MCP annotations are on the classpath
     * @param enabled whether {@code boot-mcp-doctor.enabled} is {@code true}
     * @param started whether boot-mcp-doctor's auto-configuration ran
     */
    record Application(String springBoot, String springAi, boolean actuator, boolean mcpServer,
            boolean mcpAnnotations, boolean enabled, boolean started) {
    }

    /**
     * @param warning whether to log it as a warning rather than as information
     * @param text the line
     */
    record Line(boolean warning, String text) {
    }
}
