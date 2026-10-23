package io.github.niharparikh17.bootmcpdoctor.model;

import java.util.List;
import java.util.Map;

/**
 * Result of the {@code get_metric} tool.
 *
 * @param name the requested metric name
 * @param found whether the metric exists
 * @param summary one-line description of the result
 * @param description the metric's own description, if it has one
 * @param measurements the metric's current values
 * @param availableTags tags that can be used to narrow the metric, with their values
 * @param suggestions closest matching metric names when the metric was not found
 */
public record MetricReport(String name, boolean found, String summary, String description,
        List<Measurement> measurements, Map<String, List<String>> availableTags, List<String> suggestions) {

    /**
     * A single statistic of a metric.
     *
     * @param statistic the statistic, such as {@code COUNT}, {@code TOTAL_TIME}, {@code MAX} or {@code VALUE}
     * @param value the rounded value, or {@code null} if it is not a finite number
     * @param unit the unit of {@code value}, such as {@code ms} or {@code MB}; {@code null} for plain counts
     */
    public record Measurement(String statistic, Double value, String unit) {
    }
}
