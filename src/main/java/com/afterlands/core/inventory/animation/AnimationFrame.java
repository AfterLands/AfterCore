package com.afterlands.core.inventory.animation;

import com.afterlands.core.inventory.item.GuiItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Representa um frame de animação de item.
 *
 * <p>Um frame contém:
 * <ul>
 *     <li>Material e data value</li>
 *     <li>Nome e lore customizados</li>
 *     <li>Enchantment glow flag</li>
 *     <li>NBT overrides (opcional)</li>
 *     <li>Duração do frame em ticks</li>
 * </ul>
 * </p>
 *
 * <p>Thread Safety: Imutável, thread-safe.</p>
 */
public record AnimationFrame(
        @NotNull Material material,
        short data,
        @Nullable String name,
        @NotNull List<String> lore,
        boolean enchanted,
        @NotNull Map<String, String> nbtOverrides,
        long durationTicks
) {

    /**
     * Compact constructor com validação.
     */
    public AnimationFrame {
        if (material == null) {
            throw new IllegalArgumentException("material cannot be null");
        }
        if (durationTicks <= 0) {
            durationTicks = 1L; // Mínimo 1 tick
        }
        if (lore == null) {
            lore = List.of();
        }
        if (nbtOverrides == null) {
            nbtOverrides = Map.of();
        }
    }

    /**
     * Parse frame de ConfigurationSection.
     *
     * <p>Formato YAML esperado:</p>
     * <pre>
     * - material: DIAMOND_SWORD
     *   data: 0
     *   name: "&aFrame 1"
     *   lore:
     *     - "&7Line 1"
     *   enchanted: true
     *   duration: 10  # ticks
     *   nbt:
     *     key1: value1
     * </pre>
     *
     * @param section ConfigurationSection do frame
     * @return AnimationFrame parseado
     * @throws IllegalArgumentException se configuração inválida
     */
    @NotNull
    public static AnimationFrame fromConfig(@NotNull ConfigurationSection section) {
        // Material
        String materialStr = section.getString("material", "STONE");
        Material material;
        try {
            material = Material.valueOf(materialStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid material: " + materialStr);
        }

        // Data
        short data = (short) section.getInt("data", 0);

        // Name
        String name = section.getString("name");

        // Lore
        List<String> lore = section.getStringList("lore");

        // Enchanted
        boolean enchanted = section.getBoolean("enchanted", false);

        // Duration
        long duration = section.getLong("duration", 20L);

        // NBT overrides
        Map<String, String> nbtOverrides = new HashMap<>();
        ConfigurationSection nbtSection = section.getConfigurationSection("nbt");
        if (nbtSection != null) {
            for (String key : nbtSection.getKeys(false)) {
                nbtOverrides.put(key, nbtSection.getString(key));
            }
        }

        return new AnimationFrame(material, data, name, lore, enchanted, nbtOverrides, duration);
    }

    /**
     * Converte frame para GuiItem temporário.
     *
     * <p>Este GuiItem não tem actions/animations e é usado apenas para rendering.</p>
     *
     * @param slot Slot onde o item será renderizado
     * @return GuiItem representando este frame
     */
    @NotNull
    public GuiItem toGuiItem(int slot) {
        GuiItem.Builder builder = new GuiItem.Builder()
                .slot(slot)
                .material(material)
                .data(data)
                .enchanted(enchanted)
                .cacheable(false); // Frames de animação não são cacheados

        if (name != null) {
            builder.name(name);
        }

        if (!lore.isEmpty()) {
            builder.lore(lore);
        }

        if (!nbtOverrides.isEmpty()) {
            builder.nbtTags(nbtOverrides);
        }

        return builder.build();
    }

    /**
     * Cria frame simples com apenas material e duração.
     *
     * @param material Material do item
     * @param durationTicks Duração em ticks
     * @return AnimationFrame simples
     */
    @NotNull
    public static AnimationFrame simple(@NotNull Material material, long durationTicks) {
        return new AnimationFrame(material, (short) 0, null, List.of(), false, Map.of(), durationTicks);
    }

    /**
     * Cria frame com material, data e duração.
     *
     * @param material Material do item
     * @param data Data value
     * @param durationTicks Duração em ticks
     * @return AnimationFrame
     */
    @NotNull
    public static AnimationFrame withData(@NotNull Material material, short data, long durationTicks) {
        return new AnimationFrame(material, data, null, List.of(), false, Map.of(), durationTicks);
    }

    @Override
    public String toString() {
        return "AnimationFrame{" +
                "material=" + material +
                ", data=" + data +
                ", duration=" + durationTicks +
                "t}";
    }
}
