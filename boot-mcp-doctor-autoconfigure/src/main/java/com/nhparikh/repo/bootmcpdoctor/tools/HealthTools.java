package com.nhparikh.repo.bootmcpdoctor.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.nhparikh.repo.bootmcpdoctor.model.ComponentHealth;
import com.nhparikh.repo.bootmcpdoctor.model.HealthReport;
import com.nhparikh.repo.bootmcpdoctor.safety.ResponseSanitizer;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.actuate.endpoint.IndicatedHealthDescriptor;
import org.springframework.boot.health.contributor.Status;

/**
 * MCP tools that report the application's health through an Actuator {@link HealthEndpoint}.
 */
public class HealthTools {

    static final String UNAVAILABLE = "Health not available. Is spring-boot-starter-actuator on the classpath?";

    private static final int SUMMARY_NAMES = 5;

    private static final Comparator<ComponentHealth> BY_SEVERITY = Comparator
        .comparingInt((ComponentHealth component) -> severity(component.status()))
        .thenComparing(ComponentHealth::name);

    private final Supplier<HealthEndpoint> healthEndpoint;

    private final ResponseSanitizer sanitizer;

    /**
     * Creates the {@code get_health} tool over Actuator's health endpoint.
     * @param healthEndpoint the Actuator health endpoint, which may be absent
     * @param sanitizer masks and limits the response
     */
    public HealthTools(ObjectProvider<HealthEndpoint> healthEndpoint, ResponseSanitizer sanitizer) {
        this((Supplier<HealthEndpoint>) healthEndpoint::getIfAvailable, sanitizer);
    }

    /**
     * Creates the {@code get_health} tool over any health endpoint.
     * @param healthEndpoint supplies the health endpoint to read, or {@code null} when there is none
     * @param sanitizer masks and limits the response
     */
    public HealthTools(Supplier<HealthEndpoint> healthEndpoint, ResponseSanitizer sanitizer) {
        this.healthEndpoint = healthEndpoint;
        this.sanitizer = sanitizer;
    }

    /**
     * Reports overall and per-component health.
     * @return the health report
     */
    // @formatter:off
    @McpTool(name = "get_health", title = "Get health",
            description = """
                    Returns the overall health status of this running application and the status of each health \
                    component, such as the database, disk space or a downstream service. Use it first when asked \
                    whether the application is healthy or why it is failing. Components that are not UP are \
                    listed first, with their details; healthy components are listed by name and status only. \
                    Status values: UP, DOWN, OUT_OF_SERVICE, UNKNOWN, or an application-defined code. \
                    Secret values in details are masked.""",
            annotations = @McpAnnotations(title = "Get health", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    // @formatter:on
    public HealthReport getHealth() {
        return ToolCalls.run("get_health", () -> {
            HealthEndpoint endpoint = this.healthEndpoint.get();
            if (endpoint == null) {
                return HealthReport.unavailable(UNAVAILABLE);
            }
            HealthDescriptor root = endpoint.health();
            List<ComponentHealth> components = new ArrayList<>();
            collect(null, root, components);
            components.sort(BY_SEVERITY);
            List<ComponentHealth> limited = this.sanitizer.limit(components);
            String status = root.getStatus().getCode();
            return new HealthReport(status, summarize(status, components), limited, components.size() - limited.size());
        }, (failure) -> HealthReport.unavailable(failure));
    }

    private void collect(String path, HealthDescriptor descriptor, List<ComponentHealth> out) {
        if (descriptor instanceof CompositeHealthDescriptor composite) {
            Map<String, HealthDescriptor> children = composite.getComponents();
            if (children != null) {
                children.forEach((name, child) -> collect((path != null) ? path + "/" + name : name, child, out));
            }
            return;
        }
        if (path == null) {
            return;
        }
        String status = descriptor.getStatus().getCode();
        Map<String, Object> details = Map.of();
        if (!Status.UP.getCode().equals(status) && descriptor instanceof IndicatedHealthDescriptor indicated
                && indicated.getDetails() != null) {
            details = this.sanitizer.sanitize(indicated.getDetails());
        }
        out.add(new ComponentHealth(path, status, details));
    }

    private static String summarize(String status, List<ComponentHealth> components) {
        List<ComponentHealth> unhealthy = components.stream()
            .filter((component) -> !Status.UP.getCode().equals(component.status()))
            .toList();
        if (unhealthy.isEmpty()) {
            return status + ": all " + components.size() + " components are UP.";
        }
        String names = unhealthy.stream()
            .limit(SUMMARY_NAMES)
            .map((component) -> component.name() + " (" + component.status() + ")")
            .collect(Collectors.joining(", "));
        if (unhealthy.size() > SUMMARY_NAMES) {
            names += " and " + (unhealthy.size() - SUMMARY_NAMES) + " more";
        }
        return status + ": " + unhealthy.size() + " of " + components.size() + " components are not UP: " + names + ".";
    }

    private static int severity(String status) {
        if (Status.DOWN.getCode().equals(status)) {
            return 0;
        }
        if (Status.OUT_OF_SERVICE.getCode().equals(status)) {
            return 1;
        }
        if (Status.UNKNOWN.getCode().equals(status)) {
            return 3;
        }
        if (Status.UP.getCode().equals(status)) {
            return 4;
        }
        // Application-defined codes, such as DEGRADED, rank just after the standard failures.
        return 2;
    }
}
