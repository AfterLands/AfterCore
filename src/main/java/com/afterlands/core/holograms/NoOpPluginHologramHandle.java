package com.afterlands.core.holograms;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * No-op implementation of PluginHologramHandle used when DecentHolograms is not
 * installed.
 * 
 * <p>
 * All operations silently fail and return false or null.
 * </p>
 */
public class NoOpPluginHologramHandle implements PluginHologramHandle {

    @Override
    public boolean create(@NotNull String name, @NotNull Location location,
            @NotNull List<String> lines, boolean persistent) {
        return false;
    }

    @Override
    public boolean createForPlayer(@NotNull String name, @NotNull Location location,
            @NotNull List<String> lines, @NotNull Player player) {
        return false;
    }

    @Override
    public boolean delete(@NotNull String name) {
        return false;
    }

    @Override
    public boolean exists(@NotNull String name) {
        return false;
    }

    @Override
    public boolean setLine(@NotNull String name, int lineIndex, @NotNull String text) {
        return false;
    }

    @Override
    public boolean setLines(@NotNull String name, @NotNull List<String> lines) {
        return false;
    }

    @Override
    public boolean addLine(@NotNull String name, @NotNull String text) {
        return false;
    }

    @Override
    public boolean removeLine(@NotNull String name, int lineIndex) {
        return false;
    }

    @Override
    public boolean showTo(@NotNull String name, @NotNull Player player) {
        return false;
    }

    @Override
    public boolean hideFrom(@NotNull String name, @NotNull Player player) {
        return false;
    }

    @Override
    public boolean setDefaultVisible(@NotNull String name, boolean visible) {
        return false;
    }

    @Override
    public boolean move(@NotNull String name, @NotNull Location location) {
        return false;
    }

    @Override
    public @Nullable Location getLocation(@NotNull String name) {
        return null;
    }

    @Override
    public void onClick(@NotNull String name, @NotNull Consumer<HologramClickContext> handler) {
        // No-op
    }

    @Override
    public void clearClickHandlers(@NotNull String name) {
        // No-op
    }

    @Override
    public void deleteAll() {
        // No-op
    }

    @Override
    public @NotNull String getFullName(@NotNull String name) {
        return "noop_" + name;
    }
}
