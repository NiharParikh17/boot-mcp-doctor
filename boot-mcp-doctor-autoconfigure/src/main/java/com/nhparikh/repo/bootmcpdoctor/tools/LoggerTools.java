package com.nhparikh.repo.bootmcpdoctor.tools;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.nhparikh.repo.bootmcpdoctor.model.LoggersReport;
import com.nhparikh.repo.bootmcpdoctor.model.LoggersReport.LoggerGroupLevel;
import com.nhparikh.repo.bootmcpdoctor.model.LoggersReport.LoggerLevel;
import com.nhparikh.repo.bootmcpdoctor.safety.ResponseSanitizer;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.logging.LoggersEndpoint;
import org.springframework.boot.actuate.logging.LoggersEndpoint.GroupLoggerLevelsDescriptor;
import org.springframework.boot.actuate.logging.LoggersEndpoint.LoggerLevelsDescriptor;
import org.springframework.boot.actuate.logging.LoggersEndpoint.LoggersDescriptor;
import org.springframework.boot.actuate.logging.LoggersEndpoint.SingleLoggerLevelsDescriptor;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerGroups;
import org.springframework.boot.logging.LoggingSystem;

/**
 * MCP tools that report the application's log levels.
 */
public class LoggerTools {

    static final String UNAVAILABLE = "Logging system not available.";

    private static final String ROOT = LoggingSystem.ROOT_LOGGER_NAME;

    private static final Comparator<LoggerLevel> ROOT_FIRST = Comparator
        .comparing((LoggerLevel logger) -> !ROOT.equals(logger.name()))
        .thenComparing(LoggerLevel::name);

    private final ObjectProvider<LoggingSystem> loggingSystem;

    private final ObjectProvider<LoggerGroups> loggerGroups;

    private final ResponseSanitizer sanitizer;

    /**
     * Creates the {@code get_loggers} tool.
     * @param loggingSystem the application's logging system
     * @param loggerGroups the application's logger groups, which may be absent
     * @param sanitizer limits the response
     */
    public LoggerTools(ObjectProvider<LoggingSystem> loggingSystem, ObjectProvider<LoggerGroups> loggerGroups,
            ResponseSanitizer sanitizer) {
        this.loggingSystem = loggingSystem;
        this.loggerGroups = loggerGroups;
        this.sanitizer = sanitizer;
    }

    /**
     * Reports log levels.
     * @param prefix optional logger name prefix
     * @return the matching loggers and the logger groups
     */
    @McpTool(name = "get_loggers", title = "Get loggers",
            description = """
                    Returns this application's log levels: the level configured for a logger (null when it \
                    inherits) and the effective level that actually applies. Without a prefix, returns ROOT, \
                    every logger with an explicitly configured level, and the logger groups. Pass a prefix, such \
                    as com.example or org.springframework.web, to see all loggers under it.""",
            annotations = @McpAnnotations(title = "Get loggers", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public LoggersReport getLoggers(@McpToolParam(required = false,
            description = "Logger name prefix, for example com.example") String prefix) {
        return ToolCalls.run("get_loggers", () -> {
            LoggersEndpoint endpoint = endpoint(this.loggingSystem, this.loggerGroups);
            if (endpoint == null) {
                return new LoggersReport(UNAVAILABLE, List.of(), List.of(), List.of(), 0);
            }
            LoggersDescriptor descriptor = endpoint.loggers();
            boolean all = prefix == null || prefix.isBlank();
            List<LoggerLevel> loggers = descriptor.getLoggers()
                .entrySet()
                .stream()
                .map((entry) -> loggerLevel(entry.getKey(), entry.getValue()))
                .filter((logger) -> all ? ROOT.equals(logger.name()) || logger.configuredLevel() != null
                        : logger.name().startsWith(prefix))
                .sorted(ROOT_FIRST)
                .toList();
            List<LoggerLevel> limited = this.sanitizer.limit(loggers);
            List<LoggerGroupLevel> groups = all ? groups(descriptor.getGroups()) : List.of();
            List<String> levels = descriptor.getLevels().stream().map(LogLevel::name).toList();
            String summary = all
                    ? loggers.size() + " loggers are ROOT or have a configured level; " + groups.size() + " groups."
                    : loggers.size() + " loggers start with '" + prefix + "'.";
            return new LoggersReport(summary, levels, limited, groups, loggers.size() - limited.size());
        }, (failure) -> new LoggersReport(failure, List.of(), List.of(), List.of(), 0));
    }

    private List<LoggerGroupLevel> groups(Map<String, GroupLoggerLevelsDescriptor> groups) {
        List<LoggerGroupLevel> result = groups.entrySet()
            .stream()
            .map((entry) -> new LoggerGroupLevel(entry.getKey(), entry.getValue().getConfiguredLevel(),
                    this.sanitizer.limit(entry.getValue().getMembers())))
            .sorted(Comparator.comparing(LoggerGroupLevel::name))
            .toList();
        return this.sanitizer.limit(result);
    }

    private static LoggerLevel loggerLevel(String name, LoggerLevelsDescriptor levels) {
        String effective = (levels instanceof SingleLoggerLevelsDescriptor single) ? single.getEffectiveLevel() : null;
        return new LoggerLevel(name, levels.getConfiguredLevel(), effective);
    }

    static LoggersEndpoint endpoint(ObjectProvider<LoggingSystem> loggingSystem,
            ObjectProvider<LoggerGroups> loggerGroups) {
        LoggingSystem system = loggingSystem.getIfAvailable();
        if (system == null) {
            return null;
        }
        return new LoggersEndpoint(system, loggerGroups.getIfAvailable(LoggerGroups::new));
    }
}
