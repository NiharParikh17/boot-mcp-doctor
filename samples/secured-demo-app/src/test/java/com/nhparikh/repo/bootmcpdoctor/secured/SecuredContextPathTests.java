package com.nhparikh.repo.bootmcpdoctor.secured;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Security matches paths below the context path, so the CSRF exemption for {@code /mcp} still applies when
 * clients call {@code /api/xyz/mcp}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/api/xyz")
class SecuredContextPathTests extends McpAppTest {

    @Test
    void servesTheEndpointBelowTheContextPath(CapturedOutput output) {
        assertThat(toolNames(connect("/api/xyz/mcp", basic("ops")))).hasSize(7);
        assertThat(output).contains("boot-mcp-doctor is running at /api/xyz/mcp on port " + this.port)
            .contains("user ops");
    }
}
