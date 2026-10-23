package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * The paths the Spring AI MCP server listens on, read from its {@code spring.ai.mcp.server.*} properties.
 */
final class McpEndpoints {

    private final Environment environment;

    McpEndpoints(Environment environment) {
        this.environment = environment;
    }

    /**
     * @return the Streamable HTTP endpoint, which the stateless server uses too
     */
    String streamableHttp() {
        return this.environment.getProperty("spring.ai.mcp.server.streamable-http.mcp-endpoint", "/mcp");
    }

    /**
     * @return the endpoint SSE clients connect to
     */
    String sse() {
        return this.environment.getProperty("spring.ai.mcp.server.sse-endpoint", "/sse");
    }

    /**
     * @return the endpoint SSE clients post their messages to
     */
    String sseMessage() {
        return this.environment.getProperty("spring.ai.mcp.server.sse-message-endpoint", "/mcp/message");
    }

    /**
     * @param endpoint an MCP endpoint
     * @return the endpoint below the DispatcherServlet path, as servlet filter URL patterns expect it
     */
    String inServlet(String endpoint) {
        return withoutTrailingSlash(this.environment.getProperty("spring.mvc.servlet.path", "")) + endpoint;
    }

    /**
     * @param endpoint an MCP endpoint
     * @return the path clients request: the endpoint below the context path and the DispatcherServlet path
     */
    String inRequest(String endpoint) {
        return withoutTrailingSlash(this.environment.getProperty("server.servlet.context-path", ""))
                + inServlet(endpoint);
    }

    private static String withoutTrailingSlash(String path) {
        return StringUtils.trimTrailingCharacter(path, '/');
    }
}
