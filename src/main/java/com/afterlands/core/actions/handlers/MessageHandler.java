package com.afterlands.core.actions.handlers;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handler para enviar mensagens no chat.
 *
 * <p>Formato: {@code message: Texto da mensagem}</p>
 * <p>Suporta:</p>
 * <ul>
 *     <li>Color codes (&a, &b, etc.)</li>
 *     <li>PlaceholderAPI (se disponível)</li>
 *     <li>Múltiplas linhas (separadas por \n)</li>
 * </ul>
 *
 * <p>Exemplos:</p>
 * <pre>
 * message: &aOlá, %player_name%!
 * message: &7Você tem &e%player_level% &7níveis
 * message: &cLinha 1\n&aLinha 2
 * </pre>
 */
public final class MessageHandler implements ActionHandler {

    @Override
    public void execute(@NotNull Player target, @NotNull ActionSpec spec) {
        String rawMessage = spec.rawArgs();
        if (rawMessage == null || rawMessage.isEmpty()) {
            return;
        }

        // Processar PlaceholderAPI (main thread)
        String processed = PlaceholderUtil.process(target, rawMessage);

        // Processar color codes
        String colored = ChatColor.translateAlternateColorCodes('&', processed);

        // Suportar múltiplas linhas
        String[] lines = colored.split("\\\\n");
        for (String line : lines) {
            target.sendMessage(line);
        }
    }
}
