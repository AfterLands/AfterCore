package com.afterlands.core.diagnostics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;

/**
 * Snapshot do estado atual do sistema para diagnóstico.
 *
 * @param timestamp         Momento da captura
 * @param dependencies      Dependências detectadas (ProtocolLib, PlaceholderAPI, etc.)
 * @param databaseInfo      Informações do database
 * @param threadPoolInfo    Informações dos thread pools
 * @param systemInfo        Informações do sistema (JVM, OS, cores)
 */
public record DiagnosticsSnapshot(
        @NotNull Instant timestamp,
        @NotNull Map<String, DependencyInfo> dependencies,
        @NotNull DatabaseInfo databaseInfo,
        @NotNull ThreadPoolInfo threadPoolInfo,
        @NotNull SystemInfo systemInfo
) {
    /**
     * Informações sobre uma dependência externa.
     */
    public record DependencyInfo(
            @NotNull String name,
            boolean present,
            @Nullable String version,
            @Nullable String status
    ) {
        public static DependencyInfo detected(@NotNull String name, @NotNull String version) {
            return new DependencyInfo(name, true, version, "detected");
        }

        public static DependencyInfo missing(@NotNull String name) {
            return new DependencyInfo(name, false, null, "missing");
        }
    }

    /**
     * Informações sobre o database.
     */
    public record DatabaseInfo(
            boolean enabled,
            boolean initialized,
            @Nullable String type,
            @Nullable String lastError,
            @Nullable Long pingMs,
            @Nullable PoolStats poolStats
    ) {
        public static DatabaseInfo disabled() {
            return new DatabaseInfo(false, false, null, null, null, null);
        }

        public static DatabaseInfo error(@NotNull String type, @NotNull String error) {
            return new DatabaseInfo(true, false, type, error, null, null);
        }

        public static DatabaseInfo healthy(@NotNull String type, long pingMs, @Nullable PoolStats stats) {
            return new DatabaseInfo(true, true, type, null, pingMs, stats);
        }
    }

    /**
     * Estatísticas do pool de conexões.
     */
    public record PoolStats(
            int totalConnections,
            int activeConnections,
            int idleConnections,
            int threadsAwaitingConnection
    ) {}

    /**
     * Informações sobre thread pools.
     */
    public record ThreadPoolInfo(
            int ioThreads,
            int cpuThreads,
            @Nullable String ioPoolStatus,
            @Nullable String cpuPoolStatus
    ) {}

    /**
     * Informações do sistema.
     */
    public record SystemInfo(
            int availableProcessors,
            long maxMemoryMb,
            long totalMemoryMb,
            long freeMemoryMb,
            @NotNull String javaVersion,
            @NotNull String osName,
            @NotNull String osVersion
    ) {
        public static SystemInfo capture() {
            Runtime runtime = Runtime.getRuntime();
            return new SystemInfo(
                    runtime.availableProcessors(),
                    runtime.maxMemory() / 1024 / 1024,
                    runtime.totalMemory() / 1024 / 1024,
                    runtime.freeMemory() / 1024 / 1024,
                    System.getProperty("java.version", "unknown"),
                    System.getProperty("os.name", "unknown"),
                    System.getProperty("os.version", "unknown")
            );
        }
    }
}
