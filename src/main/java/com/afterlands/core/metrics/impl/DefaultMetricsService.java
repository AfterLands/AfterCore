package com.afterlands.core.metrics.impl;

import com.afterlands.core.metrics.MetricsService;
import com.afterlands.core.metrics.MetricsSnapshot;
import com.afterlands.core.metrics.MetricsSnapshot.TimerStats;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Implementação leve do MetricsService.
 *
 * <p>Thread-safe usando ConcurrentHashMap e atomics.</p>
 */
public final class DefaultMetricsService implements MetricsService {

    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TimerMetric> timers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicDouble> gauges = new ConcurrentHashMap<>();

    @Override
    public void increment(@NotNull String name) {
        increment(name, 1);
    }

    @Override
    public void increment(@NotNull String name, long delta) {
        counters.computeIfAbsent(name, k -> new LongAdder()).add(delta);
    }

    @Override
    public void recordTime(@NotNull String name, long nanos) {
        timers.computeIfAbsent(name, k -> new TimerMetric()).record(nanos);
    }

    @Override
    @NotNull
    public <T> T time(@NotNull String name, @NotNull Supplier<T> operation) {
        long start = System.nanoTime();
        try {
            return operation.get();
        } finally {
            long elapsed = System.nanoTime() - start;
            recordTime(name, elapsed);
        }
    }

    @Override
    public void gauge(@NotNull String name, double value) {
        gauges.computeIfAbsent(name, k -> new AtomicDouble()).set(value);
    }

    @Override
    @NotNull
    public MetricsSnapshot snapshot() {
        Map<String, Long> counterSnapshot = new HashMap<>();
        counters.forEach((name, adder) -> counterSnapshot.put(name, adder.sum()));

        Map<String, TimerStats> timerSnapshot = new HashMap<>();
        timers.forEach((name, timer) -> timerSnapshot.put(name, timer.stats()));

        Map<String, Double> gaugeSnapshot = new HashMap<>();
        gauges.forEach((name, atomic) -> gaugeSnapshot.put(name, atomic.get()));

        return new MetricsSnapshot(
                Instant.now(),
                counterSnapshot,
                timerSnapshot,
                gaugeSnapshot
        );
    }

    @Override
    public void reset() {
        counters.clear();
        timers.clear();
        gauges.clear();
    }

    @Override
    public void reset(@NotNull String name) {
        counters.remove(name);
        timers.remove(name);
        gauges.remove(name);
    }

    /**
     * Métrica de timer thread-safe.
     */
    private static final class TimerMetric {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong minNanos = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxNanos = new AtomicLong(Long.MIN_VALUE);

        void record(long nanos) {
            count.increment();
            totalNanos.add(nanos);

            // Update min
            long currentMin;
            do {
                currentMin = minNanos.get();
                if (nanos >= currentMin) break;
            } while (!minNanos.compareAndSet(currentMin, nanos));

            // Update max
            long currentMax;
            do {
                currentMax = maxNanos.get();
                if (nanos <= currentMax) break;
            } while (!maxNanos.compareAndSet(currentMax, nanos));
        }

        TimerStats stats() {
            long cnt = count.sum();
            if (cnt == 0) {
                return TimerStats.empty();
            }

            double totalMs = totalNanos.sum() / 1_000_000.0;
            double minMs = minNanos.get() / 1_000_000.0;
            double maxMs = maxNanos.get() / 1_000_000.0;

            return TimerStats.of(cnt, totalMs, minMs, maxMs);
        }
    }

    /**
     * AtomicDouble (Java não tem nativo).
     */
    private static final class AtomicDouble {
        private final AtomicLong bits;

        AtomicDouble() {
            this(0.0);
        }

        AtomicDouble(double initialValue) {
            bits = new AtomicLong(Double.doubleToLongBits(initialValue));
        }

        void set(double newValue) {
            bits.set(Double.doubleToLongBits(newValue));
        }

        double get() {
            return Double.longBitsToDouble(bits.get());
        }
    }
}
