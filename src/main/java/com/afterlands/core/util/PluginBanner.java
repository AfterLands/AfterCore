package com.afterlands.core.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Utility for printing standardized plugin banners and load time information.
 */
public final class PluginBanner {

    private PluginBanner() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Prints the AfterLands ASCII banner with plugin information.
     *
     * @param plugin The plugin instance
     */
    public static void printBanner(@NotNull JavaPlugin plugin) {
        String pluginName = plugin.getDescription().getName();
        String version = plugin.getDescription().getVersion();
        String buildDate = getBuildDate(plugin);
        String authors = plugin.getDescription().getAuthors().toString();

        System.out.println(" ");
        System.out.println(
                "\u001B[1;36m     \\      _|  |   \u001B[0m\u001B[37m              _ \\   |   \u001B[0m\u001B[1;37m            _)              \u001B[0m");
        System.out.println(
                "\u001B[1;36m    _ \\    |    __|   _ \\   __|\u001B[0m\u001B[37m  |   |  |  |   |   _` |\u001B[0m\u001B[1;37m  |  __ \\    __| \u001B[0m");
        System.out.println(
                "\u001B[1;36m   ___ \\   __|  |     __/  |\u001B[0m\u001B[37m     ___/   |  |   |  (   |\u001B[0m\u001B[1;37m  |  |   | \\__ \\ \u001B[0m");
        System.out.println(
                "\u001B[1;36m _/    _\\ _|   \\__| \\___| _|\u001B[0m\u001B[37m    _|     _| \\__,_| \\__, |\u001B[0m\u001B[1;37m _| _|  _| ____/ \u001B[0m");
        System.out.println(
                "\u001B[1;37m                                                 |___/                  \u001B[0m");
        System.out.println(" ");
        System.out.println("\u001B[1;36m  " + pluginName + "\u001B[0m");
        System.out.println("\u001b[90m  Copyright © https://afterlands.com.");
        System.out.println(" ");
        System.out.println("\u001B[1;36m  Version: \u001B[1;37mv" + version
                + "\u001b[90m (Build " + buildDate + ")");
        System.out.println("\u001B[1;36m  Developers: \u001B[1;37m" + authors + "\u001B[0m");
        System.out.println(" ");
    }

    /**
     * Prints the plugin load time.
     *
     * @param plugin    The plugin instance
     * @param startTime The startup timestamp (from System.currentTimeMillis())
     */
    public static void printLoadTime(@NotNull JavaPlugin plugin, long startTime) {
        long endTime = System.currentTimeMillis();
        long elapsed = endTime - startTime;
        plugin.getLogger().info("Plugin successfully enabled! (" + elapsed + "ms)");
    }

    /**
     * Extracts the build date from plugin.yml.
     *
     * @param plugin The plugin instance
     * @return Formatted build date or "Unknown"
     */
    private static String getBuildDate(@NotNull JavaPlugin plugin) {
        try {
            FileConfiguration pluginYml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(plugin.getResource("plugin.yml"), StandardCharsets.UTF_8));
            String rawDate = pluginYml.getString("build-date", "Unknown");

            if (!rawDate.equals("Unknown")) {
                try {
                    rawDate = rawDate.replace("T", " ").replace("Z", "");
                    String[] parts = rawDate.split(" ");
                    if (parts.length == 2) {
                        String[] dateParts = parts[0].split("-");
                        String[] timeParts = parts[1].split(":");
                        return dateParts[2] + "/" + dateParts[1] + "/" + dateParts[0] +
                                " " + timeParts[0] + ":" + timeParts[1];
                    }
                    return rawDate;
                } catch (Exception e) {
                    return rawDate;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return "Unknown";
    }
}
