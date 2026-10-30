package com.nhparikh.repo.bootmcpdoctor.secured;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An authorization rule in the filter chain itself, such as a role required for {@code /mcp}, turns other authenticated
 * callers away with HTTP 403 before boot-mcp-doctor sees the request.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AuthorizationRuleTests extends McpAppTest {

    @Test
    void callersWithoutTheRoleGetForbidden() throws Exception {
        assertThat(initialize("/mcp", basic("viewer")).statusCode()).isEqualTo(403);
        assertThat(initialize("/mcp", basic("ops")).statusCode()).isEqualTo(200);
    }

    /**
     * Requires {@code ROLE_OPS} for {@code /mcp}, ahead of the application's own filter chain.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class OpsOnlyMcpEndpoint {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        SecurityFilterChain opsOnlyMcpEndpoint(HttpSecurity http) throws Exception {
            return http.securityMatcher("/mcp")
                .csrf((csrf) -> csrf.disable())
                .authorizeHttpRequests((requests) -> requests.anyRequest().hasRole("OPS"))
                .httpBasic(Customizer.withDefaults())
                .build();
        }
    }
}
