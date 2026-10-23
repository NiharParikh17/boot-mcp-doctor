package com.nhparikh.repo.bootmcpdoctor.secured;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every request needs an authenticated caller, the MCP endpoint included. Callers authenticate with HTTP Basic, or
 * with a JWT bearer token once a JWT decoder is configured, for example with
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<JwtDecoder> jwtDecoder)
            throws Exception {
        // MCP clients don't send CSRF tokens. /mcp/message is where SSE clients post their messages.
        http.csrf((csrf) -> csrf.ignoringRequestMatchers("/mcp", "/mcp/message"))
            .authorizeHttpRequests((requests) -> requests.anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults());
        if (jwtDecoder.getIfAvailable() != null) {
            http.oauth2ResourceServer((oauth2) -> oauth2.jwt(Customizer.withDefaults()));
        }
        return http.build();
    }

    // ops may call the read tools, admin may also change log levels, and viewer may call neither.
    @Bean
    UserDetailsService users(@Value("${demo.password}") String password) {
        String encoded = "{noop}" + password;
        return new InMemoryUserDetailsManager(User.withUsername("ops").password(encoded).roles("OPS").build(),
                User.withUsername("admin").password(encoded).roles("OPS", "OPS_ADMIN").build(),
                User.withUsername("viewer").password(encoded).roles("VIEWER").build());
    }
}
