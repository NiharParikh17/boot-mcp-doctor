package com.nhparikh.repo.bootmcpdoctor.secured;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What happens when {@code /mcp} is not exempt from CSRF protection: MCP clients send no CSRF token, so a POST with
 * correct HTTP Basic credentials is rejected with HTTP 401, exactly like a wrong password. Bearer tokens are not
 * affected, because Spring Security skips CSRF checks for requests that carry one.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestJwts.Decoder.class)
class CsrfTests extends McpAppTest {

    @Test
    void basicCredentialsAreRejectedWithoutACsrfExemption() throws Exception {
        assertThat(initialize("/mcp", basic("ops")).statusCode()).isEqualTo(401);
        assertThat(get("/orders", basic("ops")).statusCode()).isEqualTo(200);
    }

    @Test
    void bearerTokensDontNeedACsrfExemption() throws Exception {
        assertThat(initialize("/mcp", bearer(TestJwts.token("ops-client", "ops.read"))).statusCode()).isEqualTo(200);
    }

    /**
     * Protects {@code /mcp} without exempting it from CSRF, ahead of the application's own filter chain.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class McpEndpointWithCsrf {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        SecurityFilterChain mcpEndpointWithCsrf(HttpSecurity http) throws Exception {
            return http.securityMatcher("/mcp")
                .authorizeHttpRequests((requests) -> requests.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .oauth2ResourceServer((oauth2) -> oauth2.jwt(Customizer.withDefaults()))
                .build();
        }
    }
}
