package com.afterlands.core.holograms;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * No-op implementation of HologramService used when DecentHolograms is not
 * installed.
 * 
 * <p>
 * All operations silently fail or return false. A warning is logged once
 * on the first operation attempt.
 * </p>
 */
public class NoOpHologramService implements HologramService {

    private final Logger logger;
    private boolean warnedOnce = false;

    public NoOpHologramService(Logger logger) {
        this.logger = logger;
    }

    private void warnOnce() {
        if (!warnedOnce) {
            logger.warning("[Holograms] DecentHolograms not installed - hologram operations are disabled");
            warnedOnce = true;
        }
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public @NotNull PluginHologramHandle forPlugin(@NotNull Plugin plugin) {
        warnOnce();
        return new NoOpPluginHologramHandle();
    }

    @Override
    public void registerClickHandler(@NotNull String fullName,
            @NotNull Consumer<HologramClickContext> handler) {
        // No-op
    }

    @Override
    public void unregisterClickHandler(@NotNull String fullName) {
        // No-op
    }
}
