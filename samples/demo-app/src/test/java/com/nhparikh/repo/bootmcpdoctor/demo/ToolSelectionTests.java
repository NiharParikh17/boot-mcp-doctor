package com.nhparikh.repo.bootmcpdoctor.demo;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code tools.include} limits the tools to the ones listed, {@code tools.exclude} wins over it, and a name that
 * matches no tool is logged.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = { "boot-mcp-doctor.tools.include=get_health,get_environment,get_slow_endpoints,get_everything",
                "boot-mcp-doctor.tools.exclude=get_environment" })
class ToolSelectionTests extends McpAppTest {

    @Test
    void registersIncludedToolsThatAreNotExcluded(CapturedOutput output) {
        assertThat(toolNames(connect("/mcp"))).containsExactly("get_health", "get_slow_endpoints");
        assertThat(output).contains("with 2 read-only tools: get_health, get_slow_endpoints.")
            .contains("boot-mcp-doctor.tools names tools that will not be registered: [get_everything]");
    }
}
