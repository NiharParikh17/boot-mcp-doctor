package com.nhparikh.repo.bootmcpdoctor.secured;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With Spring Security, {@code security.allow-unauthenticated=true} lets unauthenticated callers use the read tools,
 * but only where the filter chain lets them through, and only while no read authority is set.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = { "boot-mcp-doctor.security.allow-unauthenticated=true",
                "boot-mcp-doctor.security.read-authority=" })
@Import(OpenEndpointTests.OpenMcpEndpoint.class)
class AllowUnauthenticatedTests extends McpAppTest {

    @Test
    void unauthenticatedCallersMayCallTheReadTools(CapturedOutput output) {
        assertThat(call(connect("/mcp", null), "get_health", Map.of())).contains("\"status\":\"UP\"");
        assertThat(output).contains("Read tools also accept unauthenticated callers;")
            .contains("boot-mcp-doctor.security.allow-unauthenticated=true: anyone who can reach the MCP endpoint can "
                    + "call the read tools without authenticating.")
            .containsPattern("MCP client connected: " + CLIENT + " \\([^)]*not authenticated");
    }

    @Test
    void writeToolsStillNeedTheWriteAuthority() {
        assertThat(refused(connect("/mcp", null), "set_log_level", Map.of("logger", "ROOT", "level", "DEBUG")))
            .isEqualTo("Not authorized: set_log_level requires an authenticated caller.");
    }
}
