package com.afterlands.core.input;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Serviço de captura de input do jogador.
 *
 * <p>Centraliza mecanismos de entrada: chat, placa e bigorna.</p>
 *
 * <p><b>Threading:</b> Futures completam na main thread (via runSync interno).</p>
 *
 * <p>Exemplo:</p>
 * <pre>
 * core.input().requestInput(player, InputRequest.chat()
 *     .prompt("&eDigite o novo nome:")
 *     .timeout(600)
 *     .build()
 * ).thenAccept(result -> {
 *     if (result.isSuccess()) {
 *         // usar result.value()
 *     }
 * });
 * </pre>
 *
 * @since 1.6.0
 */
public interface InputService {

    /**
     * Solicita input do jogador.
     *
     * <p>Se o jogador já tem um input ativo, ele é cancelado silenciosamente.</p>
     *
     * @param player  Jogador alvo
     * @param request Configuração da solicitação
     * @return Future que completa com o resultado do input
     */
    @NotNull CompletableFuture<InputResult> requestInput(@NotNull Player player, @NotNull InputRequest request);

    /**
     * Cancela o input ativo do jogador enviando a mensagem de cancelamento configurada.
     *
     * <p>Se não há input ativo, não faz nada.</p>
     *
     * @param player Jogador alvo
     */
    void cancelInput(@NotNull Player player);

    /**
     * Verifica se o jogador tem um input ativo.
     *
     * @param player Jogador a verificar
     * @return true se há um input aguardando
     */
    boolean hasActiveInput(@NotNull Player player);

    /**
     * Verifica se o input por placa está disponível (ProtocolLib presente).
     *
     * @return true se ProtocolLib está instalado
     */
    boolean isSignInputAvailable();
}
