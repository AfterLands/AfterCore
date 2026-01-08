package com.afterlands.core.database.impl.dialect;

import com.afterlands.core.database.DatabaseType;
import com.zaxxer.hikari.HikariConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class MySqlDialect implements DatabaseDialect {

    private static final String RELOCATED_MYSQL_DRIVER = "com.afterlands.core.libs.mysql.cj.jdbc.Driver";

    @Override
    public @NotNull DatabaseType type() {
        return DatabaseType.MYSQL;
    }

    @Override
    public void configure(@NotNull Plugin plugin, @NotNull ConfigurationSection rootDb, @NotNull HikariConfig hikari) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(rootDb, "rootDb");
        Objects.requireNonNull(hikari, "hikari");

        // Suporta tanto o novo layout (database.mysql.*) quanto o legado (database.host, etc.)
        ConfigurationSection mysql = rootDb.getConfigurationSection("mysql");
        ConfigurationSection cfg = mysql != null ? mysql : rootDb;

        String host = cfg.getString("host", "localhost");
        int port = cfg.getInt("port", 3306);
        String database = cfg.getString("database", "afterlands");
        String username = cfg.getString("username", "root");
        String password = cfg.getString("password", "");

        // Guidance do README do HikariCP: para MySQL, preferir jdbcUrl.
        String jdbcUrl = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8",
                host, port, database);

        hikari.setJdbcUrl(jdbcUrl);
        hikari.setUsername(username);
        hikari.setPassword(password);
        hikari.setDriverClassName(RELOCATED_MYSQL_DRIVER);
        hikari.setPoolName("AfterCore-MySQL");

        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    }
}

