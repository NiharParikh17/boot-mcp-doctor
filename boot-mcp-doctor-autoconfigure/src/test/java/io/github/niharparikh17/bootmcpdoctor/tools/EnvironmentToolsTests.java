package io.github.niharparikh17.bootmcpdoctor.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.niharparikh17.bootmcpdoctor.model.EnvironmentReport;
import io.github.niharparikh17.bootmcpdoctor.model.EnvironmentReport.PropertyValue;
import io.github.niharparikh17.bootmcpdoctor.safety.ResponseSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentToolsTests {

    private static final String CONFIG_FILE = "Config resource 'class path resource [application.yml]' via location "
            + "'optional:classpath:/'";

    private final StandardEnvironment environment = new StandardEnvironment();

    @BeforeEach
    void setUp() {
        MutablePropertySources sources = this.environment.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.addLast(new SystemEnvironmentPropertySource("systemEnvironment",
                Map.of("SERVER_PORT", "9090", "PATH", "/usr/bin")));
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("server.port", 8080);
        config.put("spring.application.name", "orders");
        config.put("spring.datasource.url", "jdbc:postgresql://orders:hunter2@db:5432/orders");
        config.put("spring.datasource.password", "hunter2");
        config.put("app.api-key", "abc123");
        sources.addLast(new MapPropertySource(CONFIG_FILE, config));
    }

    @Test
    void withoutPrefixReturnsConfigFilePropertiesWithEffectiveValues() {
        EnvironmentReport report = tools(List.of(), 50).getEnvironment(null);
        assertThat(report.properties()).extracting(PropertyValue::name)
            .containsExactly("app.api-key", "server.port", "spring.application.name", "spring.datasource.password",
                    "spring.datasource.url");
        PropertyValue port = report.properties().get(1);
        assertThat(port.value()).isEqualTo("9090");
        assertThat(port.origin()).contains("SERVER_PORT");
        assertThat(report.summary()).startsWith("5 properties are set in the application's config files.");
    }

    @Test
    void masksSecrets() {
        EnvironmentReport report = tools(List.of(), 50).getEnvironment(null);
        assertThat(report.properties()).extracting(PropertyValue::value)
            .containsExactly(ResponseSanitizer.MASK, "9090", "orders", ResponseSanitizer.MASK,
                    "jdbc:postgresql://orders:******@db:5432/orders");
        assertThat(report.toString()).doesNotContain("hunter2").doesNotContain("abc123");
    }

    @Test
    void prefixFindsPropertiesFromAnySource() {
        EnvironmentReport report = tools(List.of(), 50).getEnvironment("path");
        assertThat(report.properties()).containsExactly(
                new PropertyValue("path", "/usr/bin", report.properties().get(0).origin()));
        assertThat(report.summary()).isEqualTo("1 properties match prefix 'path'.");
    }

    @Test
    void prefixMatchesWholeNameSegments() {
        EnvironmentReport report = tools(List.of(), 50).getEnvironment("spring.datasource");
        assertThat(report.properties()).extracting(PropertyValue::name)
            .containsExactly("spring.datasource.password", "spring.datasource.url");
        assertThat(tools(List.of(), 50).getEnvironment("spring.data").properties()).isEmpty();
    }

    @Test
    void appliesSanitizingFunctions() {
        SanitizingFunction hideName = SanitizingFunction.sanitizeValue().ifKeyEquals("spring.application.name");
        EnvironmentReport report = tools(List.of(hideName), 50).getEnvironment("spring.application");
        assertThat(report.properties()).extracting(PropertyValue::value).containsExactly("******");
    }

    @Test
    void capsPropertiesAtMaxItems() {
        EnvironmentReport report = tools(List.of(), 2).getEnvironment(null);
        assertThat(report.properties()).hasSize(2);
        assertThat(report.omittedProperties()).isEqualTo(3);
    }

    @Test
    void reportsActiveProfiles() {
        this.environment.setActiveProfiles("prod");
        assertThat(tools(List.of(), 50).getEnvironment(null).activeProfiles()).containsExactly("prod");
    }

    @Test
    void explainsWhenNothingMatches() {
        EnvironmentReport report = tools(List.of(), 50).getEnvironment("nope");
        assertThat(report.properties()).isEmpty();
        assertThat(report.summary()).isEqualTo("No properties match prefix 'nope'.");
    }

    private EnvironmentTools tools(List<SanitizingFunction> functions, int maxItems) {
        return new EnvironmentTools(this.environment, functions, new ResponseSanitizer(List.of(), maxItems));
    }
}
