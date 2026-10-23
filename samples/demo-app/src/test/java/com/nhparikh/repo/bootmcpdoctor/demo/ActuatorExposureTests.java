package com.nhparikh.repo.bootmcpdoctor.demo;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tools read the application in-process, so most of Actuator's HTTP settings don't matter: health details hidden
 * from HTTP callers are still reported, and with Actuator on its own port the tools stay on the application's port.
 * Only the {@code metrics}, {@code env} and {@code loggers} endpoints are never exposed here, which is Actuator's
 * default.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = { "management.endpoint.health.show-details=never", "management.server.port=0" })
class ActuatorExposureTests extends McpAppTest {

    @Test
    void registersEveryToolOnTheApplicationPort() throws Exception {
        assertThat(toolNames(connect("/mcp"))).hasSize(6);
        assertThat(get("/actuator/health").statusCode()).isEqualTo(404);
    }

    @Test
    void getHealthIncludesDetailsThatHttpCallersDontSee() {
        assertThat(call(connect("/mcp"), "get_health", Map.of())).contains("inventory (DOWN)")
            .contains("Connection refused");
    }
}
