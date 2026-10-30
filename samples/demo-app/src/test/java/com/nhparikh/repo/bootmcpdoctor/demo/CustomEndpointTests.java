package com.nhparikh.repo.bootmcpdoctor.demo;

import java.util.Map;

import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A custom Streamable HTTP endpoint moves everything with it: the startup line, the connection log, and the endpoints
 * that {@code get_slow_endpoints} leaves out.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.mcp.server.streamable-http.mcp-endpoint=/ops/mcp")
class CustomEndpointTests extends McpAppTest {

    @Test
    void servesTheToolsOnTheCustomEndpoint(CapturedOutput output) throws Exception {
        McpSyncClient client = connect("/ops/mcp");
        get("/orders");
        assertThat(call(client, "get_slow_endpoints", Map.of())).contains("\"uri\":\"/orders\"")
            .doesNotContain("/ops/mcp");
        assertThat(output).contains("boot-mcp-doctor is running at /ops/mcp on port " + this.port)
            .contains("MCP client connected: " + CLIENT);
    }
}
