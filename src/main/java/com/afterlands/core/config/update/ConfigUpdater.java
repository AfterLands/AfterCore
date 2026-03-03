package com.afterlands.core.config.update;

import com.afterlands.core.config.io.AtomicConfigWriter;
import com.afterlands.core.config.io.ConfigBackupManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

/**
 * Atualiza arquivos YAML preservando valores do usuário e comentários.
 *
 * <p>
 * Suporta dois modos:
 * <ul>
 * <li><b>Versionado (ex: config.yml):</b> Gerencia migrations e verifica
 * versão.</li>
 * <li><b>Genérico (ex: messages.yml):</b> Apenas adiciona chaves faltantes
 * (Smart Merge).</li>
 * </ul>
 * </p>
 *
 * <p>
 * Usa {@link CommentAwareWriter} para reconstruir o arquivo inteiro,
 * preservando comentários do default e valores do usuário.
 * Seções marcadas como "user-owned" via {@link MergeOptions} não são
 * mergeadas do default.
 * </p>
 */
public final class ConfigUpdater {

    private final Logger logger;
    private final File configFile;
    private final ConfigBackupManager backupManager;
    private final Map<Integer, ConfigMigration> migrations = new HashMap<>();
    private MergeOptions mergeOptions = MergeOptions.none();

    public ConfigUpdater(@NotNull Logger logger, @NotNull File configFile) {
        this.logger = logger;
        this.configFile = configFile;
        this.backupManager = new ConfigBackupManager(logger);
    }

    /**
     * Registra uma migration para uma versão específica.
     */
    public void registerMigration(int version, ConfigMigration migration) {
        migrations.put(version, migration);
    }

    /**
     * Define opções de merge (seções user-owned).
     */
    public void setMergeOptions(@NotNull MergeOptions mergeOptions) {
        this.mergeOptions = mergeOptions;
    }

    /**
     * Atualiza o arquivo se necessário.
     * Versão simplificada (sem options)
     */
    public boolean update(@NotNull FileConfiguration userConfig,
            @Nullable InputStream defaultStream,
            @NotNull FileConfiguration defaultConfig) {
        return update(userConfig, defaultStream, defaultConfig, null);
    }

    /**
     * Atualiza o arquivo com opções de personalização (registro de migrations).
     */
    public boolean update(@NotNull FileConfiguration userConfig,
            @Nullable InputStream defaultStream,
            @NotNull FileConfiguration defaultConfig,
            @Nullable java.util.function.Consumer<ConfigUpdater> options) {

        // Aplica configurações extras (ex: registrar migrations externas)
        if (options != null) {
            options.accept(this);
        }

        double targetVersion = defaultConfig.getDouble("config-version", 0.0);

        if (targetVersion > 0) {
            return updateVersioned(userConfig, defaultStream, defaultConfig, targetVersion);
        } else {
            return updateGeneric(userConfig, defaultStream, defaultConfig);
        }
    }

    private boolean updateVersioned(FileConfiguration userConfig, InputStream defaultStream,
            FileConfiguration defaultConfig, double targetVersion) {
        double currentVersion = userConfig.getDouble("config-version", 0.0);
        boolean migrated = false;

        // 1. Caso crítico: Sem versão -> Assumir nova versão
        if (currentVersion == 0.0) {
            logger.info("[Config] " + configFile.getName() + " sem versão. Definindo v" + targetVersion);
            backupManager.createBackup(configFile);
            currentVersion = targetVersion;
            userConfig.set("config-version", targetVersion);
            migrated = true;
        }

        // 2. Migrations (em memória via Bukkit API)
        if (currentVersion < targetVersion) {
            logger.info("[Config] Atualizando " + configFile.getName() + ": v" + currentVersion + " -> v" + targetVersion);
            if (!migrated) {
                backupManager.createBackup(configFile);
            }

            int currentMajor = (int) Math.floor(currentVersion);
            int targetMajor = (int) Math.floor(targetVersion);

            for (int version = currentMajor + 1; version <= targetMajor; version++) {
                ConfigMigration migration = migrations.get(version);
                if (migration != null) {
                    logger.info("Aplicando migration v" + version);
                    migration.migrate(userConfig, defaultConfig);
                }
            }

            userConfig.set("config-version", targetVersion);
            migrated = true;
        } else if (currentVersion > targetVersion) {
            logger.warning("[Config] Versão futura detectada em " + configFile.getName()
                    + " (" + currentVersion + " > " + targetVersion + ")");
            return false;
        }

        // 3. Reconstruir arquivo com CommentAwareWriter
        boolean hasChanges = migrated || hasNewKeys(userConfig, defaultConfig);
        if (hasChanges) {
            return reconstructFile(userConfig, defaultStream, defaultConfig);
        }

        return false;
    }

    private boolean updateGeneric(FileConfiguration userConfig, InputStream defaultStream,
            FileConfiguration defaultConfig) {
        if (defaultStream == null) return false;

        boolean hasChanges = hasNewKeys(userConfig, defaultConfig);
        if (hasChanges) {
            backupManager.createBackup(configFile);
        }

        // Sempre reconstruir para garantir comentários atualizados e keys novas
        // Mas só se houver mudanças reais
        if (hasChanges) {
            return reconstructFile(userConfig, defaultStream, defaultConfig);
        }

        return false;
    }

    /**
     * Reconstrói o arquivo usando CommentAwareWriter.
     */
    private boolean reconstructFile(FileConfiguration userConfig, InputStream defaultStream,
            FileConfiguration defaultConfig) {
        if (defaultStream == null) return false;

        try {
            List<String> defaultLines = readLines(defaultStream);

            YamlCommentParser.ParseResult parseResult = YamlCommentParser.parse(defaultLines);
            CommentAwareWriter writer = new CommentAwareWriter();
            String output = writer.write(userConfig, defaultConfig, parseResult, mergeOptions, defaultLines);

            AtomicConfigWriter.write(configFile, output);
            return true;
        } catch (IOException e) {
            logger.severe("[Config] Falha ao reconstruir " + configFile.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Verifica se o default tem keys que o user não tem (excluindo seções user-owned).
     */
    private boolean hasNewKeys(ConfigurationSection userConfig, ConfigurationSection defaultConfig) {
        return hasNewKeysRecursive(userConfig, defaultConfig, "");
    }

    private boolean hasNewKeysRecursive(ConfigurationSection userConfig,
            ConfigurationSection defaultConfig, String prefix) {
        for (String key : defaultConfig.getKeys(false)) {
            String fullPath = prefix.isEmpty() ? key : prefix + "." + key;

            // Pular seções user-owned
            if (mergeOptions.isUserOwned(fullPath)) continue;

            if (!userConfig.contains(key)) {
                return true;
            }

            Object defaultVal = defaultConfig.get(key);
            Object userVal = userConfig.get(key);
            if (defaultVal instanceof ConfigurationSection && userVal instanceof ConfigurationSection) {
                if (hasNewKeysRecursive(
                        (ConfigurationSection) userVal,
                        (ConfigurationSection) defaultVal,
                        fullPath)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> readLines(InputStream stream) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    @FunctionalInterface
    public interface ConfigMigration {
        void migrate(@NotNull ConfigurationSection userConfig, @NotNull ConfigurationSection defaultConfig);
    }

    // Helpers estáticos para Migrations
    public static void renameKey(@NotNull ConfigurationSection config, @NotNull String oldKey, @NotNull String newKey) {
        if (config.contains(oldKey) && !config.contains(newKey)) {
            config.set(newKey, config.get(oldKey));
            config.set(oldKey, null);
        }
    }

    public static void moveKey(@NotNull ConfigurationSection config, @NotNull String oldPath, @NotNull String newPath) {
        if (config.contains(oldPath) && !config.contains(newPath)) {
            config.set(newPath, config.get(oldPath));
            config.set(oldPath, null);
        }
    }
}
