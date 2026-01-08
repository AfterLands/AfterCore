package com.afterlands.core.actions.handlers;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import com.afterlands.core.util.StringUtil;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handler para enviar mensagens centralizadas no chat.
 *
 * <p>Formato: {@code centered_message: Texto da mensagem}</p>
 * <p>Centraliza a mensagem no chat do jogador usando cálculo de pixels.</p>
 *
 * <p>Exemplos:</p>
 * <pre>
 * centered_message: &aOlá, {player}!
 * centered_message: &7Bem-vindo ao servidor
 * time: 20, centered_message: &eAviso importante!
 * </pre>
 *
 * @since 0.1.0
 */
public final class CenteredMessageHandler implements ActionHandler {

    @Override
    public void execute(@NotNull Player target, @NotNull ActionSpec spec) {
        String rawMessage = spec.rawArgs();
        if (rawMessage == null || rawMessage.isEmpty()) {
            return;
        }

        // Processar PlaceholderAPI se disponível (main thread)
        String processed = PlaceholderUtil.process(target, rawMessage);

        // Processar color codes
        String colored = ChatColor.translateAlternateColorCodes('&', processed);

        // Centralizar e enviar
        String centered = StringUtil.centeredMessage(colored);
        target.sendMessage(centered);
    }
}
