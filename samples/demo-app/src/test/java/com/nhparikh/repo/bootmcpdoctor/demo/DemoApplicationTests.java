package com.nhparikh.repo.bootmcpdoctor.demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "demo.checkout.delay=100ms")
class DemoApplicationTests {

    @Value("${local.server.port}")
    private int port;

    private McpSyncClient client;

    @BeforeEach
    void connect() {
        this.client = McpClient.sync(HttpClientStreamableHttpTransport.builder(baseUrl()).build()).build();
        this.client.initialize();
    }

    @AfterEach
    void disconnect() {
        this.client.close();
    }

    @Test
    void registersReadOnlyTools() {
        assertThat(this.client.listTools().tools()).extracting(Tool::name)
            .containsExactlyInAnyOrder("get_health", "list_metrics", "get_metric", "get_slow_endpoints",
                    "get_environment", "get_loggers");
    }

    @Test
    void getHealthReportsInventoryDown() {
        assertThat(call("get_health", Map.of())).contains("\"status\":\"DOWN\"")
            .contains("inventory (DOWN)")
            .contains("Connection refused");
    }

    @Test
    void getSlowEndpointsRanksCheckoutFirst() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        http.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/orders")).build(),
                HttpResponse.BodyHandlers.discarding());
        http.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/checkout"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build(), HttpResponse.BodyHandlers.discarding());

        assertThat(call("get_slow_endpoints", Map.of())).contains("Slowest of 2 endpoints: POST /checkout")
            .contains("\"uri\":\"/orders\"")
            .doesNotContain("/mcp")
            .doesNotContain("UNKNOWN");
    }

    @Test
    void getEnvironmentShowsWhereAPropertyIsSet() {
        assertThat(call("get_environment", Map.of("prefix", "boot-mcp-doctor")))
            .contains("\"name\":\"boot-mcp-doctor.enabled\",\"value\":true")
            .contains("class path resource [application.yml] - ");
    }

    @Test
    void getMetricReadsJvmMemory() {
        assertThat(call("get_metric", Map.of("name", "jvm.memory.used", "tags", List.of("area:heap"))))
            .contains("\"found\":true")
            .contains("\"unit\":\"MB\"");
    }

    @Test
    void getLoggersReturnsRoot() {
        assertThat(call("get_loggers", Map.of())).contains("\"name\":\"ROOT\"");
    }

    private String call(String tool, Map<String, Object> arguments) {
        CallToolResult result = this.client.callTool(CallToolRequest.builder(tool).arguments(arguments).build());
        assertThat(result.isError()).as(tool + " error").isNotEqualTo(Boolean.TRUE);
        return ((TextContent) result.content().get(0)).text();
    }

    private String baseUrl() {
        return "http://localhost:" + this.port;
    }
}
