package com.nhparikh.repo.bootmcpdoctor.secured;

import java.util.Map;

import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSE clients post their messages to {@code /mcp/message}, which needs the CSRF exemption as well.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        // The SSE stream stays open, so don't let graceful shutdown wait 30 seconds for it after the test.
        properties = { "spring.ai.mcp.server.protocol=SSE", "server.shutdown=immediate" })
class SecuredSseTests extends McpAppTest {

    @Test
    void servesTheToolsOverSse(CapturedOutput output) {
        McpSyncClient client = connectSse("/sse", basic("ops"));
        assertThat(call(client, "get_health", Map.of())).contains("\"status\":\"UP\"");
        assertThat(output).contains("boot-mcp-doctor is running at /sse on port " + this.port + " (SSE)")
            .containsPattern("MCP client connected: " + CLIENT + " \\(protocol [0-9-]+, session [0-9a-f-]{8}, "
                    + "user ops, from ");
    }
}
