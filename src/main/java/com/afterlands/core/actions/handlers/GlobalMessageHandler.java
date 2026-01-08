package com.afterlands.core.actions.handlers;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handler para broadcast de mensagens para todos os jogadores online.
 *
 * <p>Formato: {@code global_message: Texto da mensagem}</p>
 * <p>Envia a mensagem para todos os jogadores do servidor.</p>
 *
 * <p>Exemplos:</p>
 * <pre>
 * global_message: &aEvento iniciado!
 * global_message: &7{player} completou uma quest
 * time: 0, global_message: &cServidor reiniciando em 5 minutos!
 * </pre>
 *
 * @since 0.1.0
 */
public final class GlobalMessageHandler implements ActionHandler {

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

        // Broadcast para todos os jogadores online
        Bukkit.broadcastMessage(colored);
    }
}
