package com.afterlands.core.database;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Serviço de SQL (pool + helpers async).
 *
 * <p>Contrato público: não expõe HikariCP diretamente.</p>
 */
public interface SqlService extends AutoCloseable {

    /**
     * Recarrega o pool a partir do config.
     *
     * @param section "database" do config.yml (pode ser null -> desabilita)
     */
    void reloadFromConfig(@Nullable ConfigurationSection section);

    boolean isEnabled();

    boolean isInitialized();

    @NotNull DataSource dataSource();

    @NotNull Connection getConnection() throws SQLException;

    @NotNull <T> CompletableFuture<T> supplyAsync(@NotNull SqlFunction<Connection, T> fn);

    @NotNull CompletableFuture<Void> runAsync(@NotNull SqlConsumer<Connection> fn);

    /**
     * Registra uma migration/DDL que será executada após iniciar o pool.
     * Deve ser idempotente (CREATE TABLE IF NOT EXISTS etc.).\n
     */
    void registerMigration(@NotNull String id, @NotNull SqlMigration migration);

    /**
     * Executa operação dentro de uma transação (auto-commit desabilitado).
     * Commit automático em caso de sucesso, rollback automático em caso de exceção.
     *
     * @param fn Operação a executar na transação
     * @return CompletableFuture com resultado
     */
    @NotNull <T> CompletableFuture<T> inTransaction(@NotNull SqlFunction<Connection, T> fn);

    /**
     * Verifica se o database está disponível (ping test).
     *
     * @return CompletableFuture com true se disponível, false se indisponível
     */
    @NotNull CompletableFuture<Boolean> isAvailable();

    /**
     * Obtém estatísticas do pool (para diagnóstico).
     *
     * @return Map com estatísticas ou mapa vazio se pool não inicializado
     */
    @NotNull Map<String, Object> getPoolStats();

    @Override
    void close();
}

