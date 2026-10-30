package com.nhparikh.repo.bootmcpdoctor.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for boot-mcp-doctor.
 *
 * @param enabled Whether to register the MCP tools. Nothing is registered unless this is true.
 * @param readOnly Whether to register only tools that read state. Tools that change state, such as
 * set_log_level, are not registered at all while this is true.
 * @param tools Which tools to register.
 * @param security Who may call the tools.
 * @param masking Masking applied to every tool response.
 * @param limits Size limits applied to every tool response.
 */
@ConfigurationProperties(prefix = "boot-mcp-doctor")
public record BootMcpDoctorProperties(boolean enabled, @DefaultValue("true") boolean readOnly,
        @DefaultValue Tools tools, @DefaultValue Security security, @DefaultValue Masking masking,
        @DefaultValue Limits limits) {

    /**
     * Access control for tool calls. Spring Security authenticates callers; boot-mcp-doctor checks every tool call
     * against these settings. Without Spring Security on the classpath, no tools are registered unless
     * {@code allowUnauthenticated} is true, and tools that change state are never registered.
     *
     * @param allowUnauthenticated Whether read tools may be called without authentication, including when Spring
     * Security is not on the classpath. For local development only.
     * @param readAuthority Authority a caller needs for tools that only read, such as ROLE_OPS. Empty means any
     * authenticated caller.
     * @param writeAuthority Authority a caller needs for tools that change state, such as ROLE_OPS_ADMIN. Those tools
     * are not registered until this is set.
     */
    public record Security(boolean allowUnauthenticated, String readAuthority, String writeAuthority) {

        /**
         * Whether {@code readAuthority} is set, so that read tools need it.
         * @return whether a read authority is configured
         */
        public boolean requiresReadAuthority() {
            return hasText(this.readAuthority);
        }

        /**
         * Whether {@code writeAuthority} is set. Tools that change state are not registered until it is.
         * @return whether a write authority is configured
         */
        public boolean requiresWriteAuthority() {
            return hasText(this.writeAuthority);
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }

    /**
     * Tool selection. A tool is registered when it is in {@code include} (or {@code include} is empty) and is
     * not in {@code exclude}.
     *
     * @param include Names of the tools to register. Empty means all tools.
     * @param exclude Names of tools never to register, even if they are included.
     */
    public record Tools(@DefaultValue List<String> include, @DefaultValue List<String> exclude) {

        /**
         * Whether a tool passes {@code include} and {@code exclude}.
         * @param name a tool name, such as {@code get_health}
         * @return whether the tool may be registered
         */
        public boolean allows(String name) {
            return (this.include.isEmpty() || this.include.contains(name)) && !this.exclude.contains(name);
        }
    }

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

        /**
         * Validates the limits.
         * @param maxItems maximum number of entries in any list or map returned by a tool
         * @throws IllegalArgumentException if {@code maxItems} is less than 1
         */
        public Limits {
            if (maxItems < 1) {
                throw new IllegalArgumentException("boot-mcp-doctor.limits.max-items must be at least 1");
            }
        }
    }
}
