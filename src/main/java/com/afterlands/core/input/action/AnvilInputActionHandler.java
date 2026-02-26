package com.afterlands.core.input.action;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import com.afterlands.core.input.InputRequest;
import com.afterlands.core.input.InputService;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Action handler para captura de input via bigorna.
 *
 * <p>Formato: {@code anvil_input prompt=&eDigite o nome: timeout=600}</p>
 *
 * <p>Parâmetros disponíveis:</p>
 * <ul>
 *   <li>{@code prompt=<texto>} - Texto exibido no item da bigorna (slot 0)</li>
 *   <li>{@code timeout=<ticks>} - Ticks até expirar (default: 600)</li>
 *   <li>{@code cancel=<palavra>} - Palavra-chave para cancelar no chat (default: "cancelar")</li>
 *   <li>{@code max_retries=<n>} - Máximo de tentativas de validação (default: 3)</li>
 *   <li>{@code cancel_msg=<texto>} - Mensagem ao cancelar</li>
 *   <li>{@code timeout_msg=<texto>} - Mensagem ao expirar</li>
 * </ul>
 */
public final class AnvilInputActionHandler implements ActionHandler {

    private final InputService inputService;

    public AnvilInputActionHandler(@NotNull InputService inputService) {
        this.inputService = inputService;
    }

    @Override
    public void execute(@NotNull Player player, @NotNull ActionSpec spec) {
        InputRequest request = parseRequest(spec.rawArgs());
        player.closeInventory();
        inputService.requestInput(player, request);
    }

    private InputRequest parseRequest(String rawArgs) {
        InputRequest.Builder builder = InputRequest.anvil();
        if (rawArgs == null || rawArgs.isEmpty()) return builder.build();

        ChatInputActionHandler.applyArgs(builder, rawArgs);
        return builder.build();
    }
}
