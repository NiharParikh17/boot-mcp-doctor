package com.nhparikh.repo.bootmcpdoctor.demo;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The DispatcherServlet path comes before the MCP endpoint too, and connections are still logged below it.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "spring.mvc.servlet.path=/svc")
class ServletPathTests extends McpAppTest {

    @Test
    void servesTheEndpointBelowTheServletPath(CapturedOutput output) {
        assertThat(toolNames(connect("/svc/mcp"))).hasSize(6);
        assertThat(output).contains("boot-mcp-doctor is running at /svc/mcp on port " + this.port)
            .contains("MCP client connected: " + CLIENT);
    }
}
