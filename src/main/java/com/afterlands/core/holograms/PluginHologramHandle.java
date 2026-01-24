package com.afterlands.core.holograms;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Scoped handle for a plugin's holograms.
 * 
 * <p>
 * All hologram names are automatically prefixed with the plugin name
 * to prevent conflicts between plugins. For example, if plugin "MyPlugin"
 * creates a hologram named "shop", the internal name becomes "myplugin_shop".
 * </p>
 * 
 * <p>
 * <b>Example usage:</b>
 * </p>
 * 
 * <pre>{@code
 * PluginHologramHandle holograms = core.holograms().forPlugin(this);
 * 
 * // Create a global hologram visible to all players
 * holograms.create("shop", location, List.of("&6&lSHOP", "&7Click to open"), true);
 * 
 * // Create a hologram visible only to one player
 * holograms.createForPlayer("quest_marker", location,
 *         List.of("&e➤ &fTalk to the merchant"), player);
 * 
 * // Register click handler
 * holograms.onClick("shop", ctx -> {
 *     if (ctx.clickType() == ClickType.RIGHT) {
 *         openShopMenu(ctx.player());
 *     }
 * });
 * 
 * // Cleanup in onDisable
 * holograms.deleteAll();
 * }</pre>
 */
public interface PluginHologramHandle {

    // ========== BASIC CRUD ==========

    /**
     * Creates a global hologram visible to all players.
     *
     * @param name       unique identifier for the hologram (within this plugin's
     *                   namespace)
     * @param location   where to place the hologram
     * @param lines      initial text lines (supports color codes and
     *                   PlaceholderAPI)
     * @param persistent if true, survives server restarts
     * @return true if created successfully
     */
    boolean create(@NotNull String name, @NotNull Location location,
            @NotNull List<String> lines, boolean persistent);

    /**
     * Creates a non-persistent hologram visible to all players.
     */
    default boolean create(@NotNull String name, @NotNull Location location,
            @NotNull List<String> lines) {
        return create(name, location, lines, false);
    }

    /**
     * Creates a hologram visible only to a specific player.
     * 
     * <p>
     * The hologram name is automatically suffixed with the player's UUID
     * to ensure uniqueness per player.
     * </p>
     *
     * @param name     base identifier for the hologram
     * @param location where to place the hologram
     * @param lines    initial text lines
     * @param player   the only player who will see this hologram
     * @return true if created successfully
     */
    boolean createForPlayer(@NotNull String name, @NotNull Location location,
            @NotNull List<String> lines, @NotNull Player player);

    /**
     * Deletes a hologram by name.
     *
     * @param name hologram identifier
     * @return true if deleted successfully
     */
    boolean delete(@NotNull String name);

    /**
     * Checks if a hologram exists.
     *
     * @param name hologram identifier
     * @return true if the hologram exists
     */
    boolean exists(@NotNull String name);

    // ========== LINE MANAGEMENT ==========

    /**
     * Updates a specific line of a hologram.
     *
     * @param name      hologram identifier
     * @param lineIndex 0-based line index
     * @param text      new text content
     * @return true if updated successfully
     */
    boolean setLine(@NotNull String name, int lineIndex, @NotNull String text);

    /**
     * Replaces all lines of a hologram.
     *
     * @param name  hologram identifier
     * @param lines new lines to set
     * @return true if updated successfully
     */
    boolean setLines(@NotNull String name, @NotNull List<String> lines);

    /**
     * Adds a new line to the end of a hologram.
     *
     * @param name hologram identifier
     * @param text text for the new line
     * @return true if added successfully
     */
    boolean addLine(@NotNull String name, @NotNull String text);

    /**
     * Removes a line from a hologram.
     *
     * @param name      hologram identifier
     * @param lineIndex 0-based line index to remove
     * @return true if removed successfully
     */
    boolean removeLine(@NotNull String name, int lineIndex);

    // ========== VISIBILITY CONTROL ==========

    /**
     * Shows hologram to a specific player.
     * 
     * <p>
     * The hologram must have been created with {@link #setDefaultVisible}
     * set to false for this to have any effect.
     * </p>
     *
     * @param name   hologram identifier
     * @param player player to show the hologram to
     * @return true if successful
     */
    boolean showTo(@NotNull String name, @NotNull Player player);

    /**
     * Hides hologram from a specific player.
     *
     * @param name   hologram identifier
     * @param player player to hide the hologram from
     * @return true if successful
     */
    boolean hideFrom(@NotNull String name, @NotNull Player player);

    /**
     * Sets default visibility state for a hologram.
     * 
     * <p>
     * If set to false, the hologram is invisible by default and
     * must be shown to individual players using {@link #showTo}.
     * </p>
     *
     * @param name    hologram identifier
     * @param visible if false, hologram is invisible by default
     * @return true if successful
     */
    boolean setDefaultVisible(@NotNull String name, boolean visible);

    // ========== MOVEMENT ==========

    /**
     * Moves a hologram to a new location.
     *
     * @param name     hologram identifier
     * @param location new location
     * @return true if moved successfully
     */
    boolean move(@NotNull String name, @NotNull Location location);

    /**
     * Gets the location of a hologram.
     *
     * @param name hologram identifier
     * @return the hologram location, or null if not found
     */
    @Nullable
    Location getLocation(@NotNull String name);

    // ========== CLICK ACTIONS ==========

    /**
     * Registers a click handler for this hologram.
     * 
     * <p>
     * The handler will be called for any click type. Use
     * {@link HologramClickContext#clickType()} to differentiate.
     * </p>
     *
     * @param name    hologram identifier
     * @param handler handler to execute on click
     */
    void onClick(@NotNull String name, @NotNull Consumer<HologramClickContext> handler);

    /**
     * Clears all click handlers for a hologram.
     *
     * @param name hologram identifier
     */
    void clearClickHandlers(@NotNull String name);

    // ========== CLEANUP ==========

    /**
     * Deletes all holograms created by this plugin.
     * 
     * <p>
     * Call this in your plugin's {@code onDisable()} method to
     * clean up all holograms.
     * </p>
     */
    void deleteAll();

    /**
     * Gets the full namespaced name for a hologram.
     * 
     * <p>
     * For example, if plugin "MyPlugin" has a hologram named "shop",
     * this returns "myplugin_shop".
     * </p>
     *
     * @param name the short hologram name
     * @return the full namespaced name
     */
    @NotNull
    String getFullName(@NotNull String name);
}
