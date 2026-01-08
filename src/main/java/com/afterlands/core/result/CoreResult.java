package com.afterlands.core.result;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Tipo Result inspirado em Rust e outras linguagens funcionais.
 *
 * <p>Representa o resultado de uma operação que pode falhar de forma previsível,
 * sem lançar exceções em hot-path.</p>
 *
 * <p><b>Uso:</b></p>
 * <pre>{@code
 * CoreResult<PlayerData> result = loadPlayerData(uuid);
 *
 * // Pattern matching (Java 21)
 * return switch (result) {
 *     case CoreResult.Ok<PlayerData> ok -> ok.value();
 *     case CoreResult.Err<PlayerData> err -> {
 *         logger.warning("Failed to load player: " + err.message());
 *         yield null;
 *     }
 * };
 *
 * // Ou usando métodos helper
 * PlayerData data = result.orElse(DEFAULT_DATA);
 * result.ifOk(this::applyData);
 * result.ifErr(error -> logger.warning("Error: " + error.message()));
 * }</pre>
 *
 * @param <T> Tipo do valor em caso de sucesso
 */
public sealed interface CoreResult<T> permits CoreResult.Ok, CoreResult.Err {

    /**
     * Cria um resultado de sucesso.
     */
    @NotNull
    static <T> CoreResult<T> ok(@NotNull T value) {
        Objects.requireNonNull(value, "value cannot be null. Use Optional<T> if you need nullable values.");
        return new Ok<>(value);
    }

    /**
     * Cria um resultado de erro com código e mensagem.
     */
    @NotNull
    static <T> CoreResult<T> err(@NotNull CoreErrorCode code, @NotNull String message) {
        return new Err<>(code, message, null);
    }

    /**
     * Cria um resultado de erro com código, mensagem e causa.
     */
    @NotNull
    static <T> CoreResult<T> err(@NotNull CoreErrorCode code, @NotNull String message, @Nullable Throwable cause) {
        return new Err<>(code, message, cause);
    }

    /**
     * Cria um resultado de erro a partir de uma exceção.
     */
    @NotNull
    static <T> CoreResult<T> fromException(@NotNull Throwable throwable) {
        return new Err<>(CoreErrorCode.INTERNAL_ERROR, throwable.getMessage(), throwable);
    }

    /**
     * Verifica se o resultado é sucesso.
     */
    boolean isOk();

    /**
     * Verifica se o resultado é erro.
     */
    boolean isErr();

    /**
     * Retorna o valor em caso de sucesso.
     *
     * @throws NoSuchElementException se for erro
     */
    @NotNull T unwrap();

    /**
     * Retorna o valor em caso de sucesso, ou um valor padrão se for erro.
     */
    @NotNull T orElse(@NotNull T defaultValue);

    /**
     * Retorna o valor em caso de sucesso, ou null se for erro.
     */
    @Nullable T orNull();

    /**
     * Converte para Optional.
     */
    @NotNull Optional<T> toOptional();

    /**
     * Executa uma ação se o resultado for sucesso.
     */
    void ifOk(@NotNull Consumer<T> action);

    /**
     * Executa uma ação se o resultado for erro.
     */
    void ifErr(@NotNull Consumer<ErrorInfo> action);

    /**
     * Map: transforma o valor em caso de sucesso, preserva erro.
     */
    @NotNull <U> CoreResult<U> map(@NotNull Function<T, U> mapper);

    /**
     * FlatMap: transforma o valor em caso de sucesso em outro Result, preserva erro.
     */
    @NotNull <U> CoreResult<U> flatMap(@NotNull Function<T, CoreResult<U>> mapper);

    /**
     * Recupera de erro com valor padrão.
     */
    @NotNull CoreResult<T> recover(@NotNull Function<ErrorInfo, T> recovery);

    /**
     * Informações sobre o erro.
     */
    record ErrorInfo(
            @NotNull CoreErrorCode code,
            @NotNull String message,
            @Nullable Throwable cause
    ) {
        public ErrorInfo {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }

        @Override
        public String toString() {
            return "[" + code + "] " + message + (cause != null ? " (caused by: " + cause.getMessage() + ")" : "");
        }
    }

    /**
     * Resultado de sucesso.
     */
    record Ok<T>(@NotNull T value) implements CoreResult<T> {
        public Ok {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public boolean isOk() {
            return true;
        }

        @Override
        public boolean isErr() {
            return false;
        }

        @Override
        @NotNull
        public T unwrap() {
            return value;
        }

        @Override
        @NotNull
        public T orElse(@NotNull T defaultValue) {
            return value;
        }

        @Override
        @Nullable
        public T orNull() {
            return value;
        }

        @Override
        @NotNull
        public Optional<T> toOptional() {
            return Optional.of(value);
        }

        @Override
        public void ifOk(@NotNull Consumer<T> action) {
            action.accept(value);
        }

        @Override
        public void ifErr(@NotNull Consumer<ErrorInfo> action) {
            // No-op
        }

        @Override
        @NotNull
        public <U> CoreResult<U> map(@NotNull Function<T, U> mapper) {
            return CoreResult.ok(mapper.apply(value));
        }

        @Override
        @NotNull
        public <U> CoreResult<U> flatMap(@NotNull Function<T, CoreResult<U>> mapper) {
            return mapper.apply(value);
        }

        @Override
        @NotNull
        public CoreResult<T> recover(@NotNull Function<ErrorInfo, T> recovery) {
            return this;
        }
    }

    /**
     * Resultado de erro.
     */
    record Err<T>(
            @NotNull CoreErrorCode code,
            @NotNull String message,
            @Nullable Throwable cause
    ) implements CoreResult<T> {
        public Err {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }

        @Override
        public boolean isOk() {
            return false;
        }

        @Override
        public boolean isErr() {
            return true;
        }

        @Override
        @NotNull
        public T unwrap() {
            throw new NoSuchElementException("Called unwrap() on Err: " + errorInfo());
        }

        @Override
        @NotNull
        public T orElse(@NotNull T defaultValue) {
            return defaultValue;
        }

        @Override
        @Nullable
        public T orNull() {
            return null;
        }

        @Override
        @NotNull
        public Optional<T> toOptional() {
            return Optional.empty();
        }

        @Override
        public void ifOk(@NotNull Consumer<T> action) {
            // No-op
        }

        @Override
        public void ifErr(@NotNull Consumer<ErrorInfo> action) {
            action.accept(errorInfo());
        }

        @Override
        @NotNull
        @SuppressWarnings("unchecked")
        public <U> CoreResult<U> map(@NotNull Function<T, U> mapper) {
            return (CoreResult<U>) this;
        }

        @Override
        @NotNull
        @SuppressWarnings("unchecked")
        public <U> CoreResult<U> flatMap(@NotNull Function<T, CoreResult<U>> mapper) {
            return (CoreResult<U>) this;
        }

        @Override
        @NotNull
        public CoreResult<T> recover(@NotNull Function<ErrorInfo, T> recovery) {
            return CoreResult.ok(recovery.apply(errorInfo()));
        }

        public ErrorInfo errorInfo() {
            return new ErrorInfo(code, message, cause);
        }
    }
}
