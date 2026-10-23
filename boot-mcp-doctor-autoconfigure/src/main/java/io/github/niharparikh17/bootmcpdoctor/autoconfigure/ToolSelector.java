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
 * Decides which tool groups and tools are registered, from {@code boot-mcp-doctor.*}, whether Spring Security is
 * available, and the Actuator endpoint access settings.
 */
class ToolSelector {

    static final String NO_AUTHENTICATION = "boot-mcp-doctor registered no tools because nothing authenticates "
            + "callers: Spring Security is not on the classpath. Add Spring Security and protect the MCP endpoint, or "
            + "set boot-mcp-doctor.security.allow-unauthenticated=true for local development only.";

    static final String WRITE_NEEDS_SPRING_SECURITY = "Tools that change state, such as set_log_level, were not "
            + "registered: they need Spring Security on the classpath and boot-mcp-doctor.security.write-authority.";

    static final String WRITE_NEEDS_AUTHORITY = "Tools that change state, such as set_log_level, were not "
            + "registered: set boot-mcp-doctor.security.write-authority to the authority callers need for them.";

    private static final Log logger = LogFactory.getLog(ToolSelector.class);

    private final BootMcpDoctorProperties properties;

    private final Environment environment;

    private final List<Object> toolObjects;

    private final AtomicBoolean warnedAboutNames = new AtomicBoolean();

    /**
     * @param groups every tool group on the classpath
     * @param properties the boot-mcp-doctor configuration
     * @param environment where the Actuator access settings are read from
     * @param springSecurity whether Spring Security is available to authenticate callers
     */
    ToolSelector(List<ToolGroup> groups, BootMcpDoctorProperties properties, Environment environment,
            boolean springSecurity) {
        this.properties = properties;
        this.environment = environment;
        this.toolObjects = select(groups, springSecurity);
    }

    /**
     * Returns the tool objects that may be registered. Without Spring Security, there are none unless unauthenticated
     * access is allowed. A group is left out when its Actuator endpoint's access is {@code none}. A write group is
     * also left out in read-only mode, when the endpoint's access is {@code read-only}, without Spring Security, or
     * when no write authority is configured.
     * @return the tool objects
     */
    List<Object> toolObjects() {
        return this.toolObjects;
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
        // The annotation converter warns when it is given nothing to convert.
        List<T> specifications = this.toolObjects.isEmpty() ? List.of() : provider.apply(this.toolObjects);
        Set<String> available = specifications.stream().map(name).collect(Collectors.toCollection(TreeSet::new));
        warnAboutUnregisteredNames(available);
        BootMcpDoctorProperties.Tools tools = this.properties.tools();
        return specifications.stream().filter((spec) -> tools.allows(name.apply(spec))).toList();
    }

    private List<Object> select(List<ToolGroup> groups, boolean springSecurity) {
        BootMcpDoctorProperties.Security security = this.properties.security();
        if (!springSecurity && !security.allowUnauthenticated()) {
            logger.warn(NO_AUTHENTICATION);
            return List.of();
        }
        List<Object> tools = new ArrayList<>();
        String writeWarning = null;
        for (ToolGroup group : groups) {
            String access = access(group.endpointId());
            if ("none".equals(access)) {
                continue;
            }
            if (group.write()) {
                if (this.properties.readOnly() || "read-only".equals(access)) {
                    continue;
                }
                if (!springSecurity) {
                    writeWarning = WRITE_NEEDS_SPRING_SECURITY;
                    continue;
                }
                if (!security.requiresWriteAuthority()) {
                    writeWarning = WRITE_NEEDS_AUTHORITY;
                    continue;
                }
            }
            tools.add(group.tools());
        }
        if (writeWarning != null) {
            logger.warn(writeWarning);
        }
        return List.copyOf(tools);
    }

    // Called once for the regular and once for the stateless specifications; the warning only needs saying once.
    private void warnAboutUnregisteredNames(Set<String> available) {
        BootMcpDoctorProperties.Tools tools = this.properties.tools();
        List<String> unregistered = Stream.concat(tools.include().stream(), tools.exclude().stream())
            .filter((tool) -> !available.contains(tool))
            .distinct()
            .toList();
        if (!unregistered.isEmpty() && this.warnedAboutNames.compareAndSet(false, true)) {
            logger.warn("boot-mcp-doctor.tools names tools that will not be registered: " + unregistered
                    + ". They may be misspelled, need a module that is not on the classpath, or be left out by "
                    + "the security, read-only or Actuator access settings. Tools that can be registered: "
                    + available);
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
