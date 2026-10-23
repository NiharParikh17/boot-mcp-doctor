package io.github.niharparikh17.bootmcpdoctor.model;

import java.util.List;

/**
 * Result of the {@code get_health} tool.
 *
 * @param status overall status code, such as {@code UP} or {@code DOWN}; {@code null} when health is unavailable
 * @param summary one-line description of the result
 * @param components components ordered by severity, those that are not {@code UP} first
 * @param omittedComponents number of components left out because of the response size limit
 */
public record HealthReport(String status, String summary, List<ComponentHealth> components, int omittedComponents) {

    /**
     * Creates a report for when health information cannot be read.
     * @param reason why health is unavailable
     * @return the report
     */
    public static HealthReport unavailable(String reason) {
        return new HealthReport(null, reason, List.of(), 0);
    }
}
