package com.nhparikh.repo.bootmcpdoctor.demo;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Without Spring Security, and without {@code security.allow-unauthenticated=true}, nothing authenticates callers, so
 * no tools are registered. The MCP endpoint still answers, with an empty tool list.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "boot-mcp-doctor.security.allow-unauthenticated=false")
class NoAuthenticationOptInTests extends McpAppTest {

    @Test
    void registersNoToolsAndSaysWhy(CapturedOutput output) {
        assertThat(toolNames(connect("/mcp"))).isEmpty();
        assertThat(output).contains("boot-mcp-doctor registered no tools because nothing authenticates callers")
            .contains("boot-mcp-doctor is running at /mcp on port " + this.port + " (Streamable HTTP) with no tools.")
            .doesNotContain("anyone who can reach the MCP endpoint");
    }
}
