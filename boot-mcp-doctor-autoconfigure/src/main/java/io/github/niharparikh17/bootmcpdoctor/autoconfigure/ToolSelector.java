package io.github.niharparikh17.bootmcpdoctor.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.niharparikh17.bootmcpdoctor.config.BootMcpDoctorProperties;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.env.Environment;

/**
 * Decides which tool groups and tools are registered, from {@code boot-mcp-doctor.*} and the Actuator endpoint
 * access settings.
 */
class ToolSelector {

    private static final Log logger = LogFactory.getLog(ToolSelector.class);

    private final List<ToolGroup> groups;

    private final BootMcpDoctorProperties properties;

    private final Environment environment;

    ToolSelector(List<ToolGroup> groups, BootMcpDoctorProperties properties, Environment environment) {
        this.groups = groups;
        this.properties = properties;
        this.environment = environment;
    }

    /**
     * Returns the tool objects that may be registered. A group is left out when its Actuator endpoint's access is
     * {@code none}. A write group is also left out in read-only mode or when the endpoint's access is
     * {@code read-only}.
     * @return the tool objects
     */
    List<Object> toolObjects() {
        List<Object> tools = new ArrayList<>();
        for (ToolGroup group : this.groups) {
            String access = access(group.endpointId());
            if ("none".equals(access)) {
                continue;
            }
            if (group.write() && (this.properties.readOnly() || "read-only".equals(access))) {
                continue;
            }
            tools.add(group.tools());
        }
        return tools;
    }

    /**
     * Filters tool specifications by {@code boot-mcp-doctor.tools.include} and {@code exclude}, warning about names
     * that match no tool.
     * @param specifications the specifications built from {@link #toolObjects()}
     * @param name extracts the tool name from a specification
     * @param <T> the specification type
     * @return the specifications to register
     */
    <T> List<T> filter(List<T> specifications, Function<T, String> name) {
        BootMcpDoctorProperties.Tools tools = this.properties.tools();
        Set<String> available = specifications.stream().map(name).collect(Collectors.toSet());
        List<String> unknown = Stream.concat(tools.include().stream(), tools.exclude().stream())
            .filter((tool) -> !available.contains(tool))
            .distinct()
            .toList();
        if (!unknown.isEmpty()) {
            logger.warn("boot-mcp-doctor.tools lists tools that are not available and will be ignored: " + unknown
                    + ". Available tools: " + available.stream().sorted().toList());
        }
        return specifications.stream().filter((spec) -> tools.allows(name.apply(spec))).toList();
    }

    // Mirrors management.endpoint.<id>.access, capped by management.endpoints.access.max-permitted.
    private String access(String endpointId) {
        String defaultAccess = this.environment.getProperty("management.endpoints.access.default", "unrestricted");
        String access = normalize(this.environment.getProperty("management.endpoint." + endpointId + ".access",
                defaultAccess));
        String maxPermitted = normalize(
                this.environment.getProperty("management.endpoints.access.max-permitted", "unrestricted"));
        if ("none".equals(maxPermitted) || "none".equals(access)) {
            return "none";
        }
        if ("read-only".equals(maxPermitted) || "read-only".equals(access)) {
            return "read-only";
        }
        return "unrestricted";
    }

    private static String normalize(String access) {
        return access.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
