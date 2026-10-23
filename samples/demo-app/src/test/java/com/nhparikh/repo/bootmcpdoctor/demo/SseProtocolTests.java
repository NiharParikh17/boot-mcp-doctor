package com.nhparikh.repo.bootmcpdoctor.demo;

import java.util.Map;

import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With {@code protocol: SSE}, clients connect to {@code /sse} and post messages to {@code /mcp/message}; there is no
 * {@code /mcp} endpoint.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        // The SSE stream stays open, so don't let graceful shutdown wait 30 seconds for it after the test.
        properties = { "spring.ai.mcp.server.protocol=SSE", "server.shutdown=immediate" })
class SseProtocolTests extends McpAppTest {

    @Test
    void servesTheToolsOverSse(CapturedOutput output) throws Exception {
        McpSyncClient client = connectSse("/sse");
        assertThat(toolNames(client)).hasSize(6);
        assertThat(call(client, "get_health", Map.of())).contains("inventory (DOWN)");
        assertThat(output)
            .contains("boot-mcp-doctor is running at /sse on port " + this.port + " (SSE) with 6 read-only")
            .containsPattern("MCP client connected: " + CLIENT + " \\(protocol [0-9-]+, session [0-9a-f-]{8}, "
                    + "not authenticated, from [^)]+\\)");
        assertThat(post("/mcp", INITIALIZE, STREAMABLE_HEADERS).statusCode()).isEqualTo(404);
    }
}
