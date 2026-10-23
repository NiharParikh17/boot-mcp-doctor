package com.nhparikh.repo.bootmcpdoctor.demo;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With {@code boot-mcp-doctor.enabled=false}, boot-mcp-doctor registers nothing and logs nothing. The MCP server
 * itself still runs, without tools.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "boot-mcp-doctor.enabled=false")
class DisabledTests extends McpAppTest {

    @Test
    void registersNothing(CapturedOutput output) {
        assertThat(toolNames(connect("/mcp"))).isEmpty();
        assertThat(output).doesNotContain("boot-mcp-doctor is running").doesNotContain("MCP client connected");
    }
}
