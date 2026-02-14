package com.afterlands.core.inventory;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contexto de dados ao abrir inventário.
 *
 * <p>Thread-safe. Suporta placeholders dinâmicos e dados arbitrários.</p>
 *
 * <p><b>Placeholders:</b> Formato {key}. PlaceholderAPI é opcional
 * (graceful degradation se não instalado).</p>
 */
public class InventoryContext {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    private static final boolean PAPI_AVAILABLE = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

    private final Map<String, Object> data;
    private final Map<String, String> placeholders;
    private final UUID playerId;
    private final String inventoryId;
    private String pluginNamespace;

    public InventoryContext(@Nullable UUID playerId, @NotNull String inventoryId) {
        this.playerId = playerId;
        this.inventoryId = inventoryId;
        this.data = new ConcurrentHashMap<>();
        this.placeholders = new ConcurrentHashMap<>();
    }

    /**
     * Adiciona placeholder para resolução dinâmica.
     *
     * @param key Chave do placeholder (sem chaves)
     * @param value Valor
     * @return this para chaining
     */
    @NotNull
    public InventoryContext withPlaceholder(@NotNull String key, @NotNull String value) {
        this.placeholders.put(key, value);
        return this;
    }

    /**
     * Adiciona múltiplos placeholders.
     *
     * @param placeholders Map de placeholders
     * @return this para chaining
     */
    @NotNull
    public InventoryContext withPlaceholders(@NotNull Map<String, String> placeholders) {
        this.placeholders.putAll(placeholders);
        return this;
    }

    /**
     * Adiciona dado arbitrário ao contexto.
     *
     * @param key Chave
     * @param value Valor
     * @return this para chaining
     */
    @NotNull
    public InventoryContext withData(@NotNull String key, @NotNull Object value) {
        this.data.put(key, value);
        return this;
    }

    /**
     * Obtém valor do contexto com cast type-safe.
     *
     * @param key Chave
     * @param type Classe do tipo esperado
     * @param <T> Tipo
     * @return Optional com valor (vazio se não existir ou tipo incorreto)
     */
    @NotNull
    public <T> Optional<T> getData(@NotNull String key, @NotNull Class<T> type) {
        Object value = data.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    /**
     * Resolve todos os placeholders no texto.
     *
     * <p>Ordem de resolução:</p>
     * <ol>
     *     <li>Placeholders do contexto ({key})</li>
     *     <li>PlaceholderAPI (%placeholder%)</li>
     * </ol>
     *
     * <p><b>Thread:</b> MAIN THREAD se player != null e PlaceholderAPI instalado</p>
     *
     * @param text Texto com placeholders
     * @return Texto com placeholders substituídos
     */
    @NotNull
    public String resolvePlaceholders(@NotNull String text) {
        // 1. Resolve placeholders do contexto ({key})
        String result = text;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = placeholders.get(key);
            if (value != null) {
                result = result.replace("{" + key + "}", value);
            }
        }

        // 2. Resolve PlaceholderAPI (%placeholder%) se disponível
        if (PAPI_AVAILABLE && playerId != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                result = PlaceholderAPI.setPlaceholders(player, result);
            }
        }

        return result;
    }

    /**
     * Resolve placeholders para múltiplas linhas.
     *
     * @param lines Lista de linhas
     * @return Lista com placeholders resolvidos
     */
    @NotNull
    public java.util.List<String> resolvePlaceholders(@NotNull java.util.List<String> lines) {
        return lines.stream()
                .map(this::resolvePlaceholders)
                .toList();
    }

    // Getters

    @Nullable
    public UUID getPlayerId() {
        return playerId;
    }

    @NotNull
    public String getInventoryId() {
        return inventoryId;
    }

    /**
     * Gets the plugin namespace for i18n translation resolution.
     *
     * @return Plugin namespace or null if not set
     */
    @Nullable
    public String getPluginNamespace() {
        return pluginNamespace;
    }

    /**
     * Sets the plugin namespace for automatic i18n translation injection.
     *
     * @param pluginNamespace Plugin namespace (e.g., "aftertemplate")
     * @return this for chaining
     */
    @NotNull
    public InventoryContext withPluginNamespace(@Nullable String pluginNamespace) {
        this.pluginNamespace = pluginNamespace;
        return this;
    }

    @NotNull
    public Map<String, String> getPlaceholders() {
        return Map.copyOf(placeholders);
    }

    @NotNull
    public Map<String, Object> getData() {
        return Map.copyOf(data);
    }

    /**
     * Cria uma cópia deste contexto.
     *
     * <p>Útil para shared inventories (evita mutação compartilhada).</p>
     *
     * @return Nova instância com os mesmos dados
     */
    @NotNull
    public InventoryContext copy() {
        InventoryContext copy = new InventoryContext(playerId, inventoryId);
        copy.placeholders.putAll(this.placeholders);
        copy.data.putAll(this.data);
        copy.pluginNamespace = this.pluginNamespace;
        return copy;
    }

    @Override
    public String toString() {
        return "InventoryContext{" +
                "playerId=" + playerId +
                ", inventoryId='" + inventoryId + '\'' +
                ", placeholders=" + placeholders.size() +
                ", data=" + data.size() +
                '}';
    }
}
