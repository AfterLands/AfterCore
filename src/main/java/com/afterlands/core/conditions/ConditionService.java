package com.afterlands.core.conditions;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Engine unificado de condições (AfterBlockState + AfterMotion).
 *
 * <p>Regras críticas:</p>
 * <ul>
 *     <li>PlaceholderAPI só pode rodar na main thread.</li>
 *     <li>Parsing/AST deve ser cacheado (Caffeine).</li>
 * </ul>
 */
public interface ConditionService {

    void setConditionGroups(@NotNull Map<String, List<String>> groups);

    @NotNull Map<String, List<String>> getConditionGroups();

    void registerVariableProvider(@NotNull String namespace, @NotNull ConditionVariableProvider provider);

    /**
     * Avaliação síncrona. Se o expression requer PlaceholderAPI, deve ser chamada na main thread.
     */
    boolean evaluateSync(@NotNull Player player, @NotNull String expression, @NotNull ConditionContext ctx);

    /**
     * Avaliação segura (garante main thread quando necessário).
     */
    @NotNull CompletableFuture<Boolean> evaluate(@NotNull Player player, @NotNull String expression, @NotNull ConditionContext ctx);
}

