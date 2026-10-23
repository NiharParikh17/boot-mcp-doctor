package com.nhparikh.repo.bootmcpdoctor.demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base for tests that boot the demo application on a random port, with the properties of the subclass's
 * {@code @SpringBootTest}, and talk to it the way an MCP client does.
 * <p>
 * Every test class gets its own application, which is stopped afterwards, so the captured output of a class holds
 * that application's startup log.
 */
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext
abstract class McpAppTest {

    static final String READ_TOOLS = "get_environment, get_health, get_loggers, get_metric, get_slow_endpoints, "
            + "list_metrics";

    static final String CLIENT = "scenario-tests 1.0.0";

    static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",\
            "capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}""";

    static final String[] STREAMABLE_HEADERS = { "Content-Type", "application/json", "Accept",
            "application/json, text/event-stream" };

    private final HttpClient http = HttpClient.newHttpClient();

    private final List<McpSyncClient> clients = new ArrayList<>();

    @LocalServerPort
    int port;

    @AfterEach
    void closeClients() {
        this.clients.forEach(McpSyncClient::closeGracefully);
    }

    String url(String path) {
        return "http://localhost:" + this.port + path;
    }

    /**
     * Connects over Streamable HTTP, the way most clients do.
     * @param endpoint the MCP endpoint, such as {@code /mcp}
     * @return an initialized client
     */
    McpSyncClient connect(String endpoint) {
        return initialize(HttpClientStreamableHttpTransport.builder(url("")).endpoint(endpoint).build());
    }

    /**
     * Connects over the older SSE transport.
     * @param sseEndpoint the SSE endpoint, such as {@code /sse}
     * @return an initialized client
     */
    McpSyncClient connectSse(String sseEndpoint) {
        return initialize(HttpClientSseClientTransport.builder(url("")).sseEndpoint(sseEndpoint).build());
    }

    private McpSyncClient initialize(McpClientTransport transport) {
        McpSyncClient client = McpClient.sync(transport)
            .clientInfo(new Implementation("scenario-tests", "1.0.0"))
            .requestTimeout(Duration.ofSeconds(30))
            .build();
        this.clients.add(client);
        client.initialize();
        return client;
    }

    static List<String> toolNames(McpSyncClient client) {
        return client.listTools().tools().stream().map(Tool::name).sorted().toList();
    }

    /**
     * Calls a tool that is expected to succeed.
     * @param client the client
     * @param tool the tool name
     * @param arguments the tool's arguments
     * @return the JSON text the tool returned
     */
    static String call(McpSyncClient client, String tool, Map<String, Object> arguments) {
        CallToolResult result = client.callTool(CallToolRequest.builder(tool).arguments(arguments).build());
        assertThat(result.isError()).as(tool + " error").isNotEqualTo(Boolean.TRUE);
        return ((TextContent) result.content().get(0)).text();
    }

    /**
     * Sends a raw HTTP POST, like the curl check in the guide.
     * @param path the request path
     * @param body the request body
     * @param headers header names and values
     * @return the response
     * @throws Exception if the request fails
     */
    HttpResponse<String> post(String path, String body, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url(path)))
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (headers.length > 0) {
            request.headers(headers);
        }
        return this.http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> get(String path) throws Exception {
        return this.http.send(HttpRequest.newBuilder(URI.create(url(path))).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
