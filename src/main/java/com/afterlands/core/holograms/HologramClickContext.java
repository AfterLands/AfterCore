package com.afterlands.core.holograms;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Context for hologram click events.
 * 
 * @param player       the player who clicked
 * @param hologramName the full namespaced name of the hologram
 * @param clickType    the type of click performed
 * @param pageIndex    the page index that was clicked
 * @param entityId     the entity ID of the clicked hologram line
 */
public record HologramClickContext(
        @NotNull Player player,
        @NotNull String hologramName,
        @NotNull ClickType clickType,
        int pageIndex,
        int entityId) {
}
