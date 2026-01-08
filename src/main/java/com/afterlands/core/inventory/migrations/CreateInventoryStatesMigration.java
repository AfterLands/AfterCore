package com.afterlands.core.inventory.migrations;

import com.afterlands.core.database.SqlMigration;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration para criar tabela aftercore_inventory_states.
 *
 * <p>Tabela para persistência de estados de inventários.</p>
 *
 * <p><b>Migration ID:</b> aftercore:inventory_states_v1</p>
 * <p><b>Idempotente:</b> Usa CREATE TABLE IF NOT EXISTS</p>
 */
public class CreateInventoryStatesMigration implements SqlMigration {

    private static final String MYSQL_CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS aftercore_inventory_states (
                player_id CHAR(36) NOT NULL,
                inventory_id VARCHAR(64) NOT NULL,
                state_data TEXT NOT NULL,
                tab_states TEXT,
                custom_data TEXT,
                updated_at BIGINT NOT NULL,
                PRIMARY KEY (player_id, inventory_id),
                INDEX idx_updated_at (updated_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

    private static final String SQLITE_CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS aftercore_inventory_states (
                player_id TEXT NOT NULL,
                inventory_id TEXT NOT NULL,
                state_data TEXT NOT NULL,
                tab_states TEXT,
                custom_data TEXT,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (player_id, inventory_id)
            )
            """;

    private static final String SQLITE_CREATE_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_updated_at
            ON aftercore_inventory_states (updated_at)
            """;

    /**
     * Aplica migration.
     *
     * @param connection Conexão ativa com o banco
     * @throws Exception Se houver erro na migration
     */
    @Override
    public void apply(@NotNull Connection connection) throws Exception {
        String databaseType = detectDatabaseType(connection);

        try (Statement stmt = connection.createStatement()) {
            if ("mysql".equalsIgnoreCase(databaseType)) {
                // MySQL/MariaDB
                stmt.executeUpdate(MYSQL_CREATE_TABLE);
            } else {
                // SQLite (fallback)
                stmt.executeUpdate(SQLITE_CREATE_TABLE);
                stmt.executeUpdate(SQLITE_CREATE_INDEX);
            }
        } catch (SQLException e) {
            throw new Exception("Failed to create aftercore_inventory_states table", e);
        }
    }

    /**
     * Detecta tipo de banco de dados pela URL.
     *
     * @param connection Conexão
     * @return "mysql" ou "sqlite"
     */
    @NotNull
    private String detectDatabaseType(@NotNull Connection connection) {
        try {
            String url = connection.getMetaData().getURL().toLowerCase();
            if (url.contains("mysql") || url.contains("mariadb")) {
                return "mysql";
            }
        } catch (SQLException ignored) {
        }
        return "sqlite"; // Fallback
    }

    /**
     * Migration ID único.
     *
     * @return ID da migration
     */
    @NotNull
    public static String getMigrationId() {
        return "aftercore:inventory_states_v1";
    }
}
