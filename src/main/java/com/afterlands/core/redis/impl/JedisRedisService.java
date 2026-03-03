package com.afterlands.core.redis.impl;

import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.redis.RedisDataSource;
import com.afterlands.core.redis.RedisService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registry de datasources Redis baseado em Jedis.
 *
 * <p>
 * Espelha o padrão do {@link com.afterlands.core.database.impl.HikariSqlService}
 * com gerenciamento de múltiplos datasources isolados.
 * </p>
 *
 * @since 1.8.0
 */
public final class JedisRedisService implements RedisService {

    private final Plugin plugin;
    private final Logger logger;
    private final SchedulerService scheduler;
    private final boolean debug;

    private final Map<String, JedisRedisDataSource> datasources = new ConcurrentHashMap<>();

    public JedisRedisService(@NotNull Plugin plugin, @NotNull SchedulerService scheduler, boolean debug) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.scheduler = scheduler;
        this.debug = debug;
    }

    // ==================== Multi-Datasource Registry ====================

    @Override
    public @NotNull RedisDataSource datasource(@NotNull String name) {
        Objects.requireNonNull(name, "name");
        RedisDataSource ds = datasources.get(name);
        if (ds == null) {
            throw new IllegalStateException(
                    "Redis datasource '" + name + "' não encontrado. Datasources disponíveis: " + getDatasourceNames());
        }
        return ds;
    }

    @Override
    public boolean hasDatasource(@NotNull String name) {
        return datasources.containsKey(name);
    }

    @Override
    public @NotNull Set<String> getDatasourceNames() {
        return Collections.unmodifiableSet(datasources.keySet());
    }

    @Override
    public @NotNull Map<String, Map<String, Object>> getAllPoolStats() {
        Map<String, Map<String, Object>> allStats = new LinkedHashMap<>();
        for (Map.Entry<String, JedisRedisDataSource> entry : datasources.entrySet()) {
            allStats.put(entry.getKey(), entry.getValue().getPoolStats());
        }
        return allStats;
    }

    @Override
    public @NotNull RedisDataSource forPlugin(@NotNull Plugin plugin) {
        String dsName = plugin.getConfig().getString("redis.datasource", DEFAULT_DATASOURCE);
        if (!hasDatasource(dsName)) {
            throw new IllegalStateException(
                    "Redis datasource '" + dsName + "' não existe. Configure em AfterCore config.yml");
        }
        return datasource(dsName);
    }

    // ==================== Configuration Lifecycle ====================

    @Override
    public void reloadFromConfig(@Nullable ConfigurationSection section) {
        closeAll();

        if (section == null) {
            logger.info("redis: seção ausente (desabilitado)");
            return;
        }

        if (!section.getBoolean("enabled", false)) {
            logger.info("redis: disabled");
            return;
        }

        ConfigurationSection poolDefaults = section.getConfigurationSection("pool");
        ConfigurationSection datasourcesSection = section.getConfigurationSection("datasources");

        if (datasourcesSection == null || datasourcesSection.getKeys(false).isEmpty()) {
            logger.warning("redis: enabled mas sem datasources configurados");
            return;
        }

        for (String dsName : datasourcesSection.getKeys(false)) {
            ConfigurationSection dsConfig = datasourcesSection.getConfigurationSection(dsName);
            if (dsConfig != null) {
                initializeDatasource(dsName, dsConfig, poolDefaults);
            }
        }

        logger.info("Redis datasources inicializados: " + getDatasourceNames());
    }

    private void initializeDatasource(
            @NotNull String name,
            @NotNull ConfigurationSection dsConfig,
            @Nullable ConfigurationSection poolDefaults) {
        try {
            JedisRedisDataSource ds = new JedisRedisDataSource(
                    name, plugin, scheduler, dsConfig, poolDefaults, debug);
            datasources.put(name, ds);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Falha ao inicializar Redis datasource '" + name + "'", e);
        }
    }

    private void closeAll() {
        for (JedisRedisDataSource ds : datasources.values()) {
            try {
                ds.close();
            } catch (Throwable ignored) {}
        }
        datasources.clear();
    }

    // ==================== Default Datasource Delegation ====================

    private @NotNull RedisDataSource defaultDatasource() {
        if (!hasDatasource(DEFAULT_DATASOURCE)) {
            throw new IllegalStateException(
                    "Redis datasource default não inicializado. Configure 'redis.datasources.default'.");
        }
        return datasource(DEFAULT_DATASOURCE);
    }

    @Override
    public boolean isEnabled() {
        return hasDatasource(DEFAULT_DATASOURCE) && defaultDatasource().isEnabled();
    }

    @Override
    public boolean isInitialized() {
        return hasDatasource(DEFAULT_DATASOURCE) && defaultDatasource().isInitialized();
    }

    @Override
    public @NotNull CompletableFuture<Boolean> isAvailable() {
        if (!hasDatasource(DEFAULT_DATASOURCE)) {
            return CompletableFuture.completedFuture(false);
        }
        return defaultDatasource().isAvailable();
    }

    @Override
    public @NotNull Map<String, Object> getPoolStats() {
        if (!hasDatasource(DEFAULT_DATASOURCE)) {
            return Map.of();
        }
        return defaultDatasource().getPoolStats();
    }

    @Override
    public void close() {
        closeAll();
    }
}
