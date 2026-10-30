package com.nhparikh.repo.bootmcpdoctor.tools;

import java.time.Duration;
import java.util.List;

import com.nhparikh.repo.bootmcpdoctor.model.MetricNamesReport;
import com.nhparikh.repo.bootmcpdoctor.model.MetricReport;
import com.nhparikh.repo.bootmcpdoctor.model.MetricReport.Measurement;
import com.nhparikh.repo.bootmcpdoctor.model.SlowEndpointsReport;
import com.nhparikh.repo.bootmcpdoctor.model.SlowEndpointsReport.EndpointLatency;
import com.nhparikh.repo.bootmcpdoctor.safety.ResponseSanitizer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class MetricsToolsTests {

    private static final String FAILED = "failed with IllegalStateException. The application's log has the details.";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final MetricsTools tools = tools(this.registry, 50);

    @Test
    void listsNamesWithPrefix() {
        Counter.builder("orders.placed").register(this.registry);
        Counter.builder("orders.failed").register(this.registry);
        Counter.builder("jvm.threads.started").register(this.registry);
        MetricNamesReport report = this.tools.listMetrics("orders.");
        assertThat(report.names()).containsExactly("orders.failed", "orders.placed");
        assertThat(report.summary()).isEqualTo("2 metrics starting with 'orders.'.");
    }

    @Test
    void capsMetricNamesAtMaxItems() {
        Counter.builder("a").register(this.registry);
        Counter.builder("b").register(this.registry);
        Counter.builder("c").register(this.registry);
        MetricNamesReport report = tools(this.registry, 2).listMetrics(null);
        assertThat(report.names()).containsExactly("a", "b");
        assertThat(report.omittedNames()).isEqualTo(1);
    }

    @Test
    void reportsByteSizesInMegabytes() {
        Gauge.builder("jvm.memory.used", () -> 256 * 1024 * 1024)
            .baseUnit("bytes")
            .tag("area", "heap")
            .register(this.registry);
        MetricReport report = this.tools.getMetric("jvm.memory.used", null);
        assertThat(report.found()).isTrue();
        assertThat(report.measurements()).containsExactly(new Measurement("VALUE", 256.0, "MB"));
        assertThat(report.availableTags()).containsEntry("area", List.of("heap"));
        assertThat(report.summary()).isEqualTo("jvm.memory.used: VALUE 256.0 MB.");
    }

    @Test
    void reportsTimesInMilliseconds() {
        Timer.builder("orders.latency").register(this.registry).record(Duration.ofMillis(1500));
        MetricReport report = this.tools.getMetric("orders.latency", List.of());
        assertThat(report.measurements()).containsExactly(new Measurement("COUNT", 1.0, null),
                new Measurement("TOTAL_TIME", 1500.0, "ms"), new Measurement("MAX", 1500.0, "ms"));
    }

    @Test
    void filtersByTag() {
        Counter.builder("orders.placed").tag("region", "eu").register(this.registry).increment(3);
        Counter.builder("orders.placed").tag("region", "us").register(this.registry).increment(5);
        MetricReport report = this.tools.getMetric("orders.placed", List.of("region:eu"));
        assertThat(report.measurements()).containsExactly(new Measurement("COUNT", 3.0, null));
    }

    @Test
    void suggestsCloseNamesForUnknownMetric() {
        Counter.builder("jvm.memory.used").register(this.registry);
        Counter.builder("jvm.memory.max").register(this.registry);
        Counter.builder("http.server.requests").register(this.registry);
        MetricReport report = this.tools.getMetric("jvm.memory.usd", null);
        assertThat(report.found()).isFalse();
        assertThat(report.summary()).isEqualTo("No metric named 'jvm.memory.usd'.");
        assertThat(report.suggestions()).first().isEqualTo("jvm.memory.used");
    }

    @Test
    void rejectsMalformedTags() {
        Counter.builder("orders.placed").register(this.registry);
        MetricReport report = this.tools.getMetric("orders.placed", List.of("eu"));
        assertThat(report.found()).isFalse();
        assertThat(report.summary()).contains("KEY:VALUE");
    }

    @Test
    void requiresMetricName() {
        assertThat(this.tools.getMetric(" ", null).summary()).startsWith("A metric name is required");
    }

    @Test
    void ranksEndpointsByMeanLatency() {
        recordRequest("POST", "/checkout", "SUCCESS", 2000);
        recordRequest("POST", "/checkout", "SUCCESS", 2400);
        recordRequest("GET", "/orders", "SUCCESS", 20);
        recordRequest("GET", "/orders", "SUCCESS", 20);
        recordRequest("GET", "/orders", "SUCCESS", 20);
        recordRequest("GET", "/orders", "SERVER_ERROR", 30);
        recordRequest("POST", "/mcp", "SUCCESS", 5000);
        recordRequest("GET", "/actuator/health", "SUCCESS", 100);
        recordRequest("GET", "NOT_FOUND", "CLIENT_ERROR", 1);

        SlowEndpointsReport report = this.tools.getSlowEndpoints(null);

        assertThat(report.endpoints()).containsExactly(new EndpointLatency("POST", "/checkout", 2, 2200.0, 2400.0, 0.0),
                new EndpointLatency("GET", "/orders", 4, 22.5, 30.0, 25.0));
        assertThat(report.totalEndpoints()).isEqualTo(2);
        assertThat(report.summary())
            .isEqualTo("Slowest of 2 endpoints: POST /checkout averages 2200.0 ms over 2 requests (max 2400.0 ms, "
                    + "0.0% errors).");
    }

    @Test
    void returnsRequestedNumberOfSlowEndpoints() {
        recordRequest("POST", "/checkout", "SUCCESS", 2000);
        recordRequest("GET", "/orders", "SUCCESS", 20);
        SlowEndpointsReport report = this.tools.getSlowEndpoints(1);
        assertThat(report.endpoints()).extracting(EndpointLatency::uri).containsExactly("/checkout");
        assertThat(report.totalEndpoints()).isEqualTo(2);
    }

    @Test
    void explainsWhenNoRequestsWereRecorded() {
        SlowEndpointsReport report = this.tools.getSlowEndpoints(null);
        assertThat(report.endpoints()).isEmpty();
        assertThat(report.summary()).startsWith("No HTTP requests recorded yet.");
    }

    @Test
    void reportsUnavailableWithoutMeterRegistry() {
        MetricsTools unavailable = tools(null, 50);
        assertThat(unavailable.listMetrics(null).summary()).isEqualTo(MetricsTools.UNAVAILABLE);
        assertThat(unavailable.getMetric("jvm.memory.used", null).summary()).isEqualTo(MetricsTools.UNAVAILABLE);
        assertThat(unavailable.getSlowEndpoints(null).summary()).isEqualTo(MetricsTools.UNAVAILABLE);
    }

    private void recordRequest(String method, String uri, String outcome, long millis) {
        Timer.builder(MetricsTools.HTTP_REQUESTS)
            .tags("method", method, "uri", uri, "outcome", outcome)
            .register(this.registry)
            .record(Duration.ofMillis(millis));
    }

    @Test
    void reportsFailuresWithoutTheirMessage() {
        IllegalStateException failure = new IllegalStateException("registry at jdbc:h2:mem:secret");
        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willThrow(failure);
        MetricsTools failing = new MetricsTools(provider, new ResponseSanitizer(List.of(), 50), List.of());
        ToolCalls.start();
        assertThat(failing.listMetrics(null).summary()).isEqualTo("list_metrics " + FAILED);
        assertThat(failing.getMetric("jvm.memory.used", null).summary()).isEqualTo("get_metric " + FAILED);
        assertThat(failing.getSlowEndpoints(null).summary()).isEqualTo("get_slow_endpoints " + FAILED);
        assertThat(ToolCalls.finish().failure()).isSameAs(failure);
    }

    @SuppressWarnings("unchecked")
    private static MetricsTools tools(MeterRegistry registry, int maxItems) {
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(registry);
        return new MetricsTools(provider, new ResponseSanitizer(List.of(), maxItems), List.of("/mcp", "/actuator"));
    }
}
