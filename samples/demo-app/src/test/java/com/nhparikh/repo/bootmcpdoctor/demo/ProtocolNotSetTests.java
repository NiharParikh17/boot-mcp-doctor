package com.nhparikh.repo.bootmcpdoctor.demo;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The most common setup mistake: without {@code spring.ai.mcp.server.protocol}, the MCP server falls back to SSE, so
 * Streamable HTTP clients get HTTP 404 from {@code /mcp}. The startup line shows it. {@code no-protocol.yml} is the
 * demo's configuration without the protocol.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        // The SSE stream stays open, so don't let graceful shutdown wait 30 seconds for it after the test.
        properties = { "spring.config.name=no-protocol", "server.shutdown=immediate" })
class ProtocolNotSetTests extends McpAppTest {

    @Test
    void fallsBackToSse(CapturedOutput output) throws Exception {
        assertThat(output).contains("boot-mcp-doctor is running at /sse on port " + this.port + " (SSE)");
        assertThat(post("/mcp", INITIALIZE, STREAMABLE_HEADERS).statusCode()).isEqualTo(404);
        assertThat(toolNames(connectSse("/sse"))).hasSize(6);
    }
}
