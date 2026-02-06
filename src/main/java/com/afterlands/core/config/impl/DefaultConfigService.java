package com.afterlands.core.config.impl;

import com.afterlands.core.config.ConfigService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import com.afterlands.core.config.update.ConfigUpdater;
import org.jetbrains.annotations.Nullable;

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
            plugin.getLogger().info("Config reloadAll OK");
        }
    }

    @Override
    public boolean update(@NotNull Plugin targetPlugin, @NotNull String resourceName) {
        return update(targetPlugin, resourceName, null);
    }

    @Override
    public boolean update(@NotNull Plugin targetPlugin, @NotNull String resourceName,
            @Nullable Consumer<ConfigUpdater> options) {

        // Validação básica
        InputStream defaultStream = targetPlugin.getResource(resourceName);
        if (defaultStream == null) {
            if (debug)
                plugin.getLogger().warning("Recurso não encontrado no JAR do plugin alvo: " + resourceName);
            return false;
        }

        File configFile = new File(targetPlugin.getDataFolder(), resourceName);
        if (!configFile.exists()) {
            targetPlugin.saveResource(resourceName, false);
            return true;
        }

        // Fluxo completo do ConfigUpdater
        YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8));

        InputStream mergeStream = targetPlugin.getResource(resourceName);

        ConfigUpdater updater = new ConfigUpdater(
                targetPlugin.getLogger(), configFile);

        return updater.update(currentConfig, mergeStream, defaultConfig, options);
    }
}
