package com.afterlands.core.input.action;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import com.afterlands.core.input.InputRequest;
import com.afterlands.core.input.InputService;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Action handler para captura de input via chat.
 *
 * <p>Formato: {@code chat_input prompt=&eDigite o nome: timeout=600 cancel=cancelar}</p>
 *
 * <p>Parâmetros disponíveis:</p>
 * <ul>
 *   <li>{@code prompt=<texto>} - Mensagem exibida ao jogador</li>
 *   <li>{@code timeout=<ticks>} - Ticks até expirar (default: 600)</li>
 *   <li>{@code cancel=<palavra>} - Palavra-chave para cancelar (default: "cancelar")</li>
 *   <li>{@code max_retries=<n>} - Máximo de tentativas de validação (default: 3)</li>
 *   <li>{@code cancel_msg=<texto>} - Mensagem ao cancelar</li>
 *   <li>{@code timeout_msg=<texto>} - Mensagem ao expirar</li>
 * </ul>
 */
public final class ChatInputActionHandler implements ActionHandler {

    private final InputService inputService;

    public ChatInputActionHandler(@NotNull InputService inputService) {
        this.inputService = inputService;
    }

    @Override
    public void execute(@NotNull Player player, @NotNull ActionSpec spec) {
        InputRequest request = parseRequest(spec.rawArgs());
        player.closeInventory();
        inputService.requestInput(player, request);
    }

    private InputRequest parseRequest(String rawArgs) {
        InputRequest.Builder builder = InputRequest.chat();
        if (rawArgs == null || rawArgs.isEmpty()) return builder.build();

        applyArgs(builder, rawArgs);
        return builder.build();
    }

    static void applyArgs(InputRequest.Builder builder, String rawArgs) {
        for (String token : rawArgs.split("\\s+(?=\\w+=)")) {
            int eq = token.indexOf('=');
            if (eq < 0) continue;
            String key   = token.substring(0, eq).trim().toLowerCase();
            String value = token.substring(eq + 1).trim().replace("_", " ");

            switch (key) {
                case "prompt"      -> builder.prompt(value);
                case "timeout"     -> { try { builder.timeout(Long.parseLong(value)); } catch (NumberFormatException ignored) {} }
                case "cancel"      -> builder.cancelKeyword(value);
                case "max_retries" -> { try { builder.maxRetries(Integer.parseInt(value)); } catch (NumberFormatException ignored) {} }
                case "cancel_msg"  -> builder.cancelMessage(value);
                case "timeout_msg" -> builder.timeoutMessage(value);
            }
        }
    }
}
