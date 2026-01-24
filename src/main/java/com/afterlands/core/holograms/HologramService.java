package com.afterlands.core.holograms;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Service for creating and managing holograms.
 * 
 * <p>
 * Uses DecentHolograms API under the hood. If DecentHolograms
 * is not installed, a no-op implementation is used.
 * </p>
 * 
 * <p>
 * <b>Usage:</b>
 * </p>
 * 
 * <pre>{@code
 * AfterCoreAPI core = AfterCore.get();
 * PluginHologramHandle holograms = core.holograms().forPlugin(myPlugin);
 * 
 * // Create a global hologram
 * holograms.create("shop", location, List.of("&6&lSHOP", "&7Click to open"), true);
 * 
 * // Register click handler
 * holograms.onClick("shop", ctx -> {
 *     openShopMenu(ctx.player());
 * });
 * }</pre>
 */
public interface HologramService {

    /**
     * Checks if the hologram backend (DecentHolograms) is available.
     *
     * @return true if DecentHolograms is installed and operational
     */
    boolean isAvailable();

    /**
     * Gets a scoped handle for a specific plugin.
     * 
     * <p>
     * All hologram names are automatically prefixed with the plugin name
     * to prevent conflicts. For example, if plugin "MyPlugin" creates a
     * hologram named "shop", the internal name becomes "myplugin_shop".
     * </p>
     *
     * @param plugin the plugin requesting the handle
     * @return a handle scoped to the given plugin
     */
    @NotNull
    PluginHologramHandle forPlugin(@NotNull Plugin plugin);

    /**
     * Registers a click handler for a hologram by its full namespaced name.
     * 
     * <p>
     * Prefer using {@link PluginHologramHandle#onClick} instead,
     * as it handles namespacing automatically.
     * </p>
     *
     * @param fullName the complete namespaced hologram name
     * @param handler  the handler to execute on click
     */
    void registerClickHandler(@NotNull String fullName, @NotNull Consumer<HologramClickContext> handler);

    /**
     * Unregisters a click handler for a hologram.
     *
     * @param fullName the complete namespaced hologram name
     */
    void unregisterClickHandler(@NotNull String fullName);
}
