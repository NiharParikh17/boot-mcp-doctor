package com.nhparikh.repo.bootmcpdoctor.demo;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Secrets never leave the application: the built-in key patterns, the ones added with
 * {@code masking.additional-keys}, and passwords inside URIs are masked in configuration and in health details.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = { "boot-mcp-doctor.masking.additional-keys=iban", "demo.payment.api-key=not-a-real-key-123",
                "demo.customer.iban=DE00TEST00000000000000",
                "demo.orders-db.url=jdbc:postgresql://orders:hunter2@db.example.com/orders",
                "demo.orders-db.pool-size=5" })
class MaskingTests extends McpAppTest {

    @Test
    void getEnvironmentMasksSecrets() {
        String report = call(connect("/mcp"), "get_environment", Map.of("prefix", "demo"));
        assertThat(report).contains("\"name\":\"demo.payment.api-key\",\"value\":\"******\"")
            .contains("\"name\":\"demo.customer.iban\",\"value\":\"******\"")
            .contains("jdbc:postgresql://orders:******@db.example.com/orders")
            .contains("\"name\":\"demo.orders-db.pool-size\",\"value\":\"5\"")
            .doesNotContain("not-a-real-key-123", "DE00TEST", "hunter2");
    }

    @Test
    void getHealthMasksSecretsInDetails() {
        String report = call(connect("/mcp"), "get_health", Map.of());
        assertThat(report).contains("payments (DOWN)")
            .contains("\"iban\":\"******\"")
            .contains("\"accessToken\":\"******\"")
            .contains("https://payments.example.com")
            .doesNotContain("DE00TEST", "not-a-real-token-456");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PaymentsHealth {

        @Bean
        HealthIndicator paymentsHealthIndicator() {
            return () -> Health.down()
                .withDetail("endpoint", "https://payments.example.com")
                .withDetail("iban", "DE00TEST00000000000000")
                .withDetail("accessToken", "not-a-real-token-456")
                .build();
        }
    }
}
