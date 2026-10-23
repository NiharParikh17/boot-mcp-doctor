package com.nhparikh.repo.bootmcpdoctor.demo;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Setting the connection log's logger to WARN turns off the connection lines and leaves the startup line.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "logging.level.com.nhparikh.repo.bootmcpdoctor.autoconfigure.ConnectionLogFilter=WARN")
class ConnectionLogOffTests extends McpAppTest {

    @Test
    void logsNoConnections(CapturedOutput output) {
        assertThat(toolNames(connect("/mcp"))).hasSize(6);
        assertThat(output).contains("boot-mcp-doctor is running at /mcp").doesNotContain("MCP client connected");
    }
}
