package com.nhparikh.repo.bootmcpdoctor.demo;

import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When a tool fails unexpectedly, here because the application's own {@link SanitizingFunction} throws, the client gets
 * a clear message without the exception's own message, and the application's log gets the call, with the stack trace.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(ToolFailureTests.FailingSanitizer.class)
class ToolFailureTests extends McpAppTest {

    @Test
    void reportsTheFailureWithoutItsMessageAndLogsIt(CapturedOutput output) {
        assertThat(failed(connect("/mcp"), "get_environment", Map.of("prefix", "server")))
            .isEqualTo("get_environment failed with IllegalStateException. The application's log has the details.");
        assertThat(output)
            .containsPattern("WARN .*MCP tool call: get_environment\\(prefix=server\\) by an unauthenticated caller "
                    + "via " + Pattern.quote(CLIENT) + ": failed with IllegalStateException in \\d+ ms")
            .contains("java.lang.IllegalStateException: vault at https://vault.internal is sealed");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingSanitizer {

        @Bean
        SanitizingFunction failingSanitizer() {
            return (data) -> {
                throw new IllegalStateException("vault at https://vault.internal is sealed");
            };
        }
    }
}
