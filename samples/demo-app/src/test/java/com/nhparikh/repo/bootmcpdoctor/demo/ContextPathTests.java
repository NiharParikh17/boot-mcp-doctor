package com.nhparikh.repo.bootmcpdoctor.demo;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A context path comes before the MCP endpoint, and the startup line shows the full path to give a client.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/api/xyz")
class ContextPathTests extends McpAppTest {

    @Test
    void servesTheEndpointBelowTheContextPath(CapturedOutput output) throws Exception {
        assertThat(toolNames(connect("/api/xyz/mcp"))).hasSize(6);
        assertThat(output).contains("boot-mcp-doctor is running at /api/xyz/mcp on port " + this.port)
            .contains("MCP client connected: " + CLIENT);
        assertThat(post("/mcp", INITIALIZE, STREAMABLE_HEADERS).statusCode()).isEqualTo(404);
    }
}
