package com.nhparikh.repo.bootmcpdoctor.demo;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With {@code boot-mcp-doctor.enabled=false}, boot-mcp-doctor registers nothing, and only logs that it supports the
 * application but is turned off. The MCP server itself still runs, without tools.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "boot-mcp-doctor.enabled=false")
class DisabledTests extends McpAppTest {

    @Test
    void registersNothing(CapturedOutput output) {
        assertThat(toolNames(connect("/mcp"))).isEmpty();
        assertThat(output).doesNotContain("boot-mcp-doctor is running").doesNotContain("MCP client connected");
    }

    @Test
    void logsThatItIsTurnedOff(CapturedOutput output) {
        assertThat(output).containsPattern("boot-mcp-doctor (\\S+ )?supports this application, Spring Boot \\S+ "
                + "with Spring AI \\S+, but is turned off\\. Set boot-mcp-doctor\\.enabled=true to turn it on\\.");
    }
}
