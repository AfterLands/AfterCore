package com.afterlands.core.util.retry;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Executor que aplica retry policy automaticamente.
 *
 * <p>Thread-safe e pode ser reutilizado.</p>
 */
public final class RetryExecutor {

    private final RetryPolicy policy;
    private final Logger logger;
    private final boolean debug;

    public RetryExecutor(@NotNull RetryPolicy policy, @NotNull Logger logger, boolean debug) {
        this.policy = policy;
        this.logger = logger;
        this.debug = debug;
    }

    /**
     * Executa operação com retry policy.
     *
     * <p>ATENÇÃO: operação bloqueante. Deve ser executada em thread async.</p>
     *
     * @param operation Operação a executar
     * @return resultado da operação
     * @throws Exception se todas as tentativas falharem
     */
    @NotNull
    public <T> T execute(@NotNull Callable<T> operation) throws Exception {
        long startTime = System.nanoTime();
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= policy.maxRetries()) {
            attempt++;

            try {
                if (debug && attempt > 1) {
                    logger.fine("Retry attempt " + attempt + "/" + (policy.maxRetries() + 1));
                }

                return operation.call();
            } catch (Exception e) {
                lastException = e;

                // Verificar se deve retry
                if (!policy.isRetryable(e)) {
                    if (debug) {
                        logger.fine("Exception not retryable: " + e.getClass().getSimpleName());
                    }
                    throw e;
                }

                // Verificar se atingiu max retries
                if (attempt > policy.maxRetries()) {
                    if (debug) {
                        logger.fine("Max retries exceeded (" + policy.maxRetries() + ")");
                    }
                    break;
                }

                // Verificar se atingiu max elapsed time
                long elapsedNanos = System.nanoTime() - startTime;
                Duration elapsed = Duration.ofNanos(elapsedNanos);
                if (elapsed.compareTo(policy.maxElapsed()) >= 0) {
                    if (debug) {
                        logger.fine("Max elapsed time exceeded (" + policy.maxElapsed() + ")");
                    }
                    break;
                }

                // Calcular e aplicar backoff
                Duration delay = policy.calculateDelay(attempt);
                if (debug) {
                    logger.fine("Backing off for " + delay.toMillis() + "ms before retry");
                }

                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry backoff", ie);
                }
            }
        }

        // Todas as tentativas falharam
        logger.warning("Operation failed after " + attempt + " attempts");
        throw lastException;
    }

    /**
     * Executa operação com retry policy de forma assíncrona.
     *
     * @param operation Operação a executar
     * @param executor  Executor para executar a operação
     * @return CompletableFuture com resultado
     */
    @NotNull
    public <T> CompletableFuture<T> executeAsync(@NotNull Callable<T> operation, @NotNull Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return execute(operation);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }
}
