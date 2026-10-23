package io.github.niharparikh17.bootmcpdoctor.autoconfigure;

import io.github.niharparikh17.bootmcpdoctor.config.BootMcpDoctorProperties;
import io.github.niharparikh17.bootmcpdoctor.safety.ResponseSanitizer;
import io.github.niharparikh17.bootmcpdoctor.tools.HealthTools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers boot-mcp-doctor's MCP tools when Actuator and the Spring AI MCP server are on the classpath and
 * {@code boot-mcp-doctor.enabled=true}. Each tool group activates only when its Actuator module is present.
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

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HealthEndpoint.class)
    static class HealthToolsConfiguration {

        @Bean
        HealthTools bootMcpDoctorHealthTools(ObjectProvider<HealthEndpoint> healthEndpoint,
                ResponseSanitizer sanitizer) {
            return new HealthTools(healthEndpoint, sanitizer);
        }
    }
}
