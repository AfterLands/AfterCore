package com.afterlands.core.actions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Modelo comum para todos os dialetos de actions.
 */
public final class ActionSpec {
    private final String typeKey;
    private final String rawArgs;

    private final Long timeTicks;     // opcional (dialeto simples ABA)
    private final Integer frameIndex; // opcional (dialeto simples ABA)

    private final ActionTrigger trigger; // opcional (dialeto advanced)
    private final String condition;      // opcional
    private final ActionScope scope;     // default VIEWER
    private final int scopeRadius;       // se NEARBY

    private final String rawLine;

    public ActionSpec(@NotNull String typeKey,
                      @NotNull String rawArgs,
                      @Nullable Long timeTicks,
                      @Nullable Integer frameIndex,
                      @Nullable ActionTrigger trigger,
                      @Nullable String condition,
                      @NotNull ActionScope scope,
                      int scopeRadius,
                      @NotNull String rawLine) {
        this.typeKey = typeKey;
        this.rawArgs = rawArgs;
        this.timeTicks = timeTicks;
        this.frameIndex = frameIndex;
        this.trigger = trigger;
        this.condition = condition;
        this.scope = scope;
        this.scopeRadius = scopeRadius;
        this.rawLine = rawLine;
    }

    public @NotNull String typeKey() {
        return typeKey;
    }

    public @NotNull String rawArgs() {
        return rawArgs;
    }

    public @Nullable Long timeTicks() {
        return timeTicks;
    }

    public @Nullable Integer frameIndex() {
        return frameIndex;
    }

    public @Nullable ActionTrigger trigger() {
        return trigger;
    }

    public @Nullable String condition() {
        return condition;
    }

    public @NotNull ActionScope scope() {
        return scope;
    }

    public int scopeRadius() {
        return scopeRadius;
    }

    public @NotNull String rawLine() {
        return rawLine;
    }
}

