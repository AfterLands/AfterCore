package com.afterlands.core.config.validate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Representa um erro ou aviso de validação de configuração.
 *
 * @param path      Caminho da chave no config (ex: "database.pool.maximum-pool-size")
 * @param severity  Severidade: ERROR (impede funcionamento) ou WARNING (possível problema)
 * @param message   Mensagem descritiva do problema
 * @param expected  Valor ou tipo esperado (opcional)
 * @param actual    Valor atual (opcional)
 */
public record ValidationError(
        @NotNull String path,
        @NotNull Severity severity,
        @NotNull String message,
        @Nullable String expected,
        @Nullable String actual
) {
    public enum Severity {
        ERROR,
        WARNING
    }

    public static ValidationError error(@NotNull String path, @NotNull String message) {
        return new ValidationError(path, Severity.ERROR, message, null, null);
    }

    public static ValidationError error(@NotNull String path, @NotNull String message,
                                       @Nullable String expected, @Nullable String actual) {
        return new ValidationError(path, Severity.ERROR, message, expected, actual);
    }

    public static ValidationError warning(@NotNull String path, @NotNull String message) {
        return new ValidationError(path, Severity.WARNING, message, null, null);
    }

    public static ValidationError warning(@NotNull String path, @NotNull String message,
                                         @Nullable String expected, @Nullable String actual) {
        return new ValidationError(path, Severity.WARNING, message, expected, actual);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public boolean isWarning() {
        return severity == Severity.WARNING;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(severity).append("] ").append(path).append(": ").append(message);
        if (expected != null || actual != null) {
            sb.append(" (expected: ").append(expected != null ? expected : "N/A");
            sb.append(", actual: ").append(actual != null ? actual : "N/A").append(")");
        }
        return sb.toString();
    }
}
