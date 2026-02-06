package com.afterlands.core.config;

import com.afterlands.core.config.update.ConfigUpdater;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * API de configuração padronizada (YAML/JSON futuramente).
 */
public interface ConfigService {

    /**
     * Config principal do AfterCore (config.yml).
     */
    @NotNull
    FileConfiguration main();

    /**
     * Mensagens do AfterCore (messages.yml).
     */
    @NotNull
    YamlConfiguration messages();

    void reloadAll();

    /**
     * Atualiza um arquivo de configuração de forma robusta e atômica.
     * Suporta arquivos versionados (com migrations) e genéricos.
     *
     * @param plugin       Plugin dono do arquivo
     * @param resourceName Nome do arquivo (ex: config.yml)
     * @return true se o arquivo foi atualizado
     */
    boolean update(@NotNull Plugin plugin, @NotNull String resourceName);

    /**
     * Atualiza um arquivo de configuração com opções personalizadas (ex:
     * migrations).
     *
     * @param plugin       Plugin dono do arquivo
     * @param resourceName Nome do arquivo (ex: config.yml)
     * @param options      Configurador para registrar migrations, etc.
     * @return true se o arquivo foi atualizado
     */
    boolean update(@NotNull Plugin plugin, @NotNull String resourceName,
            @NotNull Consumer<ConfigUpdater> options);
}
