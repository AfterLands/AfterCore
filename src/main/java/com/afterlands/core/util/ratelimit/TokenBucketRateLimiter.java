package com.afterlands.core.util.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Implementação de rate limiter usando algoritmo Token Bucket.
 *
 * <p>Cada chave possui um bucket de tokens que se reabastece ao longo do tempo.
 * Operações consomem tokens do bucket. Se bucket estiver vazio, operação é negada.</p>
 *
 * <p>Thread-safe via Caffeine cache.</p>
 */
public final class TokenBucketRateLimiter implements RateLimiter {

    private final int capacity;
    private final Duration refillInterval;
    private final Cache<String, TokenBucket> buckets;

    /**
     * @param capacity        Capacidade máxima do bucket (número de tokens)
     * @param refillInterval  Intervalo para adicionar 1 token
     * @param expireAfter     Tempo para expirar buckets inativos
     */
    public TokenBucketRateLimiter(int capacity, @NotNull Duration refillInterval, @NotNull Duration expireAfter) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (refillInterval.isNegative() || refillInterval.isZero()) {
            throw new IllegalArgumentException("refillInterval must be positive");
        }

        this.capacity = capacity;
        this.refillInterval = refillInterval;

        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(expireAfter.toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * Cria rate limiter simples: 1 operação por intervalo.
     *
     * @param cooldown Tempo de cooldown entre operações
     */
    @NotNull
    public static TokenBucketRateLimiter simpleCooldown(@NotNull Duration cooldown) {
        return new TokenBucketRateLimiter(1, cooldown, Duration.ofMinutes(10));
    }

    /**
     * Cria rate limiter com burst: permite burst de operações, mas limita taxa ao longo do tempo.
     *
     * @param burstSize      Número de operações permitidas em burst
     * @param refillInterval Intervalo para adicionar 1 token
     */
    @NotNull
    public static TokenBucketRateLimiter withBurst(int burstSize, @NotNull Duration refillInterval) {
        return new TokenBucketRateLimiter(burstSize, refillInterval, Duration.ofMinutes(10));
    }

    @Override
    public boolean tryAcquire(@NotNull String key) {
        TokenBucket bucket = buckets.get(key, k -> new TokenBucket(capacity, refillInterval));
        return bucket != null && bucket.tryConsume();
    }

    @Override
    @NotNull
    public AcquireResult tryAcquireWithRemaining(@NotNull String key) {
        TokenBucket bucket = buckets.get(key, k -> new TokenBucket(capacity, refillInterval));
        if (bucket == null) {
            return AcquireResult.success();
        }

        if (bucket.tryConsume()) {
            return AcquireResult.success();
        } else {
            Duration remaining = bucket.timeUntilNextToken();
            return AcquireResult.failure(remaining);
        }
    }

    @Override
    public void reset(@NotNull String key) {
        buckets.invalidate(key);
    }

    @Override
    public void clear() {
        buckets.invalidateAll();
    }

    /**
     * Bucket individual de tokens.
     *
     * <p>Thread-safe via synchronized.</p>
     */
    private static final class TokenBucket {
        private final int capacity;
        private final long refillIntervalNanos;

        private int tokens;
        private long lastRefillNanos;

        TokenBucket(int capacity, Duration refillInterval) {
            this.capacity = capacity;
            this.refillIntervalNanos = refillInterval.toNanos();
            this.tokens = capacity; // Começa cheio
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            refill();

            if (tokens > 0) {
                tokens--;
                return true;
            }

            return false;
        }

        synchronized Duration timeUntilNextToken() {
            refill();

            if (tokens > 0) {
                return Duration.ZERO;
            }

            // Calcular tempo até próximo token
            long now = System.nanoTime();
            long elapsedSinceLastRefill = now - lastRefillNanos;
            long nanosUntilNextToken = refillIntervalNanos - elapsedSinceLastRefill;

            return Duration.ofNanos(Math.max(0, nanosUntilNextToken));
        }

        private void refill() {
            long now = System.nanoTime();
            long elapsedNanos = now - lastRefillNanos;

            // Calcular quantos tokens adicionar baseado no tempo decorrido
            long tokensToAdd = elapsedNanos / refillIntervalNanos;

            if (tokensToAdd > 0) {
                tokens = Math.min(capacity, tokens + (int) tokensToAdd);
                lastRefillNanos = now;
            }
        }
    }
}
