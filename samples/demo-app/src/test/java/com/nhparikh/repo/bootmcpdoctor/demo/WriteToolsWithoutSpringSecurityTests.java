package com.nhparikh.repo.bootmcpdoctor.demo;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code read-only=false} and a write authority are not enough without Spring Security: nothing could check that
 * authority, so {@code set_log_level} is never registered.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = { "boot-mcp-doctor.read-only=false", "boot-mcp-doctor.security.write-authority=ROLE_OPS_ADMIN" })
class WriteToolsWithoutSpringSecurityTests extends McpAppTest {

    @Test
    void neverRegistersSetLogLevel(CapturedOutput output) {
        assertThat(toolNames(connect("/mcp"))).doesNotContain("set_log_level").hasSize(6);
        assertThat(output)
            .contains("Tools that change state, such as set_log_level, were not registered: they need "
                    + "Spring Security on the classpath and boot-mcp-doctor.security.write-authority.")
            .contains("with 6 read-only tools: " + READ_TOOLS + ".");
    }
}
