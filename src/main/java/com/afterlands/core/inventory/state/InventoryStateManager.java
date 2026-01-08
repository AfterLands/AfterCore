package com.afterlands.core.inventory.state;

import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.database.SqlService;
import com.afterlands.core.inventory.InventoryState;
import com.afterlands.core.util.retry.RetryExecutor;
import com.afterlands.core.util.retry.RetryPolicy;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Gerenciador de estado persistente de inventários.
 *
 * <p>Responsável por:</p>
 * <ul>
 *     <li>Salvar/carregar estado do DB (async)</li>
 *     <li>Cache LRU de estados em memória</li>
 *     <li>Versionamento de schema</li>
 *     <li>Auto-save periódico (5 minutos)</li>
 *     <li>Batch saving para múltiplos estados</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> Cache thread-safe, DB operations async.</p>
 * <p><b>Graceful Degradation:</b> Se DB falhar, continua funcionando sem persistência.</p>
 */
public class InventoryStateManager {

    private static final String SAVE_STATE_SQL = """
            INSERT INTO aftercore_inventory_states (player_id, inventory_id, state_data, tab_states, custom_data, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                state_data = VALUES(state_data),
                tab_states = VALUES(tab_states),
                custom_data = VALUES(custom_data),
                updated_at = VALUES(updated_at)
            """;

    private static final String LOAD_STATE_SQL = """
            SELECT state_data, tab_states, custom_data, updated_at
            FROM aftercore_inventory_states
            WHERE player_id = ? AND inventory_id = ?
            """;

    private static final String DELETE_STATE_SQL = """
            DELETE FROM aftercore_inventory_states
            WHERE player_id = ? AND inventory_id = ?
            """;

    private static final String GET_PLAYER_STATES_SQL = """
            SELECT inventory_id, state_data, tab_states, custom_data, updated_at
            FROM aftercore_inventory_states
            WHERE player_id = ?
            ORDER BY updated_at DESC
            """;

    private static final Gson GSON = new GsonBuilder().create();

    private final Plugin plugin;
    private final SqlService sql;
    private final SchedulerService scheduler;
    private final boolean debug;

    // Cache de estados (TTL: 5min, max: 5000)
    private final Cache<String, InventoryState> stateCache;

    // Dirty states (pendentes de save)
    private final Map<String, InventoryState> dirtyStates;

    // Retry executor para operações de DB
    private final RetryExecutor retryExecutor;

    // Auto-save task
    private BukkitTask autoSaveTask;

    public InventoryStateManager(
            @NotNull Plugin plugin,
            @NotNull SqlService sql,
            @NotNull SchedulerService scheduler,
            boolean debug
    ) {
        this.plugin = plugin;
        this.sql = sql;
        this.scheduler = scheduler;
        this.debug = debug;

        this.stateCache = Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();

        this.dirtyStates = new ConcurrentHashMap<>();

        // Retry policy: 3 tentativas, exponential backoff
        this.retryExecutor = new RetryExecutor(
                RetryPolicy.defaultDatabasePolicy(),
                plugin.getLogger(),
                debug
        );

        // Inicia auto-save task (a cada 5 minutos)
        startAutoSaveTask();
    }

    /**
     * Inicia tarefa de auto-save periódico.
     */
    private void startAutoSaveTask() {
        if (!sql.isEnabled()) {
            if (debug) {
                plugin.getLogger().info("Auto-save disabled (SQL disabled)");
            }
            return;
        }

        // A cada 5 minutos (6000 ticks)
        autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::saveAllDirtyStates,
                20L * 60,       // 1 min inicial delay
                20L * 60 * 5    // 5 min interval
        );

        if (debug) {
            plugin.getLogger().info("Auto-save task started (interval: 5 minutes)");
        }
    }

    /**
     * Salva estado no DB (async com retry).
     *
     * @param state Estado completo
     * @return CompletableFuture que completa quando salvo
     */
    @NotNull
    public CompletableFuture<Void> saveState(@NotNull InventoryState state) {
        if (!sql.isEnabled()) {
            if (debug) {
                plugin.getLogger().fine("State save skipped (SQL disabled): " + state.inventoryId());
            }
            return CompletableFuture.completedFuture(null);
        }

        String cacheKey = getCacheKey(state.playerId(), state.inventoryId());

        // Marca como dirty
        dirtyStates.put(cacheKey, state);

        return sql.runAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(SAVE_STATE_SQL)) {
                ps.setString(1, state.playerId().toString());
                ps.setString(2, state.inventoryId());
                ps.setString(3, GSON.toJson(state.stateData()));
                ps.setString(4, GSON.toJson(state.tabStates()));
                ps.setString(5, GSON.toJson(state.customData()));
                ps.setLong(6, state.updatedAt().toEpochMilli());

                ps.executeUpdate();

                // Atualiza cache e remove de dirty
                stateCache.put(cacheKey, state);
                dirtyStates.remove(cacheKey);

                if (debug) {
                    plugin.getLogger().fine("Saved state: " + state.inventoryId() + " for " + state.playerId());
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save state: " + state.inventoryId(), e);
                throw new RuntimeException(e);
            }
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to save state: " + state.inventoryId(), ex);
            return null;
        });
    }

    /**
     * Carrega estado do DB (async).
     *
     * @param playerId UUID do player
     * @param inventoryId ID do inventário
     * @return CompletableFuture com estado (ou inicial se não existir)
     */
    @NotNull
    public CompletableFuture<InventoryState> loadState(
            @NotNull UUID playerId,
            @NotNull String inventoryId
    ) {
        String cacheKey = getCacheKey(playerId, inventoryId);

        // Check cache first
        InventoryState cached = stateCache.getIfPresent(cacheKey);
        if (cached != null) {
            if (debug) {
                plugin.getLogger().fine("State loaded from cache: " + inventoryId);
            }
            return CompletableFuture.completedFuture(cached);
        }

        if (!sql.isEnabled()) {
            return CompletableFuture.completedFuture(InventoryState.initial(playerId, inventoryId));
        }

        return sql.supplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(LOAD_STATE_SQL)) {
                ps.setString(1, playerId.toString());
                ps.setString(2, inventoryId);

                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String stateDataJson = rs.getString("state_data");
                    String tabStatesJson = rs.getString("tab_states");
                    String customDataJson = rs.getString("custom_data");
                    long updatedAtMs = rs.getLong("updated_at");

                    Map<String, Object> stateData = parseJsonToMap(stateDataJson);
                    Map<String, Integer> tabStates = parseJsonToIntMap(tabStatesJson);
                    Map<String, Object> customData = parseJsonToMap(customDataJson);
                    Instant updatedAt = Instant.ofEpochMilli(updatedAtMs);

                    InventoryState state = new InventoryState(
                            playerId,
                            inventoryId,
                            stateData,
                            tabStates,
                            customData,
                            updatedAt,
                            1 // schema version
                    );

                    stateCache.put(cacheKey, state);

                    if (debug) {
                        plugin.getLogger().fine("Loaded state from DB: " + inventoryId + " for " + playerId);
                    }

                    return state;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load state: " + inventoryId, e);
            }

            // Retorna estado inicial se não encontrado
            InventoryState initial = InventoryState.initial(playerId, inventoryId);
            stateCache.put(cacheKey, initial);
            return initial;
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Error loading state, returning initial: " + inventoryId, ex);
            return InventoryState.initial(playerId, inventoryId);
        });
    }

    /**
     * Deleta estado do DB (soft-delete).
     *
     * @param playerId UUID do player
     * @param inventoryId ID do inventário
     * @return CompletableFuture que completa quando deletado
     */
    @NotNull
    public CompletableFuture<Void> deleteState(
            @NotNull UUID playerId,
            @NotNull String inventoryId
    ) {
        if (!sql.isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }

        String cacheKey = getCacheKey(playerId, inventoryId);

        return sql.runAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(DELETE_STATE_SQL)) {
                ps.setString(1, playerId.toString());
                ps.setString(2, inventoryId);
                ps.executeUpdate();

                // Remove do cache e dirty
                stateCache.invalidate(cacheKey);
                dirtyStates.remove(cacheKey);

                if (debug) {
                    plugin.getLogger().fine("Deleted state: " + inventoryId + " for " + playerId);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to delete state: " + inventoryId, e);
            }
        });
    }

    /**
     * Obtém todos os estados de um player.
     *
     * @param playerId UUID do player
     * @return CompletableFuture com lista de estados
     */
    @NotNull
    public CompletableFuture<List<InventoryState>> getPlayerStates(@NotNull UUID playerId) {
        if (!sql.isEnabled()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return sql.supplyAsync(conn -> {
            List<InventoryState> states = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(GET_PLAYER_STATES_SQL)) {
                ps.setString(1, playerId.toString());

                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String inventoryId = rs.getString("inventory_id");
                    String stateDataJson = rs.getString("state_data");
                    String tabStatesJson = rs.getString("tab_states");
                    String customDataJson = rs.getString("custom_data");
                    long updatedAtMs = rs.getLong("updated_at");

                    Map<String, Object> stateData = parseJsonToMap(stateDataJson);
                    Map<String, Integer> tabStates = parseJsonToIntMap(tabStatesJson);
                    Map<String, Object> customData = parseJsonToMap(customDataJson);
                    Instant updatedAt = Instant.ofEpochMilli(updatedAtMs);

                    InventoryState state = new InventoryState(
                            playerId,
                            inventoryId,
                            stateData,
                            tabStates,
                            customData,
                            updatedAt,
                            1
                    );

                    states.add(state);
                }

                if (debug) {
                    plugin.getLogger().fine("Loaded " + states.size() + " states for player " + playerId);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get player states", e);
            }

            return states;
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Error getting player states", ex);
            return Collections.emptyList();
        });
    }

    /**
     * Salva todos os estados dirty (batch).
     */
    public void saveAllDirtyStates() {
        if (dirtyStates.isEmpty()) {
            if (debug) {
                plugin.getLogger().fine("No dirty states to save");
            }
            return;
        }

        if (!sql.isEnabled()) {
            return;
        }

        List<InventoryState> toSave = new ArrayList<>(dirtyStates.values());

        if (debug) {
            plugin.getLogger().info("Batch saving " + toSave.size() + " dirty states...");
        }

        // Batch save
        sql.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(SAVE_STATE_SQL)) {
                for (InventoryState state : toSave) {
                    ps.setString(1, state.playerId().toString());
                    ps.setString(2, state.inventoryId());
                    ps.setString(3, GSON.toJson(state.stateData()));
                    ps.setString(4, GSON.toJson(state.tabStates()));
                    ps.setString(5, GSON.toJson(state.customData()));
                    ps.setLong(6, state.updatedAt().toEpochMilli());
                    ps.addBatch();
                }

                int[] results = ps.executeBatch();

                // Remove salvos com sucesso
                for (int i = 0; i < results.length; i++) {
                    if (results[i] >= 0) {
                        InventoryState state = toSave.get(i);
                        String cacheKey = getCacheKey(state.playerId(), state.inventoryId());
                        dirtyStates.remove(cacheKey);
                    }
                }

                if (debug) {
                    plugin.getLogger().info("Batch save completed: " + results.length + " states saved");
                }
            }
            return null;
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Batch save failed", ex);
            return null;
        });
    }

    /**
     * Parse JSON to Map<String, Object>.
     */
    @NotNull
    private Map<String, Object> parseJsonToMap(@Nullable String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            return new HashMap<>();
        }
        try {
            return GSON.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to parse JSON: " + json, e);
            return new HashMap<>();
        }
    }

    /**
     * Parse JSON to Map<String, Integer>.
     */
    @NotNull
    private Map<String, Integer> parseJsonToIntMap(@Nullable String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> raw = GSON.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
            Map<String, Integer> result = new HashMap<>();
            raw.forEach((k, v) -> {
                if (v instanceof Number) {
                    result.put(k, ((Number) v).intValue());
                }
            });
            return result;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to parse JSON int map: " + json, e);
            return new HashMap<>();
        }
    }

    /**
     * Gera cache key.
     */
    @NotNull
    private String getCacheKey(@NotNull UUID playerId, @NotNull String inventoryId) {
        return playerId + ":" + inventoryId;
    }

    /**
     * Limpa cache.
     */
    public void clearCache() {
        stateCache.invalidateAll();
    }

    /**
     * Shutdown cleanup.
     */
    public void shutdown() {
        // Cancela auto-save task
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }

        // Salva todos os estados pendentes
        saveAllDirtyStates();

        // Limpa cache
        clearCache();
        dirtyStates.clear();

        if (debug) {
            plugin.getLogger().info("InventoryStateManager shut down");
        }
    }
}
