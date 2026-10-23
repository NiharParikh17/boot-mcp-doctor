package io.github.niharparikh17.bootmcpdoctor.model;

import java.util.List;

/**
 * Result of the {@code list_metrics} tool.
 *
 * @param summary one-line description of the result
 * @param names metric names in alphabetical order
 * @param omittedNames number of matching names left out because of the response size limit
 */
public record MetricNamesReport(String summary, List<String> names, int omittedNames) {
}
