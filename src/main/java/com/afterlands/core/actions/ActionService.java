package com.afterlands.core.actions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Serviço de Actions (parsing + registry).
 *
 * <p>Suporta múltiplos dialetos:</p>
 * <ul>
 *     <li>Simples: {@code "<action>: <args>"} (default)</li>
 *     <li>Simples+metadados: {@code "time: 20, message: oi"}</li>
 *     <li>Avançado: DSL do AfterMotion (@tick/@event, condição, scope)</li>
 * </ul>
 */
public interface ActionService {

    @Nullable ActionSpec parse(@NotNull String line);

    void registerHandler(@NotNull String actionTypeKey, @NotNull ActionHandler handler);

    /**
     * Retorna handlers registrados (imutável, apenas leitura).
     */
    @NotNull Map<String, ActionHandler> getHandlers();
}

