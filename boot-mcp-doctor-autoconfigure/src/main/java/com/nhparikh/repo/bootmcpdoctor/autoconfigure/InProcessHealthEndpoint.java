package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

import java.util.List;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.health.actuate.endpoint.AdditionalHealthEndpointPath;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.actuate.endpoint.HttpCodeStatusMapper;
import org.springframework.boot.health.actuate.endpoint.StatusAggregator;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.health.registry.ReactiveHealthContributorRegistry;
import org.springframework.core.env.Environment;

/**
 * Builds a health endpoint over the application's health contributors. Spring Boot only creates its own
 * {@link HealthEndpoint} while health is exposed over HTTP or JMX, but the contributor registry always exists, so
 * {@code get_health} works either way. Every contributor is included with its details, and the overall status
 * follows {@code management.endpoint.health.status.order}, as it does in Actuator.
 */
final class InProcessHealthEndpoint {

    private static final String STATUS_ORDER = "management.endpoint.health.status.order";

    private InProcessHealthEndpoint() {
    }

    /**
     * @param registry the application's health contributors
     * @param reactiveRegistry its reactive health contributors, or {@code null} if there are none
     * @param environment where the status order is read from
     * @return a health endpoint that is not registered as a bean, and so never exposed
     */
    static HealthEndpoint create(HealthContributorRegistry registry, ReactiveHealthContributorRegistry reactiveRegistry,
            Environment environment) {
        List<String> order = Binder.get(environment)
            .bind(STATUS_ORDER, Bindable.listOf(String.class))
            .orElse(List.of());
        StatusAggregator aggregator = order.isEmpty() ? StatusAggregator.getDefault() : StatusAggregator.of(order);
        return new HealthEndpoint(registry, reactiveRegistry, HealthEndpointGroups.of(new AllContributors(aggregator),
                Map.of()), null);
    }

    private static final class AllContributors implements HealthEndpointGroup {

        private final StatusAggregator statusAggregator;

        AllContributors(StatusAggregator statusAggregator) {
            this.statusAggregator = statusAggregator;
        }

        @Override
        public boolean isMember(String name) {
            return true;
        }

        @Override
        public boolean showComponents(SecurityContext securityContext) {
            return true;
        }

        @Override
        public boolean showDetails(SecurityContext securityContext) {
            return true;
        }

        @Override
        public StatusAggregator getStatusAggregator() {
            return this.statusAggregator;
        }

        @Override
        public HttpCodeStatusMapper getHttpCodeStatusMapper() {
            return HttpCodeStatusMapper.getDefault();
        }

        @Override
        public AdditionalHealthEndpointPath getAdditionalPath() {
            return null;
        }
    }
}
