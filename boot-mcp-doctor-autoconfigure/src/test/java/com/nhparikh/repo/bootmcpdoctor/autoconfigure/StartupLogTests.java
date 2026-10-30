package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

import java.time.Duration;
import java.util.List;

import com.nhparikh.repo.bootmcpdoctor.config.BootMcpDoctorProperties;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(OutputCaptureExtension.class)
class StartupLogTests {

    private static final String READ_TOOLS = "get_environment, get_health, get_loggers, get_metric, "
            + "get_slow_endpoints, list_metrics";

    private final ApplicationContextRunner enabled = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(BootMcpDoctorAutoConfiguration.class))
        .withPropertyValues("boot-mcp-doctor.enabled=true");

    private final ApplicationContextRunner streamable = this.enabled
        .withBean(McpSyncServer.class, () -> mock(McpSyncServer.class))
        .withBean(McpStreamableServerTransportProvider.class, () -> mock(McpStreamableServerTransportProvider.class));

    @Test
    void logsWhereStreamableHttpToolsAreServed(CapturedOutput output) {
        this.streamable.withPropertyValues("local.server.port=8090", "server.servlet.context-path=/api/xyz")
            .run((context) -> {
                ready(context);
                assertThat(output)
                    .contains("boot-mcp-doctor is running at /api/xyz/mcp on port 8090 (Streamable "
                            + "HTTP) with 6 read-only tools: " + READ_TOOLS
                            + ". Tool calls need a caller authenticated by Spring Security.")
                    .doesNotContain(StartupLog.ANY_CALLER);
            });
    }

    @Test
    void includesTheDispatcherServletPathAndCustomEndpoint(CapturedOutput output) {
        this.streamable
            .withPropertyValues("spring.mvc.servlet.path=/api/",
                    "spring.ai.mcp.server.streamable-http.mcp-endpoint=/ops/mcp")
            .run((context) -> {
                ready(context);
                assertThat(output).contains("boot-mcp-doctor is running at /api/ops/mcp (Streamable HTTP) with");
            });
    }

    @Test
    void logsTheSseEndpoint(CapturedOutput output) {
        this.enabled.withBean(McpSyncServer.class, () -> mock(McpSyncServer.class))
            .withBean(McpServerTransportProvider.class, () -> mock(McpServerTransportProvider.class))
            .withPropertyValues("local.server.port=8080")
            .run((context) -> {
                ready(context);
                assertThat(output).contains("boot-mcp-doctor is running at /sse on port 8080 (SSE) with 6")
                    .doesNotContain(StartupLog.SSE_BROKEN);
            });
    }

    @Test
    void warnsWhenSpringFrameworkBreaksSse(CapturedOutput output) {
        this.enabled.withBean(McpSyncServer.class, () -> mock(McpSyncServer.class))
            .withBean(McpServerTransportProvider.class, () -> mock(McpServerTransportProvider.class))
            .run((context) -> {
                new StartupLog(List.of(), new BootMcpDoctorProperties.Security(false, null, null), true, "7.0.6")
                    .onApplicationEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0],
                            context.getSourceApplicationContext(), Duration.ZERO));
                assertThat(output).contains("boot-mcp-doctor is running at /sse (SSE)" + StartupLog.NO_TOOLS)
                    .contains(StartupLog.SSE_BROKEN);
            });
    }

    @Test
    void knowsWhichSpringFrameworkBreaksSse() {
        assertThat(StartupLog.isSseBroken("7.0.6")).isTrue();
        assertThat(StartupLog.isSseBroken("7.0.5")).isFalse();
        assertThat(StartupLog.isSseBroken("7.0.7")).isFalse();
        assertThat(StartupLog.isSseBroken(null)).isFalse();
    }

    @Test
    void logsTheStatelessEndpoint(CapturedOutput output) {
        this.enabled.withBean(McpStatelessSyncServer.class, () -> mock(McpStatelessSyncServer.class)).run((context) -> {
            ready(context);
            assertThat(output).contains("boot-mcp-doctor is running at /mcp (stateless Streamable HTTP) with 6");
        });
    }

    @Test
    void logsStdio(CapturedOutput output) {
        this.enabled.withBean(McpSyncServer.class, () -> mock(McpSyncServer.class))
            .withBean(StdioServerTransportProvider.class, () -> mock(StdioServerTransportProvider.class))
            .run((context) -> {
                ready(context);
                assertThat(output).contains("boot-mcp-doctor is running over stdio with 6 read-only tools");
            });
    }

    @Test
    void warnsWhenNoSynchronousServerServesTheTools(CapturedOutput output) {
        this.enabled.run((context) -> {
            ready(context);
            assertThat(output).contains(StartupLog.NO_SERVER).doesNotContain("boot-mcp-doctor is running");
        });
    }

    @Test
    void warnsWhenNoToolsAreRegistered(CapturedOutput output) {
        this.streamable.withPropertyValues("management.endpoints.access.max-permitted=none").run((context) -> {
            ready(context);
            assertThat(output).contains("boot-mcp-doctor is running at /mcp (Streamable HTTP)" + StartupLog.NO_TOOLS);
        });
    }

    @Test
    void namesASingleTool(CapturedOutput output) {
        this.streamable.withPropertyValues("boot-mcp-doctor.tools.include=get_health").run((context) -> {
            ready(context);
            assertThat(output).contains("with 1 read-only tool: get_health. ");
        });
    }

    @Test
    void namesTheAuthoritiesForReadAndWriteTools(CapturedOutput output) {
        this.streamable
            .withPropertyValues("boot-mcp-doctor.read-only=false", "boot-mcp-doctor.security.read-authority=ROLE_OPS",
                    "boot-mcp-doctor.security.write-authority=ROLE_OPS_ADMIN",
                    "boot-mcp-doctor.security.allow-unauthenticated=true")
            .run((context) -> {
                ready(context);
                assertThat(output)
                    .contains("with 7 tools: " + READ_TOOLS + ", set_log_level. Read tools need the "
                            + "ROLE_OPS authority; set_log_level needs the ROLE_OPS_ADMIN authority.")
                    .doesNotContain(StartupLog.ANY_CALLER);
            });
    }

    @Test
    void warnsWhenSpringSecurityLetsUnauthenticatedCallersIn(CapturedOutput output) {
        this.streamable.withPropertyValues("boot-mcp-doctor.security.allow-unauthenticated=true").run((context) -> {
            ready(context);
            assertThat(output).contains("Read tools also accept unauthenticated callers.")
                .contains(StartupLog.ANY_CALLER);
        });
    }

    @Test
    void warnsWhenNothingAuthenticatesCallers(CapturedOutput output) {
        this.streamable.withClassLoader(new FilteredClassLoader(SecurityContextHolder.class))
            .withPropertyValues("boot-mcp-doctor.security.allow-unauthenticated=true",
                    "boot-mcp-doctor.security.read-authority=ROLE_OPS")
            .run((context) -> {
                ready(context);
                assertThat(output).contains("read-only tools: " + READ_TOOLS + ". Tool calls are not authenticated.")
                    .contains(StartupLog.ANY_CALLER);
            });
    }

    private static void ready(AssertableApplicationContext context) {
        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0],
                context.getSourceApplicationContext(), Duration.ZERO));
    }
}
