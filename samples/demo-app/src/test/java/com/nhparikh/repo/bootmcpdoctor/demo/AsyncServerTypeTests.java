package com.nhparikh.repo.bootmcpdoctor.demo;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * boot-mcp-doctor's tools are synchronous. With {@code spring.ai.mcp.server.type=ASYNC} they are not served, and the
 * startup log says so instead of claiming the tools are running.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "spring.ai.mcp.server.type=ASYNC")
class AsyncServerTypeTests extends McpAppTest {

    @Test
    void warnsThatTheToolsAreNotServed(CapturedOutput output) {
        assertThat(toolNames(connect("/mcp"))).isEmpty();
        assertThat(output)
            .contains("boot-mcp-doctor is enabled, but its tools are not served: no synchronous MCP server is running.")
            .doesNotContain("boot-mcp-doctor is running");
    }
}
