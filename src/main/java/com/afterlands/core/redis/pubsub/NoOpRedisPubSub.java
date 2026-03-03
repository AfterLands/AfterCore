package com.afterlands.core.redis.pubsub;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * No-op Pub/Sub usado quando Redis está desabilitado.
 *
 * @since 1.8.0
 */
public final class NoOpRedisPubSub implements RedisPubSub {

    @Override
    public CompletableFuture<Long> publish(@NotNull String channel, @NotNull String message) {
        return CompletableFuture.completedFuture(0L);
    }

    @Override
    public void subscribe(@NotNull String channel, @NotNull RedisMessageListener listener) {}

    @Override
    public void psubscribe(@NotNull String pattern, @NotNull RedisMessageListener listener) {}

    @Override
    public void unsubscribe(@NotNull String channel, @NotNull RedisMessageListener listener) {}

    @Override
    public void punsubscribe(@NotNull String pattern, @NotNull RedisMessageListener listener) {}

    @Override
    public void unsubscribeAll(@NotNull String channel) {}

    @Override
    public @NotNull Set<String> getSubscribedChannels() {
        return Collections.emptySet();
    }

    @Override
    public @NotNull Set<String> getSubscribedPatterns() {
        return Collections.emptySet();
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void shutdown() {}
}
