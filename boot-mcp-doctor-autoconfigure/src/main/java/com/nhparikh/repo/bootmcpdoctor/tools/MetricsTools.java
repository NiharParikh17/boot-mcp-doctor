package com.nhparikh.repo.bootmcpdoctor.tools;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.nhparikh.repo.bootmcpdoctor.model.MetricNamesReport;
import com.nhparikh.repo.bootmcpdoctor.model.MetricReport;
import com.nhparikh.repo.bootmcpdoctor.model.MetricReport.Measurement;
import com.nhparikh.repo.bootmcpdoctor.model.SlowEndpointsReport;
import com.nhparikh.repo.bootmcpdoctor.model.SlowEndpointsReport.EndpointLatency;
import com.nhparikh.repo.bootmcpdoctor.safety.ResponseSanitizer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint.AvailableTag;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint.MetricDescriptor;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint.Sample;

/**
 * MCP tools that read the application's Micrometer metrics.
 */
public class MetricsTools {

    static final String UNAVAILABLE = "Metrics not available. Is micrometer on the classpath "
            + "(spring-boot-starter-actuator)?";

    static final String HTTP_REQUESTS = "http.server.requests";

    static final int DEFAULT_SLOW_ENDPOINTS = 5;

    private static final int SUGGESTIONS = 5;

    // uri tag values that do not identify an endpoint
    private static final Set<String> NON_ENDPOINT_URIS = Set.of("NOT_FOUND", "REDIRECTION");

    private final ObjectProvider<MeterRegistry> meterRegistry;

    private final ResponseSanitizer sanitizer;

    private final List<String> ignoredPaths;

    /**
     * @param meterRegistry the application's meter registry, which may be absent
     * @param sanitizer limits the response
     * @param ignoredPaths request paths to leave out of {@code get_slow_endpoints}, such as the MCP and Actuator
     * endpoints; a path also covers everything below it
     */
    public MetricsTools(ObjectProvider<MeterRegistry> meterRegistry, ResponseSanitizer sanitizer,
            List<String> ignoredPaths) {
        this.meterRegistry = meterRegistry;
        this.sanitizer = sanitizer;
        this.ignoredPaths = List.copyOf(ignoredPaths);
    }

    /**
     * Lists metric names.
     * @param prefix optional name prefix
     * @return the matching names
     */
    @McpTool(name = "list_metrics", title = "List metrics",
            description = """
                    Lists the names of the metrics this application records, such as jvm.memory.used or \
                    http.server.requests. Use it to discover what can be passed to get_metric. Pass a prefix, \
                    such as jvm. or hikaricp., to narrow the list.""",
            annotations = @McpAnnotations(title = "List metrics", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public MetricNamesReport listMetrics(@McpToolParam(required = false,
            description = "Only return names that start with this prefix, for example jvm.") String prefix) {
        return ToolCalls.run("list_metrics", () -> {
            MetricsEndpoint endpoint = endpoint();
            if (endpoint == null) {
                return new MetricNamesReport(UNAVAILABLE, List.of(), 0);
            }
            List<String> names = endpoint.listNames()
                .getNames()
                .stream()
                .filter((name) -> isBlank(prefix) || name.startsWith(prefix))
                .sorted()
                .toList();
            List<String> limited = this.sanitizer.limit(names);
            String scope = isBlank(prefix) ? "" : " starting with '" + prefix + "'";
            String summary = names.isEmpty() ? "No metrics" + scope + "." : names.size() + " metrics" + scope + ".";
            return new MetricNamesReport(summary, limited, names.size() - limited.size());
        }, (failure) -> new MetricNamesReport(failure, List.of(), 0));
    }

    /**
     * Reads one metric.
     * @param name the metric name
     * @param tags optional {@code KEY:VALUE} tag filters
     * @return the metric's values, or suggestions when it does not exist
     */
    @McpTool(name = "get_metric", title = "Get metric",
            description = """
                    Returns the current values of one metric, such as jvm.memory.used, plus the tags that can \
                    narrow it down. Use list_metrics first if you are unsure of the name; an unknown name returns \
                    the closest matching names. Times are reported in ms and byte sizes in MB. Counts and totals \
                    are cumulative since the application started.""",
            annotations = @McpAnnotations(title = "Get metric", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public MetricReport getMetric(
            @McpToolParam(description = "Metric name, for example jvm.memory.used") String name,
            @McpToolParam(required = false,
                    description = "Tag filters as KEY:VALUE, for example area:heap or uri:/orders") List<String> tags) {
        return ToolCalls.run("get_metric", () -> {
            MetricsEndpoint endpoint = endpoint();
            if (endpoint == null) {
                return notFound(name, UNAVAILABLE, List.of());
            }
            if (isBlank(name)) {
                return notFound(name, "A metric name is required. Use list_metrics to see the available names.",
                        List.of());
            }
            List<String> tagFilters = (tags != null) ? tags : List.of();
            for (String tag : tagFilters) {
                if (tag == null || tag.indexOf(':') < 1) {
                    return notFound(name, "Tags must be written as KEY:VALUE, for example area:heap. Got: " + tag,
                            List.of());
                }
            }
            MetricDescriptor metric = endpoint.metric(name, tagFilters);
            if (metric == null) {
                List<String> suggestions = suggest(name, endpoint.listNames().getNames());
                String reason = tagFilters.isEmpty() ? "No metric named '" + name + "'."
                        : "No values for metric '" + name + "' with tags " + tagFilters + ".";
                return notFound(name, reason, suggestions);
            }
            List<Measurement> measurements = metric.getMeasurements()
                .stream()
                .map((sample) -> measurement(sample, metric.getBaseUnit()))
                .toList();
            Map<String, List<String>> availableTags = new LinkedHashMap<>();
            for (AvailableTag tag : this.sanitizer.limit(metric.getAvailableTags())) {
                availableTags.put(tag.getTag(), this.sanitizer.limit(tag.getValues().stream().sorted().toList()));
            }
            String summary = measurements.isEmpty() ? "no measurements" : measurements.stream()
                .map((m) -> m.statistic() + " " + m.value() + ((m.unit() != null) ? " " + m.unit() : ""))
                .collect(Collectors.joining(", "));
            return new MetricReport(name, true, name + ": " + summary + ".", metric.getDescription(), measurements,
                    availableTags, List.of());
        }, (failure) -> notFound(name, failure, List.of()));
    }

    /**
     * Ranks HTTP endpoints by latency.
     * @param limit how many endpoints to return
     * @return the slowest endpoints
     */
    @McpTool(name = "get_slow_endpoints", title = "Get slow endpoints",
            description = """
                    Returns this application's slowest HTTP endpoints, ranked by mean latency since startup, with \
                    request count, mean and max latency in ms, and the percentage of requests that failed with a \
                    5xx response. Use it when asked why the application or a request is slow. Max covers only the \
                    recent window (about two minutes), so it can be 0 for endpoints that were not called \
                    recently. The MCP and Actuator endpoints are left out.""",
            annotations = @McpAnnotations(title = "Get slow endpoints", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public SlowEndpointsReport getSlowEndpoints(@McpToolParam(required = false,
            description = "How many endpoints to return. Defaults to 5.") Integer limit) {
        return ToolCalls.run("get_slow_endpoints", () -> {
            MetricsEndpoint endpoint = endpoint();
            if (endpoint == null) {
                return new SlowEndpointsReport(UNAVAILABLE, List.of(), 0);
            }
            MetricDescriptor requests = endpoint.metric(HTTP_REQUESTS, List.of());
            if (requests == null) {
                return new SlowEndpointsReport("No HTTP requests recorded yet. The " + HTTP_REQUESTS
                        + " metric appears after the application has served its first request.", List.of(), 0);
            }
            List<EndpointLatency> endpoints = new ArrayList<>();
            for (String uri : tagValues(requests, "uri")) {
                if (isIgnored(uri)) {
                    continue;
                }
                MetricDescriptor byUri = endpoint.metric(HTTP_REQUESTS, List.of("uri:" + uri));
                Set<String> methods = tagValues(byUri, "method");
                for (String method : methods.isEmpty() ? Set.of("") : methods) {
                    List<String> tags = method.isEmpty() ? List.of("uri:" + uri)
                            : List.of("uri:" + uri, "method:" + method);
                    EndpointLatency latency = latency(endpoint, method, uri, tags);
                    if (latency != null) {
                        endpoints.add(latency);
                    }
                }
            }
            endpoints.sort(Comparator.comparingDouble(EndpointLatency::meanMs).reversed());
            int requested = (limit != null && limit > 0) ? limit : DEFAULT_SLOW_ENDPOINTS;
            int count = Math.min(requested, this.sanitizer.maxItems());
            List<EndpointLatency> slowest = endpoints.subList(0, Math.min(count, endpoints.size()));
            String summary;
            if (endpoints.isEmpty()) {
                summary = "No application endpoints have served requests yet.";
            }
            else {
                EndpointLatency top = endpoints.get(0);
                summary = "Slowest of " + endpoints.size() + " endpoints: " + top.method() + " " + top.uri()
                        + " averages " + top.meanMs() + " ms over " + top.count() + " requests (max " + top.maxMs()
                        + " ms, " + top.errorRatePercent() + "% errors).";
            }
            return new SlowEndpointsReport(summary, List.copyOf(slowest), endpoints.size());
        }, (failure) -> new SlowEndpointsReport(failure, List.of(), 0));
    }

    private EndpointLatency latency(MetricsEndpoint endpoint, String method, String uri, List<String> tags) {
        MetricDescriptor metric = endpoint.metric(HTTP_REQUESTS, tags);
        if (metric == null) {
            return null;
        }
        double count = statistic(metric, Statistic.COUNT);
        if (count <= 0) {
            return null;
        }
        double totalSeconds = statistic(metric, Statistic.TOTAL_TIME);
        double maxSeconds = statistic(metric, Statistic.MAX);
        List<String> errorTags = new ArrayList<>(tags);
        errorTags.add("outcome:SERVER_ERROR");
        MetricDescriptor errors = endpoint.metric(HTTP_REQUESTS, errorTags);
        double errorCount = (errors != null) ? statistic(errors, Statistic.COUNT) : 0;
        return new EndpointLatency(method.isEmpty() ? null : method, uri, (long) count,
                round(totalSeconds / count * 1000, 1), round(maxSeconds * 1000, 1),
                round(errorCount / count * 100, 1));
    }

    private boolean isIgnored(String uri) {
        if (NON_ENDPOINT_URIS.contains(uri)) {
            return true;
        }
        return this.ignoredPaths.stream().anyMatch((path) -> uri.equals(path) || uri.startsWith(path + "/"));
    }

    private Measurement measurement(Sample sample, String baseUnit) {
        Statistic statistic = sample.getStatistic();
        Double value = sample.getValue();
        if (value == null || value.isNaN() || value.isInfinite()) {
            return new Measurement(statistic.name(), null, null);
        }
        if (statistic == Statistic.COUNT || statistic == Statistic.ACTIVE_TASKS) {
            return new Measurement(statistic.name(), round(value, 3), null);
        }
        if ("seconds".equals(baseUnit)) {
            return new Measurement(statistic.name(), round(value * 1000, 1), "ms");
        }
        if ("bytes".equals(baseUnit)) {
            return new Measurement(statistic.name(), round(value / (1024 * 1024), 1), "MB");
        }
        return new Measurement(statistic.name(), round(value, 3), baseUnit);
    }

    private MetricReport notFound(String name, String reason, List<String> suggestions) {
        return new MetricReport(name, false, reason, null, List.of(), Map.of(), suggestions);
    }

    private MetricsEndpoint endpoint() {
        MeterRegistry registry = this.meterRegistry.getIfAvailable();
        return (registry != null) ? new MetricsEndpoint(registry) : null;
    }

    private static Set<String> tagValues(MetricDescriptor metric, String tag) {
        if (metric == null) {
            return Set.of();
        }
        return metric.getAvailableTags()
            .stream()
            .filter((available) -> available.getTag().equals(tag))
            .findFirst()
            .map(AvailableTag::getValues)
            .orElse(Set.of());
    }

    private static double statistic(MetricDescriptor metric, Statistic statistic) {
        return metric.getMeasurements()
            .stream()
            .filter((sample) -> sample.getStatistic() == statistic)
            .mapToDouble(Sample::getValue)
            .findFirst()
            .orElse(0);
    }

    static List<String> suggest(String name, Set<String> names) {
        String query = name.toLowerCase(Locale.ROOT);
        Comparator<String> byDistance = Comparator
            .comparingInt((String candidate) -> distance(query, candidate.toLowerCase(Locale.ROOT)));
        return names.stream()
            .sorted(byDistance.thenComparing(Comparator.naturalOrder()))
            .limit(SUGGESTIONS)
            .toList();
    }

    // Edit distance, with a discount for names that contain the query so that partial names rank well.
    private static int distance(String query, String candidate) {
        int[] previous = new int[candidate.length() + 1];
        int[] current = new int[candidate.length() + 1];
        for (int j = 0; j <= candidate.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= query.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= candidate.length(); j++) {
                int cost = (query.charAt(i - 1) == candidate.charAt(j - 1)) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        int distance = previous[candidate.length()];
        return candidate.contains(query) ? distance / 4 : distance;
    }

    private static double round(double value, int decimals) {
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
