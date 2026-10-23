package com.nhparikh.repo.bootmcpdoctor.demo;

import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code limits.max-items} caps every list a tool returns, and the response says how much was left out.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "boot-mcp-doctor.limits.max-items=3")
class ResponseLimitTests extends McpAppTest {

    @Test
    void capsMetricNames() {
        String report = call(connect("/mcp"), "list_metrics", Map.of());
        assertThat(JsonPath.<List<String>>read(report, "$.names")).hasSize(3);
        assertThat(JsonPath.<Integer>read(report, "$.omittedNames")).isPositive();
    }

    @Test
    void capsPropertiesAndLoggers() {
        McpSyncClient client = connect("/mcp");
        String environment = call(client, "get_environment", Map.of("prefix", "spring"));
        assertThat(JsonPath.<List<Object>>read(environment, "$.properties")).hasSize(3);
        assertThat(JsonPath.<Integer>read(environment, "$.omittedProperties")).isPositive();
        String loggers = call(client, "get_loggers", Map.of("prefix", "org.springframework"));
        assertThat(JsonPath.<List<Object>>read(loggers, "$.loggers")).hasSize(3);
        assertThat(JsonPath.<Integer>read(loggers, "$.omittedLoggers")).isPositive();
    }
}
