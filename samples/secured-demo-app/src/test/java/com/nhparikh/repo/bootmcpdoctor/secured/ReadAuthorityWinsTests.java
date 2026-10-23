package com.nhparikh.repo.bootmcpdoctor.secured;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Once {@code security.read-authority} is set, callers need it, even with {@code security.allow-unauthenticated=true}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "boot-mcp-doctor.security.allow-unauthenticated=true")
@Import(OpenEndpointTests.OpenMcpEndpoint.class)
class ReadAuthorityWinsTests extends McpAppTest {

    @Test
    void unauthenticatedCallersAreRefused(CapturedOutput output) {
        assertThat(refused(connect("/mcp", null), "get_health", Map.of()))
            .isEqualTo("Not authorized: get_health requires an authenticated caller.");
        assertThat(output).contains("Read tools need the ROLE_OPS authority;")
            .doesNotContain("anyone who can reach the MCP endpoint");
    }
}
