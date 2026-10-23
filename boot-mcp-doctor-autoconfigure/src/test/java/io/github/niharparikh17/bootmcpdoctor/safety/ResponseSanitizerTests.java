package io.github.niharparikh17.bootmcpdoctor.safety;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseSanitizerTests {

    private final ResponseSanitizer sanitizer = new ResponseSanitizer(List.of(), 50);

    @ParameterizedTest
    @ValueSource(strings = { "password", "clientSecret", "apiKey", "accessToken", "credentials", "authorization",
            "DB_PASSWORD" })
    void masksBuiltInKeysIgnoringCase(String key) {
        Map<String, Object> result = this.sanitizer.sanitize(Map.of(key, "s3cr3t-value"));
        assertThat(result).containsEntry(key, ResponseSanitizer.MASK);
        assertThat(result.toString()).doesNotContain("s3cr3t-value");
    }

    @Test
    void masksAdditionalKeys() {
        ResponseSanitizer custom = new ResponseSanitizer(List.of("IBAN"), 50);
        Map<String, Object> result = custom.sanitize(Map.of("customerIban", "DE89370400440532013000", "name", "Ada"));
        assertThat(result).containsEntry("customerIban", ResponseSanitizer.MASK).containsEntry("name", "Ada");
    }

    @Test
    void additionalKeysDoNotReplaceBuiltInKeys() {
        ResponseSanitizer custom = new ResponseSanitizer(List.of("iban"), 50);
        assertThat(custom.isSensitive("password")).isTrue();
    }

    @Test
    void masksPasswordsInUris() {
        Map<String, Object> result = this.sanitizer.sanitize(Map.of("url",
                "jdbc:postgresql://orders:hunter2@db.internal:5432/orders", "endpoint", "https://svc:9000/api"));
        assertThat(result).containsEntry("url", "jdbc:postgresql://orders:******@db.internal:5432/orders")
            .containsEntry("endpoint", "https://svc:9000/api");
    }

    @Test
    void masksInsideNestedMapsAndLists() {
        Map<String, Object> nested = Map.of("datasource", Map.of("password", "hunter2", "pool", 10), "replicas",
                List.of(Map.of("token", "abc123"), "redis://cache:hunter2@cache:6379"));
        Map<String, Object> result = this.sanitizer.sanitize(nested);
        assertThat(result.toString()).doesNotContain("hunter2").doesNotContain("abc123").contains("pool=10");
    }

    @Test
    void keepsNumbersAndBooleans() {
        Map<String, Object> result = this.sanitizer.sanitize(Map.of("free", 1024L, "exists", true));
        assertThat(result).containsEntry("free", 1024L).containsEntry("exists", true);
    }

    @Test
    void capsMapsAndListsAtMaxItems() {
        ResponseSanitizer small = new ResponseSanitizer(List.of(), 2);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", 1);
        map.put("b", List.of(1, 2, 3));
        map.put("c", 3);
        Map<String, Object> result = small.sanitize(map);
        assertThat(result).containsOnlyKeys("a", "b");
        assertThat(result.get("b")).isEqualTo(List.of(1, 2));
        assertThat(small.limit(List.of("x", "y", "z"))).containsExactly("x", "y");
    }

    @Test
    void truncatesLongStrings() {
        String value = "x".repeat(ResponseSanitizer.MAX_STRING_LENGTH + 100);
        Object result = this.sanitizer.sanitize(Map.of("message", value)).get("message");
        assertThat((String) result).hasSize(ResponseSanitizer.MAX_STRING_LENGTH + 3).endsWith("...");
    }
}
