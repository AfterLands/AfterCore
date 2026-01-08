package com.afterlands.core.inventory.tab;

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
 * Estado de tabs para um player em um inventário.
 *
 * <p>Rastreia qual tab está ativa e a posição de scroll de cada tab (para paginação por tab).</p>
 *
 * <p><b>Imutável (record):</b> Todas as modificações retornam um novo TabState.</p>
 *
 * <p><b>Serialização:</b> Compatível com JSON para persistência em InventoryState.</p>
 *
 * <p><b>Thread Safety:</b> Imutável, portanto thread-safe.</p>
 */
public record TabState(
        @NotNull UUID playerId,
        @NotNull String inventoryId,
        @NotNull String activeTabId,
        @NotNull Map<String, Integer> tabScrollPositions,
        @NotNull Instant lastSwitchTime
) {

    private static final Gson GSON = new GsonBuilder().create();

    /**
     * Construtor compacto com validação.
     */
    public TabState {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId cannot be null");
        }
        if (inventoryId == null || inventoryId.isBlank()) {
            throw new IllegalArgumentException("inventoryId cannot be null or blank");
        }
        if (activeTabId == null || activeTabId.isBlank()) {
            throw new IllegalArgumentException("activeTabId cannot be null or blank");
        }
        if (tabScrollPositions == null) {
            tabScrollPositions = new HashMap<>();
        }
        if (lastSwitchTime == null) {
            lastSwitchTime = Instant.now();
        }
    }

    /**
     * Cria estado inicial de tabs.
     *
     * @param playerId UUID do player
     * @param inventoryId ID do inventário
     * @param defaultTabId ID da tab padrão
     * @return Novo TabState inicial
     */
    @NotNull
    public static TabState initial(@NotNull UUID playerId, @NotNull String inventoryId, @NotNull String defaultTabId) {
        return new TabState(playerId, inventoryId, defaultTabId, new HashMap<>(), Instant.now());
    }

    /**
     * Cria um novo estado com tab ativa diferente.
     *
     * @param tabId ID da nova tab ativa
     * @return Novo TabState (imutável)
     */
    @NotNull
    public TabState withActiveTab(@NotNull String tabId) {
        return new TabState(playerId, inventoryId, tabId, tabScrollPositions, Instant.now());
    }

    /**
     * Cria um novo estado com scroll position atualizada para uma tab.
     *
     * @param tabId ID da tab
     * @param scrollPosition Nova posição de scroll (página, índice, etc.)
     * @return Novo TabState (imutável)
     */
    @NotNull
    public TabState withScrollPosition(@NotNull String tabId, int scrollPosition) {
        Map<String, Integer> newScrollPositions = new HashMap<>(tabScrollPositions);
        newScrollPositions.put(tabId, scrollPosition);
        return new TabState(playerId, inventoryId, activeTabId, newScrollPositions, lastSwitchTime);
    }

    /**
     * Obtém scroll position de uma tab.
     *
     * @param tabId ID da tab
     * @return Scroll position ou 0 (default)
     */
    public int getScrollPosition(@NotNull String tabId) {
        return tabScrollPositions.getOrDefault(tabId, 0);
    }

    /**
     * Obtém scroll position da tab ativa.
     *
     * @return Scroll position da tab ativa ou 0
     */
    public int getActiveScrollPosition() {
        return getScrollPosition(activeTabId);
    }

    /**
     * Verifica se uma tab está ativa.
     *
     * @param tabId ID da tab
     * @return true se a tab está ativa
     */
    public boolean isActive(@NotNull String tabId) {
        return activeTabId.equals(tabId);
    }

    /**
     * Converte para JSON para persistência.
     *
     * @return JSON string
     */
    @NotNull
    public String toJson() {
        Map<String, Object> map = new HashMap<>();
        map.put("playerId", playerId.toString());
        map.put("inventoryId", inventoryId);
        map.put("activeTabId", activeTabId);
        map.put("tabScrollPositions", tabScrollPositions);
        map.put("lastSwitchTime", lastSwitchTime.toString());
        return GSON.toJson(map);
    }

    /**
     * Converte de JSON.
     *
     * @param json JSON string
     * @return TabState ou null se inválido
     */
    @Nullable
    public static TabState fromJson(@NotNull String json) {
        try {
            Map<String, Object> map = GSON.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());

            UUID playerId = UUID.fromString((String) map.get("playerId"));
            String inventoryId = (String) map.get("inventoryId");
            String activeTabId = (String) map.get("activeTabId");

            @SuppressWarnings("unchecked")
            Map<String, Object> scrollPositionsRaw = (Map<String, Object>) map.getOrDefault("tabScrollPositions", new HashMap<>());
            Map<String, Integer> tabScrollPositions = new HashMap<>();
            scrollPositionsRaw.forEach((k, v) -> {
                if (v instanceof Number) {
                    tabScrollPositions.put(k, ((Number) v).intValue());
                }
            });

            Instant lastSwitchTime = Instant.parse((String) map.getOrDefault("lastSwitchTime", Instant.now().toString()));

            return new TabState(playerId, inventoryId, activeTabId, tabScrollPositions, lastSwitchTime);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Converte para Map (para integração com InventoryState).
     *
     * @return Map com dados do TabState
     */
    @NotNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("activeTabId", activeTabId);
        map.put("tabScrollPositions", new HashMap<>(tabScrollPositions));
        map.put("lastSwitchTime", lastSwitchTime.toString());
        return map;
    }

    /**
     * Reconstrói TabState de Map (de InventoryState).
     *
     * @param playerId UUID do player
     * @param inventoryId ID do inventário
     * @param map Map com dados
     * @param defaultTabId Tab padrão (fallback se dados inválidos)
     * @return TabState reconstruído
     */
    @NotNull
    public static TabState fromMap(@NotNull UUID playerId, @NotNull String inventoryId,
                                   @NotNull Map<String, Object> map, @NotNull String defaultTabId) {
        try {
            String activeTabId = (String) map.getOrDefault("activeTabId", defaultTabId);

            @SuppressWarnings("unchecked")
            Map<String, Object> scrollPositionsRaw = (Map<String, Object>) map.getOrDefault("tabScrollPositions", new HashMap<>());
            Map<String, Integer> tabScrollPositions = new HashMap<>();
            scrollPositionsRaw.forEach((k, v) -> {
                if (v instanceof Number) {
                    tabScrollPositions.put(k, ((Number) v).intValue());
                }
            });

            String lastSwitchTimeStr = (String) map.getOrDefault("lastSwitchTime", Instant.now().toString());
            Instant lastSwitchTime = Instant.parse(lastSwitchTimeStr);

            return new TabState(playerId, inventoryId, activeTabId, tabScrollPositions, lastSwitchTime);
        } catch (Exception e) {
            return initial(playerId, inventoryId, defaultTabId);
        }
    }

    /**
     * Reseta scroll positions de todas as tabs.
     *
     * @return Novo TabState com scroll positions zeradas
     */
    @NotNull
    public TabState resetScrollPositions() {
        return new TabState(playerId, inventoryId, activeTabId, new HashMap<>(), lastSwitchTime);
    }

    /**
     * Reseta scroll position de uma tab específica.
     *
     * @param tabId ID da tab
     * @return Novo TabState com scroll position da tab zerada
     */
    @NotNull
    public TabState resetScrollPosition(@NotNull String tabId) {
        Map<String, Integer> newScrollPositions = new HashMap<>(tabScrollPositions);
        newScrollPositions.remove(tabId);
        return new TabState(playerId, inventoryId, activeTabId, newScrollPositions, lastSwitchTime);
    }

    @Override
    public String toString() {
        return "TabState{" +
                "player=" + playerId +
                ", inventory='" + inventoryId + '\'' +
                ", activeTab='" + activeTabId + '\'' +
                ", scrollPositions=" + tabScrollPositions.size() +
                ", lastSwitch=" + lastSwitchTime +
                '}';
    }
}
