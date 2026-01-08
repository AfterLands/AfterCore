package com.afterlands.core.actions.handlers;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handler para teleportar players.
 *
 * <p>Formato: {@code teleport: <world> <x> <y> <z> [yaw] [pitch]}</p>
 * <p>Suporta:</p>
 * <ul>
 *     <li>Coordenadas absolutas</li>
 *     <li>Coordenadas relativas (~x ~y ~z)</li>
 *     <li>Yaw e pitch opcionais</li>
 * </ul>
 *
 * <p>Exemplos:</p>
 * <pre>
 * teleport: world 0 64 0
 * teleport: world_nether 100 64 200 90 0
 * teleport: world ~0 ~10 ~0          # 10 blocos acima
 * teleport: world ~5 ~ ~-5           # 5 blocos X+, 5 blocos Z-
 * </pre>
 */
public final class TeleportHandler implements ActionHandler {

    @Override
    public void execute(@NotNull Player target, @NotNull ActionSpec spec) {
        String args = spec.rawArgs();
        if (args == null || args.isEmpty()) {
            return;
        }

        // Parse: world x y z [yaw] [pitch]
        // Support both space and semicolon separators for backward compatibility
        String[] parts = args.split("[\\s;]+");
        if (parts.length < 4) {
            return; // Mínimo: world x y z
        }

        String worldName = parts[0];
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return; // Mundo inválido
        }

        Location currentLoc = target.getLocation();

        // Parse coordenadas (suportar relativas ~)
        double x = parseCoordinate(parts[1], currentLoc.getX());
        double y = parseCoordinate(parts[2], currentLoc.getY());
        double z = parseCoordinate(parts[3], currentLoc.getZ());

        float yaw = currentLoc.getYaw();
        float pitch = currentLoc.getPitch();

        if (parts.length >= 5) {
            try {
                yaw = Float.parseFloat(parts[4]);
            } catch (NumberFormatException ignored) {
            }
        }

        if (parts.length >= 6) {
            try {
                pitch = Float.parseFloat(parts[5]);
            } catch (NumberFormatException ignored) {
            }
        }

        Location destination = new Location(world, x, y, z, yaw, pitch);
        target.teleport(destination);
    }

    /**
     * Parse coordenada com suporte a relativas (~).
     *
     * @param input Coordenada (ex: "100", "~", "~5", "~-10")
     * @param current Valor atual (para relativas)
     * @return Coordenada absoluta
     */
    private double parseCoordinate(@NotNull String input, double current) {
        String trimmed = input.trim();

        if (trimmed.equals("~")) {
            return current; // Relativa sem offset
        }

        if (trimmed.startsWith("~")) {
            // Relativa com offset: ~5 = current + 5
            try {
                double offset = Double.parseDouble(trimmed.substring(1));
                return current + offset;
            } catch (NumberFormatException e) {
                return current; // Fallback
            }
        }

        // Absoluta
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            return current; // Fallback
        }
    }
}
