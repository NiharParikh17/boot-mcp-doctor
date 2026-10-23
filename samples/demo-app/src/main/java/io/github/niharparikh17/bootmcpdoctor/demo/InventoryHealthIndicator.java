package io.github.niharparikh17.bootmcpdoctor.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Simulates a downstream inventory service. It reports DOWN unless {@code demo.inventory.available=true}, so
 * {@code get_health} has something to explain.
 */
@Component
class InventoryHealthIndicator implements HealthIndicator {

    private static final String URL = "http://inventory.internal:8081";

    private final boolean available;

    InventoryHealthIndicator(@Value("${demo.inventory.available:false}") boolean available) {
        this.available = available;
    }

    @Override
    public Health health() {
        if (this.available) {
            return Health.up().withDetail("url", URL).build();
        }
        return Health.down().withDetail("url", URL).withDetail("error", "Connection refused").build();
    }
}
