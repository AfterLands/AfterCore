package com.afterlands.core.redis;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Serviço de Redis com suporte a múltiplos datasources.
 *
 * <p>
 * Espelha o padrão do {@link com.afterlands.core.database.SqlService}
 * com registry de datasources nomeados, cada um com pool Jedis isolado.
 * </p>
 *
 * <p>
 * <b>Exemplo:</b>
 * </p>
 * <pre>{@code
 * // Datasource default
 * core.redis().set("key", "value");
 *
 * // Datasource específico
 * core.redis().datasource("cache").get("key");
 * }</pre>
 *
 * @since 1.8.0
 */
public interface RedisService extends AutoCloseable {

    String DEFAULT_DATASOURCE = "default";

    // ==================== Multi-Datasource Registry ====================

    @NotNull RedisDataSource datasource(@NotNull String name);

    boolean hasDatasource(@NotNull String name);

    @NotNull Set<String> getDatasourceNames();

    @NotNull Map<String, Map<String, Object>> getAllPoolStats();

    @NotNull RedisDataSource forPlugin(@NotNull org.bukkit.plugin.Plugin plugin);

    // ==================== Configuration Lifecycle ====================

    void reloadFromConfig(@Nullable ConfigurationSection section);

    // ==================== Default Datasource Delegation ====================

    boolean isEnabled();

    boolean isInitialized();

    @NotNull CompletableFuture<Boolean> isAvailable();

    @NotNull Map<String, Object> getPoolStats();

    default CompletableFuture<@Nullable String> get(@NotNull String key) {
        return datasource(DEFAULT_DATASOURCE).get(key);
    }

    default CompletableFuture<Void> set(@NotNull String key, @NotNull String value) {
        return datasource(DEFAULT_DATASOURCE).set(key, value);
    }

    default CompletableFuture<Void> setex(@NotNull String key, long seconds, @NotNull String value) {
        return datasource(DEFAULT_DATASOURCE).setex(key, seconds, value);
    }

    @Override
    void close();
}
