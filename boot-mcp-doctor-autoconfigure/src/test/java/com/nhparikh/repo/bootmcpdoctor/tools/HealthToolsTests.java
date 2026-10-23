package com.nhparikh.repo.bootmcpdoctor.tools;

import java.util.List;
import java.util.Map;

import com.nhparikh.repo.bootmcpdoctor.model.ComponentHealth;
import com.nhparikh.repo.bootmcpdoctor.model.HealthReport;
import com.nhparikh.repo.bootmcpdoctor.safety.ResponseSanitizer;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointAutoConfiguration;
import org.springframework.boot.health.autoconfigure.registry.HealthContributorRegistryAutoConfiguration;
import org.springframework.boot.health.contributor.CompositeHealthContributor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class HealthToolsTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(HealthContributorRegistryAutoConfiguration.class,
                HealthEndpointAutoConfiguration.class));

    @Test
    void reportsUpWhenAllComponentsAreUp() {
        this.contextRunner
            .withBean("db", HealthIndicator.class, () -> indicator(Health.up().withDetail("database", "PostgreSQL")))
            .withBean("diskSpace", HealthIndicator.class, () -> indicator(Health.up()))
            .run((context) -> {
                HealthReport report = tools(context, 50).getHealth();
                assertThat(report.status()).isEqualTo("UP");
                assertThat(report.summary()).isEqualTo("UP: all 2 components are UP.");
                assertThat(report.components()).extracting(ComponentHealth::name).containsExactly("db", "diskSpace");
                assertThat(report.components()).allSatisfy((component) -> assertThat(component.details()).isEmpty());
                assertThat(report.omittedComponents()).isZero();
            });
    }

    @Test
    void listsFailingComponentsFirstWithMaskedDetails() {
        this.contextRunner
            .withBean("db", HealthIndicator.class,
                    () -> indicator(Health.down()
                        .withDetail("url", "jdbc:postgresql://orders:hunter2@db.internal:5432/orders")
                        .withDetail("password", "hunter2")
                        .withDetail("error", "Connection refused")))
            .withBean("cache", HealthIndicator.class, () -> indicator(Health.outOfService()))
            .withBean("diskSpace", HealthIndicator.class, () -> indicator(Health.up().withDetail("free", 1024)))
            .run((context) -> {
                HealthReport report = tools(context, 50).getHealth();
                assertThat(report.status()).isEqualTo("DOWN");
                assertThat(report.summary())
                    .isEqualTo("DOWN: 2 of 3 components are not UP: db (DOWN), cache (OUT_OF_SERVICE).");
                assertThat(report.components()).extracting(ComponentHealth::name)
                    .containsExactly("db", "cache", "diskSpace");
                assertThat(report.components().get(0).details()).containsEntry("password", ResponseSanitizer.MASK)
                    .containsEntry("url", "jdbc:postgresql://orders:******@db.internal:5432/orders")
                    .containsEntry("error", "Connection refused");
                assertThat(report.components().get(2).details()).isEmpty();
                assertThat(report.toString()).doesNotContain("hunter2");
            });
    }

    @Test
    void ranksCustomStatusesBetweenFailuresAndUnknown() {
        this.contextRunner.withBean("disk", HealthIndicator.class, () -> indicator(Health.up()))
            .withBean("legacy", HealthIndicator.class, () -> indicator(Health.unknown()))
            .withBean("payments", HealthIndicator.class, () -> indicator(Health.status(new Status("DEGRADED"))))
            .withBean("db", HealthIndicator.class, () -> indicator(Health.down()))
            .run((context) -> assertThat(tools(context, 50).getHealth().components())
                .extracting(ComponentHealth::name)
                .containsExactly("db", "payments", "legacy", "disk"));
    }

    @Test
    void flattensNestedComponents() {
        this.contextRunner
            .withBean("db", CompositeHealthContributor.class,
                    () -> CompositeHealthContributor.fromMap(Map.of("primary", indicator(Health.up()), "replica",
                            indicator(Health.down().withDetail("error", "Replication lag")))))
            .run((context) -> {
                List<ComponentHealth> components = tools(context, 50).getHealth().components();
                assertThat(components).extracting(ComponentHealth::name).containsExactly("db/replica", "db/primary");
                assertThat(components.get(0).details()).containsEntry("error", "Replication lag");
            });
    }

    @Test
    void capsComponentsAtMaxItemsKeepingFailures() {
        this.contextRunner.withBean("a", HealthIndicator.class, () -> indicator(Health.up()))
            .withBean("b", HealthIndicator.class, () -> indicator(Health.up()))
            .withBean("z", HealthIndicator.class, () -> indicator(Health.down()))
            .run((context) -> {
                HealthReport report = tools(context, 2).getHealth();
                assertThat(report.components()).extracting(ComponentHealth::name).containsExactly("z", "a");
                assertThat(report.omittedComponents()).isEqualTo(1);
                assertThat(report.summary()).startsWith("DOWN: 1 of 3 components");
            });
    }

    @Test
    void reportsUnavailableWhenHealthEndpointIsMissing() {
        HealthReport report = new HealthTools(provider(null), sanitizer()).getHealth();
        assertThat(report.status()).isNull();
        assertThat(report.summary()).isEqualTo(HealthTools.UNAVAILABLE);
        assertThat(report.components()).isEmpty();
    }

    @Test
    void reportsUnavailableWhenNothingSuppliesAnEndpoint() {
        HealthReport report = new HealthTools(() -> null, sanitizer()).getHealth();
        assertThat(report.status()).isNull();
        assertThat(report.summary()).isEqualTo(HealthTools.UNAVAILABLE);
    }

    @Test
    void reportsFailureWhenHealthEndpointThrows() {
        HealthEndpoint endpoint = mock(HealthEndpoint.class);
        given(endpoint.health()).willThrow(new IllegalStateException("registry closed"));
        HealthReport report = new HealthTools(provider(endpoint), sanitizer()).getHealth();
        assertThat(report.status()).isNull();
        assertThat(report.summary()).isEqualTo("Health check failed: registry closed");
    }

    private static HealthTools tools(AssertableApplicationContext context, int maxItems) {
        return new HealthTools(context.getBeanProvider(HealthEndpoint.class),
                new ResponseSanitizer(List.of(), maxItems));
    }

    private static HealthIndicator indicator(Health.Builder health) {
        Health built = health.build();
        return () -> built;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<HealthEndpoint> provider(HealthEndpoint endpoint) {
        ObjectProvider<HealthEndpoint> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(endpoint);
        return provider;
    }

    private static ResponseSanitizer sanitizer() {
        return new ResponseSanitizer(List.of(), 50);
    }
}
