package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

import java.time.Duration;

import com.nhparikh.repo.bootmcpdoctor.autoconfigure.CompatibilityLog.Application;
import com.nhparikh.repo.bootmcpdoctor.autoconfigure.CompatibilityLog.Line;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class CompatibilityLogTests {

    private static final String SEE = " See " + CompatibilityLog.CHART + " for the supported versions.";

    private static final String NEEDS = "it needs Spring Boot 4.1 with Spring AI 2.0, and this application has ";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CompatibilityLogAutoConfiguration.class));

    @Test
    void supportedVersions() {
        assertThat(line(supported(true))).isEqualTo(new Line(false,
                "boot-mcp-doctor 0.1.2 supports this application: Spring Boot 4.1.2 with Spring AI 2.0.2."));
    }

    @Test
    void supportedVersionsWhileTurnedOff() {
        assertThat(line(supported(false))).isEqualTo(new Line(false, "boot-mcp-doctor 0.1.2 supports this "
                + "application, Spring Boot 4.1.2 with Spring AI 2.0.2, but is turned off. Set "
                + "boot-mcp-doctor.enabled=true to turn it on."));
    }

    @Test
    void anyPatchOrSnapshotOfTheSupportedLines() {
        Application snapshots = new Application("4.1.3-SNAPSHOT", "2.0.3-SNAPSHOT", true, true, true, true, true);
        assertThat(line(snapshots).text()).endsWith("supports this application: Spring Boot 4.1.3-SNAPSHOT with "
                + "Spring AI 2.0.3-SNAPSHOT.");
        assertThat(line(new Application("4.10.0", "2.0.0", true, true, true, true, true)).warning()).isTrue();
    }

    @Test
    void leavesOutItsVersionWhenItCantBeRead() {
        assertThat(CompatibilityLog.line(null, supported(true)).text())
            .startsWith("boot-mcp-doctor supports this application:");
    }

    @Test
    void olderSpringBootWhileItsToolsAreRegistered() {
        Line line = line(new Application("4.0.9", "2.0.2", true, true, true, true, true));
        assertThat(line).isEqualTo(new Line(true, "boot-mcp-doctor 0.1.2 does not support this application: "
                + NEEDS + "Spring Boot 4.0.9 with Spring AI 2.0.2. It started anyway, so some tools may not work."
                + SEE));
    }

    @Test
    void newerSpringBoot() {
        assertThat(line(new Application("4.2.0", "2.0.2", true, true, true, true, true)).text())
            .contains(NEEDS + "Spring Boot 4.2.0 with Spring AI 2.0.2.");
    }

    // Spring AI 1.1 has its MCP annotations elsewhere, so the version is all that is reported.
    @Test
    void springBoot3WithItsSpringAi() {
        Line line = line(new Application("3.5.16", "1.1.8", true, true, false, true, false));
        assertThat(line).isEqualTo(new Line(true, "boot-mcp-doctor 0.1.2 does not support this application: "
                + NEEDS + "Spring Boot 3.5.16 with Spring AI 1.1.8. It registered no tools." + SEE));
    }

    @Test
    void unsupportedVersionsWhileTurnedOff() {
        Line line = line(new Application("3.5.16", "1.1.8", true, true, false, false, false));
        assertThat(line).isEqualTo(new Line(false, "boot-mcp-doctor 0.1.2 does not support this application, and "
                + "is turned off: " + NEEDS + "Spring Boot 3.5.16 with Spring AI 1.1.8." + SEE));
    }

    @Test
    void withoutSpringAi() {
        assertThat(line(new Application("4.1.2", null, true, false, false, true, false)).text())
            .contains(NEEDS + "Spring Boot 4.1.2 without Spring AI's MCP server. It registered no tools.");
    }

    @Test
    void withoutActuatorOrMcpAnnotations() {
        assertThat(line(new Application("4.1.2", "2.0.2", false, true, true, true, false)).text())
            .contains(": it needs Spring Boot Actuator on the classpath. It registered no tools.");
        assertThat(line(new Application("4.1.2", "2.0.2", false, false, false, true, false)).text())
            .contains(": it needs Spring Boot Actuator, the MCP server and Spring AI's MCP annotations on the "
                    + "classpath.");
    }

    @Test
    void reportsEveryProblem() {
        assertThat(line(new Application("4.0.9", "2.0.2", false, true, true, true, false)).text())
            .contains(NEEDS + "Spring Boot 4.0.9 with Spring AI 2.0.2; it needs Spring Boot Actuator on the "
                    + "classpath.");
    }

    @Test
    void versionsThatCantBeRead() {
        assertThat(line(new Application(null, "2.0.2", true, true, true, true, true))).isEqualTo(new Line(false,
                "boot-mcp-doctor 0.1.2 could not read this application's Spring Boot version, so it can't tell "
                        + "whether it supports it. It supports Spring Boot 4.1 with Spring AI 2.0. See "
                        + CompatibilityLog.CHART + "."));
        assertThat(line(new Application("4.1.2", null, true, true, true, true, true)).text())
            .contains("could not read this application's Spring AI version");
        assertThat(line(new Application(null, "1.1.8", true, true, false, true, false)).text())
            .contains(NEEDS + "Spring Boot (unknown version) with Spring AI 1.1.8.");
        assertThat(line(new Application("4.0.9", null, true, true, true, true, true)).text())
            .contains(NEEDS + "Spring Boot 4.0.9 with Spring AI (unknown version).");
    }

    // The supported lines must be the ones boot-mcp-doctor is built and tested with.
    @Test
    void supportedLinesMatchTheBuild() {
        assertThat(SpringBootVersion.getVersion()).startsWith(CompatibilityLog.SPRING_BOOT + ".");
        assertThat(CompatibilityLog.springAiVersion(getClass().getClassLoader()))
            .startsWith(CompatibilityLog.SPRING_AI + ".");
    }

    @Test
    void readsNoSpringAiVersionWithoutSpringAi() {
        ClassPathResource[] poms = CompatibilityLog.SPRING_AI_POMS.stream()
            .map(ClassPathResource::new)
            .toArray(ClassPathResource[]::new);
        assertThat(CompatibilityLog.springAiVersion(new FilteredClassLoader(poms))).isNull();
    }

    @Test
    void logsOnceReadyWhileTurnedOff(CapturedOutput output) {
        this.contextRunner.run((context) -> {
            assertThat(output).doesNotContain("supports this application");
            ready(context);
            assertThat(output).contains("INFO")
                .contains("supports this application, Spring Boot " + SpringBootVersion.getVersion()
                        + " with Spring AI " + springAi() + ", but is turned off. " + CompatibilityLog.TURN_ON);
        });
    }

    @Test
    void logsOnceReadyWhileOn(CapturedOutput output) {
        this.contextRunner.withPropertyValues("boot-mcp-doctor.enabled=true").run((context) -> {
            ready(context);
            assertThat(output).contains("supports this application: Spring Boot " + SpringBootVersion.getVersion()
                    + " with Spring AI " + springAi() + ".");
        });
    }

    @Test
    void warnsWhenSomethingItNeedsIsMissing(CapturedOutput output) {
        this.contextRunner.withPropertyValues("boot-mcp-doctor.enabled=true")
            .withClassLoader(new FilteredClassLoader(CompatibilityLog.ACTUATOR))
            .run((context) -> {
                ready(context);
                assertThat(output).contains("WARN")
                    .contains("does not support this application: it needs Spring Boot Actuator on the classpath. "
                            + "It registered no tools.");
            });
    }

    @Test
    void logsBeforeTheStartupLine() {
        assertThat(new CompatibilityLog().getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    private static Application supported(boolean enabled) {
        return new Application("4.1.2", "2.0.2", true, true, true, enabled, enabled);
    }

    private static Line line(Application application) {
        return CompatibilityLog.line("0.1.2", application);
    }

    private static String springAi() {
        return CompatibilityLog.springAiVersion(CompatibilityLogTests.class.getClassLoader());
    }

    private static void ready(AssertableApplicationContext context) {
        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0],
                context.getSourceApplicationContext(), Duration.ZERO));
    }
}
