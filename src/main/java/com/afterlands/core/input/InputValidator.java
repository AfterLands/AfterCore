package com.afterlands.core.input;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Validador de input.
 *
 * <p>Retorna {@code null} se o input for válido, ou uma mensagem de erro se inválido.</p>
 */
@FunctionalInterface
public interface InputValidator {

    /**
     * Valida o input fornecido pelo jogador.
     *
     * @param input Texto digitado pelo jogador
     * @return {@code null} se válido, mensagem de erro se inválido
     */
    @Nullable String validate(@NotNull String input);
}
