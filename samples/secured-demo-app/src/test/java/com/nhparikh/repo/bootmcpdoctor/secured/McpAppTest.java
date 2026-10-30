package com.nhparikh.repo.bootmcpdoctor.secured;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base for tests that boot the secured demo application on a random port, with the properties of the subclass's
 * {@code @SpringBootTest}, and talk to it the way an MCP client does, sending an {@code Authorization} header.
 * <p>
 * Every test class gets its own application, which is stopped afterwards, so the captured output of a class holds that
 * application's startup log.
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

    private final HttpClient http = HttpClient.newHttpClient();

    private final List<McpSyncClient> clients = new ArrayList<>();

    @LocalServerPort
    int port;

    @Value("${demo.password}")
    String password;

    @AfterEach
    void closeClients() {
        this.clients.forEach(McpSyncClient::closeGracefully);
    }

    String url(String path) {
        return "http://localhost:" + this.port + path;
    }

    String basic(String user) {
        return basic(user, this.password);
    }

    static String basic(String user, String password) {
        String credentials = user + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    static String bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * Connects over Streamable HTTP.
     * @param endpoint the MCP endpoint, such as {@code /mcp}
     * @param authorization the {@code Authorization} header, or {@code null} for none
     * @return an initialized client
     */
    McpSyncClient connect(String endpoint, String authorization) {
        return initialize(HttpClientStreamableHttpTransport.builder(url(""))
            .endpoint(endpoint)
            .requestBuilder(request(authorization))
            .build());
    }

    /**
     * Connects over the older SSE transport.
     * @param sseEndpoint the SSE endpoint, such as {@code /sse}
     * @param authorization the {@code Authorization} header, or {@code null} for none
     * @return an initialized client
     */
    McpSyncClient connectSse(String sseEndpoint, String authorization) {
        return initialize(HttpClientSseClientTransport.builder(url(""))
            .sseEndpoint(sseEndpoint)
            .requestBuilder(request(authorization))
            .build());
    }

    private static HttpRequest.Builder request(String authorization) {
        HttpRequest.Builder request = HttpRequest.newBuilder();
        return (authorization != null) ? request.header("Authorization", authorization) : request;
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
     * Calls a tool that boot-mcp-doctor is expected to refuse.
     * @param client the client
     * @param tool the tool name
     * @param arguments the tool's arguments
     * @return the error message the tool returned
     */
    static String refused(McpSyncClient client, String tool, Map<String, Object> arguments) {
        CallToolResult result = client.callTool(CallToolRequest.builder(tool).arguments(arguments).build());
        assertThat(result.isError()).as(tool + " error").isTrue();
        return ((TextContent) result.content().get(0)).text();
    }

    /**
     * Sends the {@code initialize} request a client sends first, like the curl check in the guide.
     * @param path the MCP endpoint
     * @param authorization the {@code Authorization} header, or {@code null} for none
     * @return the response
     * @throws Exception if the request fails
     */
    HttpResponse<String> initialize(String path, String authorization) throws Exception {
        HttpRequest.Builder request = request(authorization).uri(URI.create(url(path)))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(INITIALIZE));
        return this.http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> get(String path, String authorization) throws Exception {
        return this.http.send(request(authorization).uri(URI.create(url(path))).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
