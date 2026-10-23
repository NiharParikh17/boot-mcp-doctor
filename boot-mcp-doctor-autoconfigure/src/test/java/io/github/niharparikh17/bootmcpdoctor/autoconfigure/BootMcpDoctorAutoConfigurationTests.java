package io.github.niharparikh17.bootmcpdoctor.autoconfigure;

import java.util.List;
import java.util.Map;

import io.github.niharparikh17.bootmcpdoctor.safety.ResponseSanitizer;
import io.github.niharparikh17.bootmcpdoctor.tools.HealthTools;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerSpecificationFactoryAutoConfiguration;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointAutoConfiguration;
import org.springframework.boot.health.autoconfigure.registry.HealthContributorRegistryAutoConfiguration;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.ResolvableType;

import static org.assertj.core.api.Assertions.assertThat;

class BootMcpDoctorAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(BootMcpDoctorAutoConfiguration.class));

    @Test
    void registersNothingWhenEnabledIsMissing() {
        this.contextRunner.run((context) -> assertThat(context).doesNotHaveBean(ResponseSanitizer.class)
            .doesNotHaveBean(HealthTools.class));
    }

    @Test
    void registersNothingWhenDisabled() {
        this.contextRunner.withPropertyValues("boot-mcp-doctor.enabled=false")
            .run((context) -> assertThat(context).doesNotHaveBean(ResponseSanitizer.class)
                .doesNotHaveBean(HealthTools.class));
    }

    @Test
    void registersHealthToolsWhenEnabled() {
        this.contextRunner.withPropertyValues("boot-mcp-doctor.enabled=true")
            .run((context) -> assertThat(context).hasSingleBean(ResponseSanitizer.class)
                .hasSingleBean(HealthTools.class));
    }

    @Test
    void registersNothingWithoutActuator() {
        this.contextRunner.withPropertyValues("boot-mcp-doctor.enabled=true")
            .withClassLoader(new FilteredClassLoader(Endpoint.class))
            .run((context) -> assertThat(context).doesNotHaveBean(ResponseSanitizer.class));
    }

    @Test
    void registersNothingWithoutMcpAnnotations() {
        this.contextRunner.withPropertyValues("boot-mcp-doctor.enabled=true")
            .withClassLoader(new FilteredClassLoader(McpTool.class))
            .run((context) -> assertThat(context).doesNotHaveBean(ResponseSanitizer.class));
    }

    @Test
    void registersNothingWithoutMcpServer() {
        this.contextRunner.withPropertyValues("boot-mcp-doctor.enabled=true")
            .withClassLoader(new FilteredClassLoader("io.modelcontextprotocol.server"))
            .run((context) -> assertThat(context).doesNotHaveBean(ResponseSanitizer.class));
    }

    @Test
    void skipsHealthToolsWithoutHealthModule() {
        this.contextRunner.withPropertyValues("boot-mcp-doctor.enabled=true")
            .withClassLoader(new FilteredClassLoader(HealthEndpoint.class))
            .run((context) -> assertThat(context).hasSingleBean(ResponseSanitizer.class)
                .doesNotHaveBean(HealthTools.class));
    }

    @Test
    void failsWhenMaxItemsIsBelowOne() {
        this.contextRunner.withPropertyValues("boot-mcp-doctor.enabled=true", "boot-mcp-doctor.limits.max-items=0")
            .run((context) -> assertThat(context).hasFailed());
    }

    @Test
    void exposesGetHealthAsReadOnlyMcpTool() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HealthContributorRegistryAutoConfiguration.class,
                    HealthEndpointAutoConfiguration.class, McpServerAnnotationScannerAutoConfiguration.class,
                    McpServerSpecificationFactoryAutoConfiguration.class, BootMcpDoctorAutoConfiguration.class))
            .withPropertyValues("boot-mcp-doctor.enabled=true")
            .withBean("db", HealthIndicator.class, () -> () -> Health.up().build())
            .run((context) -> {
                ResolvableType specsType = ResolvableType.forClassWithGenerics(List.class,
                        SyncToolSpecification.class);
                List<SyncToolSpecification> specs = context.<List<SyncToolSpecification>>getBeanProvider(specsType)
                    .getObject();
                SyncToolSpecification getHealth = specs.stream()
                    .filter((spec) -> spec.tool().name().equals("get_health"))
                    .findFirst()
                    .orElseThrow();
                assertThat(getHealth.tool().annotations().readOnlyHint()).isTrue();
                assertThat(getHealth.tool().annotations().destructiveHint()).isFalse();

                CallToolResult result = getHealth.callHandler()
                    .apply(null, new CallToolRequest("get_health", Map.of()));
                assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
                assertThat(((TextContent) result.content().get(0)).text()).contains("\"status\":\"UP\"")
                    .contains("\"name\":\"db\"");
            });
    }
}
