package com.afterlands.core.database.impl.dialect;

import com.afterlands.core.database.DatabaseType;
import com.zaxxer.hikari.HikariConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Locale;
import java.util.Objects;

public final class SqliteDialect implements DatabaseDialect {

    private static final String SQLITE_DRIVER = "org.sqlite.JDBC";

    @Override
    public @NotNull DatabaseType type() {
        return DatabaseType.SQLITE;
    }

    @Override
    public void configure(@NotNull Plugin plugin, @NotNull ConfigurationSection rootDb, @NotNull HikariConfig hikari) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(rootDb, "rootDb");
        Objects.requireNonNull(hikari, "hikari");

        ConfigurationSection sqlite = rootDb.getConfigurationSection("sqlite");
        if (sqlite == null) {
            throw new IllegalArgumentException("database.sqlite section ausente (type=sqlite)");
        }

        String file = sqlite.getString("file", "aftercore.db");
        if (file == null || file.trim().isEmpty()) {
            file = "aftercore.db";
        }

        File dbFile = new File(file);
        if (!dbFile.isAbsolute()) {
            dbFile = new File(plugin.getDataFolder(), file);
        }

        // Garante pasta do plugin
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }

        String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        hikari.setJdbcUrl(jdbcUrl);

        // sqlite-jdbc registra driver automaticamente, mas set explícito é ok.
        hikari.setDriverClassName(SQLITE_DRIVER);
        hikari.setPoolName("AfterCore-SQLite");

        // SQLite é single-writer; por segurança, o pool deve ser pequeno.
        // (O pool genérico ainda pode ser sobrescrito pelo usuário em database.pool.*)

        String initSql = sqlite.getString("connection-init-sql", "");
        if (initSql != null && !initSql.trim().isEmpty()) {
            // Hikari aceita uma string; PRAGMA single é o caso comum.
            hikari.setConnectionInitSql(initSql.trim());
        }

        // Pequenas otimizações/segurança (evitar lock prolongado)
        // Users podem também definir via connection-init-sql (PRAGMAs).
        hikari.addDataSourceProperty("busy_timeout", String.valueOf(sqlite.getInt("busy-timeout-ms", 5000)));

        // Normaliza path em logs (opcional)
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[AfterCore][SQLite] file=" + dbFile.getAbsolutePath().replace('\\', '/')
                    .toLowerCase(Locale.ROOT));
        }
    }
}

