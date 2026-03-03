package com.afterlands.core.redis;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * No-op RedisService usado quando Redis está desabilitado.
 *
 * <p>
 * Segue o padrão {@code warnOnce()} do {@link com.afterlands.core.holograms.NoOpHologramService}.
 * </p>
 *
 * @since 1.8.0
 */
public final class NoOpRedisService implements RedisService {

    private final Logger logger;
    private boolean warnedOnce = false;

    public NoOpRedisService(@NotNull Logger logger) {
        this.logger = logger;
    }

    private void warnOnce() {
        if (!warnedOnce) {
            logger.warning("[Redis] Redis not enabled - all Redis operations are disabled");
            warnedOnce = true;
        }
    }

    @Override
    public @NotNull RedisDataSource datasource(@NotNull String name) {
        warnOnce();
        return NoOpRedisDataSource.INSTANCE;
    }

    @Override
    public boolean hasDatasource(@NotNull String name) {
        return false;
    }

    @Override
    public @NotNull Set<String> getDatasourceNames() {
        return Collections.emptySet();
    }

    @Override
    public @NotNull Map<String, Map<String, Object>> getAllPoolStats() {
        return Collections.emptyMap();
    }

    @Override
    public @NotNull RedisDataSource forPlugin(@NotNull org.bukkit.plugin.Plugin plugin) {
        warnOnce();
        return NoOpRedisDataSource.INSTANCE;
    }

    @Override
    public void reloadFromConfig(@Nullable ConfigurationSection section) {
        // No-op
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public boolean isInitialized() {
        return false;
    }

    @Override
    public @NotNull CompletableFuture<Boolean> isAvailable() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public @NotNull Map<String, Object> getPoolStats() {
        return Collections.emptyMap();
    }

    @Override
    public void close() {
        // No-op
    }
}
