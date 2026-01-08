package com.afterlands.core.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * API de configuração padronizada (YAML/JSON futuramente).
 */
public interface ConfigService {

    /**
     * Config principal do AfterCore (config.yml).
     */
    @NotNull FileConfiguration main();

    /**
     * Mensagens do AfterCore (messages.yml).
     */
    @NotNull YamlConfiguration messages();

    void reloadAll();
}

