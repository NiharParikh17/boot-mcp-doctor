package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.nhparikh.repo.bootmcpdoctor.autoconfigure.ToolCallLog.Call;
import com.nhparikh.repo.bootmcpdoctor.tools.ToolCalls;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Wraps tool specifications so that every call is authorized before the tool runs, logged with who made it and how
 * it went, and answered with a clear message when it fails. A denied call returns an error result instead of data.
 */
final class ToolGuard {

    private final ToolAuthorizer authorizer;

    private final ToolCallLog log;

    /**
     * @param authorizer checks each call, or {@code null} when nothing authenticates callers; then only read tools
     * are registered, and calls are not checked
     * @param log logs each call
     */
    ToolGuard(ToolAuthorizer authorizer, ToolCallLog log) {
        this.authorizer = authorizer;
        this.log = log;
    }

    McpServerFeatures.SyncToolSpecification guard(McpServerFeatures.SyncToolSpecification spec) {
        String name = spec.tool().name();
        boolean write = isWrite(spec.tool());
        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(spec.tool())
            .callHandler((exchange, request) -> call(name, write, request, client(exchange),
                    () -> spec.callHandler().apply(exchange, request)))
            .build();
    }

    McpStatelessServerFeatures.SyncToolSpecification guard(McpStatelessServerFeatures.SyncToolSpecification spec) {
        String name = spec.tool().name();
        boolean write = isWrite(spec.tool());
        // Stateless requests carry no client information.
        return McpStatelessServerFeatures.SyncToolSpecification.builder()
            .tool(spec.tool())
            .callHandler((context, request) -> call(name, write, request, null,
                    () -> spec.callHandler().apply(context, request)))
            .build();
    }

    // Anything not explicitly marked read-only is treated as changing state.
    static boolean isWrite(Tool tool) {
        return tool.annotations() == null || !Boolean.TRUE.equals(tool.annotations().readOnlyHint());
    }

    private CallToolResult call(String name, boolean write, CallToolRequest request, String client,
            Supplier<CallToolResult> tool) {
        long start = System.nanoTime();
        Call call = new Call(name, request, (this.authorizer != null) ? this.authorizer.caller() : null, client);
        String reason = (this.authorizer != null) ? this.authorizer.denyReason(name, write) : null;
        if (reason != null) {
            this.log.refused(call, reason);
            return error("Not authorized: " + reason);
        }
        ToolCalls.start();
        CallToolResult result;
        try {
            result = tool.get();
        }
        catch (RuntimeException | LinkageError ex) {
            // Outside the tool's own work, such as while its result is written.
            ToolCalls.finish();
            this.log.failed(call, ex, millisSince(start));
            return error(ToolCalls.failureMessage(name, ex));
        }
        ToolCalls.Call recorded = ToolCalls.finish();
        long millis = millisSince(start);
        if (recorded.failure() != null) {
            this.log.failed(call, recorded.failure(), millis);
            return error(ToolCalls.failureMessage(name, recorded.failure()));
        }
        if (Boolean.TRUE.equals(result.isError())) {
            this.log.failed(call, text(result), millis);
            return result;
        }
        this.log.succeeded(call, recorded.note(), millis);
        return result;
    }

    private static String client(McpSyncServerExchange exchange) {
        Implementation client = (exchange != null) ? exchange.getClientInfo() : null;
        if (client == null || client.name() == null) {
            return null;
        }
        return (client.version() != null) ? client.name() + " " + client.version() : client.name();
    }

    private static String text(CallToolResult result) {
        return result.content()
            .stream()
            .filter(TextContent.class::isInstance)
            .map((content) -> ((TextContent) content).text())
            .findFirst()
            .orElse("no message");
    }

    private static long millisSince(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder().isError(true).addTextContent(message).build();
    }
}
