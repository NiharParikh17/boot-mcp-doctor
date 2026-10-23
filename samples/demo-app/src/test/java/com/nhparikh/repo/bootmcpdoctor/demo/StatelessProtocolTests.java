package com.nhparikh.repo.bootmcpdoctor.demo;

import java.util.Map;

import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With {@code protocol: STATELESS}, every request stands alone: no sessions, so any instance can answer. Streamable
 * HTTP clients connect to {@code /mcp} as usual.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "spring.ai.mcp.server.protocol=STATELESS")
class StatelessProtocolTests extends McpAppTest {

    @Test
    void servesTheToolsWithoutSessions(CapturedOutput output) {
        McpSyncClient client = connect("/mcp");
        assertThat(toolNames(client)).hasSize(6);
        assertThat(call(client, "get_health", Map.of())).contains("inventory (DOWN)");
        assertThat(output)
            .contains("boot-mcp-doctor is running at /mcp on port " + this.port + " (stateless Streamable HTTP) with 6")
            .containsPattern("MCP client connected: " + CLIENT + " \\(protocol [0-9-]+, not authenticated, from ");
    }
}
