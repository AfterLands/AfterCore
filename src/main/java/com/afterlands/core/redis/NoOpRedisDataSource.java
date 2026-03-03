package com.afterlands.core.redis;

import com.afterlands.core.redis.lock.RedisLock;
import com.afterlands.core.redis.pubsub.NoOpRedisPubSub;
import com.afterlands.core.redis.pubsub.RedisPubSub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * No-op datasource usado quando Redis está desabilitado.
 *
 * @since 1.8.0
 */
public final class NoOpRedisDataSource implements RedisDataSource {

    public static final NoOpRedisDataSource INSTANCE = new NoOpRedisDataSource();

    private static final RedisPubSub NO_OP_PUBSUB = new NoOpRedisPubSub();

    @Override public @NotNull String name() { return "noop"; }
    @Override public @NotNull RedisTopology topology() { return RedisTopology.STANDALONE; }
    @Override public boolean isEnabled() { return false; }
    @Override public boolean isInitialized() { return false; }
    @Override public @NotNull CompletableFuture<Boolean> isAvailable() { return CompletableFuture.completedFuture(false); }
    @Override public @NotNull Map<String, Object> getPoolStats() { return Map.of(); }
    @Override public void close() {}

    // Raw
    @Override public <T> CompletableFuture<T> execute(@NotNull RedisFunction<Jedis, T> fn) { return completed(null); }
    @Override public CompletableFuture<Void> run(@NotNull RedisConsumer<Jedis> fn) { return completed(null); }
    @Override public <T> CompletableFuture<List<T>> pipeline(@NotNull RedisFunction<Pipeline, List<T>> fn) { return completed(Collections.emptyList()); }

    // String
    @Override public CompletableFuture<@Nullable String> get(@NotNull String key) { return completed(null); }
    @Override public CompletableFuture<Void> set(@NotNull String key, @NotNull String value) { return completed(null); }
    @Override public CompletableFuture<Void> setex(@NotNull String key, long seconds, @NotNull String value) { return completed(null); }
    @Override public CompletableFuture<Boolean> setnx(@NotNull String key, @NotNull String value) { return completed(false); }
    @Override public CompletableFuture<Long> incr(@NotNull String key) { return completed(0L); }
    @Override public CompletableFuture<Long> incrBy(@NotNull String key, long amount) { return completed(0L); }
    @Override public CompletableFuture<Long> decr(@NotNull String key) { return completed(0L); }
    @Override public CompletableFuture<Boolean> exists(@NotNull String key) { return completed(false); }
    @Override public CompletableFuture<Long> del(@NotNull String... keys) { return completed(0L); }
    @Override public CompletableFuture<Boolean> expire(@NotNull String key, long seconds) { return completed(false); }
    @Override public CompletableFuture<Long> ttl(@NotNull String key) { return completed(-2L); }
    @Override public CompletableFuture<List<String>> mget(@NotNull String... keys) { return completed(Collections.emptyList()); }
    @Override public CompletableFuture<Void> mset(@NotNull String... keysValues) { return completed(null); }

    // Hash
    @Override public CompletableFuture<@Nullable String> hget(@NotNull String key, @NotNull String field) { return completed(null); }
    @Override public CompletableFuture<Void> hset(@NotNull String key, @NotNull String field, @NotNull String value) { return completed(null); }
    @Override public CompletableFuture<Void> hmset(@NotNull String key, @NotNull Map<String, String> hash) { return completed(null); }
    @Override public CompletableFuture<Map<String, String>> hgetAll(@NotNull String key) { return completed(Collections.emptyMap()); }
    @Override public CompletableFuture<Long> hdel(@NotNull String key, @NotNull String... fields) { return completed(0L); }
    @Override public CompletableFuture<Boolean> hexists(@NotNull String key, @NotNull String field) { return completed(false); }
    @Override public CompletableFuture<Long> hincrBy(@NotNull String key, @NotNull String field, long amount) { return completed(0L); }

    // List
    @Override public CompletableFuture<Long> lpush(@NotNull String key, @NotNull String... values) { return completed(0L); }
    @Override public CompletableFuture<Long> rpush(@NotNull String key, @NotNull String... values) { return completed(0L); }
    @Override public CompletableFuture<@Nullable String> lpop(@NotNull String key) { return completed(null); }
    @Override public CompletableFuture<@Nullable String> rpop(@NotNull String key) { return completed(null); }
    @Override public CompletableFuture<List<String>> lrange(@NotNull String key, long start, long stop) { return completed(Collections.emptyList()); }
    @Override public CompletableFuture<Long> llen(@NotNull String key) { return completed(0L); }

    // Set
    @Override public CompletableFuture<Long> sadd(@NotNull String key, @NotNull String... members) { return completed(0L); }
    @Override public CompletableFuture<Long> srem(@NotNull String key, @NotNull String... members) { return completed(0L); }
    @Override public CompletableFuture<Set<String>> smembers(@NotNull String key) { return completed(Collections.emptySet()); }
    @Override public CompletableFuture<Boolean> sismember(@NotNull String key, @NotNull String member) { return completed(false); }
    @Override public CompletableFuture<Long> scard(@NotNull String key) { return completed(0L); }

    // Sorted Set
    @Override public CompletableFuture<Long> zadd(@NotNull String key, double score, @NotNull String member) { return completed(0L); }
    @Override public CompletableFuture<Long> zrem(@NotNull String key, @NotNull String... members) { return completed(0L); }
    @Override public CompletableFuture<List<String>> zrange(@NotNull String key, long start, long stop) { return completed(Collections.emptyList()); }
    @Override public CompletableFuture<List<String>> zrevrange(@NotNull String key, long start, long stop) { return completed(Collections.emptyList()); }
    @Override public CompletableFuture<@Nullable Double> zscore(@NotNull String key, @NotNull String member) { return completed(null); }
    @Override public CompletableFuture<@Nullable Long> zrank(@NotNull String key, @NotNull String member) { return completed(null); }
    @Override public CompletableFuture<Long> zcard(@NotNull String key) { return completed(0L); }

    // Pub/Sub
    @Override public @NotNull RedisPubSub pubsub() { return NO_OP_PUBSUB; }

    // Locks
    @Override public CompletableFuture<Optional<RedisLock>> tryLock(@NotNull String key, long ttlMs) { return completed(Optional.empty()); }
    @Override public CompletableFuture<Boolean> unlock(@NotNull RedisLock lock) { return completed(false); }

    private static <T> CompletableFuture<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }
}
