package com.afterlands.core.metrics;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;

/**
 * Snapshot de métricas em um momento específico.
 *
 * @param timestamp Momento da captura
 * @param counters  Contadores (nome -> valor)
 * @param timers    Timers (nome -> estatísticas)
 * @param gauges    Gauges (nome -> valor)
 */
public record MetricsSnapshot(
        @NotNull Instant timestamp,
        @NotNull Map<String, Long> counters,
        @NotNull Map<String, TimerStats> timers,
        @NotNull Map<String, Double> gauges
) {
    /**
     * Estatísticas de um timer.
     *
     * @param count Número de medições
     * @param totalMs Tempo total em ms
     * @param avgMs Tempo médio em ms
     * @param minMs Tempo mínimo em ms
     * @param maxMs Tempo máximo em ms
     */
    public record TimerStats(
            long count,
            double totalMs,
            double avgMs,
            double minMs,
            double maxMs
    ) {
        public static TimerStats empty() {
            return new TimerStats(0, 0, 0, 0, 0);
        }

        public static TimerStats of(long count, double totalMs, double minMs, double maxMs) {
            double avgMs = count > 0 ? totalMs / count : 0;
            return new TimerStats(count, totalMs, avgMs, minMs, maxMs);
        }
    }

    /**
     * Formata o snapshot em texto legível.
     */
    @NotNull
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("Metrics Snapshot @ ").append(timestamp).append("\n");

        if (!counters.isEmpty()) {
            sb.append("\nCounters:\n");
            counters.forEach((name, value) ->
                    sb.append("  ").append(name).append(": ").append(value).append("\n"));
        }

        if (!timers.isEmpty()) {
            sb.append("\nTimers:\n");
            timers.forEach((name, stats) ->
                    sb.append("  ").append(name).append(": ")
                            .append("count=").append(stats.count)
                            .append(", avg=").append(String.format("%.2f", stats.avgMs)).append("ms")
                            .append(", min=").append(String.format("%.2f", stats.minMs)).append("ms")
                            .append(", max=").append(String.format("%.2f", stats.maxMs)).append("ms")
                            .append(", total=").append(String.format("%.2f", stats.totalMs)).append("ms")
                            .append("\n"));
        }

        if (!gauges.isEmpty()) {
            sb.append("\nGauges:\n");
            gauges.forEach((name, value) ->
                    sb.append("  ").append(name).append(": ").append(String.format("%.2f", value)).append("\n"));
        }

        return sb.toString();
    }
}
