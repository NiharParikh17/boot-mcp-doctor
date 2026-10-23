package com.nhparikh.repo.bootmcpdoctor.demo;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code management.endpoints.access.max-permitted=none} turns off every Actuator endpoint, and with them every tool.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "management.endpoints.access.max-permitted=none")
class MaxPermittedAccessTests extends McpAppTest {

    @Test
    void registersNoTools(CapturedOutput output) {
        assertThat(toolNames(connect("/mcp"))).isEmpty();
        assertThat(output)
            .contains("boot-mcp-doctor is running at /mcp on port " + this.port + " (Streamable HTTP) with no tools.");
    }
}
