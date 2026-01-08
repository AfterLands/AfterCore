package com.afterlands.core.metrics;

/**
 * Tipo de métrica.
 */
public enum MetricType {
    /**
     * Contador incremental (ex: queries executadas, eventos processados).
     */
    COUNTER,

    /**
     * Timer para medir duração de operações (ex: tempo de query, tempo de processamento).
     */
    TIMER,

    /**
     * Gauge para valores instantâneos (ex: players online, conexões ativas).
     */
    GAUGE
}
