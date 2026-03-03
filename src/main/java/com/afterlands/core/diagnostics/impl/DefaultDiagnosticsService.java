package com.afterlands.core.diagnostics.impl;

import com.afterlands.core.database.SqlService;
import com.afterlands.core.diagnostics.DiagnosticsService;
import com.afterlands.core.diagnostics.DiagnosticsSnapshot;
import com.afterlands.core.diagnostics.DiagnosticsSnapshot.*;
import com.afterlands.core.redis.RedisService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementação padrão do DiagnosticsService.
 */
public final class DefaultDiagnosticsService implements DiagnosticsService {

    private final Plugin plugin;
    private final SqlService sqlService;
    private final RedisService redisService;
    private final int ioThreads;
    private final int cpuThreads;

    public DefaultDiagnosticsService(@NotNull Plugin plugin,
                                    @NotNull SqlService sqlService,
                                    @NotNull RedisService redisService,
                                    int ioThreads,
                                    int cpuThreads) {
        this.plugin = plugin;
        this.sqlService = sqlService;
        this.redisService = redisService;
        this.ioThreads = ioThreads;
        this.cpuThreads = cpuThreads;
    }

    @Override
    @NotNull
    public DiagnosticsSnapshot captureSnapshot() {
        return new DiagnosticsSnapshot(
                Instant.now(),
                detectDependencies(),
                captureDatabaseInfo(),
                captureRedisInfo(),
                captureThreadPoolInfo(),
                SystemInfo.capture()
        );
    }

    @Override
    public long pingDatabase() {
        if (!sqlService.isEnabled() || !sqlService.isInitialized()) {
            return -1;
        }

        try {
            long start = System.nanoTime();
            try (Connection conn = sqlService.getConnection()) {
                // Simple validation query
                conn.isValid(5);
            }
            long elapsed = System.nanoTime() - start;
            return elapsed / 1_000_000; // Convert to ms
        } catch (Exception e) {
            plugin.getLogger().warning("Database ping failed: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public long pingRedis() {
        if (!redisService.isEnabled() || !redisService.isInitialized()) {
            return -1;
        }

        try {
            long start = System.nanoTime();
            Boolean available = redisService.isAvailable().get(5, java.util.concurrent.TimeUnit.SECONDS);
            long elapsed = System.nanoTime() - start;
            return Boolean.TRUE.equals(available) ? elapsed / 1_000_000 : -1;
        } catch (Exception e) {
            plugin.getLogger().warning("Redis ping failed: " + e.getMessage());
            return -1;
        }
    }

    private RedisInfo captureRedisInfo() {
        if (!redisService.isEnabled()) {
            return RedisInfo.disabled();
        }

        if (!redisService.isInitialized()) {
            return RedisInfo.error("unknown");
        }

        try {
            Map<String, Object> stats = redisService.getPoolStats();
            RedisPoolStats poolStats = null;
            if (!stats.isEmpty()) {
                poolStats = new RedisPoolStats(
                        ((Number) stats.getOrDefault("active", 0)).intValue(),
                        ((Number) stats.getOrDefault("idle", 0)).intValue(),
                        ((Number) stats.getOrDefault("waiters", 0)).intValue(),
                        ((Number) stats.getOrDefault("max_total", 0)).intValue()
                );
            }

            String topology = String.valueOf(stats.getOrDefault("topology", "unknown"));
            return RedisInfo.healthy(topology, -1, poolStats);
        } catch (Exception e) {
            return RedisInfo.error("unknown");
        }
    }

    private Map<String, DependencyInfo> detectDependencies() {
        Map<String, DependencyInfo> deps = new HashMap<>();

        // ProtocolLib
        Plugin protocolLib = Bukkit.getPluginManager().getPlugin("ProtocolLib");
        if (protocolLib != null && protocolLib.isEnabled()) {
            deps.put("ProtocolLib", DependencyInfo.detected("ProtocolLib",
                    protocolLib.getDescription().getVersion()));
        } else {
            deps.put("ProtocolLib", DependencyInfo.missing("ProtocolLib"));
        }

        // PlaceholderAPI
        Plugin placeholderAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholderAPI != null && placeholderAPI.isEnabled()) {
            deps.put("PlaceholderAPI", DependencyInfo.detected("PlaceholderAPI",
                    placeholderAPI.getDescription().getVersion()));
        } else {
            deps.put("PlaceholderAPI", DependencyInfo.missing("PlaceholderAPI"));
        }

        return deps;
    }

    private DatabaseInfo captureDatabaseInfo() {
        if (!sqlService.isEnabled()) {
            return DatabaseInfo.disabled();
        }

        if (!sqlService.isInitialized()) {
            return DatabaseInfo.error("unknown", "Not initialized");
        }

        try {
            // Obter stats do pool via API pública do SqlService
            PoolStats poolStats = null;
            Map<String, Object> stats = sqlService.getPoolStats();
            if (!stats.isEmpty()) {
                poolStats = new PoolStats(
                        (Integer) stats.getOrDefault("total_connections", 0),
                        (Integer) stats.getOrDefault("active_connections", 0),
                        (Integer) stats.getOrDefault("idle_connections", 0),
                        (Integer) stats.getOrDefault("threads_awaiting_connection", 0)
                );
            }

            // Ping (non-blocking snapshot - retorna último valor se disponível)
            long ping = -1; // Ping será feito separadamente via pingDatabase()

            return DatabaseInfo.healthy("unknown", ping, poolStats);
        } catch (Exception e) {
            return DatabaseInfo.error("unknown", e.getMessage());
        }
    }

    private ThreadPoolInfo captureThreadPoolInfo() {
        // Thread pools são fixed, então apenas retornar configuração
        return new ThreadPoolInfo(
                ioThreads,
                cpuThreads,
                "running",
                "running"
        );
    }
}
