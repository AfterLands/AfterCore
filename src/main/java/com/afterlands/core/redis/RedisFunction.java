package com.afterlands.core.redis;

/**
 * Functional interface para operações Redis que retornam resultado.
 *
 * @param <I> tipo de entrada (ex: Jedis, Pipeline)
 * @param <O> tipo de saída
 * @since 1.8.0
 */
@FunctionalInterface
public interface RedisFunction<I, O> {
    O apply(I input) throws Exception;
}
