package com.afterlands.core.conditions;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provider para placeholders custom do core (ex.: %abs_flag:key%).
 */
@FunctionalInterface
public interface ConditionVariableProvider {

    /**
     * @param namespace namespace registrado (ex.: "abs_flag")
     * @param key       chave após ':' (ex.: "gate_opened")
     * @return valor substituto (nunca com %), ou null se não resolver
     */
    @Nullable String resolve(@NotNull Player player, @NotNull String namespace, @NotNull String key, @NotNull ConditionContext ctx);
}

