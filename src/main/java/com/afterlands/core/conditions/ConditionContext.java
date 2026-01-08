package com.afterlands.core.conditions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Contexto de avaliação (ex.: flags do jogador, variáveis derivadas).
 *
 * <p>Intencionalmente simples para ser reutilizável por múltiplos plugins.</p>
 */
public interface ConditionContext {

    /**
     * Variáveis simples (ex.: flags).
     */
    @NotNull Map<String, String> variables();

    @Nullable
    default String get(@NotNull String key) {
        return variables().get(key);
    }

    @NotNull
    default String getOrDefault(@NotNull String key, @NotNull String defaultValue) {
        String v = get(key);
        return v == null ? defaultValue : v;
    }
}

