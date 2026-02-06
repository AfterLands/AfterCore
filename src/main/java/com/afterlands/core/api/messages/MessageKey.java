package com.afterlands.core.api.messages;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Type-safe translation key with namespace isolation.
 *
 * <p>Namespaces isolate keys per plugin, preventing collisions
 * between plugins with similar key names.</p>
 *
 * <p><b>Examples:</b></p>
 * <pre>{@code
 * MessageKey key1 = MessageKey.of("afterjournal", "quests.started");
 * MessageKey key2 = MessageKey.of("quest.completed"); // Uses "default" namespace
 * }</pre>
 *
 * <p><b>Full key format:</b> {@code namespace:path}</p>
 *
 * @param namespace Plugin namespace (e.g., "afterjournal", "afterguilds")
 * @param path Key path within namespace (e.g., "gui.title", "quests.started")
 */
public record MessageKey(
        @NotNull String namespace,
        @NotNull String path
) {

    /** Default namespace for keys without explicit namespace */
    public static final String DEFAULT_NAMESPACE = "default";

    /**
     * Compact constructor with validation.
     */
    public MessageKey {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(path, "path cannot be null");

        if (namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace cannot be empty");
        }
        if (path.isEmpty()) {
            throw new IllegalArgumentException("path cannot be empty");
        }

        // Validate namespace format (lowercase, alphanumeric + underscore)
        if (!namespace.matches("^[a-z0-9_]+$")) {
            throw new IllegalArgumentException(
                "Invalid namespace format: '" + namespace + "' (must be lowercase alphanumeric + underscore)"
            );
        }
    }

    /**
     * Creates a message key with explicit namespace.
     *
     * @param namespace Plugin namespace
     * @param path Key path
     * @return MessageKey instance
     */
    @NotNull
    public static MessageKey of(@NotNull String namespace, @NotNull String path) {
        return new MessageKey(namespace, path);
    }

    /**
     * Creates a message key using the default namespace.
     *
     * @param path Key path
     * @return MessageKey with default namespace
     */
    @NotNull
    public static MessageKey of(@NotNull String path) {
        return new MessageKey(DEFAULT_NAMESPACE, path);
    }

    /**
     * Parses a full key string in format "namespace:path".
     * Falls back to default namespace if no colon present.
     *
     * @param fullKey Full key string
     * @return MessageKey instance
     */
    @NotNull
    public static MessageKey parse(@NotNull String fullKey) {
        Objects.requireNonNull(fullKey, "fullKey cannot be null");

        int colonIndex = fullKey.indexOf(':');
        if (colonIndex > 0 && colonIndex < fullKey.length() - 1) {
            String ns = fullKey.substring(0, colonIndex);
            String p = fullKey.substring(colonIndex + 1);
            return new MessageKey(ns, p);
        }

        // No colon or invalid format: use default namespace
        return new MessageKey(DEFAULT_NAMESPACE, fullKey);
    }

    /**
     * Returns the full key in format "namespace:path".
     *
     * @return Full key string
     */
    @NotNull
    public String fullKey() {
        return namespace + ":" + path;
    }

    /**
     * Creates a child key by appending a suffix to the path.
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * MessageKey parent = MessageKey.of("afterjournal", "quests");
     * MessageKey child = parent.child("started"); // afterjournal:quests.started
     * }</pre>
     *
     * @param suffix Suffix to append (automatically adds dot separator)
     * @return New MessageKey with extended path
     */
    @NotNull
    public MessageKey child(@NotNull String suffix) {
        Objects.requireNonNull(suffix, "suffix cannot be null");
        return new MessageKey(namespace, path + "." + suffix);
    }

    /**
     * Checks if this key belongs to a specific namespace.
     *
     * @param namespace Namespace to check
     * @return true if key is in the given namespace
     */
    public boolean inNamespace(@NotNull String namespace) {
        return this.namespace.equals(namespace);
    }

    /**
     * Checks if this key uses the default namespace.
     *
     * @return true if using default namespace
     */
    public boolean isDefaultNamespace() {
        return DEFAULT_NAMESPACE.equals(namespace);
    }

    @Override
    public String toString() {
        return fullKey();
    }
}
