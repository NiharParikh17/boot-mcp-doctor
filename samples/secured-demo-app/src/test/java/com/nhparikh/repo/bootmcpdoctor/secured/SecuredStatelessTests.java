package com.nhparikh.repo.bootmcpdoctor.secured;

import java.util.Map;

import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Without sessions, every request is authenticated on its own, and tool calls are still checked against the caller.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "spring.ai.mcp.server.protocol=STATELESS")
class SecuredStatelessTests extends McpAppTest {

    @Test
    void checksEveryCallAgainstTheCaller(CapturedOutput output) {
        McpSyncClient ops = connect("/mcp", basic("ops"));
        assertThat(call(ops, "get_health", Map.of())).contains("\"status\":\"UP\"");
        assertThat(refused(connect("/mcp", basic("viewer")), "get_health", Map.of()))
            .isEqualTo("Not authorized: get_health requires the ROLE_OPS authority.");
        assertThat(output).contains("(stateless Streamable HTTP)")
            .containsPattern("MCP client connected: " + CLIENT + " \\(protocol [0-9-]+, user ops, from ");
    }
}
