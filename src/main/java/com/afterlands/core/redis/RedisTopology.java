package com.afterlands.core.redis;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Topologia de conexão Redis suportada.
 *
 * @since 1.8.0
 */
public enum RedisTopology {
    STANDALONE,
    SENTINEL,
    CLUSTER;

    @NotNull
    public static RedisTopology fromString(@Nullable String raw) {
        if (raw == null) return STANDALONE;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "sentinel" -> SENTINEL;
            case "cluster" -> CLUSTER;
            default -> STANDALONE;
        };
    }
}
