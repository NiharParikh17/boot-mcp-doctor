package com.nhparikh.repo.bootmcpdoctor.safety;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Masks secrets and enforces size limits on everything a tool returns. Every tool response passes through
 * this class before it reaches the MCP client.
 */
public final class ResponseSanitizer {

    /** Replacement for masked values. */
    public static final String MASK = "******";

    static final List<String> DEFAULT_KEYS = List.of("password", "secret", "key", "token", "credential", "auth");

    static final int MAX_STRING_LENGTH = 500;

    // Password part of user:password@ in URIs such as jdbc:postgresql://user:pass@host/db
    private static final Pattern URI_PASSWORD = Pattern.compile("(://[^/@:\\s]*):[^/@\\s]+@");

    private final List<String> keys;

    private final int maxItems;

    /**
     * @param additionalKeys extra key patterns to mask on top of the built-in ones
     * @param maxItems maximum number of entries in any list or map
     */
    public ResponseSanitizer(List<String> additionalKeys, int maxItems) {
        this.keys = Stream.concat(DEFAULT_KEYS.stream(), additionalKeys.stream())
            .map((key) -> key.toLowerCase(Locale.ROOT))
            .toList();
        this.maxItems = maxItems;
    }

    /**
     * @return maximum number of entries in any list or map
     */
    public int maxItems() {
        return this.maxItems;
    }

    /**
     * Returns at most {@link #maxItems()} entries of {@code items}, keeping their order.
     * @param items the list to cap
     * @param <T> the element type
     * @return the capped list
     */
    public <T> List<T> limit(List<T> items) {
        return (items.size() <= this.maxItems) ? items : List.copyOf(items.subList(0, this.maxItems));
    }

    /**
     * Masks sensitive keys and caps the size of a details map, recursing into nested maps and collections.
     * @param details the details to sanitize
     * @return a new map that is safe to return to a client
     */
    public Map<String, Object> sanitize(Map<String, ?> details) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : details.entrySet()) {
            if (result.size() == this.maxItems) {
                break;
            }
            result.put(entry.getKey(), sanitize(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /**
     * Masks a single value if its key is sensitive, otherwise sanitizes the value itself.
     * @param key the property, detail or field name
     * @param value the value to sanitize
     * @return a value that is safe to return to a client
     */
    public Object sanitize(String key, Object value) {
        return isSensitive(key) ? MASK : sanitizeValue(value);
    }

    /**
     * @param key a property, detail or field name
     * @return whether values under this key must be masked
     */
    public boolean isSensitive(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return this.keys.stream().anyMatch(lower::contains);
    }

    private Object sanitizeValue(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> stringKeyed = new LinkedHashMap<>();
            map.forEach((k, v) -> stringKeyed.put(String.valueOf(k), v));
            return sanitize(stringKeyed);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>();
            for (Object item : collection) {
                if (result.size() == this.maxItems) {
                    break;
                }
                result.add(sanitizeValue(item));
            }
            return result;
        }
        return sanitizeString(String.valueOf(value));
    }

    private String sanitizeString(String value) {
        String masked = URI_PASSWORD.matcher(value).replaceAll("$1:" + MASK + "@");
        return (masked.length() <= MAX_STRING_LENGTH) ? masked : masked.substring(0, MAX_STRING_LENGTH) + "...";
    }
}
