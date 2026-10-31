package com.nhparikh.repo.bootmcpdoctor.secured;

import java.util.Map;
import java.util.regex.Pattern;

import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The secured demo as it ships: HTTP Basic users, read tools for {@code ROLE_OPS}, and {@code set_log_level} for
 * {@code ROLE_OPS_ADMIN}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SecuredDemoApplicationTests extends McpAppTest {

    @Test
    void logsTheToolsAndWhoMayCallThem(CapturedOutput output) {
        connect("/mcp", basic("ops"));
        assertThat(output)
            .contains("boot-mcp-doctor is running at /mcp on port " + this.port + " (Streamable HTTP) with 7 tools: "
                    + READ_TOOLS + ", set_log_level. Read tools need the ROLE_OPS authority; set_log_level needs "
                    + "the ROLE_OPS_ADMIN authority.")
            .containsPattern("MCP client connected: " + CLIENT + " \\(protocol [0-9-]+, session [0-9a-f-]{8}, "
                    + "user ops, from [^)]+\\)")
            .doesNotContain("anyone who can reach the MCP endpoint");
    }

    @Test
    void turnsAwayCallersWithoutValidCredentialsAndDoesNotLogThem(CapturedOutput output) throws Exception {
        assertThat(initialize("/mcp", null).statusCode()).isEqualTo(401);
        assertThat(initialize("/mcp", basic("ops", "wrong")).statusCode()).isEqualTo(401);
        // The class's captured output includes its other tests, which connect as scenario-tests.
        assertThat(output).doesNotContain("MCP client connected: curl");
    }

    @Test
    void opsCanCallTheReadTools() {
        McpSyncClient client = connect("/mcp", basic("ops"));
        assertThat(toolNames(client)).hasSize(7);
        assertThat(call(client, "get_health", Map.of())).contains("\"status\":\"UP\"");
    }

    @Test
    void callersWithoutTheReadAuthorityAreRefused() {
        assertThat(refused(connect("/mcp", basic("viewer")), "get_health", Map.of()))
            .isEqualTo("Not authorized: get_health requires the ROLE_OPS authority.");
    }

    @Test
    void onlyAdminCanChangeLogLevels(CapturedOutput output) {
        Map<String, Object> debug = Map.of("logger", "com.nhparikh.repo.bootmcpdoctor.secured", "level", "DEBUG");
        assertThat(refused(connect("/mcp", basic("ops")), "set_log_level", debug))
            .isEqualTo("Not authorized: set_log_level requires the ROLE_OPS_ADMIN authority.");

        McpSyncClient admin = connect("/mcp", basic("admin"));
        assertThat(call(admin, "set_log_level", debug)).contains("\"changed\":true").contains("\"newLevel\":\"DEBUG\"");
        assertThat(call(admin, "get_loggers", Map.of("prefix", "com.nhparikh.repo.bootmcpdoctor.secured")))
            .contains("\"configuredLevel\":\"DEBUG\"");
        assertThat(call(admin, "set_log_level",
                Map.of("logger", "com.nhparikh.repo.bootmcpdoctor.secured", "level", "RESET")))
            .contains("\"newLevel\":null");

        String client = " via " + Pattern.quote(CLIENT) + ": ";
        String logger = Pattern.quote("com.nhparikh.repo.bootmcpdoctor.secured");
        assertThat(output)
            .containsPattern("WARN .*MCP tool call: set_log_level\\(.*\\) by ops" + client
                    + "refused: set_log_level requires the ROLE_OPS_ADMIN authority\\.")
            .containsPattern("INFO .*MCP tool call: set_log_level\\(.*level=DEBUG.*\\) by admin" + client
                    + "ok in \\d+ ms\\. Set " + logger + " from inherited to DEBUG")
            .containsPattern("by admin" + client + "ok in \\d+ ms\\. Set " + logger + " from DEBUG to inherited");
    }
}
