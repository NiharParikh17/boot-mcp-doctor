package io.github.niharparikh17.bootmcpdoctor.tools;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import io.github.niharparikh17.bootmcpdoctor.model.LogLevelChange;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.logging.LoggersEndpoint;
import org.springframework.boot.actuate.logging.LoggersEndpoint.LoggerLevelsDescriptor;
import org.springframework.boot.actuate.logging.LoggersEndpoint.SingleLoggerLevelsDescriptor;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerGroups;
import org.springframework.boot.logging.LoggingSystem;

/**
 * MCP tool that changes log levels at runtime. It is only registered when {@code boot-mcp-doctor.read-only=false}.
 */
public class LogLevelTools {

    static final String RESET = "RESET";

    private static final String VALID_LEVELS = Arrays.stream(LogLevel.values())
        .map(LogLevel::name)
        .collect(Collectors.joining(", ")) + " or " + RESET;

    private final ObjectProvider<LoggingSystem> loggingSystem;

    private final ObjectProvider<LoggerGroups> loggerGroups;

    /**
     * @param loggingSystem the application's logging system
     * @param loggerGroups the application's logger groups, which may be absent
     */
    public LogLevelTools(ObjectProvider<LoggingSystem> loggingSystem, ObjectProvider<LoggerGroups> loggerGroups) {
        this.loggingSystem = loggingSystem;
        this.loggerGroups = loggerGroups;
    }

    /**
     * Sets or resets the level of a logger or logger group.
     * @param logger the logger or group name
     * @param level the new level, or {@code RESET}
     * @return the old and new levels
     */
    @McpTool(name = "set_log_level", title = "Set log level",
            description = """
                    Changes the log level of a logger or logger group in this running application, for example to \
                    turn on DEBUG for com.example.orders while investigating a problem. The change lasts until \
                    the application restarts. Use RESET to remove a configured level so the logger inherits again. \
                    Returns the previous and new levels.""",
            annotations = @McpAnnotations(title = "Set log level", readOnlyHint = false, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public LogLevelChange setLogLevel(
            @McpToolParam(description = "Logger or logger group name, for example com.example.orders or ROOT")
            String logger,
            @McpToolParam(description = "TRACE, DEBUG, INFO, WARN, ERROR, FATAL, OFF, or RESET to clear the level")
            String level) {
        LoggersEndpoint endpoint = LoggerTools.endpoint(this.loggingSystem, this.loggerGroups);
        if (endpoint == null) {
            return LogLevelChange.rejected(logger, LoggerTools.UNAVAILABLE);
        }
        if (logger == null || logger.isBlank()) {
            return LogLevelChange.rejected(logger, "A logger name is required, for example ROOT or com.example.");
        }
        if (level == null || level.isBlank()) {
            return LogLevelChange.rejected(logger, "A level is required: " + VALID_LEVELS + ".");
        }
        String normalized = level.trim().toUpperCase(Locale.ROOT);
        LogLevel newLevel;
        if (RESET.equals(normalized)) {
            newLevel = null;
        }
        else {
            try {
                newLevel = LogLevel.valueOf(normalized);
            }
            catch (IllegalArgumentException ex) {
                return LogLevelChange.rejected(logger, "Unknown level '" + level + "'. Use " + VALID_LEVELS + ".");
            }
        }
        String previous = configuredLevel(endpoint.loggerLevels(logger));
        endpoint.configureLogLevel(logger, newLevel);
        LoggerLevelsDescriptor after = endpoint.loggerLevels(logger);
        String configured = configuredLevel(after);
        String effective = (after instanceof SingleLoggerLevelsDescriptor single) ? single.getEffectiveLevel() : null;
        String summary = "Set " + logger + " from " + describe(previous) + " to " + describe(configured)
                + ((effective != null) ? " (effective " + effective + ")." : ".");
        return new LogLevelChange(summary, logger, true, previous, configured, effective);
    }

    private static String configuredLevel(LoggerLevelsDescriptor levels) {
        return (levels != null) ? levels.getConfiguredLevel() : null;
    }

    private static String describe(String level) {
        return (level != null) ? level : "inherited";
    }
}
