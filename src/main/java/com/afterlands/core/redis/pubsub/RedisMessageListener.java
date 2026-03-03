package com.afterlands.core.redis.pubsub;

import org.jetbrains.annotations.NotNull;

/**
 * Listener para mensagens Redis Pub/Sub.
 *
 * <p>
 * <b>ATENÇÃO:</b> Callbacks rodam na subscriber thread do Redis.
 * Para acessar a Bukkit API, use {@code scheduler.runSync()}.
 * </p>
 *
 * @since 1.8.0
 */
@FunctionalInterface
public interface RedisMessageListener {
    void onMessage(@NotNull RedisMessage message);
}
