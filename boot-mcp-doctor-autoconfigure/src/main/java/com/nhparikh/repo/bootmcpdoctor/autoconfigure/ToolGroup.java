package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

/**
 * A set of {@code @McpTool} methods backed by one Actuator endpoint. Tool objects are wrapped rather than
 * registered as beans so that only boot-mcp-doctor decides which of them reach the MCP server.
 *
 * @param endpointId the Actuator endpoint the tools are based on, such as {@code health} or {@code env}
 * @param tools the object whose {@code @McpTool} methods become tools
 * @param write whether the tools change application state
 */
record ToolGroup(String endpointId, Object tools, boolean write) {
}
