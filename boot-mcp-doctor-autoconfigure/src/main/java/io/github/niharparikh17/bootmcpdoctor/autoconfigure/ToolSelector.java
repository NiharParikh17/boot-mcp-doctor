package io.github.niharparikh17.bootmcpdoctor.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private final AtomicBoolean warned = new AtomicBoolean();

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
     * Builds the tool specifications to register: converts {@link #toolObjects()} with {@code provider}, then keeps
     * the ones that {@code boot-mcp-doctor.tools.include} and {@code exclude} allow. Names in those lists that match
     * no tool that can be registered are logged once.
     * @param provider converts tool objects into specifications
     * @param name extracts the tool name from a specification
     * @param <T> the specification type
     * @return the specifications to register
     */
    <T> List<T> specifications(Function<List<Object>, List<T>> provider, Function<T, String> name) {
        List<Object> objects = toolObjects();
        // The annotation converter warns when it is given nothing to convert.
        List<T> specifications = objects.isEmpty() ? List.of() : provider.apply(objects);
        Set<String> available = specifications.stream().map(name).collect(Collectors.toCollection(TreeSet::new));
        warnAboutUnregisteredNames(available);
        BootMcpDoctorProperties.Tools tools = this.properties.tools();
        return specifications.stream().filter((spec) -> tools.allows(name.apply(spec))).toList();
    }

    // Called once for the regular and once for the stateless specifications; the warning only needs saying once.
    private void warnAboutUnregisteredNames(Set<String> available) {
        BootMcpDoctorProperties.Tools tools = this.properties.tools();
        List<String> unregistered = Stream.concat(tools.include().stream(), tools.exclude().stream())
            .filter((tool) -> !available.contains(tool))
            .distinct()
            .toList();
        if (!unregistered.isEmpty() && this.warned.compareAndSet(false, true)) {
            logger.warn("boot-mcp-doctor.tools names tools that will not be registered: " + unregistered
                    + ". They may be misspelled, need a module that is not on the classpath, or be left out by "
                    + "read-only mode or Actuator access settings. Tools that can be registered: " + available);
        }
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
