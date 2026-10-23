package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.InitializeRequest;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCRequest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * Logs each MCP client that connects: its name and version, the protocol version, the session, the authenticated
 * user and the remote address. It runs on the MCP endpoints after Spring Security, and only logs {@code initialize}
 * requests that succeed, so rejected requests are not logged.
 */
final class ConnectionLogFilter extends OncePerRequestFilter {

    /**
     * After Spring Security's filter chain, which defaults to -100, so the caller is known.
     */
    static final int ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    // The SSE transport passes the session in this query parameter.
    private static final String SSE_SESSION_PARAMETER = "sessionId";

    // Much larger than any initialize request. Only what the transport reads is cached.
    private static final int CACHE_LIMIT = 64 * 1024;

    private static final int MAX_VALUE_LENGTH = 100;

    private static final int SESSION_PREFIX_LENGTH = 8;

    private static final Log logger = LogFactory.getLog(ConnectionLogFilter.class);

    private final McpJsonMapper jsonMapper;

    ConnectionLogFilter(McpJsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !logger.isInfoEnabled();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper cachingRequest = new ContentCachingRequestWrapper(request, CACHE_LIMIT);
        chain.doFilter(cachingRequest, response);
        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            return;
        }
        InitializeRequest initialize = initializeRequest(cachingRequest.getContentAsByteArray());
        if (initialize != null) {
            logger.info(describe(initialize, cachingRequest, response));
        }
    }

    // The initialize request in body, or null when body holds another message.
    private InitializeRequest initializeRequest(byte[] body) {
        String json = new String(body, StandardCharsets.UTF_8);
        if (!json.contains(McpSchema.METHOD_INITIALIZE)) {
            return null;
        }
        try {
            if (McpSchema.deserializeJsonRpcMessage(this.jsonMapper, json) instanceof JSONRPCRequest request
                    && McpSchema.METHOD_INITIALIZE.equals(request.method())) {
                return this.jsonMapper.convertValue(request.params(), InitializeRequest.class);
            }
        }
        catch (IOException | RuntimeException ex) {
            // The transport rejects messages it cannot read, so there is no connection to log.
        }
        return null;
    }

    private static String describe(InitializeRequest initialize, HttpServletRequest request,
            HttpServletResponse response) {
        Implementation client = initialize.clientInfo();
        StringBuilder message = new StringBuilder("MCP client connected: ");
        message.append((client != null) ? clean(client.name()) : "unknown");
        if (client != null && client.version() != null) {
            message.append(' ').append(clean(client.version()));
        }
        message.append(" (protocol ").append(clean(initialize.protocolVersion()));
        String session = session(request, response);
        if (session != null) {
            message.append(", session ").append(session);
        }
        Principal user = request.getUserPrincipal();
        message.append((user != null) ? ", user " + clean(user.getName()) : ", not authenticated");
        return message.append(", from ").append(request.getRemoteAddr()).append(')').toString();
    }

    // Only a prefix: enough to tell sessions apart, without writing a usable session ID to the logs.
    private static String session(HttpServletRequest request, HttpServletResponse response) {
        String session = response.getHeader(HttpHeaders.MCP_SESSION_ID);
        if (session == null) {
            session = request.getParameter(SSE_SESSION_PARAMETER);
        }
        if (session == null || session.isEmpty()) {
            return null;
        }
        return clean(session.substring(0, Math.min(session.length(), SESSION_PREFIX_LENGTH)));
    }

    /**
     * Keeps a client-supplied value on one line and short, so it cannot forge or flood log entries.
     * @param value the value, possibly {@code null}
     * @return the value with control, line-separator and formatting characters replaced, truncated
     */
    static String clean(String value) {
        if (value == null) {
            return "unknown";
        }
        StringBuilder cleaned = new StringBuilder();
        value.codePoints()
            .limit(MAX_VALUE_LENGTH)
            .forEach((codePoint) -> cleaned.appendCodePoint(isSafe(codePoint) ? codePoint : '?'));
        if (value.codePointCount(0, value.length()) > MAX_VALUE_LENGTH) {
            cleaned.append("...");
        }
        return cleaned.toString();
    }

    private static boolean isSafe(int codePoint) {
        int type = Character.getType(codePoint);
        return !Character.isISOControl(codePoint) && type != Character.LINE_SEPARATOR
                && type != Character.PARAGRAPH_SEPARATOR && type != Character.FORMAT
                && type != Character.SURROGATE;
    }
}
