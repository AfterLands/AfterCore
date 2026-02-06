package com.afterlands.core.api.messages;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Placeholder for message templates using {key} syntax.
 *
 * <p>Uses {@code {key}} syntax to avoid conflicts with PlaceholderAPI's
 * {@code %placeholder%} syntax.</p>
 *
 * <p><b>Examples:</b></p>
 * <pre>{@code
 * Placeholder p1 = Placeholder.of("player", "Steve");
 * Placeholder p2 = Placeholder.of("level", 10);
 * Placeholder p3 = Placeholder.of("progress", 75.5);
 * }</pre>
 *
 * @param key Placeholder key (without braces)
 * @param value Placeholder value (will be converted to string)
 */
public record Placeholder(
        @NotNull String key,
        @NotNull Object value
) {

    /**
     * Compact constructor with validation.
     */
    public Placeholder {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        if (key.isEmpty()) {
            throw new IllegalArgumentException("key cannot be empty");
        }

        // Validate key format (alphanumeric + underscore, no special chars)
        if (!key.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException(
                "Invalid placeholder key format: '" + key + "' (must be alphanumeric + underscore)"
            );
        }
    }

    /**
     * Creates a placeholder.
     *
     * @param key Placeholder key
     * @param value Placeholder value
     * @return Placeholder instance
     */
    @NotNull
    public static Placeholder of(@NotNull String key, @NotNull Object value) {
        return new Placeholder(key, value);
    }

    /**
     * Returns the placeholder pattern with braces: {@code {key}}.
     *
     * @return Pattern string
     */
    @NotNull
    public String pattern() {
        return "{" + key + "}";
    }

    /**
     * Returns the value as a string.
     *
     * @return String representation of value
     */
    @NotNull
    public String valueAsString() {
        return String.valueOf(value);
    }

    /**
     * Replaces this placeholder's pattern in the given text.
     *
     * @param text Text containing placeholders
     * @return Text with this placeholder replaced
     */
    @NotNull
    public String replace(@NotNull String text) {
        return text.replace(pattern(), valueAsString());
    }

    /**
     * Checks if text contains this placeholder pattern.
     *
     * @param text Text to check
     * @return true if text contains {@code {key}}
     */
    public boolean isPresentIn(@NotNull String text) {
        return text.contains(pattern());
    }

    /**
     * Creates a builder for constructing multiple placeholders.
     *
     * @return Builder instance
     */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Converts varargs array of placeholders to a map.
     *
     * @param placeholders Placeholders
     * @return Map of key -&gt; value
     */
    @NotNull
    public static Map<String, Object> toMap(@NotNull Placeholder... placeholders) {
        Map<String, Object> map = new HashMap<>(placeholders.length);
        for (Placeholder placeholder : placeholders) {
            map.put(placeholder.key(), placeholder.value());
        }
        return map;
    }

    /**
     * Creates placeholders from a map.
     *
     * @param map Map of key -&gt; value
     * @return Array of placeholders
     */
    @NotNull
    public static Placeholder[] fromMap(@NotNull Map<String, Object> map) {
        return map.entrySet().stream()
                .map(e -> new Placeholder(e.getKey(), e.getValue()))
                .toArray(Placeholder[]::new);
    }

    /**
     * Replaces multiple placeholders in text.
     *
     * @param text Text containing placeholders
     * @param placeholders Placeholders to replace
     * @return Text with all placeholders replaced
     */
    @NotNull
    public static String replaceAll(@NotNull String text, @NotNull Placeholder... placeholders) {
        String result = text;
        for (Placeholder placeholder : placeholders) {
            result = placeholder.replace(result);
        }
        return result;
    }

    /**
     * Replaces multiple placeholders in text using a map.
     *
     * @param text Text containing placeholders
     * @param placeholders Map of placeholders
     * @return Text with all placeholders replaced
     */
    @NotNull
    public static String replaceAll(@NotNull String text, @NotNull Map<String, Object> placeholders) {
        String result = text;
        for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
            String pattern = "{" + entry.getKey() + "}";
            String value = String.valueOf(entry.getValue());
            result = result.replace(pattern, value);
        }
        return result;
    }

    @Override
    public String toString() {
        return pattern() + " = " + valueAsString();
    }

    /**
     * Builder for constructing multiple placeholders fluently.
     */
    public static class Builder {
        private final Map<String, Object> placeholders = new HashMap<>();

        /**
         * Adds a placeholder.
         *
         * @param key Placeholder key
         * @param value Placeholder value
         * @return This builder
         */
        @NotNull
        public Builder with(@NotNull String key, @NotNull Object value) {
            placeholders.put(key, value);
            return this;
        }

        /**
         * Adds a placeholder if value is not null.
         *
         * @param key Placeholder key
         * @param value Placeholder value (nullable)
         * @return This builder
         */
        @NotNull
        public Builder withIfPresent(@NotNull String key, @Nullable Object value) {
            if (value != null) {
                placeholders.put(key, value);
            }
            return this;
        }

        /**
         * Builds an array of placeholders.
         *
         * @return Array of placeholders
         */
        @NotNull
        public Placeholder[] build() {
            return fromMap(placeholders);
        }

        /**
         * Returns the placeholders as a map.
         *
         * @return Map of placeholders
         */
        @NotNull
        public Map<String, Object> toMap() {
            return new HashMap<>(placeholders);
        }
    }
}
