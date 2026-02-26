package com.afterlands.core.input;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configuração de uma solicitação de input.
 *
 * <p>Use os factory methods {@link #chat()}, {@link #sign()}, {@link #anvil()} para criar.</p>
 *
 * <p>Exemplo:</p>
 * <pre>
 * InputRequest request = InputRequest.chat()
 *     .prompt("&eDigite o nome:")
 *     .timeout(600)
 *     .cancelKeyword("cancelar")
 *     .validator(input -> input.length() > 16 ? "&cMáximo 16 caracteres" : null)
 *     .build();
 * </pre>
 */
public final class InputRequest {

    private final InputType type;
    private final long timeoutTicks;
    private final String cancelKeyword;
    private final @Nullable String promptMessage;
    private final @Nullable String promptTitle;
    private final @Nullable String promptSubtitle;
    private final @Nullable String promptActionBar;
    private final @Nullable InputValidator validator;
    private final int maxRetries;
    private final @Nullable String validationFailMessage;
    private final @Nullable String cancelMessage;
    private final @Nullable String timeoutMessage;
    private final @Nullable String[] signLines;

    private InputRequest(Builder builder) {
        this.type = builder.type;
        this.timeoutTicks = builder.timeoutTicks;
        this.cancelKeyword = builder.cancelKeyword;
        this.promptMessage = builder.promptMessage;
        this.promptTitle = builder.promptTitle;
        this.promptSubtitle = builder.promptSubtitle;
        this.promptActionBar = builder.promptActionBar;
        this.validator = builder.validator;
        this.maxRetries = builder.maxRetries;
        this.validationFailMessage = builder.validationFailMessage;
        this.cancelMessage = builder.cancelMessage;
        this.timeoutMessage = builder.timeoutMessage;
        this.signLines = builder.signLines;
    }

    public static @NotNull Builder chat() {
        return new Builder(InputType.CHAT);
    }

    public static @NotNull Builder sign() {
        return new Builder(InputType.SIGN);
    }

    public static @NotNull Builder anvil() {
        return new Builder(InputType.ANVIL);
    }

    public @NotNull InputType type() { return type; }
    public long timeoutTicks() { return timeoutTicks; }
    public @NotNull String cancelKeyword() { return cancelKeyword; }
    public @Nullable String promptMessage() { return promptMessage; }
    public @Nullable String promptTitle() { return promptTitle; }
    public @Nullable String promptSubtitle() { return promptSubtitle; }
    public @Nullable String promptActionBar() { return promptActionBar; }
    public @Nullable InputValidator validator() { return validator; }
    public int maxRetries() { return maxRetries; }
    public @Nullable String validationFailMessage() { return validationFailMessage; }
    public @Nullable String cancelMessage() { return cancelMessage; }
    public @Nullable String timeoutMessage() { return timeoutMessage; }
    public @Nullable String[] signLines() { return signLines; }

    public static final class Builder {

        private final InputType type;
        private long timeoutTicks = 600;
        private String cancelKeyword = "cancelar";
        private @Nullable String promptMessage;
        private @Nullable String promptTitle;
        private @Nullable String promptSubtitle;
        private @Nullable String promptActionBar;
        private @Nullable InputValidator validator;
        private int maxRetries = 3;
        private @Nullable String validationFailMessage;
        private @Nullable String cancelMessage;
        private @Nullable String timeoutMessage;
        private @Nullable String[] signLines;

        private Builder(@NotNull InputType type) {
            this.type = type;
        }

        public @NotNull Builder prompt(@NotNull String message) {
            this.promptMessage = message;
            return this;
        }

        public @NotNull Builder title(@NotNull String title) {
            this.promptTitle = title;
            return this;
        }

        public @NotNull Builder subtitle(@NotNull String subtitle) {
            this.promptSubtitle = subtitle;
            return this;
        }

        public @NotNull Builder actionBar(@NotNull String actionBar) {
            this.promptActionBar = actionBar;
            return this;
        }

        public @NotNull Builder timeout(long ticks) {
            this.timeoutTicks = ticks;
            return this;
        }

        public @NotNull Builder cancelKeyword(@NotNull String keyword) {
            this.cancelKeyword = keyword;
            return this;
        }

        public @NotNull Builder validator(@NotNull InputValidator validator) {
            this.validator = validator;
            return this;
        }

        public @NotNull Builder maxRetries(int retries) {
            this.maxRetries = retries;
            return this;
        }

        public @NotNull Builder validationFailMessage(@NotNull String message) {
            this.validationFailMessage = message;
            return this;
        }

        public @NotNull Builder cancelMessage(@NotNull String message) {
            this.cancelMessage = message;
            return this;
        }

        public @NotNull Builder timeoutMessage(@NotNull String message) {
            this.timeoutMessage = message;
            return this;
        }

        public @NotNull Builder signLines(@NotNull String... lines) {
            this.signLines = lines;
            return this;
        }

        public @NotNull InputRequest build() {
            return new InputRequest(this);
        }
    }
}
