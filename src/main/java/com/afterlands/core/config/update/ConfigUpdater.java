package com.afterlands.core.config.update;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.logging.Logger;

/**
 * Atualiza o config.yml preservando valores do usuário e mesclando novos defaults.
 *
 * <p>Estratégia:
 * <ul>
 *   <li>Compara versão atual com versão esperada</li>
 *   <li>Mescla novas chaves dos defaults preservando valores existentes</li>
 *   <li>Aplica migrations específicas quando necessário</li>
 * </ul>
 * </p>
 */
public final class ConfigUpdater {

    private static final int CURRENT_VERSION = 1;

    private final Logger logger;
    private final Map<Integer, ConfigMigration> migrations = new HashMap<>();

    public ConfigUpdater(@NotNull Logger logger) {
        this.logger = logger;
        registerMigrations();
    }

    /**
     * Atualiza o config se necessário, retornando true se houve alterações.
     *
     * @param userConfig   Config atual do usuário
     * @param defaultConfig Config com defaults (do JAR)
     * @return true se config foi atualizado
     */
    public boolean update(@NotNull FileConfiguration userConfig,
                         @NotNull FileConfiguration defaultConfig) {
        int currentVersion = userConfig.getInt("config-version", 0);

        if (currentVersion == 0) {
            logger.info("Config sem versão detectado. Adicionando versão " + CURRENT_VERSION);
            userConfig.set("config-version", CURRENT_VERSION);
            mergeDefaults(userConfig, defaultConfig);
            return true;
        }

        if (currentVersion == CURRENT_VERSION) {
            // Mesmo assim, mesclar defaults novos
            boolean changed = mergeDefaults(userConfig, defaultConfig);
            if (changed) {
                logger.info("Novos valores padrão adicionados ao config");
            }
            return changed;
        }

        if (currentVersion > CURRENT_VERSION) {
            logger.warning("Config possui versão futura (" + currentVersion + "). " +
                    "Plugin atual suporta apenas versão " + CURRENT_VERSION + ". " +
                    "Possível downgrade de versão do plugin?");
            return false;
        }

        // currentVersion < CURRENT_VERSION: aplicar migrations
        logger.info("Atualizando config da versão " + currentVersion + " para " + CURRENT_VERSION);

        boolean changed = false;
        for (int version = currentVersion + 1; version <= CURRENT_VERSION; version++) {
            ConfigMigration migration = migrations.get(version);
            if (migration != null) {
                logger.info("Aplicando migration para versão " + version);
                migration.migrate(userConfig, defaultConfig);
                changed = true;
            }
        }

        userConfig.set("config-version", CURRENT_VERSION);

        // Mesclar defaults após migrations
        if (mergeDefaults(userConfig, defaultConfig)) {
            changed = true;
        }

        if (changed) {
            logger.info("Config atualizado com sucesso para versão " + CURRENT_VERSION);
        }

        return changed;
    }

    /**
     * Mescla valores padrão que não existem no config do usuário.
     *
     * @return true se algum valor foi adicionado
     */
    private boolean mergeDefaults(@NotNull ConfigurationSection userConfig,
                                 @NotNull ConfigurationSection defaultConfig) {
        boolean changed = false;

        for (String key : defaultConfig.getKeys(true)) {
            // Pular seções (processar apenas valores finais)
            if (defaultConfig.isConfigurationSection(key)) {
                continue;
            }

            // Se não existe no config do usuário, adicionar
            if (!userConfig.contains(key)) {
                Object defaultValue = defaultConfig.get(key);
                userConfig.set(key, defaultValue);
                logger.fine("Adicionada chave ausente: " + key + " = " + defaultValue);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Registra migrations por versão.
     */
    private void registerMigrations() {
        // Exemplo: migration para versão 1 (inicial)
        // Se no futuro houver versão 2, adicionar aqui
        // migrations.put(2, new MigrationTo2());
    }

    /**
     * Interface para migrations de config.
     */
    @FunctionalInterface
    public interface ConfigMigration {
        /**
         * Aplica a migration no config do usuário.
         *
         * @param userConfig   Config do usuário (será modificado)
         * @param defaultConfig Config padrão (apenas leitura)
         */
        void migrate(@NotNull ConfigurationSection userConfig,
                    @NotNull ConfigurationSection defaultConfig);
    }

    /**
     * Cria uma cópia profunda de um ConfigurationSection.
     * Útil para testes e comparações.
     */
    @NotNull
    public static YamlConfiguration deepCopy(@NotNull ConfigurationSection section) {
        YamlConfiguration copy = new YamlConfiguration();
        for (String key : section.getKeys(true)) {
            copy.set(key, section.get(key));
        }
        return copy;
    }

    /**
     * Helper para renomear uma chave preservando o valor.
     */
    public static void renameKey(@NotNull ConfigurationSection config,
                                @NotNull String oldKey,
                                @NotNull String newKey) {
        if (config.contains(oldKey) && !config.contains(newKey)) {
            Object value = config.get(oldKey);
            config.set(newKey, value);
            config.set(oldKey, null);
        }
    }

    /**
     * Helper para mover uma chave para outra seção.
     */
    public static void moveKey(@NotNull ConfigurationSection config,
                              @NotNull String oldPath,
                              @NotNull String newPath) {
        if (config.contains(oldPath) && !config.contains(newPath)) {
            Object value = config.get(oldPath);
            config.set(newPath, value);
            config.set(oldPath, null);
        }
    }
}
