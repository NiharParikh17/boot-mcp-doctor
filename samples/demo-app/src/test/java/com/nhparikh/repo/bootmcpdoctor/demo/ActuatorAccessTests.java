package com.nhparikh.repo.bootmcpdoctor.demo;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Actuator's access settings apply to the tools too: {@code access=none} on an endpoint removes the tools that read
 * it.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = { "management.endpoint.env.access=none", "management.endpoint.metrics.access=none" })
class ActuatorAccessTests extends McpAppTest {

    @Test
    void leavesOutToolsWhoseEndpointAccessIsNone(CapturedOutput output) {
        assertThat(toolNames(connect("/mcp"))).containsExactly("get_health", "get_loggers");
        assertThat(output).contains("with 2 read-only tools: get_health, get_loggers.");
    }
}
