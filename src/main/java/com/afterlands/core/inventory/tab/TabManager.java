package com.afterlands.core.inventory.tab;

import com.afterlands.core.inventory.InventoryConfig;
import com.afterlands.core.inventory.item.GuiItem;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Logger;

/**
 * Gerenciador de abas/tabs para inventários.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *     <li>Criar estado inicial de tabs</li>
 *     <li>Trocar aba ativa</li>
 *     <li>Renderizar items da aba ativa</li>
 *     <li>Renderizar ícones de todas as tabs</li>
 *     <li>Validar tabs</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> Todas as operações são thread-safe (stateless).</p>
 */
public class TabManager {

    private static final Logger LOGGER = Logger.getLogger(TabManager.class.getName());

    /**
     * Cria estado inicial de tabs para um player.
     *
     * @param config Configuração do inventário
     * @param playerId UUID do player
     * @return TabState inicial com tab padrão selecionada
     */
    @NotNull
    public TabState createInitialState(@NotNull InventoryConfig config, @NotNull UUID playerId) {
        String defaultTabId = getDefaultTabId(config);
        return TabState.initial(playerId, config.id(), defaultTabId);
    }

    /**
     * Troca aba ativa.
     *
     * @param currentState Estado atual
     * @param targetTabId ID da tab alvo
     * @return Novo TabState com tab ativa atualizada
     * @throws IllegalArgumentException se targetTabId for inválido
     */
    @NotNull
    public TabState switchTab(@NotNull TabState currentState, @NotNull String targetTabId) {
        if (targetTabId.isBlank()) {
            throw new IllegalArgumentException("targetTabId cannot be blank");
        }

        // Verificação de tab válida deve ser feita pelo caller
        // (TabManager não tem referência à config aqui)
        return currentState.withActiveTab(targetTabId);
    }

    /**
     * Troca aba ativa com validação.
     *
     * @param currentState Estado atual
     * @param targetTabId ID da tab alvo
     * @param config Configuração do inventário (para validação)
     * @return Novo TabState ou currentState se tab inválida
     */
    @NotNull
    public TabState switchTabSafe(@NotNull TabState currentState, @NotNull String targetTabId, @NotNull InventoryConfig config) {
        if (!isValidTab(config, targetTabId)) {
            LOGGER.warning("Attempted to switch to invalid tab: " + targetTabId + " in inventory " + config.id());
            return currentState;
        }

        return currentState.withActiveTab(targetTabId);
    }

    /**
     * Renderiza items da aba ativa.
     *
     * <p>Retorna apenas os items que pertencem à tab ativa.</p>
     *
     * @param state Estado de tabs
     * @param config Configuração do inventário
     * @return Map de slot -> GuiItem da tab ativa
     */
    @NotNull
    public Map<Integer, GuiItem> renderActiveTab(@NotNull TabState state, @NotNull InventoryConfig config) {
        TabConfig activeTab = getTabConfig(config, state.activeTabId());
        if (activeTab == null) {
            LOGGER.warning("Active tab not found: " + state.activeTabId() + " in inventory " + config.id());
            return Map.of();
        }

        Map<Integer, GuiItem> items = new HashMap<>();

        // Items específicos da tab
        for (GuiItem item : activeTab.items()) {
            items.put(item.getSlot(), item);

            // Handle duplicate slots
            for (int dupSlot : item.getDuplicateSlots()) {
                items.put(dupSlot, item);
            }
        }

        return items;
    }

    /**
     * Renderiza ícones de todas as tabs.
     *
     * <p>Cria items clicáveis para cada tab. A tab ativa pode ter visual diferente (enchanted, lore, etc.).</p>
     *
     * @param state Estado de tabs
     * @param config Configuração do inventário
     * @return Map de slot -> GuiItem com ícones das tabs
     */
    @NotNull
    public Map<Integer, GuiItem> renderTabIcons(@NotNull TabState state, @NotNull InventoryConfig config) {
        Map<Integer, GuiItem> icons = new HashMap<>();

        List<TabConfig> tabs = config.tabs();
        if (tabs.isEmpty()) {
            return icons;
        }

        // Assumindo que tab icons são renderizados em slots fixos
        // (ex: slots 48, 49, 50 para 3 tabs em um inventário 6x9)
        // O layout exato deve vir da config, aqui usamos heurística simples

        // Por padrão, tabs aparecem na última linha (row 5 = slots 45-53)
        // Distribuir igualmente
        int tabCount = tabs.size();
        int startSlot = 45 + (9 - tabCount) / 2; // Centraliza tabs na última linha

        for (int i = 0; i < tabs.size(); i++) {
            TabConfig tab = tabs.get(i);
            int slot = startSlot + i;

            boolean isActive = state.isActive(tab.tabId());

            GuiItem icon = createTabIcon(tab, isActive);
            icons.put(slot, icon);
        }

        return icons;
    }

    /**
     * Renderiza ícones de tabs em slots específicos.
     *
     * <p>Usa slots customizados definidos na config.</p>
     *
     * @param state Estado de tabs
     * @param config Configuração do inventário
     * @param tabIconSlots Map de tabId -> slot
     * @return Map de slot -> GuiItem com ícones das tabs
     */
    @NotNull
    public Map<Integer, GuiItem> renderTabIconsAtSlots(@NotNull TabState state, @NotNull InventoryConfig config,
                                                        @NotNull Map<String, Integer> tabIconSlots) {
        Map<Integer, GuiItem> icons = new HashMap<>();

        for (TabConfig tab : config.tabs()) {
            Integer slot = tabIconSlots.get(tab.tabId());
            if (slot == null) {
                LOGGER.warning("No slot defined for tab icon: " + tab.tabId());
                continue;
            }

            boolean isActive = state.isActive(tab.tabId());
            GuiItem icon = createTabIcon(tab, isActive);
            icons.put(slot, icon);
        }

        return icons;
    }

    /**
     * Valida se uma tab existe na configuração.
     *
     * @param config Configuração do inventário
     * @param tabId ID da tab
     * @return true se a tab existe
     */
    public boolean isValidTab(@NotNull InventoryConfig config, @NotNull String tabId) {
        return config.tabs().stream()
                .anyMatch(tab -> tab.tabId().equals(tabId));
    }

    /**
     * Obtém ID da tab padrão.
     *
     * @param config Configuração do inventário
     * @return ID da tab padrão ou primeira tab disponível, ou "default" se nenhuma tab
     */
    @NotNull
    public String getDefaultTabId(@NotNull InventoryConfig config) {
        List<TabConfig> tabs = config.tabs();
        if (tabs.isEmpty()) {
            return "default";
        }

        // Procura tab marcada como default
        return tabs.stream()
                .filter(TabConfig::defaultTab)
                .map(TabConfig::tabId)
                .findFirst()
                .orElse(tabs.get(0).tabId()); // Fallback: primeira tab
    }

    /**
     * Obtém configuração de uma tab específica.
     *
     * @param config Configuração do inventário
     * @param tabId ID da tab
     * @return TabConfig ou null se não encontrada
     */
    @Nullable
    public TabConfig getTabConfig(@NotNull InventoryConfig config, @NotNull String tabId) {
        return config.tabs().stream()
                .filter(tab -> tab.tabId().equals(tabId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Obtém todas as tab IDs disponíveis.
     *
     * @param config Configuração do inventário
     * @return Lista de tab IDs
     */
    @NotNull
    public List<String> getAllTabIds(@NotNull InventoryConfig config) {
        return config.tabs().stream()
                .map(TabConfig::tabId)
                .toList();
    }

    /**
     * Calcula próxima tab (navegação circular).
     *
     * @param currentState Estado atual
     * @param config Configuração do inventário
     * @return Novo TabState com próxima tab ativa
     */
    @NotNull
    public TabState switchToNextTab(@NotNull TabState currentState, @NotNull InventoryConfig config) {
        List<String> tabIds = getAllTabIds(config);
        if (tabIds.isEmpty() || tabIds.size() == 1) {
            return currentState;
        }

        int currentIndex = tabIds.indexOf(currentState.activeTabId());
        int nextIndex = (currentIndex + 1) % tabIds.size();
        String nextTabId = tabIds.get(nextIndex);

        return currentState.withActiveTab(nextTabId);
    }

    /**
     * Calcula tab anterior (navegação circular).
     *
     * @param currentState Estado atual
     * @param config Configuração do inventário
     * @return Novo TabState com tab anterior ativa
     */
    @NotNull
    public TabState switchToPreviousTab(@NotNull TabState currentState, @NotNull InventoryConfig config) {
        List<String> tabIds = getAllTabIds(config);
        if (tabIds.isEmpty() || tabIds.size() == 1) {
            return currentState;
        }

        int currentIndex = tabIds.indexOf(currentState.activeTabId());
        int prevIndex = (currentIndex - 1 + tabIds.size()) % tabIds.size();
        String prevTabId = tabIds.get(prevIndex);

        return currentState.withActiveTab(prevTabId);
    }

    // ========== Helper Methods ==========

    /**
     * Cria GuiItem para ícone de tab.
     */
    @NotNull
    private GuiItem createTabIcon(@NotNull TabConfig tab, boolean isActive) {
        GuiItem.Builder builder = new GuiItem.Builder()
                .type("tab-icon-" + tab.tabId())
                .material(tab.icon())
                .name(tab.displayName())
                .addAction("switch_tab:" + tab.tabId());

        // Tab ativa: adiciona enchant glow e lore indicativa
        if (isActive) {
            builder.enchanted(true)
                    .hideFlags(true)
                    .addLoreLine("&a▶ Active");
        } else {
            builder.addLoreLine("&7Click to switch");
        }

        return builder.build();
    }

    /**
     * Extrai slots de tab icons de items do inventário.
     *
     * <p>Procura por items com type "tab-icon-*" e extrai seus slots.</p>
     *
     * @param config Configuração do inventário
     * @return Map de tabId -> slot
     */
    @NotNull
    public Map<String, Integer> extractTabIconSlots(@NotNull InventoryConfig config) {
        Map<String, Integer> slots = new HashMap<>();

        for (GuiItem item : config.items()) {
            String type = item.getType();
            if (type != null && type.startsWith("tab-icon-")) {
                String tabId = type.substring("tab-icon-".length());
                slots.put(tabId, item.getSlot());
            }
        }

        return slots;
    }

    /**
     * Combina items de tab ativa com ícones de tabs.
     *
     * <p>Útil para renderização completa do inventário.</p>
     *
     * @param state Estado de tabs
     * @param config Configuração do inventário
     * @return Map combinado de slot -> GuiItem
     */
    @NotNull
    public Map<Integer, GuiItem> renderComplete(@NotNull TabState state, @NotNull InventoryConfig config) {
        Map<Integer, GuiItem> combined = new HashMap<>();

        // 1. Items da tab ativa
        combined.putAll(renderActiveTab(state, config));

        // 2. Ícones de tabs
        Map<String, Integer> tabIconSlots = extractTabIconSlots(config);
        if (!tabIconSlots.isEmpty()) {
            combined.putAll(renderTabIconsAtSlots(state, config, tabIconSlots));
        } else {
            // Fallback: renderiza tabs na última linha
            combined.putAll(renderTabIcons(state, config));
        }

        return combined;
    }
}
