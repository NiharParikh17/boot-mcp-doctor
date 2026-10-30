package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.nhparikh.repo.bootmcpdoctor.config.BootMcpDoctorProperties;
import com.nhparikh.repo.bootmcpdoctor.safety.ResponseSanitizer;
import com.nhparikh.repo.bootmcpdoctor.tools.EnvironmentTools;
import com.nhparikh.repo.bootmcpdoctor.tools.HealthTools;
import com.nhparikh.repo.bootmcpdoctor.tools.LogLevelTools;
import com.nhparikh.repo.bootmcpdoctor.tools.LoggerTools;
import com.nhparikh.repo.bootmcpdoctor.tools.MetricsTools;
import io.micrometer.core.instrument.MeterRegistry;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.health.registry.ReactiveHealthContributorRegistry;
import org.springframework.boot.logging.LoggerGroups;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.SpringVersion;
import org.springframework.core.env.Environment;
import org.springframework.util.function.SingletonSupplier;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Registers boot-mcp-doctor's MCP tools when Actuator and the Spring AI MCP server are on the classpath and
 * {@code boot-mcp-doctor.enabled=true}.
 * <p>
 * Tools read the application in-process, so the Actuator endpoints do not need to be exposed over HTTP. Each tool group
 * activates only when its module is on the classpath, and is left out when its Actuator endpoint's access is set to
 * {@code none}.
 * <p>
 * With Spring Security on the classpath, every tool call is checked against the authenticated caller and
 * {@code boot-mcp-doctor.security.*}. Without it, no tools are registered unless
 * {@code boot-mcp-doctor.security.allow-unauthenticated=true}, and tools that change state never are.
 * <p>
 * Once the application is ready, it logs where the tools are served and who may call them. In servlet applications, it
 * also logs each MCP client that connects.
 */
@AutoConfiguration
@ConditionalOnClass(value = { Endpoint.class, McpTool.class }, name = "io.modelcontextprotocol.server.McpServer")
@ConditionalOnProperty(prefix = "boot-mcp-doctor", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(BootMcpDoctorProperties.class)
public class BootMcpDoctorAutoConfiguration {

    /**
     * Creates the auto-configuration. Spring Boot does this when it applies it.
     */
    public BootMcpDoctorAutoConfiguration() {
    }

    @Bean
    ResponseSanitizer bootMcpDoctorResponseSanitizer(BootMcpDoctorProperties properties) {
        return new ResponseSanitizer(properties.masking().additionalKeys(), properties.limits().maxItems());
    }

    @Bean
    ToolSelector bootMcpDoctorToolSelector(ObjectProvider<ToolGroup> groups, ObjectProvider<ToolAuthorizer> authorizer,
            BootMcpDoctorProperties properties, Environment environment) {
        return new ToolSelector(groups.orderedStream().toList(), properties, environment,
                authorizer.getIfAvailable() != null);
    }

    @Bean
    ToolGuard bootMcpDoctorToolGuard(ObjectProvider<ToolAuthorizer> authorizer, ResponseSanitizer sanitizer) {
        return new ToolGuard(authorizer.getIfAvailable(), new ToolCallLog(sanitizer));
    }

    @Bean
    List<McpServerFeatures.SyncToolSpecification> bootMcpDoctorToolSpecifications(ToolSelector selector,
            ToolGuard guard) {
        return selector.specifications(SyncMcpAnnotationProviders::toolSpecifications, (spec) -> spec.tool().name())
            .stream()
            .map(guard::guard)
            .toList();
    }

    @Bean
    List<McpStatelessServerFeatures.SyncToolSpecification> bootMcpDoctorStatelessToolSpecifications(
            ToolSelector selector, ToolGuard guard) {
        return selector
            .specifications(SyncMcpAnnotationProviders::statelessToolSpecifications, (spec) -> spec.tool().name())
            .stream()
            .map(guard::guard)
            .toList();
    }

    @Bean
    StartupLog bootMcpDoctorStartupLog(
            @Qualifier("bootMcpDoctorToolSpecifications") List<McpServerFeatures.SyncToolSpecification> specifications,
            ObjectProvider<ToolAuthorizer> authorizer, BootMcpDoctorProperties properties) {
        return new StartupLog(specifications.stream().map(McpServerFeatures.SyncToolSpecification::tool).toList(),
                properties.security(), authorizer.getIfAvailable() != null, SpringVersion.getVersion());
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

    // Only Spring Security can tell who is calling. Without it, ToolSelector decides what may be registered at all.
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
    static class SpringSecurityConfiguration {

        @Bean
        ToolAuthorizer bootMcpDoctorToolAuthorizer(BootMcpDoctorProperties properties) {
            return new SpringSecurityToolAuthorizer(properties.security());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(OncePerRequestFilter.class)
    static class ConnectionLogConfiguration {

        @Bean
        FilterRegistrationBean<ConnectionLogFilter> bootMcpDoctorConnectionLogFilter(Environment environment) {
            McpEndpoints endpoints = new McpEndpoints(environment);
            FilterRegistrationBean<ConnectionLogFilter> registration = new FilterRegistrationBean<>(
                    new ConnectionLogFilter(McpJsonDefaults.getMapper()));
            registration.setName("bootMcpDoctorConnectionLogFilter");
            registration.addUrlPatterns(endpoints.inServlet(endpoints.streamableHttp()),
                    endpoints.inServlet(endpoints.sseMessage()));
            registration.setOrder(ConnectionLogFilter.ORDER);
            return registration;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HealthEndpoint.class)
    static class HealthToolsConfiguration {

        @Bean
        ToolGroup bootMcpDoctorHealthTools(ObjectProvider<HealthEndpoint> healthEndpoint,
                ObjectProvider<HealthContributorRegistry> registry,
                ObjectProvider<ReactiveHealthContributorRegistry> reactiveRegistry, Environment environment,
                ResponseSanitizer sanitizer) {
            // Spring Boot only creates the health endpoint while health is exposed; otherwise, build one over the
            // contributors, so that get_health doesn't depend on exposure.
            Supplier<HealthEndpoint> endpoint = SingletonSupplier.of(() -> {
                HealthEndpoint bean = healthEndpoint.getIfAvailable();
                if (bean != null) {
                    return bean;
                }
                HealthContributorRegistry contributors = registry.getIfAvailable();
                return (contributors != null)
                        ? InProcessHealthEndpoint.create(contributors, reactiveRegistry.getIfAvailable(), environment)
                        : null;
            });
            return new ToolGroup("health", new HealthTools(endpoint, sanitizer), false);
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
            McpEndpoints endpoints = new McpEndpoints(environment);
            List<String> paths = new ArrayList<>();
            paths.add(endpoints.streamableHttp());
            paths.add(endpoints.sse());
            paths.add(endpoints.sseMessage());
            String actuator = environment.getProperty("management.endpoints.web.base-path", "/actuator");
            if (!actuator.isEmpty() && !"/".equals(actuator)) {
                paths.add(actuator);
            }
            return paths;
        }
    }
}
