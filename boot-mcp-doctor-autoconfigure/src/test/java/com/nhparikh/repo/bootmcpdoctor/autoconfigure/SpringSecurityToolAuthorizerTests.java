package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

import com.nhparikh.repo.bootmcpdoctor.config.BootMcpDoctorProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class SpringSecurityToolAuthorizerTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deniesWriteToolsWhenNoWriteAuthorityIsConfigured() {
        authenticate("ROLE_OPS_ADMIN");
        SpringSecurityToolAuthorizer authorizer = new SpringSecurityToolAuthorizer(
                new BootMcpDoctorProperties.Security(true, null, " "));
        assertThat(authorizer.denyReason("set_log_level", true))
            .isEqualTo("set_log_level requires boot-mcp-doctor.security.write-authority to be set.");
    }

    @Test
    void deniesUnauthenticatedTokens() {
        SecurityContextHolder.getContext()
            .setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated("ops", "secret"));
        SpringSecurityToolAuthorizer authorizer = new SpringSecurityToolAuthorizer(
                new BootMcpDoctorProperties.Security(false, null, null));
        assertThat(authorizer.denyReason("get_health", false))
            .isEqualTo("get_health requires an authenticated caller.");
    }

    @Test
    void namesTheAuthenticatedCaller() {
        authenticate("ROLE_OPS");
        assertThat(authorizer().caller()).isEqualTo("ops");
    }

    @Test
    void namesNoCallerUnlessAuthenticated() {
        assertThat(authorizer().caller()).isNull();
        SecurityContextHolder.getContext()
            .setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
                    AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(authorizer().caller()).isNull();
        SecurityContextHolder.getContext()
            .setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated("ops", "secret"));
        assertThat(authorizer().caller()).isNull();
    }

    private static SpringSecurityToolAuthorizer authorizer() {
        return new SpringSecurityToolAuthorizer(new BootMcpDoctorProperties.Security(false, null, null));
    }

    private static void authenticate(String... authorities) {
        SecurityContextHolder.getContext()
            .setAuthentication(UsernamePasswordAuthenticationToken.authenticated("ops", null,
                    AuthorityUtils.createAuthorityList(authorities)));
    }
}
