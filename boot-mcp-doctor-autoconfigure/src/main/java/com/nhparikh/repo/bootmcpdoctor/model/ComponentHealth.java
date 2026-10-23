package com.nhparikh.repo.bootmcpdoctor.model;

import java.util.Map;

/**
 * Health of a single component, such as {@code db} or {@code diskSpace}.
 *
 * @param name component name; nested components are joined with a slash, for example {@code db/primary}
 * @param status the component's status code
 * @param details masked details reported by the component; empty when the component is {@code UP}
 */
public record ComponentHealth(String name, String status, Map<String, Object> details) {
}
