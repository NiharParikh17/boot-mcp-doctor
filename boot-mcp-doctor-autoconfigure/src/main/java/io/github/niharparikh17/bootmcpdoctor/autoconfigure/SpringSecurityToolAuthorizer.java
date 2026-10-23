package io.github.niharparikh17.bootmcpdoctor.autoconfigure;

import io.github.niharparikh17.bootmcpdoctor.config.BootMcpDoctorProperties;

import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Checks tool calls against the caller that Spring Security authenticated for the current request. Tool calls run
 * on the request thread, so the caller is in the {@link SecurityContextHolder}. If it isn't, the call is denied.
 */
class SpringSecurityToolAuthorizer implements ToolAuthorizer {

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    private final BootMcpDoctorProperties.Security security;

    SpringSecurityToolAuthorizer(BootMcpDoctorProperties.Security security) {
        this.security = security;
    }

    @Override
    public String denyReason(String tool, boolean write) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !this.trustResolver.isAnonymous(authentication);
        if (write) {
            if (!this.security.requiresWriteAuthority()) {
                // Fail closed: write tools are only registered once a write authority is configured.
                return tool + " requires boot-mcp-doctor.security.write-authority to be set.";
            }
            return check(tool, authenticated, authentication, this.security.writeAuthority());
        }
        if (this.security.requiresReadAuthority()) {
            return check(tool, authenticated, authentication, this.security.readAuthority());
        }
        if (!authenticated && !this.security.allowUnauthenticated()) {
            return tool + " requires an authenticated caller.";
        }
        return null;
    }

    private static String check(String tool, boolean authenticated, Authentication authentication,
            String authority) {
        if (!authenticated) {
            return tool + " requires an authenticated caller.";
        }
        boolean granted = authentication.getAuthorities()
            .stream()
            .anyMatch((candidate) -> authority.equals(candidate.getAuthority()));
        return granted ? null : tool + " requires the " + authority + " authority.";
    }
}
