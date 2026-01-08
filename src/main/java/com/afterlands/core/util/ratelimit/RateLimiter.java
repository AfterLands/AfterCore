package com.afterlands.core.util.ratelimit;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Interface para rate limiting.
 *
 * <p>Implementações devem ser thread-safe.</p>
 */
public interface RateLimiter {

    /**
     * Tenta adquirir permissão para executar uma operação.
     *
     * @param key Chave para rate limiting (ex: UUID do player, nome do comando, etc.)
     * @return true se operação é permitida, false se está em cooldown/rate limited
     */
    boolean tryAcquire(@NotNull String key);

    /**
     * Tenta adquirir permissão, retornando tempo restante se negado.
     *
     * @param key Chave para rate limiting
     * @return resultado com permissão e tempo restante
     */
    @NotNull
    AcquireResult tryAcquireWithRemaining(@NotNull String key);

    /**
     * Reseta o rate limit para uma chave específica.
     *
     * @param key Chave a resetar
     */
    void reset(@NotNull String key);

    /**
     * Limpa todos os rate limits.
     */
    void clear();

    /**
     * Resultado de tentativa de aquisição.
     *
     * @param allowed    Se operação é permitida
     * @param remaining  Tempo restante até próxima tentativa (Duration.ZERO se allowed=true)
     */
    record AcquireResult(boolean allowed, @NotNull Duration remaining) {
        public static AcquireResult success() {
            return new AcquireResult(true, Duration.ZERO);
        }

        public static AcquireResult failure(@NotNull Duration remaining) {
            return new AcquireResult(false, remaining);
        }
    }
}
