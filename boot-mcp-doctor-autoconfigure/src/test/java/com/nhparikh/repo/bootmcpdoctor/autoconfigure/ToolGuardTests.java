package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import com.nhparikh.repo.bootmcpdoctor.model.HealthReport;
import com.nhparikh.repo.bootmcpdoctor.safety.ResponseSanitizer;
import com.nhparikh.repo.bootmcpdoctor.tools.HealthTools;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(OutputCaptureExtension.class)
class ToolGuardTests {

    private static final ResponseSanitizer SANITIZER = new ResponseSanitizer(List.of(), 50);

    private static final CallToolResult OK = CallToolResult.builder().addTextContent("{\"summary\":\"ok\"}").build();

    private final McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);

    @Test
    void logsSuccessfulCallsWithTheirArgumentsCallerAndClient(CapturedOutput output) {
        given(this.exchange.getClientInfo()).willReturn(new Implementation("Visual Studio Code", "1.105.1"));
        CallToolResult result = call(guard("ops", null), "get_metric", Map.of("name", "jvm.memory.used"),
                (exchange, request) -> OK);
        assertThat(result).isSameAs(OK);
        assertThat(output).containsPattern("INFO .*MCP tool call: get_metric\\(name=jvm\\.memory\\.used\\) by ops via "
                + "Visual Studio Code 1\\.105\\.1: ok in \\d+ ms");
    }

    @Test
    void logsRefusedCallsAsWarnings(CapturedOutput output) {
        CallToolResult result = call(guard("viewer", "set_log_level requires the ROLE_OPS_ADMIN authority."),
                "set_log_level", Map.of("logger", "ROOT", "level", "DEBUG"), (exchange, request) -> OK);
        assertThat(result.isError()).isTrue();
        assertThat(text(result)).isEqualTo("Not authorized: set_log_level requires the ROLE_OPS_ADMIN authority.");
        assertThat(output).containsPattern("WARN .*MCP tool call: set_log_level\\((logger=ROOT, level=DEBUG"
                + "|level=DEBUG, logger=ROOT)\\) by viewer: refused: set_log_level requires the ROLE_OPS_ADMIN "
                + "authority\\.");
    }

    @Test
    void logsFailuresWithTheirStackTraceAndReportsThemAsErrors(CapturedOutput output) {
        HealthEndpoint endpoint = mock(HealthEndpoint.class);
        given(endpoint.health()).willThrow(new IllegalStateException("registry closed, password=hunter2"));
        HealthTools tools = new HealthTools(provider(endpoint), SANITIZER);
        CallToolResult result = call(guard("ops", null), "get_health", Map.of(), (exchange, request) -> {
            HealthReport report = tools.getHealth();
            return CallToolResult.builder().addTextContent(report.summary()).build();
        });
        assertThat(result.isError()).isTrue();
        assertThat(text(result))
            .isEqualTo("get_health failed with IllegalStateException. The application's log has the details.");
        assertThat(output)
            .containsPattern(
                    "WARN .*MCP tool call: get_health\\(\\) by ops: failed with IllegalStateException in \\d+ ms")
            .contains("java.lang.IllegalStateException: registry closed")
            .contains("at com.nhparikh.repo.bootmcpdoctor.tools.HealthTools");
    }

    @Test
    void reportsFailuresOutsideTheToolsWork(CapturedOutput output) {
        CallToolResult result = call(guard("ops", null), "get_health", Map.of(), (exchange, request) -> {
            throw new IllegalArgumentException("could not write jdbc:postgresql://u:secret@db/orders");
        });
        assertThat(result.isError()).isTrue();
        assertThat(text(result))
            .isEqualTo("get_health failed with IllegalArgumentException. The application's log has the details.");
        assertThat(output).contains("by ops: failed with IllegalArgumentException in ");
    }

    // The MCP server library answers some failures itself, such as an argument it can't convert.
    @Test
    void logsErrorResultsFromTheServerLibrary(CapturedOutput output) {
        CallToolResult error = CallToolResult.builder()
            .isError(true)
            .addTextContent("Error invoking method: For input string: \"abc\"")
            .build();
        CallToolResult result = call(guard("ops", null), "get_slow_endpoints", Map.of("limit", "abc"),
                (exchange, request) -> error);
        assertThat(result).isSameAs(error);
        assertThat(output).containsPattern("WARN .*MCP tool call: get_slow_endpoints\\(limit=abc\\) by ops: failed in "
                + "\\d+ ms: Error invoking method: For input string: \"abc\"");
    }

    @Test
    void masksSensitiveArgumentsAndKeepsThemOnOneLine(CapturedOutput output) {
        call(guard("ops", null), "get_environment",
                Map.of("password", "hunter2", "prefix", "jdbc:postgresql://orders:s3cret@db/orders\nforged line"),
                (exchange, request) -> OK);
        assertThat(output).contains("password=******")
            .contains("prefix=jdbc:postgresql://orders:******@db/orders?forged line")
            .doesNotContain("hunter2", "s3cret");
    }

    @Test
    void namesUnauthenticatedCallers(CapturedOutput output) {
        ToolGuard guard = new ToolGuard(null, new ToolCallLog(SANITIZER));
        call(guard, "get_health", Map.of(), (exchange, request) -> OK);
        assertThat(output).containsPattern("MCP tool call: get_health\\(\\) by an unauthenticated caller: ok in");
    }

    @Test
    void guardsStatelessCallsWithoutAClient(CapturedOutput output) {
        McpStatelessServerFeatures.SyncToolSpecification spec = McpStatelessServerFeatures.SyncToolSpecification
            .builder()
            .tool(tool("get_health"))
            .callHandler((context, request) -> OK)
            .build();
        CallToolResult result = guard("ops", null).guard(spec)
            .callHandler()
            .apply(null, new CallToolRequest("get_health", Map.of()));
        assertThat(result).isSameAs(OK);
        assertThat(output).containsPattern("MCP tool call: get_health\\(\\) by ops: ok in \\d+ ms");
    }

    private CallToolResult call(ToolGuard guard, String name, Map<String, Object> arguments,
            BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> handler) {
        SyncToolSpecification spec = SyncToolSpecification.builder().tool(tool(name)).callHandler(handler).build();
        return guard.guard(spec).callHandler().apply(this.exchange, new CallToolRequest(name, arguments));
    }

    private static ToolGuard guard(String caller, String denyReason) {
        ToolAuthorizer authorizer = new ToolAuthorizer() {

            @Override
            public String denyReason(String tool, boolean write) {
                return denyReason;
            }

            @Override
            public String caller() {
                return caller;
            }
        };
        return new ToolGuard(authorizer, new ToolCallLog(SANITIZER));
    }

    private static Tool tool(String name) {
        return Tool.builder(name).annotations(new ToolAnnotations(name, true, false, true, false, null)).build();
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().get(0)).text();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<HealthEndpoint> provider(HealthEndpoint endpoint) {
        ObjectProvider<HealthEndpoint> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(endpoint);
        return provider;
    }
}
