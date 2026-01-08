package com.afterlands.core.util.retry;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Estratégia de backoff para retries.
 *
 * <p>Implementa backoff exponencial com jitter para evitar thundering herd.</p>
 */
public sealed interface Backoff permits Backoff.Exponential, Backoff.Fixed, Backoff.Linear {

    /**
     * Calcula o delay para o próximo retry.
     *
     * @param attempt Número da tentativa (1-indexed: 1, 2, 3, ...)
     * @return delay antes do próximo retry
     */
    @NotNull Duration calculateDelay(int attempt);

    /**
     * Backoff exponencial: delay = base * 2^(attempt-1) + jitter
     */
    record Exponential(
            @NotNull Duration baseDelay,
            @NotNull Duration maxDelay,
            double jitterFactor
    ) implements Backoff {
        public Exponential {
            if (baseDelay.isNegative() || baseDelay.isZero()) {
                throw new IllegalArgumentException("baseDelay must be positive");
            }
            if (maxDelay.compareTo(baseDelay) < 0) {
                throw new IllegalArgumentException("maxDelay must be >= baseDelay");
            }
            if (jitterFactor < 0 || jitterFactor > 1) {
                throw new IllegalArgumentException("jitterFactor must be between 0 and 1");
            }
        }

        public static Exponential defaultBackoff() {
            return new Exponential(
                    Duration.ofMillis(100),
                    Duration.ofSeconds(10),
                    0.1
            );
        }

        @Override
        @NotNull
        public Duration calculateDelay(int attempt) {
            if (attempt <= 0) {
                throw new IllegalArgumentException("attempt must be positive");
            }

            // Exponential: base * 2^(attempt-1)
            long baseMs = baseDelay.toMillis();
            long exponentialMs = baseMs * (1L << (attempt - 1));

            // Cap at maxDelay
            long delayMs = Math.min(exponentialMs, maxDelay.toMillis());

            // Add jitter: random between [delay * (1 - jitter), delay * (1 + jitter)]
            if (jitterFactor > 0) {
                Random rng = ThreadLocalRandom.current();
                double jitterMultiplier = 1.0 + (rng.nextDouble() * 2 - 1) * jitterFactor;
                delayMs = (long) (delayMs * jitterMultiplier);
            }

            return Duration.ofMillis(Math.max(0, delayMs));
        }
    }

    /**
     * Backoff fixo: sempre o mesmo delay.
     */
    record Fixed(@NotNull Duration delay) implements Backoff {
        public Fixed {
            if (delay.isNegative() || delay.isZero()) {
                throw new IllegalArgumentException("delay must be positive");
            }
        }

        @Override
        @NotNull
        public Duration calculateDelay(int attempt) {
            return delay;
        }
    }

    /**
     * Backoff linear: delay = base * attempt
     */
    record Linear(
            @NotNull Duration baseDelay,
            @NotNull Duration maxDelay
    ) implements Backoff {
        public Linear {
            if (baseDelay.isNegative() || baseDelay.isZero()) {
                throw new IllegalArgumentException("baseDelay must be positive");
            }
            if (maxDelay.compareTo(baseDelay) < 0) {
                throw new IllegalArgumentException("maxDelay must be >= baseDelay");
            }
        }

        @Override
        @NotNull
        public Duration calculateDelay(int attempt) {
            if (attempt <= 0) {
                throw new IllegalArgumentException("attempt must be positive");
            }

            long delayMs = baseDelay.toMillis() * attempt;
            delayMs = Math.min(delayMs, maxDelay.toMillis());

            return Duration.ofMillis(delayMs);
        }
    }
}
