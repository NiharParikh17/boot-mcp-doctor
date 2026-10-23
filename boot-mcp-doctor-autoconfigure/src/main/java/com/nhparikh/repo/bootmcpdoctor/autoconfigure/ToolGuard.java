package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Wraps tool specifications so that every call is authorized before the tool runs. A denied call returns an error
 * result instead of data.
 */
final class ToolGuard {

    private final ToolAuthorizer authorizer;

    /**
     * @param authorizer checks each call, or {@code null} when nothing authenticates callers; then only read tools
     * are registered, and calls are not checked
     */
    ToolGuard(ToolAuthorizer authorizer) {
        this.authorizer = authorizer;
    }

    McpServerFeatures.SyncToolSpecification guard(McpServerFeatures.SyncToolSpecification spec) {
        if (this.authorizer == null) {
            return spec;
        }
        String name = spec.tool().name();
        boolean write = isWrite(spec.tool());
        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(spec.tool())
            .callHandler((exchange, request) -> {
                String reason = this.authorizer.denyReason(name, write);
                return (reason != null) ? denied(reason) : spec.callHandler().apply(exchange, request);
            })
            .build();
    }

    McpStatelessServerFeatures.SyncToolSpecification guard(McpStatelessServerFeatures.SyncToolSpecification spec) {
        if (this.authorizer == null) {
            return spec;
        }
        String name = spec.tool().name();
        boolean write = isWrite(spec.tool());
        return McpStatelessServerFeatures.SyncToolSpecification.builder()
            .tool(spec.tool())
            .callHandler((context, request) -> {
                String reason = this.authorizer.denyReason(name, write);
                return (reason != null) ? denied(reason) : spec.callHandler().apply(context, request);
            })
            .build();
    }

    // Anything not explicitly marked read-only is treated as changing state.
    static boolean isWrite(Tool tool) {
        return tool.annotations() == null || !Boolean.TRUE.equals(tool.annotations().readOnlyHint());
    }

    private static CallToolResult denied(String reason) {
        return CallToolResult.builder().isError(true).addTextContent("Not authorized: " + reason).build();
    }
}
