package com.afterlands.core.database.impl.dialect;

import com.afterlands.core.database.DatabaseType;
import com.zaxxer.hikari.HikariConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Prepara o HikariConfig para um tipo específico de database.
 *
 * <p>Objetivo: deixar o AfterCore pronto para múltiplos backends sem if/else espalhado.</p>
 */
public interface DatabaseDialect {

    @NotNull DatabaseType type();

    /**
     * Aplica configurações específicas (jdbcUrl, driver, propriedades, init SQL).
     *
     * @param plugin   plugin do core (para resolver paths)
     * @param rootDb   seção "database"
     * @param hikari   config a ser preenchido
     */
    void configure(@NotNull Plugin plugin, @NotNull ConfigurationSection rootDb, @NotNull HikariConfig hikari);
}

