package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.HttpHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ConnectionLogFilterTests {

    private static final String SESSION = "3f9c2a1e-7b4d-4c1e-9a55-0d1c2e3f4a5b";

    private final ConnectionLogFilter filter = new ConnectionLogFilter(McpJsonDefaults.getMapper());

    @Test
    void logsStreamableHttpClients(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = post(initialize("Visual Studio Code"));
        request.setUserPrincipal(() -> "ops");
        request.setRemoteAddr("10.0.0.7");
        this.filter.doFilter(request, new MockHttpServletResponse(), transport(SESSION, 200));
        assertThat(output)
            .contains("MCP client connected: Visual Studio Code 1.105.1 (protocol 2025-06-18, "
                    + "session 3f9c2a1e, user ops, from 10.0.0.7)")
            .doesNotContain("7b4d");
    }

    @Test
    void logsSseClientsWithTheSessionFromTheQuery(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = post(initialize("claude-ai"));
        request.setParameter("sessionId", SESSION);
        this.filter.doFilter(request, new MockHttpServletResponse(), transport(null, 200));
        assertThat(output).contains("MCP client connected: claude-ai 1.105.1 (protocol 2025-06-18, session 3f9c2a1e, "
                + "not authenticated, from 127.0.0.1)");
    }

    @Test
    void logsStatelessClientsWithoutASession(CapturedOutput output) throws Exception {
        this.filter.doFilter(post(initialize("claude-ai")), new MockHttpServletResponse(), transport(null, 200));
        assertThat(output).contains("(protocol 2025-06-18, not authenticated, from 127.0.0.1)");
    }

    @Test
    void ignoresOtherMessages(CapturedOutput output) throws Exception {
        this.filter.doFilter(post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"),
                new MockHttpServletResponse(), transport(null, 202));
        this.filter
            .doFilter(
                    post("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":"
                            + "\"get_health\",\"arguments\":{}}}"),
                    new MockHttpServletResponse(), transport(null, 200));
        assertThat(output).doesNotContain("MCP client connected");
    }

    @Test
    void ignoresRejectedRequests(CapturedOutput output) throws Exception {
        this.filter.doFilter(post(initialize("claude-ai")), new MockHttpServletResponse(), transport(null, 401));
        assertThat(output).doesNotContain("MCP client connected");
    }

    @Test
    void ignoresBodiesThatAreNotJsonRpc(CapturedOutput output) throws Exception {
        this.filter.doFilter(post("{\"method\":\"initialize\""), new MockHttpServletResponse(), transport(null, 200));
        this.filter.doFilter(post("initialize"), new MockHttpServletResponse(), transport(null, 200));
        assertThat(output).doesNotContain("MCP client connected");
    }

    @Test
    void passesOtherMethodsThroughUnwrapped(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mcp");
        AtomicReference<ServletRequest> passed = new AtomicReference<>();
        this.filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> passed.set(req));
        assertThat(passed.get()).isSameAs(request);
        assertThat(output).doesNotContain("MCP client connected");
    }

    @Test
    void logsNothingWhenItsLoggerIsAboveInfo(CapturedOutput output) throws Exception {
        LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());
        loggingSystem.setLogLevel(ConnectionLogFilter.class.getName(), LogLevel.WARN);
        try {
            this.filter.doFilter(post(initialize("claude-ai")), new MockHttpServletResponse(), transport(null, 200));
        }
        finally {
            loggingSystem.setLogLevel(ConnectionLogFilter.class.getName(), null);
        }
        assertThat(output).doesNotContain("MCP client connected");
    }

    @Test
    void keepsClientValuesOnOneLine(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = post(initialize("evil\\n2026-10-23 INFO forged\\u202e"));
        request.setParameter("sessionId", "ab\r\ncd\u2028e-rest");
        this.filter.doFilter(request, new MockHttpServletResponse(), transport(null, 200));
        assertThat(output).contains("MCP client connected: evil?2026-10-23 INFO forged? 1.105.1 (protocol 2025-06-18, "
                + "session ab??cd?e,");
    }

    @Test
    void shortensLongClientValues() {
        assertThat(ConnectionLogFilter.clean("a".repeat(500))).isEqualTo("a".repeat(100) + "...");
        assertThat(ConnectionLogFilter.clean("a".repeat(100))).isEqualTo("a".repeat(100));
    }

    @Test
    void keepsPrintableUnicode() {
        assertThat(ConnectionLogFilter.clean("Café ☕ 客户端")).isEqualTo("Café ☕ 客户端");
        assertThat(ConnectionLogFilter.clean(null)).isEqualTo("unknown");
    }

    private static String initialize(String clientName) {
        return """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",\
                "capabilities":{},"clientInfo":{"name":"%s","version":"1.105.1"}}}""".formatted(clientName);
    }

    private static MockHttpServletRequest post(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    // Reads the message like the MCP transport does, then answers with the given session and status.
    private static FilterChain transport(String session, int status) {
        return (request, response) -> {
            request.getInputStream().readAllBytes();
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            if (session != null) {
                httpResponse.setHeader(HttpHeaders.MCP_SESSION_ID, session);
            }
            httpResponse.setStatus(status);
        };
    }
}
