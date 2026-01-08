package com.afterlands.core.inventory.tab;

import com.afterlands.core.inventory.item.GuiItem;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Configuração de uma aba/tab.
 *
 * <p>Tabs permitem múltiplas "páginas" dentro de um mesmo inventário,
 * com layouts e itens diferentes por tab.</p>
 */
public record TabConfig(
        @NotNull String tabId,
        @NotNull String displayName,
        @NotNull Material icon,
        @NotNull List<Integer> slots,
        @NotNull List<String> layout,
        @NotNull List<GuiItem> items,
        boolean defaultTab
) {

    /**
     * Construtor compacto com validação.
     */
    public TabConfig {
        if (tabId == null || tabId.isBlank()) {
            throw new IllegalArgumentException("tabId cannot be null or blank");
        }
        if (displayName == null) {
            displayName = tabId;
        }
        if (icon == null) {
            icon = Material.PAPER;
        }
        if (slots == null) {
            slots = List.of();
        }
        if (layout == null) {
            layout = List.of();
        }
        if (items == null) {
            items = List.of();
        }
    }

    /**
     * Verifica se um slot pertence a esta tab.
     *
     * @param slot Índice do slot
     * @return true se pertence
     */
    public boolean hasSlot(int slot) {
        return slots.contains(slot);
    }

    @Override
    public String toString() {
        return "TabConfig{" +
                "id='" + tabId + '\'' +
                ", name='" + displayName + '\'' +
                ", icon=" + icon +
                ", slots=" + slots.size() +
                ", default=" + defaultTab +
                '}';
    }
}
