package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

import java.util.List;
import java.util.Map;

import com.nhparikh.repo.bootmcpdoctor.safety.ResponseSanitizer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointAutoConfiguration;
import org.springframework.boot.health.autoconfigure.registry.HealthContributorRegistryAutoConfiguration;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class BootMcpDoctorAutoConfigurationTests {

    private static final String WRITE_AUTHORITY = "boot-mcp-doctor.security.write-authority=ROLE_OPS_ADMIN";

    private static final List<String> READ_TOOLS = List.of("get_environment", "get_health", "get_loggers",
            "get_metric", "get_slow_endpoints", "list_metrics");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(BootMcpDoctorAutoConfiguration.class));

    private final ApplicationContextRunner enabled = this.contextRunner
        .withPropertyValues("boot-mcp-doctor.enabled=true");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registersNothingWhenEnabledIsMissing() {
        this.contextRunner.run((context) -> assertThat(context).doesNotHaveBean(ResponseSanitizer.class)
            .doesNotHaveBean("bootMcpDoctorToolSpecifications"));
    }

    @Test
    void registersNothingWhenDisabled() {
        this.contextRunner.withPropertyValues("boot-mcp-doctor.enabled=false")
            .run((context) -> assertThat(context).doesNotHaveBean(ResponseSanitizer.class)
                .doesNotHaveBean("bootMcpDoctorToolSpecifications"));
    }

    @Test
    void registersOnlyReadToolsByDefault() {
        this.enabled.run((context) -> assertThat(toolNames(context)).containsExactlyElementsOf(READ_TOOLS));
    }

    @Test
    void registersSetLogLevelWhenNotReadOnly() {
        this.enabled.withPropertyValues("boot-mcp-doctor.read-only=false", WRITE_AUTHORITY)
            .run((context) -> assertThat(toolNames(context)).contains("set_log_level"));
    }

    @Test
    void marksOnlySetLogLevelAsChangingState() {
        this.enabled.withPropertyValues("boot-mcp-doctor.read-only=false", WRITE_AUTHORITY).run((context) -> {
            for (SyncToolSpecification spec : specifications(context)) {
                boolean write = spec.tool().name().equals("set_log_level");
                assertThat(spec.tool().annotations().readOnlyHint()).as(spec.tool().name()).isEqualTo(!write);
                assertThat(spec.tool().annotations().destructiveHint()).as(spec.tool().name()).isFalse();
            }
        });
    }

    @Test
    void keepsSetLogLevelOutWhenLoggersEndpointIsReadOnly() {
        this.enabled
            .withPropertyValues("boot-mcp-doctor.read-only=false", WRITE_AUTHORITY,
                    "management.endpoint.loggers.access=read-only")
            .run((context) -> assertThat(toolNames(context)).contains("get_loggers").doesNotContain("set_log_level"));
    }

    @Test
    void leavesOutToolsWhoseEndpointAccessIsNone() {
        this.enabled.withPropertyValues("management.endpoint.env.access=none")
            .run((context) -> assertThat(toolNames(context)).doesNotContain("get_environment").contains("get_health"));
    }

    @Test
    void endpointAccessFallsBackToTheDefaultAccess() {
        this.enabled
            .withPropertyValues("management.endpoints.access.default=none",
                    "management.endpoint.health.access=read-only")
            .run((context) -> assertThat(toolNames(context)).containsExactly("get_health"));
    }

    @Test
    void leavesOutEverythingWhenMaxPermittedAccessIsNone() {
        this.enabled.withPropertyValues("management.endpoints.access.max-permitted=none")
            .run((context) -> assertThat(toolNames(context)).isEmpty());
    }

    @Test
    void registersOnlyIncludedTools() {
        this.enabled.withPropertyValues("boot-mcp-doctor.tools.include=get_health,get_metric")
            .run((context) -> assertThat(toolNames(context)).containsExactly("get_health", "get_metric"));
    }

    @Test
    void excludeWinsOverInclude() {
        this.enabled
            .withPropertyValues("boot-mcp-doctor.tools.include=get_health,get_metric",
                    "boot-mcp-doctor.tools.exclude=get_metric")
            .run((context) -> assertThat(toolNames(context)).containsExactly("get_health"));
    }

    @Test
    void includingAWriteToolDoesNotBypassReadOnly() {
        this.enabled.withPropertyValues("boot-mcp-doctor.tools.include=set_log_level")
            .run((context) -> assertThat(toolNames(context)).isEmpty());
    }

    @Test
    void ignoresUnknownToolNames() {
        this.enabled.withPropertyValues("boot-mcp-doctor.tools.include=get_health,get_everything")
            .run((context) -> assertThat(toolNames(context)).containsExactly("get_health"));
    }

    @Test
    void warnsOnceAboutToolNamesThatWillNotBeRegistered(CapturedOutput output) {
        this.enabled.withPropertyValues("boot-mcp-doctor.tools.include=get_health,get_everything").run((context) -> {
            assertThat(context).hasNotFailed();
            assertThat(output).containsOnlyOnce("will not be registered: [get_everything]");
        });
    }

    @Test
    void skipsTheAnnotationConverterWhenNoToolCanBeRegistered(CapturedOutput output) {
        this.enabled.withPropertyValues("management.endpoints.access.max-permitted=none").run((context) -> {
            assertThat(toolNames(context)).isEmpty();
            assertThat(output).doesNotContain("No tool methods found");
        });
    }

    @Test
    void registersTheSameToolsForStatelessServers() {
        this.enabled.withPropertyValues("boot-mcp-doctor.tools.exclude=get_loggers").run((context) -> {
            @SuppressWarnings("unchecked")
            List<McpStatelessServerFeatures.SyncToolSpecification> stateless = context
                .getBean("bootMcpDoctorStatelessToolSpecifications", List.class);
            assertThat(stateless.stream().map((spec) -> spec.tool().name()).sorted().toList())
                .isEqualTo(toolNames(context));
        });
    }

    @Test
    void registersNothingWithoutActuator() {
        this.enabled.withClassLoader(new FilteredClassLoader(Endpoint.class))
            .run((context) -> assertThat(context).doesNotHaveBean(ResponseSanitizer.class));
    }

    @Test
    void registersNothingWithoutMcpAnnotations() {
        this.enabled.withClassLoader(new FilteredClassLoader(McpTool.class))
            .run((context) -> assertThat(context).doesNotHaveBean(ResponseSanitizer.class));
    }

    @Test
    void registersNothingWithoutMcpServer() {
        this.enabled.withClassLoader(new FilteredClassLoader("io.modelcontextprotocol.server"))
            .run((context) -> assertThat(context).doesNotHaveBean(ResponseSanitizer.class));
    }

    @Test
    void skipsHealthToolsWithoutHealthModule() {
        this.enabled.withClassLoader(new FilteredClassLoader(HealthEndpoint.class))
            .run((context) -> assertThat(toolNames(context)).doesNotContain("get_health").contains("get_metric"));
    }

    @Test
    void skipsMetricsToolsWithoutMetricsModule() {
        this.enabled.withClassLoader(new FilteredClassLoader(MetricsEndpoint.class))
            .run((context) -> assertThat(toolNames(context)).doesNotContain("get_metric", "list_metrics",
                    "get_slow_endpoints")
                .contains("get_health"));
    }

    @Test
    void registersTheConnectionLogFilterOnTheMcpEndpoints() {
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(BootMcpDoctorAutoConfiguration.class))
            .withPropertyValues("boot-mcp-doctor.enabled=true")
            .run((context) -> {
                FilterRegistrationBean<?> registration = context.getBean("bootMcpDoctorConnectionLogFilter",
                        FilterRegistrationBean.class);
                assertThat(registration.getFilter()).isInstanceOf(ConnectionLogFilter.class);
                assertThat(registration.getUrlPatterns()).containsExactly("/mcp", "/mcp/message");
                assertThat(registration.getOrder()).isEqualTo(ConnectionLogFilter.ORDER);
            });
    }

    @Test
    void connectionLogFilterFollowsTheServletPathAndEndpoints() {
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(BootMcpDoctorAutoConfiguration.class))
            .withPropertyValues("boot-mcp-doctor.enabled=true", "spring.mvc.servlet.path=/api/",
                    "spring.ai.mcp.server.streamable-http.mcp-endpoint=/ops/mcp",
                    "spring.ai.mcp.server.sse-message-endpoint=/ops/message")
            .run((context) -> assertThat(
                    context.getBean("bootMcpDoctorConnectionLogFilter", FilterRegistrationBean.class).getUrlPatterns())
                .containsExactly("/api/ops/mcp", "/api/ops/message"));
    }

    @Test
    void noConnectionLogFilterOutsideServletApplications() {
        this.enabled.run((context) -> assertThat(context).hasNotFailed()
            .doesNotHaveBean("bootMcpDoctorConnectionLogFilter"));
    }

    @Test
    void noConnectionLogFilterWithoutSpringWeb() {
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(BootMcpDoctorAutoConfiguration.class))
            .withPropertyValues("boot-mcp-doctor.enabled=true")
            .withClassLoader(new FilteredClassLoader(OncePerRequestFilter.class))
            .run((context) -> assertThat(context).hasNotFailed()
                .doesNotHaveBean("bootMcpDoctorConnectionLogFilter")
                .hasBean("bootMcpDoctorToolSpecifications"));
    }

    @Test
    void failsWhenMaxItemsIsBelowOne() {
        this.enabled.withPropertyValues("boot-mcp-doctor.limits.max-items=0")
            .run((context) -> assertThat(context).hasFailed());
    }

    @Test
    void getHealthCanBeCalledThroughItsSpecification() {
        this.enabled
            .withConfiguration(AutoConfigurations.of(HealthContributorRegistryAutoConfiguration.class,
                    HealthEndpointAutoConfiguration.class))
            .withBean("db", HealthIndicator.class, () -> () -> Health.up().build())
            .run((context) -> {
                assertThat(context).hasSingleBean(HealthEndpoint.class);
                assertThat(getHealth(context)).contains("\"status\":\"UP\"").contains("\"name\":\"db\"");
            });
    }

    // Spring Boot leaves out the health endpoint while health is not exposed; samples/demo-app's
    // HealthNotExposedTests covers that in a running application.
    @Test
    void getHealthReadsTheContributorsWithoutTheHealthEndpoint() {
        this.enabled.withConfiguration(AutoConfigurations.of(HealthContributorRegistryAutoConfiguration.class))
            .withBean("db", HealthIndicator.class,
                    () -> () -> Health.down().withDetail("password", "s3cret").withDetail("pool", "orders").build())
            .withBean("diskSpace", HealthIndicator.class, () -> () -> Health.up().build())
            .run((context) -> {
                assertThat(context).doesNotHaveBean(HealthEndpoint.class);
                assertThat(getHealth(context)).contains("\"status\":\"DOWN\"")
                    .contains("1 of 2 components are not UP: db (DOWN)")
                    .contains("\"password\":\"******\"")
                    .contains("\"pool\":\"orders\"")
                    .doesNotContain("s3cret");
            });
    }

    @Test
    void getHealthFollowsTheStatusOrderWithoutTheHealthEndpoint() {
        this.enabled.withConfiguration(AutoConfigurations.of(HealthContributorRegistryAutoConfiguration.class))
            .withPropertyValues("management.endpoint.health.status.order=out-of-service,up,down")
            .withBean("db", HealthIndicator.class, () -> () -> Health.down().build())
            .withBean("diskSpace", HealthIndicator.class, () -> () -> Health.up().build())
            .run((context) -> assertThat(getHealth(context)).contains("\"status\":\"UP\""));
    }

    @Test
    void getHealthIsUnavailableWithoutHealthContributors() {
        this.enabled.run((context) -> assertThat(getHealth(context))
            .contains("Health not available. Is spring-boot-starter-actuator on the classpath?"));
    }

    private static String getHealth(AssertableApplicationContext context) {
        SyncToolSpecification getHealth = specifications(context).stream()
            .filter((spec) -> spec.tool().name().equals("get_health"))
            .findFirst()
            .orElseThrow();
        SecurityContextHolder.getContext()
            .setAuthentication(UsernamePasswordAuthenticationToken.authenticated("ops", null, List.of()));
        CallToolResult result = getHealth.callHandler()
            .apply(null, CallToolRequest.builder("get_health").arguments(Map.of()).build());
        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        return ((TextContent) result.content().get(0)).text();
    }

    @SuppressWarnings("unchecked")
    private static List<SyncToolSpecification> specifications(AssertableApplicationContext context) {
        return context.getBean("bootMcpDoctorToolSpecifications", List.class);
    }

    private static List<String> toolNames(AssertableApplicationContext context) {
        return specifications(context).stream().map((spec) -> spec.tool().name()).sorted().toList();
    }
}
