package io.github.niharparikh17.bootmcpdoctor.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import io.github.niharparikh17.bootmcpdoctor.config.BootMcpDoctorProperties;
import io.github.niharparikh17.bootmcpdoctor.safety.ResponseSanitizer;
import io.github.niharparikh17.bootmcpdoctor.tools.EnvironmentTools;
import io.github.niharparikh17.bootmcpdoctor.tools.HealthTools;
import io.github.niharparikh17.bootmcpdoctor.tools.LogLevelTools;
import io.github.niharparikh17.bootmcpdoctor.tools.LoggerTools;
import io.github.niharparikh17.bootmcpdoctor.tools.MetricsTools;
import io.micrometer.core.instrument.MeterRegistry;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.logging.LoggerGroups;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Registers boot-mcp-doctor's MCP tools when Actuator and the Spring AI MCP server are on the classpath and
 * {@code boot-mcp-doctor.enabled=true}.
 * <p>
 * Tools read the application in-process, so the Actuator endpoints do not need to be exposed over HTTP. Each tool
 * group activates only when its module is on the classpath, and is left out when its Actuator endpoint's access
 * is set to {@code none}.
 */
@AutoConfiguration
@ConditionalOnClass(value = { Endpoint.class, McpTool.class }, name = "io.modelcontextprotocol.server.McpServer")
@ConditionalOnProperty(prefix = "boot-mcp-doctor", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(BootMcpDoctorProperties.class)
public class BootMcpDoctorAutoConfiguration {

    @Bean
    ResponseSanitizer bootMcpDoctorResponseSanitizer(BootMcpDoctorProperties properties) {
        return new ResponseSanitizer(properties.masking().additionalKeys(), properties.limits().maxItems());
    }

    @Bean
    ToolSelector bootMcpDoctorToolSelector(ObjectProvider<ToolGroup> groups, BootMcpDoctorProperties properties,
            Environment environment) {
        return new ToolSelector(groups.orderedStream().toList(), properties, environment);
    }

    @Bean
    List<McpServerFeatures.SyncToolSpecification> bootMcpDoctorToolSpecifications(ToolSelector selector) {
        return selector.filter(SyncMcpAnnotationProviders.toolSpecifications(selector.toolObjects()),
                (spec) -> spec.tool().name());
    }

    @Bean
    List<McpStatelessServerFeatures.SyncToolSpecification> bootMcpDoctorStatelessToolSpecifications(
            ToolSelector selector) {
        return selector.filter(SyncMcpAnnotationProviders.statelessToolSpecifications(selector.toolObjects()),
                (spec) -> spec.tool().name());
    }

    @Bean
    ToolGroup bootMcpDoctorEnvironmentTools(Environment environment,
            ObjectProvider<SanitizingFunction> sanitizingFunctions, ResponseSanitizer sanitizer) {
        return new ToolGroup("env",
                new EnvironmentTools(environment, sanitizingFunctions.orderedStream().toList(), sanitizer), false);
    }

    @Bean
    ToolGroup bootMcpDoctorLoggerTools(ObjectProvider<LoggingSystem> loggingSystem,
            ObjectProvider<LoggerGroups> loggerGroups, ResponseSanitizer sanitizer) {
        return new ToolGroup("loggers", new LoggerTools(loggingSystem, loggerGroups, sanitizer), false);
    }

    @Bean
    ToolGroup bootMcpDoctorLogLevelTools(ObjectProvider<LoggingSystem> loggingSystem,
            ObjectProvider<LoggerGroups> loggerGroups) {
        return new ToolGroup("loggers", new LogLevelTools(loggingSystem, loggerGroups), true);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HealthEndpoint.class)
    static class HealthToolsConfiguration {

        @Bean
        ToolGroup bootMcpDoctorHealthTools(ObjectProvider<HealthEndpoint> healthEndpoint,
                ResponseSanitizer sanitizer) {
            return new ToolGroup("health", new HealthTools(healthEndpoint, sanitizer), false);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({ MeterRegistry.class, MetricsEndpoint.class })
    static class MetricsToolsConfiguration {

        @Bean
        ToolGroup bootMcpDoctorMetricsTools(ObjectProvider<MeterRegistry> meterRegistry, ResponseSanitizer sanitizer,
                Environment environment) {
            return new ToolGroup("metrics", new MetricsTools(meterRegistry, sanitizer, ignoredPaths(environment)),
                    false);
        }

        // The MCP endpoints and Actuator are infrastructure, not application endpoints.
        private static List<String> ignoredPaths(Environment environment) {
            List<String> paths = new ArrayList<>();
            paths.add(environment.getProperty("spring.ai.mcp.server.streamable-http.mcp-endpoint", "/mcp"));
            paths.add(environment.getProperty("spring.ai.mcp.server.sse-endpoint", "/sse"));
            paths.add(environment.getProperty("spring.ai.mcp.server.sse-message-endpoint", "/mcp/message"));
            String actuator = environment.getProperty("management.endpoints.web.base-path", "/actuator");
            if (!actuator.isEmpty() && !"/".equals(actuator)) {
                paths.add(actuator);
            }
            return paths;
        }
    }
}
