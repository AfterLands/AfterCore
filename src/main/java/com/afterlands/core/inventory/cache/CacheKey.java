package com.afterlands.core.inventory.cache;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Chave de cache para ItemStacks compilados.
 *
 * <p>Imutável e thread-safe. Usa hash de placeholders para diferenciar
 * items dinâmicos vs estáticos.</p>
 *
 * <p><b>Formato da chave:</b>
 * <ul>
 *     <li>Estático: {@code inventory:menu_principal:item:close_button:static}</li>
 *     <li>Dinâmico: {@code inventory:stats:item:player_level:ph_hash_12345}</li>
 * </ul>
 * </p>
 */
public record CacheKey(
        @NotNull String inventoryId,
        @NotNull String itemKey,
        int placeholderHash,
        @Nullable java.util.UUID playerId
) {

    /**
     * Cria cache key para item estático (sem placeholders dinâmicos).
     *
     * @param inventoryId ID do inventário
     * @param itemKey Chave do item (type + slot)
     * @return Cache key estática
     */
    @NotNull
    public static CacheKey ofStatic(@NotNull String inventoryId, @NotNull String itemKey) {
        return new CacheKey(inventoryId, itemKey, 0, null);
    }

    /**
     * Cria cache key para item estático com escopo de jogador.
     *
     * @param inventoryId ID do inventário
     * @param itemKey Chave do item (type + slot)
     * @param playerId UUID do jogador para escopo da key
     * @return Cache key estática
     */
    @NotNull
    public static CacheKey ofStatic(
            @NotNull String inventoryId,
            @NotNull String itemKey,
            @Nullable java.util.UUID playerId
    ) {
        return new CacheKey(inventoryId, itemKey, 0, playerId);
    }

    /**
     * Cria cache key para item dinâmico (com placeholders).
     *
     * @param inventoryId ID do inventário
     * @param itemKey Chave do item (type + slot)
     * @param placeholders Mapa de placeholders resolvidos
     * @return Cache key dinâmica
     */
    @NotNull
    public static CacheKey ofDynamic(
            @NotNull String inventoryId,
            @NotNull String itemKey,
            @NotNull Map<String, String> placeholders
    ) {
        int hash = computePlaceholderHash(placeholders);
        return new CacheKey(inventoryId, itemKey, hash, null);
    }

    /**
     * Cria cache key para item dinâmico com escopo de jogador.
     *
     * @param inventoryId ID do inventário
     * @param itemKey Chave do item (type + slot)
     * @param placeholders Mapa de placeholders resolvidos
     * @param playerId UUID do jogador para escopo da key
     * @return Cache key dinâmica
     */
    @NotNull
    public static CacheKey ofDynamic(
            @NotNull String inventoryId,
            @NotNull String itemKey,
            @NotNull Map<String, String> placeholders,
            @Nullable java.util.UUID playerId
    ) {
        int hash = computePlaceholderHash(placeholders);
        return new CacheKey(inventoryId, itemKey, hash, playerId);
    }

    /**
     * Computa hash consistente dos placeholders.
     *
     * <p>Usa sorted keys para garantir hash determinístico.</p>
     *
     * @param placeholders Mapa de placeholders
     * @return Hash code
     */
    private static int computePlaceholderHash(@NotNull Map<String, String> placeholders) {
        if (placeholders.isEmpty()) {
            return 0;
        }

        // Hash consistente: ordena keys para garantir determinismo
        return placeholders.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> Objects.hash(e.getKey(), e.getValue()))
                .reduce(0, (a, b) -> 31 * a + b);
    }

    /**
     * Verifica se esta key é estática (sem placeholders).
     *
     * @return true se estática
     */
    public boolean isStatic() {
        return placeholderHash == 0;
    }

    /**
     * Retorna string representation para logging.
     *
     * @return String no formato inventory:id:item:key:hash
     */
    @Override
    public String toString() {
        String playerScope = playerId != null ? ":player:" + playerId : "";
        if (isStatic()) {
            return String.format("inventory:%s:item:%s:static%s", inventoryId, itemKey, playerScope);
        }
        return String.format("inventory:%s:item:%s:ph_hash_%d%s", inventoryId, itemKey, placeholderHash, playerScope);
    }

    /**
     * Pattern matching para invalidação de cache por inventoryId.
     *
     * @param targetInventoryId ID do inventário a invalidar
     * @return true se esta key pertence ao inventário
     */
    public boolean matchesInventory(@NotNull String targetInventoryId) {
        return this.inventoryId.equals(targetInventoryId);
    }

    /**
     * Pattern matching para invalidação de cache por item específico.
     *
     * @param targetInventoryId ID do inventário
     * @param targetItemKey Chave do item
     * @return true se esta key corresponde ao item
     */
    public boolean matchesItem(@NotNull String targetInventoryId, @NotNull String targetItemKey) {
        return this.inventoryId.equals(targetInventoryId) && this.itemKey.equals(targetItemKey);
    }

    /**
     * Pattern matching para invalidação por jogador.
     *
     * @param targetPlayerId UUID do jogador
     * @return true se esta key está no escopo do jogador
     */
    public boolean matchesPlayer(@NotNull java.util.UUID targetPlayerId) {
        return targetPlayerId.equals(this.playerId);
    }
}
