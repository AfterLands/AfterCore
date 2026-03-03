package com.afterlands.core.redis.pubsub;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Mensagem recebida via Redis Pub/Sub.
 *
 * @param channel canal em que a mensagem foi publicada
 * @param payload conteúdo da mensagem
 * @param pattern pattern que matchou (null se subscribe normal, preenchido se psubscribe)
 * @since 1.8.0
 */
public record RedisMessage(
        @NotNull String channel,
        @NotNull String payload,
        @Nullable String pattern
) {}
