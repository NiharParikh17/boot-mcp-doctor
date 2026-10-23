package com.nhparikh.repo.bootmcpdoctor.model;

import java.util.List;

/**
 * Result of the {@code get_slow_endpoints} tool.
 *
 * @param summary one-line description of the result
 * @param endpoints endpoints ordered by mean latency, slowest first
 * @param totalEndpoints number of endpoints that have served requests
 */
public record SlowEndpointsReport(String summary, List<EndpointLatency> endpoints, int totalEndpoints) {

    /**
     * Latency of one HTTP endpoint since the application started.
     *
     * @param method the HTTP method
     * @param uri the URI template, such as {@code /orders/{id}}
     * @param count number of requests served
     * @param meanMs mean latency in milliseconds
     * @param maxMs highest latency in milliseconds within the recent window (about the last two minutes)
     * @param errorRatePercent percentage of requests that ended with a 5xx response
     */
    public record EndpointLatency(String method, String uri, long count, double meanMs, double maxMs,
            double errorRatePercent) {
    }
}
