package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.health.actuate.endpoint.AdditionalHealthEndpointPath;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.actuate.endpoint.HttpCodeStatusMapper;
import org.springframework.boot.health.actuate.endpoint.StatusAggregator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.health.registry.ReactiveHealthContributorRegistry;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Builds a health endpoint over the application's health contributors. Spring Boot only creates its own
 * {@link HealthEndpoint} while health is exposed over HTTP or JMX, but the contributor registry always exists, so
 * {@code get_health} works either way. Every contributor is included with its details, and the overall status
 * follows {@code management.endpoint.health.status.order}, as it does in Actuator.
 * <p>
 * Spring Boot 4.1 added {@code StatusAggregator.of} and {@code HttpCodeStatusMapper.getDefault()}, and deprecated
 * what Spring Boot 4.0 has instead for removal. So neither is called directly, and this works on both.
 */
final class InProcessHealthEndpoint {

    private static final String STATUS_ORDER = "management.endpoint.health.status.order";

    private static final Method STATUS_AGGREGATOR_OF = ClassUtils.getStaticMethod(StatusAggregator.class, "of",
            List.class);

    private static final String SIMPLE_STATUS_AGGREGATOR = "org.springframework.boot.health.actuate.endpoint."
            + "SimpleStatusAggregator";

    // As Actuator maps statuses by default. get_health doesn't answer with HTTP codes, so it's never used.
    private static final Set<Status> UNAVAILABLE = Set.of(Status.DOWN, Status.OUT_OF_SERVICE);

    private static final HttpCodeStatusMapper HTTP_CODES = (status) -> UNAVAILABLE.contains(status) ? 503 : 200;

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
        return new HealthEndpoint(registry, reactiveRegistry,
                HealthEndpointGroups.of(new AllContributors(statusAggregator(order, STATUS_AGGREGATOR_OF)), Map.of()),
                null);
    }

    /**
     * @param order the status order, or an empty list for Actuator's default order
     * @param of {@code StatusAggregator.of(List)}, or {@code null} on Spring Boot 4.0, which doesn't have it
     * @return an aggregator that follows the order
     */
    static StatusAggregator statusAggregator(List<String> order, Method of) {
        if (order.isEmpty()) {
            return StatusAggregator.getDefault();
        }
        if (of != null) {
            return (StatusAggregator) ReflectionUtils.invokeMethod(of, null, order);
        }
        Class<?> simple = ClassUtils.resolveClassName(SIMPLE_STATUS_AGGREGATOR,
                InProcessHealthEndpoint.class.getClassLoader());
        return (StatusAggregator) BeanUtils.instantiateClass(ClassUtils.getConstructorIfAvailable(simple, List.class),
                order);
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
            return HTTP_CODES;
        }

        @Override
        public AdditionalHealthEndpointPath getAdditionalPath() {
            return null;
        }
    }
}
