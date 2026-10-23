package com.nhparikh.repo.bootmcpdoctor.demo;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot only creates Actuator's health endpoint while health is exposed, but {@code get_health} reads the
 * health contributors directly when it is missing, so hiding health from HTTP callers doesn't affect the tool.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "management.endpoints.web.exposure.exclude=health")
class HealthNotExposedTests extends McpAppTest {

    @Test
    void getHealthWorksWithoutExposure() throws Exception {
        assertThat(get("/actuator/health").statusCode()).isEqualTo(404);
        assertThat(call(connect("/mcp"), "get_health", Map.of())).contains("\"status\":\"DOWN\"")
            .contains("inventory (DOWN)")
            .contains("Connection refused");
    }
}
