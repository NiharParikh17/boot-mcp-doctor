package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Logs whether boot-mcp-doctor supports the application's Spring Boot and Spring AI versions, whether or not
 * {@code boot-mcp-doctor.enabled} is {@code true}. It has no conditions, so that it also runs on the versions it
 * reports as unsupported.
 */
@AutoConfiguration
public class CompatibilityLogAutoConfiguration {

    /**
     * Creates the auto-configuration. Spring Boot does this when it applies it.
     */
    public CompatibilityLogAutoConfiguration() {
    }

    @Bean
    CompatibilityLog bootMcpDoctorCompatibilityLog() {
        return new CompatibilityLog();
    }
}
