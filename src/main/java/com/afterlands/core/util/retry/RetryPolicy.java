package com.afterlands.core.util.retry;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Política de retry para operações que podem falhar transitoriamente.
 *
 * <p>Configuração:
 * <ul>
 *   <li>Max retries: limite de tentativas</li>
 *   <li>Max elapsed: tempo máximo total (incluindo todos os retries)</li>
 *   <li>Backoff: estratégia de backoff entre retries</li>
 *   <li>Retryable predicate: determina se exceção deve ser retried</li>
 * </ul>
 * </p>
 */
public final class RetryPolicy {

    private final int maxRetries;
    private final Duration maxElapsed;
    private final Backoff backoff;
    private final Predicate<Throwable> retryablePredicate;

    private RetryPolicy(int maxRetries,
                       Duration maxElapsed,
                       Backoff backoff,
                       Predicate<Throwable> retryablePredicate) {
        this.maxRetries = maxRetries;
        this.maxElapsed = maxElapsed;
        this.backoff = backoff;
        this.retryablePredicate = retryablePredicate;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public Duration maxElapsed() {
        return maxElapsed;
    }

    public Backoff backoff() {
        return backoff;
    }

    /**
     * Verifica se exceção deve ser retried.
     */
    public boolean isRetryable(@NotNull Throwable throwable) {
        return retryablePredicate.test(throwable);
    }

    /**
     * Calcula o delay para o próximo retry.
     *
     * @param attempt Número da tentativa (1-indexed)
     * @return delay antes do próximo retry
     */
    @NotNull
    public Duration calculateDelay(int attempt) {
        return backoff.calculateDelay(attempt);
    }

    /**
     * Builder para RetryPolicy.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Política padrão para database/network: 3 retries com exponential backoff.
     */
    @NotNull
    public static RetryPolicy defaultDatabasePolicy() {
        return builder()
                .maxRetries(3)
                .maxElapsed(Duration.ofSeconds(30))
                .backoff(Backoff.Exponential.defaultBackoff())
                .retryOnSqlExceptions()
                .build();
    }

    /**
     * Política agressiva: mais retries, mais tempo.
     */
    @NotNull
    public static RetryPolicy aggressivePolicy() {
        return builder()
                .maxRetries(5)
                .maxElapsed(Duration.ofMinutes(2))
                .backoff(new Backoff.Exponential(
                        Duration.ofMillis(200),
                        Duration.ofSeconds(30),
                        0.2
                ))
                .retryOnSqlExceptions()
                .build();
    }

    public static final class Builder {
        private int maxRetries = 3;
        private Duration maxElapsed = Duration.ofSeconds(30);
        private Backoff backoff = Backoff.Exponential.defaultBackoff();
        private final Set<Class<? extends Throwable>> retryableExceptions = new HashSet<>();
        private Predicate<Throwable> customPredicate = null;

        private Builder() {}

        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be >= 0");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder maxElapsed(@NotNull Duration maxElapsed) {
            if (maxElapsed.isNegative()) {
                throw new IllegalArgumentException("maxElapsed must be positive");
            }
            this.maxElapsed = maxElapsed;
            return this;
        }

        public Builder backoff(@NotNull Backoff backoff) {
            this.backoff = backoff;
            return this;
        }

        /**
         * Adiciona uma classe de exceção que deve ser retried.
         */
        @SafeVarargs
        public final Builder retryOn(@NotNull Class<? extends Throwable>... exceptionClasses) {
            for (Class<? extends Throwable> clazz : exceptionClasses) {
                retryableExceptions.add(clazz);
            }
            return this;
        }

        /**
         * Adiciona exceções SQL comuns como retryable.
         */
        public Builder retryOnSqlExceptions() {
            try {
                // SQLException
                retryableExceptions.add(Class.forName("java.sql.SQLException").asSubclass(Throwable.class));
                // SQLTransientException (conexão perdida, timeout, etc.)
                retryableExceptions.add(Class.forName("java.sql.SQLTransientException").asSubclass(Throwable.class));
            } catch (ClassNotFoundException e) {
                // Não deveria acontecer, mas ignorar se classes SQL não existirem
            }
            return this;
        }

        /**
         * Adiciona predicado customizado para determinar se exceção é retryable.
         */
        public Builder retryIf(@NotNull Predicate<Throwable> predicate) {
            this.customPredicate = predicate;
            return this;
        }

        @NotNull
        public RetryPolicy build() {
            Predicate<Throwable> finalPredicate;

            if (customPredicate != null) {
                finalPredicate = customPredicate;
            } else if (!retryableExceptions.isEmpty()) {
                finalPredicate = throwable -> {
                    for (Class<? extends Throwable> clazz : retryableExceptions) {
                        if (clazz.isInstance(throwable)) {
                            return true;
                        }
                    }
                    return false;
                };
            } else {
                // Default: retry todas as exceções (não recomendado, mas funcional)
                finalPredicate = t -> true;
            }

            return new RetryPolicy(maxRetries, maxElapsed, backoff, finalPredicate);
        }
    }
}
