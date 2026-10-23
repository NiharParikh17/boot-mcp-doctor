package com.nhparikh.repo.bootmcpdoctor.demo;

import org.junit.jupiter.api.Test;

import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A {@code limits.max-items} below 1 stops the application from starting, rather than returning empty responses.
 */
class InvalidMaxItemsTests {

    @Test
    void refusesToStart() {
        SpringApplicationBuilder application = new SpringApplicationBuilder(DemoApplication.class)
            .properties("server.port=0", "boot-mcp-doctor.limits.max-items=0");
        assertThatThrownBy(() -> application.run())
            .hasRootCauseMessage("boot-mcp-doctor.limits.max-items must be at least 1");
    }
}
