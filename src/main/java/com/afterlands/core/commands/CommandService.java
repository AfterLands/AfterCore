package com.afterlands.core.commands;

import org.jetbrains.annotations.NotNull;

/**
 * Framework leve de comandos.
 *
 * <p>Fase inicial: contrato + implementação mínima. Uma versão completa (subcommands, permissions, help)
 * pode ser evoluída sem quebrar consumidores.</p>
 */
public interface CommandService {

    /**
     * Registra um handler de comandos (via API explícita ou anotações).
     */
    void register(@NotNull Object commandHandler);

    /**
     * Unregister/cancelar tudo que foi registrado por este serviço.
     */
    void unregisterAll();
}

