package com.nhparikh.repo.bootmcpdoctor.demo;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With the MCP server turned off, there is no endpoint at all, and the startup log warns that the tools are not served.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "spring.ai.mcp.server.enabled=false")
class McpServerDisabledTests extends McpAppTest {

    @Test
    void warnsThatTheToolsAreNotServed(CapturedOutput output) throws Exception {
        assertThat(post("/mcp", INITIALIZE, STREAMABLE_HEADERS).statusCode()).isEqualTo(404);
        assertThat(output).contains(
                "boot-mcp-doctor is enabled, but its tools are not served: no synchronous MCP server is running.");
    }
}
