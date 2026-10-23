package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

import java.util.List;

import com.nhparikh.repo.bootmcpdoctor.config.BootMcpDoctorProperties;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;

/**
 * Logs, once the application is ready, where boot-mcp-doctor's tools are served, which tools are registered and
 * who may call them.
 */
final class StartupLog implements ApplicationListener<ApplicationReadyEvent> {

    static final String NO_SERVER = "boot-mcp-doctor is enabled, but its tools are not served: no synchronous MCP "
            + "server is running. Check that spring.ai.mcp.server.enabled is not false and that "
            + "spring.ai.mcp.server.type is SYNC.";

    static final String NO_TOOLS = " with no tools. Check the boot-mcp-doctor.security, read-only and tools "
            + "settings, and the Actuator access settings.";

    static final String ANY_CALLER = "boot-mcp-doctor.security.allow-unauthenticated=true: anyone who can reach the "
            + "MCP endpoint can call the read tools without authenticating. Use this only where untrusted clients "
            + "cannot reach it.";

    private static final Log logger = LogFactory.getLog(StartupLog.class);

    private final List<Tool> tools;

    private final BootMcpDoctorProperties.Security security;

    private final boolean springSecurity;

    /**
     * @param tools the registered tools
     * @param security who may call them
     * @param springSecurity whether Spring Security authenticates callers
     */
    StartupLog(List<Tool> tools, BootMcpDoctorProperties.Security security, boolean springSecurity) {
        this.tools = tools;
        this.security = security;
        this.springSecurity = springSecurity;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ApplicationContext context = event.getApplicationContext();
        String location = location(context);
        if (location == null) {
            logger.warn(NO_SERVER);
            return;
        }
        String running = "boot-mcp-doctor is running" + location;
        if (this.tools.isEmpty()) {
            logger.warn(running + NO_TOOLS);
            return;
        }
        List<String> names = this.tools.stream().map(Tool::name).sorted().toList();
        List<String> writeNames = this.tools.stream().filter(ToolGuard::isWrite).map(Tool::name).sorted().toList();
        logger.info(running + " with " + names.size() + (writeNames.isEmpty() ? " read-only" : "")
                + ((names.size() == 1) ? " tool: " : " tools: ") + String.join(", ", names) + ". "
                + access(writeNames) + ".");
        if (acceptsAnyCaller()) {
            logger.warn(ANY_CALLER);
        }
    }

    // Where clients reach the server, or null when no synchronous server serves these tools.
    private static String location(ApplicationContext context) {
        McpEndpoints endpoints = new McpEndpoints(context.getEnvironment());
        String port = context.getEnvironment().getProperty("local.server.port");
        if (has(context, McpStatelessSyncServer.class)) {
            return at(endpoints.inRequest(endpoints.streamableHttp()), port, "stateless Streamable HTTP");
        }
        if (!has(context, McpSyncServer.class)) {
            return null;
        }
        if (has(context, McpStreamableServerTransportProvider.class)) {
            return at(endpoints.inRequest(endpoints.streamableHttp()), port, "Streamable HTTP");
        }
        if (has(context, StdioServerTransportProvider.class)) {
            return " over stdio";
        }
        if (has(context, McpServerTransportProvider.class)) {
            return at(endpoints.inRequest(endpoints.sse()), port, "SSE");
        }
        return "";
    }

    private static boolean has(ApplicationContext context, Class<?> type) {
        return context.getBeanNamesForType(type).length > 0;
    }

    private static String at(String path, String port, String transport) {
        return " at " + path + ((port != null) ? " on port " + port : "") + " (" + transport + ")";
    }

    // Mirrors SpringSecurityToolAuthorizer: write tools only need the write authority.
    private String access(List<String> writeNames) {
        String read;
        if (!this.springSecurity) {
            read = "Tool calls are not authenticated";
        }
        else if (this.security.requiresReadAuthority()) {
            read = "Read tools need the " + this.security.readAuthority() + " authority";
        }
        else if (this.security.allowUnauthenticated()) {
            read = "Read tools also accept unauthenticated callers";
        }
        else {
            read = "Tool calls need a caller authenticated by Spring Security";
        }
        if (writeNames.isEmpty()) {
            return read;
        }
        return read + "; " + String.join(", ", writeNames) + ((writeNames.size() == 1) ? " needs" : " need")
                + " the " + this.security.writeAuthority() + " authority";
    }

    private boolean acceptsAnyCaller() {
        return this.security.allowUnauthenticated() && !(this.springSecurity && this.security.requiresReadAuthority());
    }
}
