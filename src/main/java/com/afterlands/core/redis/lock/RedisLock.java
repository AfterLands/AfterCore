package com.afterlands.core.redis.lock;

import org.jetbrains.annotations.NotNull;

/**
 * Representa um lock distribuído adquirido via Redis.
 *
 * @param key        chave do lock (sem prefix)
 * @param token      UUID para ownership verification
 * @param ttlMs      TTL original em milissegundos
 * @param acquiredAt timestamp de aquisição (System.currentTimeMillis)
 * @since 1.8.0
 */
public record RedisLock(
        @NotNull String key,
        @NotNull String token,
        long ttlMs,
        long acquiredAt
) {
    public boolean isExpired() {
        return System.currentTimeMillis() > (acquiredAt + ttlMs);
    }

    public long remainingMs() {
        return Math.max(0, (acquiredAt + ttlMs) - System.currentTimeMillis());
    }
}
