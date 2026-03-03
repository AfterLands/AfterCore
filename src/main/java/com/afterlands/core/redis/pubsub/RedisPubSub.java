package com.afterlands.core.redis.pubsub;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Interface para operações Redis Pub/Sub.
 *
 * <p>
 * Publish usa o IO executor do SchedulerService.
 * Subscribe/unsubscribe são gerenciados por thread dedicada.
 * </p>
 *
 * @since 1.8.0
 */
public interface RedisPubSub {

    CompletableFuture<Long> publish(@NotNull String channel, @NotNull String message);

    void subscribe(@NotNull String channel, @NotNull RedisMessageListener listener);

    void psubscribe(@NotNull String pattern, @NotNull RedisMessageListener listener);

    void unsubscribe(@NotNull String channel, @NotNull RedisMessageListener listener);

    void punsubscribe(@NotNull String pattern, @NotNull RedisMessageListener listener);

    void unsubscribeAll(@NotNull String channel);

    @NotNull Set<String> getSubscribedChannels();

    @NotNull Set<String> getSubscribedPatterns();

    boolean isActive();

    void shutdown();
}
