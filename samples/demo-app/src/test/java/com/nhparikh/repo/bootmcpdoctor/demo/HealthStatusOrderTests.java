package com.nhparikh.repo.bootmcpdoctor.demo;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When health is not exposed, {@code get_health} builds the overall status itself, following
 * {@code management.endpoint.health.status.order} as Actuator does. Here UP ranks before DOWN, so the failing inventory
 * check doesn't make the application DOWN.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "management.endpoints.web.exposure.exclude=health", "management.endpoint.health.status.order=up,down" })
class HealthStatusOrderTests extends McpAppTest {

    @Test
    void getHealthFollowsTheStatusOrder() throws Exception {
        assertThat(call(connect("/mcp"), "get_health", Map.of())).contains("\"status\":\"UP\"")
            .contains("inventory (DOWN)");
    }
}
