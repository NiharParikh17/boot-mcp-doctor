package com.nhparikh.repo.bootmcpdoctor.autoconfigure;

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ToolSecurityTests {

    private static final List<String> READ_TOOLS = List.of("get_environment", "get_health", "get_loggers", "get_metric",
            "get_slow_endpoints", "list_metrics");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(BootMcpDoctorAutoConfiguration.class))
        .withPropertyValues("boot-mcp-doctor.enabled=true");

    private final ApplicationContextRunner withoutSpringSecurity = this.contextRunner
        .withClassLoader(new FilteredClassLoader(SecurityContextHolder.class));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refusesToRegisterToolsWithoutSpringSecurity(CapturedOutput output) {
        this.withoutSpringSecurity.run((context) -> {
            assertThat(toolNames(context)).isEmpty();
            assertThat(output).containsOnlyOnce(ToolSelector.NO_AUTHENTICATION);
        });
    }

    @Test
    void registersReadToolsWithoutSpringSecurityWhenUnauthenticatedAccessIsAllowed() {
        this.withoutSpringSecurity.withPropertyValues("boot-mcp-doctor.security.allow-unauthenticated=true")
            .run((context) -> assertThat(toolNames(context)).containsExactlyElementsOf(READ_TOOLS));
    }

    @Test
    void neverRegistersWriteToolsWithoutSpringSecurity(CapturedOutput output) {
        this.withoutSpringSecurity
            .withPropertyValues("boot-mcp-doctor.security.allow-unauthenticated=true",
                    "boot-mcp-doctor.read-only=false", "boot-mcp-doctor.security.write-authority=ROLE_OPS_ADMIN")
            .run((context) -> {
                assertThat(toolNames(context)).doesNotContain("set_log_level");
                assertThat(output).containsOnlyOnce(ToolSelector.WRITE_NEEDS_SPRING_SECURITY);
            });
    }

    @Test
    void doesNotGuardCallsWithoutSpringSecurity() {
        this.withoutSpringSecurity.withPropertyValues("boot-mcp-doctor.security.allow-unauthenticated=true")
            .run((context) -> assertThat(text(call(context, "get_health"))).doesNotContain("Not authorized"));
    }

    @Test
    void registersWriteToolsOnlyOnceAWriteAuthorityIsSet(CapturedOutput output) {
        this.contextRunner.withPropertyValues("boot-mcp-doctor.read-only=false").run((context) -> {
            assertThat(toolNames(context)).doesNotContain("set_log_level");
            assertThat(output).containsOnlyOnce(ToolSelector.WRITE_NEEDS_AUTHORITY);
        });
        this.contextRunner
            .withPropertyValues("boot-mcp-doctor.read-only=false",
                    "boot-mcp-doctor.security.write-authority=ROLE_OPS_ADMIN")
            .run((context) -> assertThat(toolNames(context)).contains("set_log_level"));
    }

    @Test
    void deniesCallsWithoutAnAuthenticatedCaller() {
        this.contextRunner.run((context) -> {
            CallToolResult result = call(context, "get_health");
            assertThat(result.isError()).isTrue();
            assertThat(text(result)).isEqualTo("Not authorized: get_health requires an authenticated caller.");
        });
    }

    @Test
    void deniesAnonymousCallers() {
        this.contextRunner.run((context) -> {
            SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
            assertThat(call(context, "get_health").isError()).isTrue();
        });
    }

    @Test
    void allowsAuthenticatedCallers() {
        this.contextRunner.run((context) -> {
            authenticate("ROLE_USER");
            CallToolResult result = call(context, "get_health");
            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(text(result)).doesNotContain("Not authorized");
        });
    }

    @Test
    void checksTheReadAuthority() {
        this.contextRunner.withPropertyValues("boot-mcp-doctor.security.read-authority=ROLE_OPS").run((context) -> {
            authenticate("ROLE_USER");
            assertThat(text(call(context, "get_health")))
                .isEqualTo("Not authorized: get_health requires the ROLE_OPS authority.");
            authenticate("ROLE_OPS");
            assertThat(text(call(context, "get_health"))).doesNotContain("Not authorized");
        });
    }

    @Test
    void checksTheWriteAuthority() {
        this.contextRunner
            .withPropertyValues("boot-mcp-doctor.read-only=false",
                    "boot-mcp-doctor.security.write-authority=ROLE_OPS_ADMIN")
            .run((context) -> {
                authenticate("ROLE_OPS");
                assertThat(text(call(context, "set_log_level")))
                    .isEqualTo("Not authorized: set_log_level requires the ROLE_OPS_ADMIN authority.");
                assertThat(text(call(context, "get_health"))).doesNotContain("Not authorized");
                authenticate("ROLE_OPS_ADMIN");
                assertThat(text(call(context, "set_log_level"))).doesNotContain("Not authorized");
            });
    }

    @Test
    void allowsAnonymousReadsButNotWritesWhenUnauthenticatedAccessIsAllowed() {
        this.contextRunner
            .withPropertyValues("boot-mcp-doctor.security.allow-unauthenticated=true",
                    "boot-mcp-doctor.read-only=false", "boot-mcp-doctor.security.write-authority=ROLE_OPS_ADMIN")
            .run((context) -> {
                assertThat(text(call(context, "get_health"))).doesNotContain("Not authorized");
                assertThat(text(call(context, "set_log_level")))
                    .isEqualTo("Not authorized: set_log_level requires an authenticated caller.");
            });
    }

    @Test
    void guardsStatelessSpecificationsToo() {
        this.contextRunner.run((context) -> {
            @SuppressWarnings("unchecked")
            List<McpStatelessServerFeatures.SyncToolSpecification> specs = context
                .getBean("bootMcpDoctorStatelessToolSpecifications", List.class);
            McpStatelessServerFeatures.SyncToolSpecification getHealth = specs.stream()
                .filter((spec) -> spec.tool().name().equals("get_health"))
                .findFirst()
                .orElseThrow();
            CallToolResult result = getHealth.callHandler().apply(null, request("get_health"));
            assertThat(text(result)).isEqualTo("Not authorized: get_health requires an authenticated caller.");
        });
    }

    private static void authenticate(String... authorities) {
        SecurityContextHolder.getContext()
            .setAuthentication(UsernamePasswordAuthenticationToken.authenticated("ops", null,
                    AuthorityUtils.createAuthorityList(authorities)));
    }

    private static CallToolResult call(AssertableApplicationContext context, String tool) {
        SyncToolSpecification spec = specifications(context).stream()
            .filter((candidate) -> candidate.tool().name().equals(tool))
            .findFirst()
            .orElseThrow();
        return spec.callHandler().apply(null, request(tool));
    }

    private static CallToolRequest request(String tool) {
        Map<String, Object> arguments = tool.equals("set_log_level") ? Map.of("logger", "com.example", "level", "DEBUG")
                : Map.of();
        return CallToolRequest.builder(tool).arguments(arguments).build();
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().get(0)).text();
    }

    @SuppressWarnings("unchecked")
    private static List<SyncToolSpecification> specifications(AssertableApplicationContext context) {
        return context.getBean("bootMcpDoctorToolSpecifications", List.class);
    }

    private static List<String> toolNames(AssertableApplicationContext context) {
        return specifications(context).stream().map((spec) -> spec.tool().name()).sorted().toList();
    }
}
