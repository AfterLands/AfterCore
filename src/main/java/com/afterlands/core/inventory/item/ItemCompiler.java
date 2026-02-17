package com.afterlands.core.inventory.item;

import com.afterlands.core.api.messages.MessageKey;
import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.config.MessageService;
import com.afterlands.core.inventory.InventoryContext;
import com.afterlands.core.inventory.cache.CacheKey;
import com.afterlands.core.inventory.cache.ItemCache;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Compilador de GuiItem → ItemStack final.
 *
 * <p>
 * Pipeline de compilação:
 * <ol>
 * <li>Verifica cache (se item é cacheável)</li>
 * <li>Cria ItemStack base com material/data/amount</li>
 * <li>Resolve placeholders (PlaceholderAPI + context) - MAIN THREAD</li>
 * <li>Aplica ItemMeta (name, lore, enchantments, flags)</li>
 * <li>Aplica NBT customizado via NBTItemBuilder</li>
 * <li>Aplica skull texture (se aplicável)</li>
 * <li>Cacheia resultado (se cacheável)</li>
 * </ol>
 * </p>
 *
 * <p>
 * <b>Thread Safety:</b> Compilação DEVE rodar na main thread (PlaceholderAPI
 * requirement).
 * </p>
 *
 * <p>
 * <b>Performance:</b> Cache inteligente reduz compilações redundantes em
 * 80-90%.
 * </p>
 */
public class ItemCompiler {

    private final SchedulerService scheduler;
    private final ItemCache cache;
    private final PlaceholderResolver placeholderResolver;
    private final MessageService messageService;
    private final Logger logger;
    private final boolean debug;

    /**
     * Cria compiler com dependências.
     *
     * @param scheduler           Scheduler service
     * @param cache               Item cache
     * @param placeholderResolver Placeholder resolver
     * @param logger              Logger
     * @param debug               Habilita debug logging
     */
    public ItemCompiler(
            @NotNull SchedulerService scheduler,
            @NotNull ItemCache cache,
            @NotNull PlaceholderResolver placeholderResolver,
            @NotNull MessageService messageService,
            @NotNull Logger logger,
            boolean debug) {
        this.scheduler = scheduler;
        this.cache = cache;
        this.placeholderResolver = placeholderResolver;
        this.messageService = messageService;
        this.logger = logger;
        this.debug = debug;
    }

    /**
     * Compila GuiItem em ItemStack final.
     *
     * <p>
     * <b>Thread:</b> MAIN THREAD (PlaceholderAPI requirement).
     * </p>
     *
     * @param item    GuiItem a compilar
     * @param player  Player alvo (para placeholders)
     * @param context Contexto com dados adicionais
     * @return CompletableFuture com ItemStack compilado
     */
    @NotNull
    public CompletableFuture<ItemStack> compile(
            @NotNull GuiItem item,
            @Nullable Player player,
            @NotNull InventoryContext context) {

        // Mesclar item placeholders com context global
        // Item placeholders têm prioridade (override)
        InventoryContext mergedContext = context;
        if (!item.getItemPlaceholders().isEmpty()) {
            mergedContext = context.copy();
            mergedContext.withPlaceholders(item.getItemPlaceholders());
        }

        // Determina se item é cacheável
        // NOTE: hasDynamicPlaceholders() now includes context placeholders ({key})
        // so items with {duration}, {alias}, etc. will be treated as dynamic
        boolean isCacheable = isCacheable(item);

        if (isCacheable) {
            // Tenta obter do cache
            CacheKey cacheKey = buildCacheKey(item, player, mergedContext);

            InventoryContext finalContext = mergedContext;
            return cache.get(
                    cacheKey,
                    () -> compileInternal(item, player, finalContext),
                    scheduler::runSync);
        } else {
            // Item dinâmico: compila sempre
            InventoryContext finalContext = mergedContext;
            return CompletableFuture.supplyAsync(
                    () -> compileInternal(item, player, finalContext),
                    scheduler::runSync);
        }
    }

    /**
     * Compila item internamente (sem cache).
     *
     * <p>
     * <b>CRITICAL:</b> MUST run on main thread.
     * </p>
     *
     * @param item    GuiItem
     * @param player  Player
     * @param context Contexto
     * @return ItemStack compilado
     */
    @NotNull
    private ItemStack compileInternal(
            @NotNull GuiItem item,
            @Nullable Player player,
            @NotNull InventoryContext context) {
        if (debug) {
            logger.fine("Compiling item: " + item.getType() + " (slot " + item.getSlot() + ")");
        }

        // 1. Cria ItemStack base
        ItemStack itemStack = new ItemStack(
                item.getMaterial(),
                item.getAmount(),
                item.getData());

        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return itemStack;
        }

        // 2. Auto-inject i18n translations (before placeholder resolution)
        String displayName = item.getName();
        List<String> displayLore = item.getLore();

        if (player != null && context.getPluginNamespace() != null) {
            String ns = context.getPluginNamespace();
            // Extract local inventory ID (strip namespace prefix like "PluginName:")
            String invId = context.getInventoryId();
            if (invId.contains(":")) {
                invId = invId.substring(invId.indexOf(':') + 1);
            }
            String itemKey = item.getType() != null && !item.getType().isEmpty()
                    ? item.getType() : "slot-" + item.getSlot();

            // Try translation for name
            if (displayName != null && !displayName.isBlank()) {
                String translationKey = "gui." + invId + "." + itemKey + ".name";
                String translated = messageService.getOrDefault(player,
                        MessageKey.of(ns, translationKey), displayName);
                displayName = translated;
            }

            // Try translation for lore
            if (displayLore != null && !displayLore.isEmpty()) {
                String loreKey = "gui." + invId + "." + itemKey + ".lore";
                String loreTranslated = messageService.getOrDefault(player,
                        MessageKey.of(ns, loreKey), null);
                if (loreTranslated != null && !loreTranslated.equals(loreKey)) {
                    displayLore = java.util.Arrays.asList(loreTranslated.split("\n"));
                }
            }
        }

        // 2b. Display name (resolve placeholders)
        if (displayName != null && !displayName.isEmpty()) {
            String resolvedName = placeholderResolver.resolve(displayName, player, context);
            meta.setDisplayName(resolvedName.replace("&", "§"));
        }

        // 3. Lore (resolve placeholders with multiline expansion support)
        if (displayLore != null && !displayLore.isEmpty()) {
            List<String> resolvedLore = new java.util.ArrayList<>();

            for (String line : displayLore) {
                String resolved = placeholderResolver.resolve(line, player, context);

                // Check if line contains newline separator (\n) - expand to multiple lines
                if (resolved.contains("\\n")) {
                    String[] parts = resolved.split("\\\\n");
                    for (String part : parts) {
                        resolvedLore.add(part.replace("&", "§"));
                    }
                } else {
                    resolvedLore.add(resolved.replace("&", "§"));
                }
            }

            meta.setLore(resolvedLore);
        }

        // 4. Enchantment glow (simple glow effect)
        boolean glowRequested = item.isEnchanted();
        boolean isHeadItem = item.getMaterial() == Material.SKULL_ITEM && item.getData() == 3;
        boolean glowAppliedOnMeta = false;
        if (glowRequested && !isHeadItem) {
            glowAppliedOnMeta = meta.addEnchant(Enchantment.DURABILITY, 1, true);
        }

        // 4b. Multiple enchantments (from enchantments map)
        if (!item.getEnchantments().isEmpty()) {
            for (var entry : item.getEnchantments().entrySet()) {
                try {
                    Enchantment ench = Enchantment.getByName(entry.getKey());
                    if (ench != null) {
                        meta.addEnchant(ench, entry.getValue(), true);
                    } else {
                        logger.warning("Unknown enchantment: " + entry.getKey());
                    }
                } catch (Exception e) {
                    logger.warning("Failed to apply enchantment " + entry.getKey() + ": " + e.getMessage());
                }
            }
        }

        // Hide enchant tooltip when glow is requested.
        if (glowRequested) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        // 5. Hide flags
        if (item.isHideFlags()) {
            meta.addItemFlags(ItemFlag.values());
        }

        itemStack.setItemMeta(meta);

        // Fallback for non-enchantable materials (Paper, Flint, Skull, etc.)
        if (glowRequested && !glowAppliedOnMeta && !isHeadItem) {
            itemStack.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
        }

        // 6. Apply NBT tags
        if (!item.getNbtTags().isEmpty()) {
            NBTItemBuilder nbtBuilder = new NBTItemBuilder(itemStack);
            nbtBuilder.setNBT(item.getNbtTags());
            itemStack = nbtBuilder.build();
        }

        // 6b. Apply custom model data via NBT (for 1.14+ resource pack support)
        if (item.getCustomModelData() > 0) {
            NBTItemBuilder nbtBuilder = new NBTItemBuilder(itemStack);
            nbtBuilder.setCustomModelData(item.getCustomModelData());
            itemStack = nbtBuilder.build();
        }

        // 7. Apply skull texture
        if (item.getMaterial() == Material.SKULL_ITEM && item.getData() == 3) {
            NBTItemBuilder nbtBuilder = new NBTItemBuilder(itemStack);
            String textureValue = resolveSkullTexture(item, player, context);
            nbtBuilder.setSkullTexture(textureValue, player);
            itemStack = nbtBuilder.build();
        }

        return itemStack;
    }

    /**
     * Resolve textura de skull com placeholders.
     *
     * @param item    GuiItem
     * @param player  Player
     * @param context Contexto
     * @return Texture value resolvido
     */
    @NotNull
    private String resolveSkullTexture(
            @NotNull GuiItem item,
            @Nullable Player player,
            @NotNull InventoryContext context) {
        String headValue = item.getHeadValue();
        if (headValue == null) {
            return "self";
        }

        // Resolve placeholders no headValue
        return placeholderResolver.resolve(headValue, player, context);
    }

    /**
     * Verifica se item é cacheável.
     *
     * <p>
     * Item é cacheável se:
     * <ul>
     * <li>GuiItem.cacheable == true</li>
     * <li>Não contém placeholders dinâmicos (PlaceholderAPI)</li>
     * </ul>
     * </p>
     *
     * @param item GuiItem
     * @return true se cacheável
     */
    public boolean isCacheable(@NotNull GuiItem item) {
        if (!item.isCacheable()) {
            return false;
        }

        // Verifica se contém placeholders voláteis (PlaceholderAPI)
        // Itens com placeholders de contexto ({key}) AGORA SÃO CACHEÁVEIS
        // pois usamos CacheKey.ofDynamic que inclui o contexto.
        boolean hasVolatile = false;

        if (item.getName() != null) {
            hasVolatile |= placeholderResolver.hasVolatilePlaceholders(item.getName());
        }

        if (item.getLore() != null) {
            for (String line : item.getLore()) {
                hasVolatile |= placeholderResolver.hasVolatilePlaceholders(line);
            }
        }

        if (item.getHeadValue() != null) {
            hasVolatile |= placeholderResolver.hasVolatilePlaceholders(item.getHeadValue());
        }

        // Log apenas se volátil (não cacheável)
        if (hasVolatile) {
            logger.info("[ItemCompiler] DEBUG: Item type=" + item.getType() +
                    " has volatile placeholders -> NOT CACHEABLE");
        }

        return !hasVolatile;
    }

    /**
     * Constrói cache key para item.
     *
     * @param item    GuiItem
     * @param context Contexto
     * @return CacheKey
     */
    @NotNull
    private CacheKey buildCacheKey(
            @NotNull GuiItem item,
            @Nullable Player player,
            @NotNull InventoryContext context) {
        String itemKey = item.getType() + ":" + item.getSlot();
        UUID playerId = player != null ? player.getUniqueId() : null;
        boolean requiresPlayerScope = playerId != null && context.getPluginNamespace() != null;

        // Check if item depends on context keys ({key} or {lang:...})
        boolean isContextDependent = false;

        if (item.getName() != null) {
            isContextDependent |= placeholderResolver.hasContextAwarePlaceholders(item.getName());
        }
        if (!isContextDependent && item.getLore() != null) {
            for (String line : item.getLore()) {
                if (placeholderResolver.hasContextAwarePlaceholders(line)) {
                    isContextDependent = true;
                    break;
                }
            }
        }
        if (!isContextDependent && item.getHeadValue() != null) {
            isContextDependent |= placeholderResolver.hasContextAwarePlaceholders(item.getHeadValue());
        }

        if (isContextDependent) {
            // Item dinâmico (depende do contexto): inclui hash dos placeholders
            return requiresPlayerScope
                    ? CacheKey.ofDynamic(context.getInventoryId(), itemKey, context.getPlaceholders(), playerId)
                    : CacheKey.ofDynamic(context.getInventoryId(), itemKey, context.getPlaceholders());
        } else {
            // Item estático: cache simples
            return requiresPlayerScope
                    ? CacheKey.ofStatic(context.getInventoryId(), itemKey, playerId)
                    : CacheKey.ofStatic(context.getInventoryId(), itemKey);
        }
    }

    /**
     * Invalida cache de items de um inventário.
     *
     * @param inventoryId ID do inventário
     */
    public void invalidateCache(@NotNull String inventoryId) {
        cache.invalidate(inventoryId);

        if (debug) {
            logger.info("Invalidated item cache for inventory: " + inventoryId);
        }
    }

    /**
     * Invalida cache de item específico.
     *
     * @param inventoryId ID do inventário
     * @param itemKey     Chave do item
     */
    public void invalidateCache(@NotNull String inventoryId, @NotNull String itemKey) {
        cache.invalidate(inventoryId, itemKey);

        if (debug) {
            logger.fine("Invalidated item cache: " + inventoryId + ":" + itemKey);
        }
    }

    /**
     * Limpa todo o cache.
     */
    public void clearCache() {
        cache.invalidateAll();
        placeholderResolver.clearCache();

        if (debug) {
            logger.info("Cleared all item caches");
        }
    }

    /**
     * Limpa cache apenas de um jogador.
     *
     * @param playerId UUID do jogador
     */
    public void clearPlayerCache(@NotNull UUID playerId) {
        cache.invalidateByPlayer(playerId);
        placeholderResolver.clearCache(playerId);

        if (debug) {
            logger.fine("Cleared item and placeholder caches for player: " + playerId);
        }
    }

    /**
     * Obtém estatísticas do cache.
     *
     * @return String formatada com stats
     */
    @NotNull
    public String getCacheStats() {
        return cache.formatStats();
    }
}
