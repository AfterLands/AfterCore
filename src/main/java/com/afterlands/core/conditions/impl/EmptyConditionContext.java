package com.afterlands.core.conditions.impl;

import com.afterlands.core.conditions.ConditionContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * ConditionContext vazio (sem variáveis).
 */
public final class EmptyConditionContext implements ConditionContext {

    private static final EmptyConditionContext INSTANCE = new EmptyConditionContext();

    private EmptyConditionContext() {
    }

    public static EmptyConditionContext getInstance() {
        return INSTANCE;
    }

    @Override
    public @NotNull Map<String, String> variables() {
        return Collections.emptyMap();
    }
}
