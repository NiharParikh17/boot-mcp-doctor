package com.nhparikh.repo.bootmcpdoctor.secured;

import java.util.Map;

import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Defense in depth: even if the MCP endpoint is accidentally left open in Spring Security, boot-mcp-doctor still
 * refuses tool calls from unauthenticated callers.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "boot-mcp-doctor.security.read-authority=")
class OpenEndpointTests extends McpAppTest {

    @Test
    void refusesUnauthenticatedToolCalls() {
        McpSyncClient anonymous = connect("/mcp", null);
        assertThat(toolNames(anonymous)).hasSize(7);
        assertThat(refused(anonymous, "get_health", Map.of()))
            .isEqualTo("Not authorized: get_health requires an authenticated caller.");
    }

    /**
     * A filter chain that lets anyone reach {@code /mcp}, ahead of the application's own.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class OpenMcpEndpoint {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        SecurityFilterChain openMcpEndpoint(HttpSecurity http) throws Exception {
            return http.securityMatcher("/mcp")
                .csrf((csrf) -> csrf.disable())
                .authorizeHttpRequests((requests) -> requests.anyRequest().permitAll())
                .build();
        }
    }
}
