package com.afterlands.core.config.impl;

import com.afterlands.core.config.ConfigService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class DefaultConfigService implements ConfigService {

    private final Plugin plugin;
    private final boolean debug;

    private YamlConfiguration messages;

    public DefaultConfigService(@NotNull Plugin plugin, boolean debug) {
        this.plugin = plugin;
        this.debug = debug;
        ensureDefaults();
        reloadAll();
    }

    private void ensureDefaults() {
        plugin.saveDefaultConfig();
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
    }

    @Override
    public @NotNull FileConfiguration main() {
        return plugin.getConfig();
    }

    @Override
    public @NotNull YamlConfiguration messages() {
        return messages;
    }

    @Override
    public void reloadAll() {
        plugin.reloadConfig();
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(messagesFile);
        if (debug) {
            plugin.getLogger().info("[AfterCore] Config reloadAll OK");
        }
    }
}

