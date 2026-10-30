package com.nhparikh.repo.bootmcpdoctor.secured;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * boot-mcp-doctor's defaults with Spring Security: read-only, and any authenticated caller may call the read tools.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = { "boot-mcp-doctor.read-only=true",
        "boot-mcp-doctor.security.read-authority=", "boot-mcp-doctor.security.write-authority=" })
class AnyAuthenticatedCallerTests extends McpAppTest {

    @Test
    void anyAuthenticatedCallerMayCallTheReadTools(CapturedOutput output) {
        assertThat(call(connect("/mcp", basic("viewer")), "get_health", Map.of())).contains("\"status\":\"UP\"");
        assertThat(output).contains("with 6 read-only tools: " + READ_TOOLS
                + ". Tool calls need a caller authenticated by Spring Security.");
    }
}
