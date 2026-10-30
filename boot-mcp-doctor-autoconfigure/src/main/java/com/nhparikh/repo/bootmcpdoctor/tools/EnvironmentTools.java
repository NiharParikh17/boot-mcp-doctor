package com.nhparikh.repo.bootmcpdoctor.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.nhparikh.repo.bootmcpdoctor.model.EnvironmentReport;
import com.nhparikh.repo.bootmcpdoctor.model.EnvironmentReport.PropertyValue;
import com.nhparikh.repo.bootmcpdoctor.safety.ResponseSanitizer;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.boot.origin.Origin;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

/**
 * MCP tools that describe the application's effective configuration. Values are always masked: by the built-in
 * and configured key patterns, and by any {@link SanitizingFunction} beans the application defines for Actuator.
 */
public class EnvironmentTools {

    // Spring Boot names the property sources it loads from application.properties/yml "Config resource '...'".
    static final String CONFIG_FILE_SOURCE_PREFIX = "Config resource";

    private final Environment environment;

    private final List<SanitizingFunction> sanitizingFunctions;

    private final ResponseSanitizer sanitizer;

    /**
     * Creates the {@code get_environment} tool.
     * @param environment the application's environment
     * @param sanitizingFunctions extra sanitizing applied to every value, in order
     * @param sanitizer masks and limits the response
     */
    public EnvironmentTools(Environment environment, List<SanitizingFunction> sanitizingFunctions,
            ResponseSanitizer sanitizer) {
        this.environment = environment;
        this.sanitizingFunctions = List.copyOf(sanitizingFunctions);
        this.sanitizer = sanitizer;
    }

    /**
     * Reports effective property values.
     * @param prefix optional property name prefix
     * @return the matching properties
     */
    @McpTool(name = "get_environment", title = "Get environment",
            description = """
                    Returns this application's effective configuration properties: the value that actually \
                    applies for each property and where it comes from (a config file and line, an environment \
                    variable, a system property and so on), plus the active profiles. Without a prefix, only \
                    properties set in the application's own config files are returned. Pass a prefix, such as \
                    spring.datasource or server, to look up any property, including ones set by environment \
                    variables. Secret values are always masked as ******.""",
            annotations = @McpAnnotations(title = "Get environment", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public EnvironmentReport getEnvironment(@McpToolParam(required = false,
            description = "Property name prefix, for example spring.datasource or server") String prefix) {
        return ToolCalls.run("get_environment", () -> {
            ConfigurationPropertyName prefixName = (prefix != null && !prefix.isBlank())
                    ? ConfigurationPropertyName.adapt(prefix.trim(), '.') : null;
            List<ConfigurationPropertySource> sources = new ArrayList<>();
            ConfigurationPropertySources.get(this.environment).forEach(sources::add);

            Set<ConfigurationPropertyName> names = new TreeSet<>(
                    Comparator.comparing(ConfigurationPropertyName::toString));
            for (ConfigurationPropertySource source : sources) {
                if (!(source instanceof IterableConfigurationPropertySource iterable)) {
                    continue;
                }
                if (prefixName == null) {
                    if (isConfigFile(source)) {
                        iterable.stream().forEach(names::add);
                    }
                }
                else {
                    iterable.stream()
                        .filter((name) -> prefixName.equals(name) || prefixName.isAncestorOf(name))
                        .forEach(names::add);
                }
            }

            List<PropertyValue> properties = new ArrayList<>();
            for (ConfigurationPropertyName name : names) {
                PropertyValue value = effectiveValue(name, sources);
                if (value != null) {
                    properties.add(value);
                }
            }
            List<PropertyValue> limited = this.sanitizer.limit(properties);
            return new EnvironmentReport(summarize(prefix, prefixName, properties.size()),
                    List.of(this.environment.getActiveProfiles()), limited, properties.size() - limited.size());
        }, (failure) -> new EnvironmentReport(failure, List.of(), List.of(), 0));
    }

    // The first source that has the property wins, as it does when Spring binds configuration.
    private PropertyValue effectiveValue(ConfigurationPropertyName name, List<ConfigurationPropertySource> sources) {
        for (ConfigurationPropertySource source : sources) {
            ConfigurationProperty property = source.getConfigurationProperty(name);
            if (property != null) {
                String key = name.toString();
                Object value = sanitize(source, key, property.getValue());
                Origin origin = property.getOrigin();
                String from = (origin != null) ? origin.toString() : sourceName(source);
                return new PropertyValue(key, value, from);
            }
        }
        return null;
    }

    private Object sanitize(ConfigurationPropertySource source, String key, Object value) {
        if (this.sanitizer.isSensitive(key)) {
            return ResponseSanitizer.MASK;
        }
        if (!this.sanitizingFunctions.isEmpty() && source.getUnderlyingSource() instanceof PropertySource<?> ps) {
            SanitizableData data = new SanitizableData(ps, key, value);
            for (SanitizingFunction function : this.sanitizingFunctions) {
                data = function.applyUnlessFiltered(data);
            }
            value = data.getValue();
        }
        return this.sanitizer.sanitize(key, value);
    }

    private static String summarize(String prefix, ConfigurationPropertyName prefixName, int count) {
        if (prefixName == null) {
            return count + " properties are set in the application's config files. Pass a prefix to look up any "
                    + "property, including environment variables and system properties.";
        }
        return (count == 0) ? "No properties match prefix '" + prefix + "'."
                : count + " properties match prefix '" + prefix + "'.";
    }

    private static boolean isConfigFile(ConfigurationPropertySource source) {
        return sourceName(source).startsWith(CONFIG_FILE_SOURCE_PREFIX);
    }

    private static String sourceName(ConfigurationPropertySource source) {
        return (source.getUnderlyingSource() instanceof PropertySource<?> ps) ? ps.getName() : "";
    }
}
