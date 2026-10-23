package com.nhparikh.repo.bootmcpdoctor.model;

/**
 * Result of the {@code set_log_level} tool.
 *
 * @param summary one-line description of the result
 * @param logger the logger or logger group that was changed
 * @param changed whether the level was changed
 * @param previousLevel the level configured before the change, or {@code null} if none was set
 * @param newLevel the level configured after the change, or {@code null} if it was reset
 * @param effectiveLevel the level that now applies, or {@code null} for a logger group
 */
public record LogLevelChange(String summary, String logger, boolean changed, String previousLevel, String newLevel,
        String effectiveLevel) {

    /**
     * Creates a result for a request that was rejected.
     * @param logger the requested logger
     * @param reason why nothing changed
     * @return the result
     */
    public static LogLevelChange rejected(String logger, String reason) {
        return new LogLevelChange(reason, logger, false, null, null, null);
    }
}
