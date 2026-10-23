package com.nhparikh.repo.bootmcpdoctor.demo;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The raw HTTP responses that the guide's curl check describes.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class HttpResponsesTests extends McpAppTest {

    private static final String LIST_TOOLS = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";

    @Test
    void initializeReturnsASession() throws Exception {
        HttpResponse<String> response = post("/mcp", INITIALIZE, STREAMABLE_HEADERS);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Mcp-Session-Id")).isNotEmpty();
    }

    @Test
    void requestsMustAcceptJsonAndEventStreams() throws Exception {
        HttpResponse<String> response = post("/mcp", INITIALIZE, "Content-Type", "application/json");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Invalid Accept headers");
    }

    @Test
    void unknownSessionsAreNotFound() throws Exception {
        HttpResponse<String> response = post("/mcp", LIST_TOOLS, "Content-Type", "application/json", "Accept",
                "application/json, text/event-stream", "Mcp-Session-Id", "no-such-session");
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("Session not found");
    }
}
