package com.afterlands.core.redis;

import com.afterlands.core.redis.lock.RedisLock;
import com.afterlands.core.redis.pubsub.RedisPubSub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Representa um datasource Redis individual dentro do RedisService.
 *
 * <p>
 * Cada datasource tem seu próprio pool Jedis, configuração de Pub/Sub
 * e gerenciador de locks distribuídos.
 * </p>
 *
 * <p>
 * <b>Exemplo de uso:</b>
 * </p>
 * <pre>{@code
 * RedisDataSource cache = core.redis().datasource("default");
 * cache.set("key", "value").thenRun(() -> {
 *     // Operação concluída
 * });
 * }</pre>
 *
 * @since 1.8.0
 * @see RedisService#datasource(String)
 */
public interface RedisDataSource {

    // ==================== Metadata ====================

    @NotNull String name();

    @NotNull RedisTopology topology();

    boolean isEnabled();

    boolean isInitialized();

    @NotNull CompletableFuture<Boolean> isAvailable();

    @NotNull Map<String, Object> getPoolStats();

    void close();

    // ==================== Raw Access ====================

    <T> CompletableFuture<T> execute(@NotNull RedisFunction<Jedis, T> fn);

    CompletableFuture<Void> run(@NotNull RedisConsumer<Jedis> fn);

    <T> CompletableFuture<List<T>> pipeline(@NotNull RedisFunction<Pipeline, List<T>> fn);

    // ==================== String Operations ====================

    CompletableFuture<@Nullable String> get(@NotNull String key);

    CompletableFuture<Void> set(@NotNull String key, @NotNull String value);

    CompletableFuture<Void> setex(@NotNull String key, long seconds, @NotNull String value);

    CompletableFuture<Boolean> setnx(@NotNull String key, @NotNull String value);

    CompletableFuture<Long> incr(@NotNull String key);

    CompletableFuture<Long> incrBy(@NotNull String key, long amount);

    CompletableFuture<Long> decr(@NotNull String key);

    CompletableFuture<Boolean> exists(@NotNull String key);

    CompletableFuture<Long> del(@NotNull String... keys);

    CompletableFuture<Boolean> expire(@NotNull String key, long seconds);

    CompletableFuture<Long> ttl(@NotNull String key);

    CompletableFuture<List<String>> mget(@NotNull String... keys);

    CompletableFuture<Void> mset(@NotNull String... keysValues);

    // ==================== Hash Operations ====================

    CompletableFuture<@Nullable String> hget(@NotNull String key, @NotNull String field);

    CompletableFuture<Void> hset(@NotNull String key, @NotNull String field, @NotNull String value);

    CompletableFuture<Void> hmset(@NotNull String key, @NotNull Map<String, String> hash);

    CompletableFuture<Map<String, String>> hgetAll(@NotNull String key);

    CompletableFuture<Long> hdel(@NotNull String key, @NotNull String... fields);

    CompletableFuture<Boolean> hexists(@NotNull String key, @NotNull String field);

    CompletableFuture<Long> hincrBy(@NotNull String key, @NotNull String field, long amount);

    // ==================== List Operations ====================

    CompletableFuture<Long> lpush(@NotNull String key, @NotNull String... values);

    CompletableFuture<Long> rpush(@NotNull String key, @NotNull String... values);

    CompletableFuture<@Nullable String> lpop(@NotNull String key);

    CompletableFuture<@Nullable String> rpop(@NotNull String key);

    CompletableFuture<List<String>> lrange(@NotNull String key, long start, long stop);

    CompletableFuture<Long> llen(@NotNull String key);

    // ==================== Set Operations ====================

    CompletableFuture<Long> sadd(@NotNull String key, @NotNull String... members);

    CompletableFuture<Long> srem(@NotNull String key, @NotNull String... members);

    CompletableFuture<Set<String>> smembers(@NotNull String key);

    CompletableFuture<Boolean> sismember(@NotNull String key, @NotNull String member);

    CompletableFuture<Long> scard(@NotNull String key);

    // ==================== Sorted Set Operations ====================

    CompletableFuture<Long> zadd(@NotNull String key, double score, @NotNull String member);

    CompletableFuture<Long> zrem(@NotNull String key, @NotNull String... members);

    CompletableFuture<List<String>> zrange(@NotNull String key, long start, long stop);

    CompletableFuture<List<String>> zrevrange(@NotNull String key, long start, long stop);

    CompletableFuture<@Nullable Double> zscore(@NotNull String key, @NotNull String member);

    CompletableFuture<@Nullable Long> zrank(@NotNull String key, @NotNull String member);

    CompletableFuture<Long> zcard(@NotNull String key);

    // ==================== Pub/Sub ====================

    @NotNull RedisPubSub pubsub();

    // ==================== Distributed Locks ====================

    CompletableFuture<Optional<RedisLock>> tryLock(@NotNull String key, long ttlMs);

    CompletableFuture<Boolean> unlock(@NotNull RedisLock lock);
}
