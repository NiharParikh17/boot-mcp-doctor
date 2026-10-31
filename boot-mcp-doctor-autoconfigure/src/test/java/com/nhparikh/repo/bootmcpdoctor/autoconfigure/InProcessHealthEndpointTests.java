package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.boot.health.actuate.endpoint.StatusAggregator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

class InProcessHealthEndpointTests {

    private static final Set<Status> STATUSES = Set.of(Status.UP, Status.DOWN, Status.OUT_OF_SERVICE);

    private static final List<String> ORDER = List.of("out-of-service", "up", "down");

    private static final Method OF = ClassUtils.getStaticMethod(StatusAggregator.class, "of", List.class);

    @Test
    void usesActuatorsDefaultOrderWithoutOne() {
        assertThat(InProcessHealthEndpoint.statusAggregator(List.of(), OF).getAggregateStatus(STATUSES))
            .isEqualTo(Status.DOWN);
        assertThat(InProcessHealthEndpoint.statusAggregator(List.of(), null).getAggregateStatus(STATUSES))
            .isEqualTo(Status.DOWN);
    }

    @Test
    void followsTheOrderOnSpringBoot41() {
        assertThat(OF).isNotNull();
        assertThat(InProcessHealthEndpoint.statusAggregator(ORDER, OF).getAggregateStatus(STATUSES))
            .isEqualTo(Status.OUT_OF_SERVICE);
    }

    @Test
    void followsTheOrderOnSpringBoot40() {
        StatusAggregator aggregator = InProcessHealthEndpoint.statusAggregator(ORDER, null);
        assertThat(aggregator.getClass().getSimpleName()).isEqualTo("SimpleStatusAggregator");
        assertThat(aggregator.getAggregateStatus(STATUSES)).isEqualTo(Status.OUT_OF_SERVICE);
    }
}
