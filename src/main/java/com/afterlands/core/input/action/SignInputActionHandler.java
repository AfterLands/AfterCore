package com.afterlands.core.input.action;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import com.afterlands.core.input.InputRequest;
import com.afterlands.core.input.InputService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Action handler para captura de input via placa virtual.
 *
 * <p><b>Requer:</b> ProtocolLib. Envia mensagem de erro se ausente.</p>
 *
 * <p>Formato: {@code sign_input timeout=600 cancel=cancelar line1=texto_inicial}</p>
 *
 * <p>Parâmetros disponíveis:</p>
 * <ul>
 *   <li>{@code timeout=<ticks>} - Ticks até expirar (default: 600)</li>
 *   <li>{@code cancel=<palavra>} - Palavra-chave para cancelar (default: "cancelar")</li>
 *   <li>{@code line1=<texto>} - Texto pré-preenchido na linha 1 da placa</li>
 *   <li>{@code cancel_msg=<texto>} - Mensagem ao cancelar</li>
 *   <li>{@code timeout_msg=<texto>} - Mensagem ao expirar</li>
 * </ul>
 */
public final class SignInputActionHandler implements ActionHandler {

    private final InputService inputService;

    public SignInputActionHandler(@NotNull InputService inputService) {
        this.inputService = inputService;
    }

    @Override
    public void execute(@NotNull Player player, @NotNull ActionSpec spec) {
        if (!inputService.isSignInputAvailable()) {
            player.sendMessage(ChatColor.RED + "Entrada por placa indisponível (ProtocolLib necessário).");
            return;
        }

        InputRequest request = parseRequest(spec.rawArgs());
        player.closeInventory();
        inputService.requestInput(player, request);
    }

    private InputRequest parseRequest(String rawArgs) {
        InputRequest.Builder builder = InputRequest.sign();
        if (rawArgs == null || rawArgs.isEmpty()) return builder.build();

        String line1 = null;
        for (String token : rawArgs.split("\\s+(?=\\w+=)")) {
            int eq = token.indexOf('=');
            if (eq < 0) continue;
            String key   = token.substring(0, eq).trim().toLowerCase();
            String value = token.substring(eq + 1).trim().replace("_", " ");

            switch (key) {
                case "timeout"     -> { try { builder.timeout(Long.parseLong(value)); } catch (NumberFormatException ignored) {} }
                case "cancel"      -> builder.cancelKeyword(value);
                case "line1"       -> line1 = value;
                case "cancel_msg"  -> builder.cancelMessage(value);
                case "timeout_msg" -> builder.timeoutMessage(value);
            }
        }
        if (line1 != null) builder.signLines(line1, "", "", "");
        return builder.build();
    }
}
