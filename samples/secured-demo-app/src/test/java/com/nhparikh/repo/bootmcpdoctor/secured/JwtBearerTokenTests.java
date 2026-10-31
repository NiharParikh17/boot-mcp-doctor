package com.nhparikh.repo.bootmcpdoctor.secured;

import java.net.http.HttpResponse;
import java.util.Map;

import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An OAuth2 resource server: callers send JWT access tokens, and their scopes become {@code SCOPE_<scope>} authorities.
 * {@link TestJwts} plays the identity provider.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = { "boot-mcp-doctor.security.read-authority=SCOPE_ops.read",
                "boot-mcp-doctor.security.write-authority=SCOPE_ops.write" })
@Import(TestJwts.Decoder.class)
class JwtBearerTokenTests extends McpAppTest {

    @Test
    void tokensWithTheReadScopeMayCallTheReadTools(CapturedOutput output) {
        McpSyncClient client = connect("/mcp", bearer(TestJwts.token("ops-client", "ops.read")));
        assertThat(call(client, "get_health", Map.of())).contains("\"status\":\"UP\"");
        assertThat(output)
            .contains("Read tools need the SCOPE_ops.read authority; set_log_level needs the "
                    + "SCOPE_ops.write authority.")
            .containsPattern("MCP client connected: " + CLIENT + " \\([^)]*user ops-client, from ");
    }

    @Test
    void tokensWithoutTheReadScopeAreRefused() {
        assertThat(refused(connect("/mcp", bearer(TestJwts.token("ops-client"))), "get_health", Map.of()))
            .isEqualTo("Not authorized: get_health requires the SCOPE_ops.read authority.");
    }

    @Test
    void setLogLevelOnlyChecksTheWriteScope() {
        McpSyncClient client = connect("/mcp", bearer(TestJwts.token("admin-client", "ops.write")));
        assertThat(call(client, "set_log_level",
                Map.of("logger", "com.nhparikh.repo.bootmcpdoctor.secured", "level", "INFO")))
            .contains("\"changed\":true");
        assertThat(refused(client, "get_health", Map.of()))
            .isEqualTo("Not authorized: get_health requires the SCOPE_ops.read authority.");
    }

    @Test
    void expiredTokensAreRejectedWithTheReason() throws Exception {
        HttpResponse<String> response = initialize("/mcp", bearer(TestJwts.expiredToken("ops-client", "ops.read")));
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate"))
            .hasValueSatisfying((challenge) -> assertThat(challenge).contains("invalid_token").contains("Jwt expired"));
    }

    @Test
    void tokensFromAnotherIssuerAreRejected() throws Exception {
        HttpResponse<String> response = initialize("/mcp",
                bearer(TestJwts.tokenSignedByAnotherKey("ops-client", "ops.read")));
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate"))
            .hasValueSatisfying((challenge) -> assertThat(challenge).contains("invalid_token"));
    }
}
