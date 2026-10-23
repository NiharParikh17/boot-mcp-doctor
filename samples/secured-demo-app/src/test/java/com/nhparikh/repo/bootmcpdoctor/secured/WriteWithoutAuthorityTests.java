package com.nhparikh.repo.bootmcpdoctor.secured;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code read-only=false} alone is not enough: until {@code security.write-authority} names who may change the
 * application, {@code set_log_level} is not registered.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = { "boot-mcp-doctor.read-only=false", "boot-mcp-doctor.security.write-authority=" })
class WriteWithoutAuthorityTests extends McpAppTest {

    @Test
    void doesNotRegisterSetLogLevel(CapturedOutput output) {
        assertThat(toolNames(connect("/mcp", basic("admin")))).doesNotContain("set_log_level").hasSize(6);
        assertThat(output).contains("Tools that change state, such as set_log_level, were not registered: set "
                + "boot-mcp-doctor.security.write-authority to the authority callers need for them.");
    }
}
