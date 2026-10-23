package io.github.niharparikh17.bootmcpdoctor.model;

import java.util.List;

/**
 * Result of the {@code get_loggers} tool.
 *
 * @param summary one-line description of the result
 * @param levels the log levels the logging system supports
 * @param loggers matching loggers, {@code ROOT} first and then in alphabetical order
 * @param groups logger groups, such as {@code web} and {@code sql}
 * @param omittedLoggers number of matching loggers left out because of the response size limit
 */
public record LoggersReport(String summary, List<String> levels, List<LoggerLevel> loggers,
        List<LoggerGroupLevel> groups, int omittedLoggers) {

    /**
     * Levels of one logger.
     *
     * @param name the logger name
     * @param configuredLevel the level set explicitly for this logger, or {@code null} if it inherits
     * @param effectiveLevel the level that actually applies
     */
    public record LoggerLevel(String name, String configuredLevel, String effectiveLevel) {
    }

    /**
     * Level of a logger group.
     *
     * @param name the group name
     * @param configuredLevel the level set for the group, or {@code null} if none
     * @param members the loggers in the group
     */
    public record LoggerGroupLevel(String name, String configuredLevel, List<String> members) {
    }
}
