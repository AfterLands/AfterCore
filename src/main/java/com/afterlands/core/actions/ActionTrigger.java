package com.afterlands.core.actions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Trigger opcional (dialeto avançado).
 */
public final class ActionTrigger {
    private final Integer tick;
    private final String event;
    private final int actorIndex;

    private ActionTrigger(@Nullable Integer tick, @Nullable String event, int actorIndex) {
        this.tick = tick;
        this.event = event;
        this.actorIndex = actorIndex;
    }

    public static @NotNull ActionTrigger atTick(int tick) {
        return new ActionTrigger(tick, null, -1);
    }

    public static @NotNull ActionTrigger onEvent(@NotNull String event) {
        return new ActionTrigger(null, event, -1);
    }

    public static @NotNull ActionTrigger onActorEvent(@NotNull String event, int actorIndex) {
        return new ActionTrigger(null, event, actorIndex);
    }

    public @Nullable Integer tick() {
        return tick;
    }

    public @Nullable String event() {
        return event;
    }

    public int actorIndex() {
        return actorIndex;
    }
}

