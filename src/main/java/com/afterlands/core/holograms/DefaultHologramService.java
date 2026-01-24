package com.afterlands.core.holograms;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import eu.decentsoftware.holograms.event.HologramClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Default implementation of HologramService using DecentHolograms API.
 * 
 * <p>
 * This implementation registers a Bukkit listener to capture hologram
 * click events and dispatch them to registered handlers.
 * </p>
 */
public class DefaultHologramService implements HologramService, Listener {

    private final Logger logger;
    private final boolean debug;
    private final Map<String, Consumer<HologramClickContext>> clickHandlers = new ConcurrentHashMap<>();

    public DefaultHologramService(Plugin corePlugin, Logger logger, boolean debug) {
        this.logger = logger;
        this.debug = debug;

        // Register event listener for hologram clicks
        Bukkit.getPluginManager().registerEvents(this, corePlugin);

        if (debug) {
            logger.info("[Holograms] DefaultHologramService initialized with click event listener");
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public @NotNull PluginHologramHandle forPlugin(@NotNull Plugin plugin) {
        return new DefaultPluginHologramHandle(plugin, this, logger, debug);
    }

    @Override
    public void registerClickHandler(@NotNull String fullName,
            @NotNull Consumer<HologramClickContext> handler) {
        clickHandlers.put(fullName.toLowerCase(), handler);
        if (debug) {
            logger.info("[Holograms] Registered click handler for: " + fullName);
        }
    }

    @Override
    public void unregisterClickHandler(@NotNull String fullName) {
        clickHandlers.remove(fullName.toLowerCase());
        if (debug) {
            logger.info("[Holograms] Unregistered click handler for: " + fullName);
        }
    }

    @EventHandler
    public void onHologramClick(HologramClickEvent event) {
        String name = event.getHologram().getName().toLowerCase();
        Consumer<HologramClickContext> handler = clickHandlers.get(name);

        if (handler != null) {
            ClickType clickType = mapClickType(event.getClick());
            HologramClickContext ctx = new HologramClickContext(
                    event.getPlayer(),
                    name,
                    clickType,
                    event.getPage().getIndex(),
                    event.getEntityId());

            try {
                handler.accept(ctx);
            } catch (Exception e) {
                logger.warning("[Holograms] Error executing click handler for " + name + ": " + e.getMessage());
                if (debug) {
                    e.printStackTrace();
                }
            }
        }
    }

    private ClickType mapClickType(Object dhClickType) {
        if (dhClickType == null) {
            return ClickType.RIGHT;
        }

        String typeName = dhClickType.toString().toUpperCase();
        return switch (typeName) {
            case "LEFT" -> ClickType.LEFT;
            case "SHIFT_LEFT" -> ClickType.SHIFT_LEFT;
            case "SHIFT_RIGHT" -> ClickType.SHIFT_RIGHT;
            default -> ClickType.RIGHT;
        };
    }

    /**
     * Clears all registered click handlers.
     * Called during shutdown.
     */
    public void shutdown() {
        clickHandlers.clear();
        if (debug) {
            logger.info("[Holograms] DefaultHologramService shutdown complete");
        }
    }
}
