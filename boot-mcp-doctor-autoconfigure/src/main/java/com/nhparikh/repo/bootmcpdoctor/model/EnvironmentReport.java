package com.nhparikh.repo.bootmcpdoctor.model;

import java.util.List;

/**
 * Result of the {@code get_environment} tool.
 *
 * @param summary one-line description of the result
 * @param activeProfiles the active Spring profiles
 * @param properties effective property values in alphabetical order, with secrets masked
 * @param omittedProperties number of matching properties left out because of the response size limit
 */
public record EnvironmentReport(String summary, List<String> activeProfiles, List<PropertyValue> properties,
        int omittedProperties) {

    /**
     * The value that applies for one property, after all property sources are taken into account.
     *
     * @param name the property name in canonical form, such as {@code spring.datasource.url}
     * @param value the masked value
     * @param origin where the value comes from, such as a file and line or an environment variable
     */
    public record PropertyValue(String name, Object value, String origin) {
    }
}
