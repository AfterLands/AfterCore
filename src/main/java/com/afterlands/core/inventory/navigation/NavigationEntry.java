package com.afterlands.core.inventory.navigation;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single entry in the inventory navigation history.
 *
 * <p>
 * Stores the panel ID and placeholders needed to restore the previous inventory state
 * when navigating back using the "previous_panel" action.
 * </p>
 *
 * <p>Thread-safe: immutable after construction.</p>
 */
public class NavigationEntry {

    private final String panelId;
    private final Map<String, String> placeholders;

    /**
     * Creates a new navigation entry.
     *
     * @param panelId The inventory panel ID
     * @param placeholders The placeholders at the time of navigation
     */
    public NavigationEntry(@NotNull String panelId, @NotNull Map<String, String> placeholders) {
        this.panelId = panelId;
        // Create defensive copy to ensure immutability
        this.placeholders = Map.copyOf(placeholders);
    }

    /**
     * Gets the panel ID.
     *
     * @return The panel ID
     */
    @NotNull
    public String getPanelId() {
        return panelId;
    }

    /**
     * Gets the placeholders.
     *
     * @return Immutable map of placeholders
     */
    @NotNull
    public Map<String, String> getPlaceholders() {
        return placeholders;
    }

    /**
     * Creates a mutable copy of the placeholders.
     *
     * @return Mutable HashMap containing all placeholders
     */
    @NotNull
    public Map<String, String> getPlaceholdersCopy() {
        return new HashMap<>(placeholders);
    }

    @Override
    public String toString() {
        return "NavigationEntry{" +
                "panelId='" + panelId + '\'' +
                ", placeholders=" + placeholders.size() +
                '}';
    }
}
