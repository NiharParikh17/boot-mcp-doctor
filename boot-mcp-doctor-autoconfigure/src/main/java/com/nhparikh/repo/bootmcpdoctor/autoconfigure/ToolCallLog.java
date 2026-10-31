package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

import java.util.Map;
import java.util.stream.Collectors;

import com.nhparikh.repo.bootmcpdoctor.safety.ResponseSanitizer;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Logs one line for every tool call: the tool and its arguments, who called it and from which client, and how it went.
 * Calls that succeed are logged at INFO, and calls that are refused or fail at WARN, failures with their stack trace.
 * Argument values are masked like tool responses, and kept on one line and short, since clients send them.
 */
final class ToolCallLog {

    static final String PREFIX = "MCP tool call: ";

    private static final Log logger = LogFactory.getLog(ToolCallLog.class);

    private final ResponseSanitizer sanitizer;

    ToolCallLog(ResponseSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    void succeeded(Call call, String note, long millis) {
        if (logger.isInfoEnabled()) {
            logger.info(describe(call) + ": ok in " + millis + " ms" + ((note != null) ? ". " + clean(note) : ""));
        }
    }

    void refused(Call call, String reason) {
        logger.warn(describe(call) + ": refused: " + reason);
    }

    void failed(Call call, Throwable failure, long millis) {
        logger.warn(describe(call) + ": failed with " + failure.getClass().getSimpleName() + " in " + millis + " ms",
                failure);
    }

    // The MCP server library turned the failure into an error result itself, such as for an argument it could not
    // convert.
    void failed(Call call, String error, long millis) {
        logger.warn(describe(call) + ": failed in " + millis + " ms: " + clean(error));
    }

    private String describe(Call call) {
        String caller = (call.caller() != null) ? clean(call.caller()) : "an unauthenticated caller";
        String client = (call.client() != null) ? " via " + clean(call.client()) : "";
        return PREFIX + call.tool() + "(" + arguments(call.request()) + ") by " + caller + client;
    }

    private String arguments(CallToolRequest request) {
        Map<String, Object> arguments = (request != null) ? request.arguments() : null;
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }
        return arguments.entrySet()
            .stream()
            .map((argument) -> clean(argument.getKey()) + "="
                    + clean(String.valueOf(this.sanitizer.sanitize(argument.getKey(), argument.getValue()))))
            .collect(Collectors.joining(", "));
    }

    private static String clean(String value) {
        return ConnectionLogFilter.clean(value);
    }

    /**
     * One tool call.
     *
     * @param tool the tool's name
     * @param request the request, with the arguments
     * @param caller the authenticated caller, or {@code null}
     * @param client the client's name and version, or {@code null} when they are not known
     */
    record Call(String tool, CallToolRequest request, String caller, String client) {
    }
}
