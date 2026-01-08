package com.afterlands.core.inventory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Estado persistente de um inventário.
 *
 * <p>Serializável para JSON (DB storage). Imutável (record).</p>
 *
 * <p><b>Schema versioning:</b> Inclui schema version para migrações futuras.</p>
 */
public record InventoryState(
        @NotNull UUID playerId,
        @NotNull String inventoryId,
        @NotNull Map<String, Object> stateData,
        @NotNull Map<String, Integer> tabStates,
        @NotNull Map<String, Object> customData,
        @NotNull Instant updatedAt,
        int schemaVersion
) {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * Construtor compacto com validação.
     */
    public InventoryState {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId cannot be null");
        }
        if (inventoryId == null || inventoryId.isBlank()) {
            throw new IllegalArgumentException("inventoryId cannot be null or blank");
        }
        if (stateData == null) {
            stateData = new HashMap<>();
        }
        if (tabStates == null) {
            tabStates = new HashMap<>();
        }
        if (customData == null) {
            customData = new HashMap<>();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (schemaVersion <= 0) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
        }
    }

    /**
     * Converte para JSON para DB storage.
     *
     * @return JSON string
     */
    @NotNull
    public String toJson() {
        Map<String, Object> map = new HashMap<>();
        map.put("playerId", playerId.toString());
        map.put("inventoryId", inventoryId);
        map.put("stateData", stateData);
        map.put("tabStates", tabStates);
        map.put("customData", customData);
        map.put("updatedAt", updatedAt.toString());
        map.put("schemaVersion", schemaVersion);
        return GSON.toJson(map);
    }

    /**
     * Converte de JSON.
     *
     * @param json JSON string
     * @return InventoryState ou null se inválido
     */
    @Nullable
    public static InventoryState fromJson(@NotNull String json) {
        try {
            Map<String, Object> map = GSON.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());

            UUID playerId = UUID.fromString((String) map.get("playerId"));
            String inventoryId = (String) map.get("inventoryId");

            @SuppressWarnings("unchecked")
            Map<String, Object> stateData = (Map<String, Object>) map.getOrDefault("stateData", new HashMap<>());

            @SuppressWarnings("unchecked")
            Map<String, Object> tabStatesRaw = (Map<String, Object>) map.getOrDefault("tabStates", new HashMap<>());
            Map<String, Integer> tabStates = new HashMap<>();
            tabStatesRaw.forEach((k, v) -> {
                if (v instanceof Number) {
                    tabStates.put(k, ((Number) v).intValue());
                }
            });

            @SuppressWarnings("unchecked")
            Map<String, Object> customData = (Map<String, Object>) map.getOrDefault("customData", new HashMap<>());

            Instant updatedAt = Instant.parse((String) map.getOrDefault("updatedAt", Instant.now().toString()));

            int schemaVersion = map.containsKey("schemaVersion")
                    ? ((Number) map.get("schemaVersion")).intValue()
                    : CURRENT_SCHEMA_VERSION;

            return new InventoryState(playerId, inventoryId, stateData, tabStates, customData, updatedAt, schemaVersion);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Cria estado inicial.
     *
     * @param playerId UUID do player
     * @param inventoryId ID do inventário
     * @return Novo estado vazio
     */
    @NotNull
    public static InventoryState initial(@NotNull UUID playerId, @NotNull String inventoryId) {
        return new InventoryState(
                playerId,
                inventoryId,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                Instant.now(),
                CURRENT_SCHEMA_VERSION
        );
    }

    /**
     * Cria um novo estado com dados atualizados.
     *
     * @param key Chave do dado
     * @param value Valor
     * @return Novo estado com dado atualizado (imutável)
     */
    @NotNull
    public InventoryState withStateData(@NotNull String key, @NotNull Object value) {
        Map<String, Object> newStateData = new HashMap<>(stateData);
        newStateData.put(key, value);
        return new InventoryState(playerId, inventoryId, newStateData, tabStates, customData, Instant.now(), schemaVersion);
    }

    /**
     * Cria um novo estado com tab state atualizado.
     *
     * @param tabId ID da tab
     * @param state Estado da tab (índice)
     * @return Novo estado com tab atualizada (imutável)
     */
    @NotNull
    public InventoryState withTabState(@NotNull String tabId, int state) {
        Map<String, Integer> newTabStates = new HashMap<>(tabStates);
        newTabStates.put(tabId, state);
        return new InventoryState(playerId, inventoryId, stateData, newTabStates, customData, Instant.now(), schemaVersion);
    }

    /**
     * Cria um novo estado com custom data atualizado.
     *
     * @param key Chave
     * @param value Valor
     * @return Novo estado com custom data atualizado (imutável)
     */
    @NotNull
    public InventoryState withCustomData(@NotNull String key, @NotNull Object value) {
        Map<String, Object> newCustomData = new HashMap<>(customData);
        newCustomData.put(key, value);
        return new InventoryState(playerId, inventoryId, stateData, tabStates, newCustomData, Instant.now(), schemaVersion);
    }

    /**
     * Obtém valor de state data.
     *
     * @param key Chave
     * @return Valor ou null
     */
    @Nullable
    public Object getStateData(@NotNull String key) {
        return stateData.get(key);
    }

    /**
     * Obtém tab state (índice).
     *
     * @param tabId ID da tab
     * @return Índice ou 0 (default)
     */
    public int getTabState(@NotNull String tabId) {
        return tabStates.getOrDefault(tabId, 0);
    }

    /**
     * Obtém custom data.
     *
     * @param key Chave
     * @return Valor ou null
     */
    @Nullable
    public Object getCustomData(@NotNull String key) {
        return customData.get(key);
    }
}
