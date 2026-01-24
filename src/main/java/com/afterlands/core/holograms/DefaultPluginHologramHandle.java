package com.afterlands.core.holograms;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Default implementation of PluginHologramHandle using DecentHolograms API.
 * 
 * <p>
 * All hologram names are automatically prefixed with the plugin name
 * to ensure namespace isolation between plugins.
 * </p>
 */
public class DefaultPluginHologramHandle implements PluginHologramHandle {

    private final Plugin plugin;
    private final DefaultHologramService service;
    private final Logger logger;
    private final boolean debug;
    private final String prefix;
    private final Set<String> createdHolograms = new HashSet<>();

    public DefaultPluginHologramHandle(Plugin plugin, DefaultHologramService service,
            Logger logger, boolean debug) {
        this.plugin = plugin;
        this.service = service;
        this.logger = logger;
        this.debug = debug;
        this.prefix = plugin.getName().toLowerCase() + "_";
    }

    @Override
    public @NotNull String getFullName(@NotNull String name) {
        return prefix + name.toLowerCase();
    }

    @Override
    public boolean create(@NotNull String name, @NotNull Location location,
            @NotNull List<String> lines, boolean persistent) {
        String fullName = getFullName(name);

        if (DHAPI.getHologram(fullName) != null) {
            if (debug) {
                logger.warning("[Holograms] Hologram '" + fullName + "' already exists");
            }
            return false;
        }

        try {
            DHAPI.createHologram(fullName, location, persistent, lines);
            createdHolograms.add(fullName);

            if (debug) {
                logger.info("[Holograms] Created hologram '" + fullName + "' at " +
                        location.getWorld().getName() + " " +
                        location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
            }
            return true;
        } catch (Exception e) {
            logger.warning("[Holograms] Failed to create hologram '" + fullName + "': " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean createForPlayer(@NotNull String name, @NotNull Location location,
            @NotNull List<String> lines, @NotNull Player player) {
        // Create unique name for player-specific hologram
        String playerName = name + "_" + player.getUniqueId().toString().replace("-", "");
        String fullName = getFullName(playerName);

        if (DHAPI.getHologram(fullName) != null) {
            if (debug) {
                logger.warning("[Holograms] Player hologram '" + fullName + "' already exists");
            }
            return false;
        }

        try {
            // Create non-persistent hologram
            DHAPI.createHologram(fullName, location, false, lines);
            Hologram hologram = DHAPI.getHologram(fullName);

            if (hologram != null) {
                // Make invisible by default, show only to target player
                hologram.setDefaultVisibleState(false);
                hologram.setShowPlayer(player);
                createdHolograms.add(fullName);

                if (debug) {
                    logger.info("[Holograms] Created player-specific hologram '" + fullName +
                            "' for " + player.getName());
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.warning("[Holograms] Failed to create player hologram '" + fullName + "': " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(@NotNull String name) {
        String fullName = getFullName(name);
        Hologram hologram = DHAPI.getHologram(fullName);

        if (hologram == null) {
            return false;
        }

        try {
            hologram.delete();
            createdHolograms.remove(fullName);
            service.unregisterClickHandler(fullName);

            if (debug) {
                logger.info("[Holograms] Deleted hologram '" + fullName + "'");
            }
            return true;
        } catch (Exception e) {
            logger.warning("[Holograms] Failed to delete hologram '" + fullName + "': " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean exists(@NotNull String name) {
        return DHAPI.getHologram(getFullName(name)) != null;
    }

    @Override
    public boolean setLine(@NotNull String name, int lineIndex, @NotNull String text) {
        String fullName = getFullName(name);
        Hologram hologram = DHAPI.getHologram(fullName);

        if (hologram == null) {
            return false;
        }

        try {
            DHAPI.setHologramLine(hologram, lineIndex, text);
            return true;
        } catch (Exception e) {
            if (debug) {
                logger.warning("[Holograms] Failed to set line " + lineIndex +
                        " of '" + fullName + "': " + e.getMessage());
            }
            return false;
        }
    }

    @Override
    public boolean setLines(@NotNull String name, @NotNull List<String> lines) {
        String fullName = getFullName(name);
        Hologram hologram = DHAPI.getHologram(fullName);

        if (hologram == null) {
            return false;
        }

        try {
            DHAPI.setHologramLines(hologram, lines);
            return true;
        } catch (Exception e) {
            if (debug) {
                logger.warning("[Holograms] Failed to set lines of '" + fullName + "': " + e.getMessage());
            }
            return false;
        }
    }

    @Override
    public boolean addLine(@NotNull String name, @NotNull String text) {
        String fullName = getFullName(name);
        Hologram hologram = DHAPI.getHologram(fullName);

        if (hologram == null) {
            return false;
        }

        try {
            DHAPI.addHologramLine(hologram, text);
            return true;
        } catch (Exception e) {
            if (debug) {
                logger.warning("[Holograms] Failed to add line to '" + fullName + "': " + e.getMessage());
            }
            return false;
        }
    }

    @Override
    public boolean removeLine(@NotNull String name, int lineIndex) {
        String fullName = getFullName(name);
        Hologram hologram = DHAPI.getHologram(fullName);

        if (hologram == null) {
            return false;
        }

        try {
            DHAPI.removeHologramLine(hologram, lineIndex);
            return true;
        } catch (Exception e) {
            if (debug) {
                logger.warning("[Holograms] Failed to remove line " + lineIndex +
                        " from '" + fullName + "': " + e.getMessage());
            }
            return false;
        }
    }

    @Override
    public boolean showTo(@NotNull String name, @NotNull Player player) {
        String fullName = getFullName(name);
        Hologram hologram = DHAPI.getHologram(fullName);

        if (hologram == null) {
            return false;
        }

        try {
            hologram.setShowPlayer(player);
            return true;
        } catch (Exception e) {
            if (debug) {
                logger.warning("[Holograms] Failed to show '" + fullName +
                        "' to " + player.getName() + ": " + e.getMessage());
            }
            return false;
        }
    }

    @Override
    public boolean hideFrom(@NotNull String name, @NotNull Player player) {
        String fullName = getFullName(name);
        Hologram hologram = DHAPI.getHologram(fullName);

        if (hologram == null) {
            return false;
        }

        try {
            hologram.setHidePlayer(player);
            return true;
        } catch (Exception e) {
            if (debug) {
                logger.warning("[Holograms] Failed to hide '" + fullName +
                        "' from " + player.getName() + ": " + e.getMessage());
            }
            return false;
        }
    }

    @Override
    public boolean setDefaultVisible(@NotNull String name, boolean visible) {
        String fullName = getFullName(name);
        Hologram hologram = DHAPI.getHologram(fullName);

        if (hologram == null) {
            return false;
        }

        try {
            hologram.setDefaultVisibleState(visible);
            return true;
        } catch (Exception e) {
            if (debug) {
                logger.warning("[Holograms] Failed to set visibility of '" + fullName + "': " + e.getMessage());
            }
            return false;
        }
    }

    @Override
    public boolean move(@NotNull String name, @NotNull Location location) {
        String fullName = getFullName(name);
        Hologram hologram = DHAPI.getHologram(fullName);

        if (hologram == null) {
            return false;
        }

        try {
            DHAPI.moveHologram(hologram, location);
            return true;
        } catch (Exception e) {
            if (debug) {
                logger.warning("[Holograms] Failed to move '" + fullName + "': " + e.getMessage());
            }
            return false;
        }
    }

    @Override
    public @Nullable Location getLocation(@NotNull String name) {
        String fullName = getFullName(name);
        Hologram hologram = DHAPI.getHologram(fullName);
        return hologram != null ? hologram.getLocation() : null;
    }

    @Override
    public void onClick(@NotNull String name, @NotNull Consumer<HologramClickContext> handler) {
        service.registerClickHandler(getFullName(name), handler);
    }

    @Override
    public void clearClickHandlers(@NotNull String name) {
        service.unregisterClickHandler(getFullName(name));
    }

    @Override
    public void deleteAll() {
        int count = 0;
        for (String fullName : new ArrayList<>(createdHolograms)) {
            try {
                Hologram hologram = DHAPI.getHologram(fullName);
                if (hologram != null) {
                    hologram.delete();
                    count++;
                }
                service.unregisterClickHandler(fullName);
            } catch (Exception e) {
                if (debug) {
                    logger.warning("[Holograms] Failed to delete hologram '" + fullName +
                            "' during cleanup: " + e.getMessage());
                }
            }
        }
        createdHolograms.clear();

        if (debug) {
            logger.info("[Holograms] Cleaned up " + count + " hologram(s) for plugin " + plugin.getName());
        }
    }
}
