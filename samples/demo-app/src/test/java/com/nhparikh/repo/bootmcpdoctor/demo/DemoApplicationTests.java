package com.nhparikh.repo.bootmcpdoctor.demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The demo application as it ships: Streamable HTTP on {@code /mcp}, read-only, and no Spring Security, so it opts in
 * to unauthenticated calls. Every read tool is called the way an assistant would call it.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = { "demo.checkout.delay=100ms", "demo.request-id-header=X-Request-Id" })
class DemoApplicationTests extends McpAppTest {

    private McpSyncClient client;

    @BeforeEach
    void connect() {
        this.client = connect("/mcp");
    }

    @Test
    void logsWhereToolsAreServedAndWhoConnected(CapturedOutput output) {
        assertThat(output)
            .contains("boot-mcp-doctor is running at /mcp on port " + this.port + " (Streamable HTTP) with 6 read-only "
                    + "tools: " + READ_TOOLS + ". Tool calls are not authenticated.")
            .contains("boot-mcp-doctor.security.allow-unauthenticated=true: anyone who can reach the MCP endpoint")
            .containsPattern("MCP client connected: " + CLIENT + " \\(protocol [0-9-]+, session [0-9a-f-]{8}, "
                    + "not authenticated, from [^)]+\\)");
    }

    @Test
    void registersReadOnlyTools() {
        assertThat(toolNames(this.client)).containsExactly("get_environment", "get_health", "get_loggers",
                "get_metric", "get_slow_endpoints", "list_metrics");
    }

    @Test
    void getHealthReportsInventoryDown() {
        assertThat(call(this.client, "get_health", Map.of())).contains("\"status\":\"DOWN\"")
            .contains("inventory (DOWN)")
            .contains("Connection refused");
    }

    @Test
    void getSlowEndpointsRanksCheckoutFirst() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        http.send(HttpRequest.newBuilder(URI.create(url("/orders"))).build(), HttpResponse.BodyHandlers.discarding());
        http.send(HttpRequest.newBuilder(URI.create(url("/checkout"))).POST(HttpRequest.BodyPublishers.noBody())
            .build(), HttpResponse.BodyHandlers.discarding());

        String report = call(this.client, "get_slow_endpoints", Map.of());
        assertThat(report).contains("Slowest of 2 endpoints: POST /checkout")
            .contains("\"uri\":\"/orders\"")
            .doesNotContain("/mcp")
            .doesNotContain("UNKNOWN");
        assertThat(call(this.client, "get_slow_endpoints", Map.of("limit", 1))).contains("\"uri\":\"/checkout\"")
            .doesNotContain("\"uri\":\"/orders\"");
    }

    @Test
    void getEnvironmentShowsWhereAPropertyIsSet() {
        assertThat(call(this.client, "get_environment", Map.of("prefix", "boot-mcp-doctor")))
            .contains("\"name\":\"boot-mcp-doctor.enabled\",\"value\":true")
            .contains("class path resource [application.yml] - ");
    }

    @Test
    void getEnvironmentWithoutAPrefixOnlyReturnsConfigFiles() {
        String report = call(this.client, "get_environment", Map.of());
        assertThat(report).contains("properties are set in the application's config files")
            .contains("\"name\":\"spring.application.name\",\"value\":\"order-service\"")
            .contains("\"name\":\"demo.checkout.delay\",\"value\":\"100ms\"")
            .doesNotContain("demo.request-id-header");
        assertThat(call(this.client, "get_environment", Map.of("prefix", "demo.request-id-header")))
            .contains("\"name\":\"demo.request-id-header\",\"value\":\"X-Request-Id\"");
    }

    @Test
    void listMetricsFiltersByPrefix() {
        List<String> names = JsonPath.read(call(this.client, "list_metrics", Map.of("prefix", "jvm.memory")),
                "$.names");
        assertThat(names).isNotEmpty().allMatch((name) -> name.startsWith("jvm.memory")).contains("jvm.memory.used");
    }

    @Test
    void getMetricReadsJvmMemory() {
        assertThat(call(this.client, "get_metric", Map.of("name", "jvm.memory.used", "tags", List.of("area:heap"))))
            .contains("\"found\":true")
            .contains("\"unit\":\"MB\"");
    }

    @Test
    void getMetricSuggestsNamesForATypo() {
        String report = call(this.client, "get_metric", Map.of("name", "jvm.memory.usd"));
        assertThat(report).contains("\"found\":false").contains("No metric named 'jvm.memory.usd'.");
        assertThat(JsonPath.<List<String>>read(report, "$.suggestions")).contains("jvm.memory.used");
    }

    @Test
    void getMetricRejectsMalformedTags() {
        assertThat(call(this.client, "get_metric", Map.of("name", "jvm.memory.used", "tags", List.of("heap"))))
            .contains("Tags must be written as KEY:VALUE");
    }

    @Test
    void getLoggersReturnsRootAndFiltersByPrefix() {
        assertThat(call(this.client, "get_loggers", Map.of())).contains("\"name\":\"ROOT\"");
        String report = call(this.client, "get_loggers", Map.of("prefix", "com.nhparikh.repo.bootmcpdoctor"));
        assertThat(JsonPath.<List<String>>read(report, "$.loggers[*].name"))
            .allMatch((name) -> name.startsWith("com.nhparikh.repo.bootmcpdoctor"));
    }
}
