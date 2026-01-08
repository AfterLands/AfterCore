package com.afterlands.core.actions;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handler genérico (plugins registram suas actions).
 *
 * <p>Execução não é implementada no core nesta fase; este contrato é o ponto de extensão.</p>
 */
@FunctionalInterface
public interface ActionHandler {
    void execute(@NotNull Player target, @NotNull ActionSpec spec);
}

