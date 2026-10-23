package io.github.niharparikh17.bootmcpdoctor.demo;

import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class DemoApplicationTests {

    @Value("${local.server.port}")
    private int port;

    @Test
    void mcpClientCanCallGetHealth() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
            .builder("http://localhost:" + this.port)
            .build();
        try (McpSyncClient client = McpClient.sync(transport).build()) {
            client.initialize();
            assertThat(client.listTools().tools()).extracting(Tool::name).contains("get_health");

            CallToolResult result = client.callTool(new CallToolRequest("get_health", Map.of()));
            String text = ((TextContent) result.content().get(0)).text();
            assertThat(text).contains("\"status\":\"DOWN\"")
                .contains("inventory (DOWN)")
                .contains("Connection refused");
        }
    }
}
