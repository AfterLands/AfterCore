package com.afterlands.core.metrics;

import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Serviço leve de métricas para monitorar performance do AfterCore.
 *
 * <p>Design:
 * <ul>
 *   <li>Métricas baratas por default (contadores simples, timers baseados em nanoTime)</li>
 *   <li>Thread-safe</li>
 *   <li>Overhead mínimo em hot-path</li>
 *   <li>Suporta debug/sampling para métricas detalhadas</li>
 * </ul>
 * </p>
 *
 * <p>Casos de uso:
 * <ul>
 *   <li>Contar queries executadas</li>
 *   <li>Medir tempo de processamento de eventos</li>
 *   <li>Monitorar hit rate de caches</li>
 *   <li>Detectar regressões de performance</li>
 * </ul>
 * </p>
 */
public interface MetricsService {

    /**
     * Incrementa um contador.
     *
     * @param name Nome da métrica
     */
    void increment(@NotNull String name);

    /**
     * Incrementa um contador por um valor específico.
     *
     * @param name  Nome da métrica
     * @param delta Valor a incrementar
     */
    void increment(@NotNull String name, long delta);

    /**
     * Registra um tempo de execução (em nanosegundos).
     *
     * @param name   Nome da métrica
     * @param nanos  Tempo em nanosegundos
     */
    void recordTime(@NotNull String name, long nanos);

    /**
     * Mede o tempo de execução de uma operação.
     *
     * <p>Exemplo:
     * <pre>{@code
     * metrics.time("database.query", () -> {
     *     // código a medir
     *     return result;
     * });
     * }</pre>
     *
     * @param name      Nome da métrica
     * @param operation Operação a medir
     * @return resultado da operação
     */
    @NotNull <T> T time(@NotNull String name, @NotNull Supplier<T> operation);

    /**
     * Registra um valor de gauge (valor instantâneo).
     *
     * @param name  Nome da métrica
     * @param value Valor atual
     */
    void gauge(@NotNull String name, double value);

    /**
     * Captura snapshot de todas as métricas.
     *
     * @return snapshot com contadores, timers e gauges
     */
    @NotNull MetricsSnapshot snapshot();

    /**
     * Reseta todas as métricas.
     */
    void reset();

    /**
     * Reseta uma métrica específica.
     *
     * @param name Nome da métrica
     */
    void reset(@NotNull String name);
}
