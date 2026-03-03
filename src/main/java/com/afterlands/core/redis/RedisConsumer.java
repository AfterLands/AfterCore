package com.afterlands.core.redis;

/**
 * Functional interface para operações Redis sem retorno.
 *
 * @param <I> tipo de entrada (ex: Jedis)
 * @since 1.8.0
 */
@FunctionalInterface
public interface RedisConsumer<I> {
    void accept(I input) throws Exception;
}
