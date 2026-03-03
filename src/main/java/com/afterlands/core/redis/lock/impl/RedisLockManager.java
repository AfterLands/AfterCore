package com.afterlands.core.redis.lock.impl;

import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.redis.RedisTopology;
import com.afterlands.core.redis.lock.RedisLock;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gerenciador de locks distribuídos via Redis usando Lua scripts atômicos.
 *
 * <p>
 * Lock: SETNX + PEXPIRE em script Lua (evita race condition).
 * Unlock: GET + compare token + DEL em script Lua (evita unlock por outro processo).
 * </p>
 *
 * @since 1.8.0
 */
public final class RedisLockManager {

    private static final String KEY_PREFIX = "aftercore:lock:";

    /**
     * Lua script para adquirir lock atomicamente.
     * KEYS[1] = lock key
     * ARGV[1] = token (UUID)
     * ARGV[2] = ttl in milliseconds
     * Returns 1 if acquired, 0 if not
     */
    private static final String LOCK_SCRIPT =
            "if redis.call('setnx', KEYS[1], ARGV[1]) == 1 then " +
            "  redis.call('pexpire', KEYS[1], ARGV[2]) " +
            "  return 1 " +
            "else " +
            "  return 0 " +
            "end";

    /**
     * Lua script para liberar lock atomicamente (verifica ownership).
     * KEYS[1] = lock key
     * ARGV[1] = token (UUID)
     * Returns 1 if released, 0 if not owner
     */
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end";

    private final Logger logger;
    private final SchedulerService scheduler;
    private final RedisTopology topology;
    private final Supplier<Jedis> jedisSupplier;
    private final JedisCluster cluster;
    private final boolean debug;

    // Config
    private final long defaultTtlMs;
    private final long retryDelayMs;
    private final int maxRetries;

    public RedisLockManager(
            @NotNull Logger logger,
            @NotNull SchedulerService scheduler,
            @NotNull RedisTopology topology,
            @NotNull Supplier<Jedis> jedisSupplier,
            JedisCluster cluster,
            boolean debug,
            long defaultTtlMs,
            long retryDelayMs,
            int maxRetries) {
        this.logger = logger;
        this.scheduler = scheduler;
        this.topology = topology;
        this.jedisSupplier = jedisSupplier;
        this.cluster = cluster;
        this.debug = debug;
        this.defaultTtlMs = defaultTtlMs;
        this.retryDelayMs = retryDelayMs;
        this.maxRetries = maxRetries;
    }

    public CompletableFuture<Optional<RedisLock>> tryLock(@NotNull String key, long ttlMs) {
        long effectiveTtl = ttlMs > 0 ? ttlMs : defaultTtlMs;
        String fullKey = KEY_PREFIX + key;
        String token = UUID.randomUUID().toString();

        return CompletableFuture.supplyAsync(() -> {
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    boolean acquired = doLock(fullKey, token, effectiveTtl);
                    if (acquired) {
                        RedisLock lock = new RedisLock(key, token, effectiveTtl, System.currentTimeMillis());
                        if (debug) {
                            logger.info("[Redis-Lock] Acquired lock '" + key + "' (attempt " + (attempt + 1) + ")");
                        }
                        return Optional.of(lock);
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "[Redis-Lock] Error acquiring lock '" + key + "'", e);
                    return Optional.<RedisLock>empty();
                }

                if (attempt < maxRetries) {
                    // Retry with jitter
                    long jitter = ThreadLocalRandom.current().nextLong(0, retryDelayMs);
                    try {
                        Thread.sleep(retryDelayMs + jitter);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return Optional.<RedisLock>empty();
                    }
                }
            }

            if (debug) {
                logger.info("[Redis-Lock] Failed to acquire lock '" + key + "' after " + (maxRetries + 1) + " attempts");
            }
            return Optional.<RedisLock>empty();
        }, scheduler.ioExecutor());
    }

    public CompletableFuture<Boolean> unlock(@NotNull RedisLock lock) {
        String fullKey = KEY_PREFIX + lock.key();
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean released = doUnlock(fullKey, lock.token());
                if (debug) {
                    logger.info("[Redis-Lock] " + (released ? "Released" : "Failed to release") +
                            " lock '" + lock.key() + "'");
                }
                return released;
            } catch (Exception e) {
                logger.log(Level.WARNING, "[Redis-Lock] Error releasing lock '" + lock.key() + "'", e);
                return false;
            }
        }, scheduler.ioExecutor());
    }

    private boolean doLock(String fullKey, String token, long ttlMs) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> {
                try (Jedis jedis = jedisSupplier.get()) {
                    Object result = jedis.eval(LOCK_SCRIPT,
                            Collections.singletonList(fullKey),
                            Arrays.asList(token, String.valueOf(ttlMs)));
                    yield Long.valueOf(1L).equals(result);
                }
            }
            case CLUSTER -> {
                Object result = cluster.eval(LOCK_SCRIPT,
                        Collections.singletonList(fullKey),
                        Arrays.asList(token, String.valueOf(ttlMs)));
                yield Long.valueOf(1L).equals(result);
            }
        };
    }

    private boolean doUnlock(String fullKey, String token) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> {
                try (Jedis jedis = jedisSupplier.get()) {
                    Object result = jedis.eval(UNLOCK_SCRIPT,
                            Collections.singletonList(fullKey),
                            Collections.singletonList(token));
                    yield Long.valueOf(1L).equals(result);
                }
            }
            case CLUSTER -> {
                Object result = cluster.eval(UNLOCK_SCRIPT,
                        Collections.singletonList(fullKey),
                        Collections.singletonList(token));
                yield Long.valueOf(1L).equals(result);
            }
        };
    }
}
