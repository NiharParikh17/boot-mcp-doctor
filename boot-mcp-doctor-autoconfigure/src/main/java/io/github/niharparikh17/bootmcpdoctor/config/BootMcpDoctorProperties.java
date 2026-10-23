package io.github.niharparikh17.bootmcpdoctor.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for boot-mcp-doctor.
 *
 * @param enabled Whether to register the MCP tools. Nothing is registered unless this is true.
 * @param masking Masking applied to every tool response.
 * @param limits Size limits applied to every tool response.
 */
@ConfigurationProperties(prefix = "boot-mcp-doctor")
public record BootMcpDoctorProperties(boolean enabled, @DefaultValue Masking masking, @DefaultValue Limits limits) {

    /**
     * Masking settings. Built-in patterns ({@code password}, {@code secret}, {@code key}, {@code token},
     * {@code credential}, {@code auth}) are always applied and cannot be turned off.
     *
     * @param additionalKeys Extra key patterns to mask, matched case-insensitively anywhere in the key.
     */
    public record Masking(@DefaultValue List<String> additionalKeys) {
    }

    /**
     * Response size limits.
     *
     * @param maxItems Maximum number of entries in any list or map returned by a tool.
     */
    public record Limits(@DefaultValue("50") int maxItems) {

        public Limits {
            if (maxItems < 1) {
                throw new IllegalArgumentException("boot-mcp-doctor.limits.max-items must be at least 1");
            }
        }
    }
}
