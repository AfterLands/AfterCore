package com.afterlands.core.actions.handlers;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import com.afterlands.core.util.StringUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handler para broadcast de mensagens centralizadas para todos os jogadores.
 *
 * <p>Formato: {@code global_centered_message: Texto da mensagem}</p>
 * <p>Envia mensagem centralizada para todos os jogadores do servidor.</p>
 *
 * <p>Exemplos:</p>
 * <pre>
 * global_centered_message: &aEvento Especial!
 * global_centered_message: &7Bem-vindos ao evento de {player}
 * time: 100, global_centered_message: &c&lATENÇÃO
 * </pre>
 *
 * @since 0.1.0
 */
public final class GlobalCenteredMessageHandler implements ActionHandler {

    @Override
    public void execute(@NotNull Player target, @NotNull ActionSpec spec) {
        String rawMessage = spec.rawArgs();
        if (rawMessage == null || rawMessage.isEmpty()) {
            return;
        }

        // Processar PlaceholderAPI com o player target como contexto (main thread)
        String processed = PlaceholderUtil.process(target, rawMessage);

        // Processar color codes
        String colored = ChatColor.translateAlternateColorCodes('&', processed);

        // Centralizar
        String centered = StringUtil.centeredMessage(colored);

        // Broadcast para todos os jogadores online
        Bukkit.broadcastMessage(centered);
    }
}
