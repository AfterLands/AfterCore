package com.afterlands.core.input;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resultado imutável de uma solicitação de input.
 */
public final class InputResult {

    public enum Status {
        SUCCESS,
        CANCELLED,
        TIMED_OUT,
        DISCONNECTED,
        UNAVAILABLE
    }

    private final Status status;
    private final @Nullable String value;
    private final InputType type;

    private InputResult(@NotNull Status status, @Nullable String value, @NotNull InputType type) {
        this.status = status;
        this.value = value;
        this.type = type;
    }

    public static @NotNull InputResult success(@NotNull String value, @NotNull InputType type) {
        return new InputResult(Status.SUCCESS, value, type);
    }

    public static @NotNull InputResult cancelled(@NotNull InputType type) {
        return new InputResult(Status.CANCELLED, null, type);
    }

    public static @NotNull InputResult timedOut(@NotNull InputType type) {
        return new InputResult(Status.TIMED_OUT, null, type);
    }

    public static @NotNull InputResult disconnected(@NotNull InputType type) {
        return new InputResult(Status.DISCONNECTED, null, type);
    }

    public static @NotNull InputResult unavailable(@NotNull InputType type) {
        return new InputResult(Status.UNAVAILABLE, null, type);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public @NotNull Status status() {
        return status;
    }

    public @Nullable String value() {
        return value;
    }

    public @NotNull InputType type() {
        return type;
    }

    @Override
    public String toString() {
        return "InputResult{status=" + status + ", type=" + type + ", value=" + value + "}";
    }
}
