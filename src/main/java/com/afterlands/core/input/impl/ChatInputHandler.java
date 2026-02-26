package com.afterlands.core.input.impl;

import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.input.InputType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Captura input via chat (AsyncPlayerChatEvent).
 *
 * <p>Usa prioridade LOWEST para interceptar antes de outros plugins.</p>
 *
 * <p><b>Thread Safety:</b> Mensagens ao jogador são enviadas via {@code scheduler.runSync()}.</p>
 */
final class ChatInputHandler implements Listener {

    private final DefaultInputService service;
    private final SchedulerService scheduler;

    ChatInputHandler(@NotNull DefaultInputService service, @NotNull SchedulerService scheduler) {
        this.service = service;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Fast path: no session active
        ActiveInputSession session = service.getSession(uuid);
        if (session == null) return;
        if (session.request().type() != InputType.CHAT) return;

        // Suppress the chat message
        event.setCancelled(true);

        String input = event.getMessage();

        // Check cancel keyword (case-insensitive)
        if (input.equalsIgnoreCase(session.request().cancelKeyword())) {
            scheduler.runSync(() -> service.cancelInputWithMessage(uuid));
            return;
        }

        // Validate input
        if (session.request().validator() != null) {
            String error = session.request().validator().validate(input);
            if (error != null) {
                int attempt = session.getAndIncrementRetry();
                if (attempt >= session.request().maxRetries()) {
                    scheduler.runSync(() -> {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.sendMessage(DefaultInputService.colorize("&cNúmero máximo de tentativas excedido."));
                        }
                        service.cancelInputSilent(uuid);
                    });
                } else {
                    String finalError = error;
                    scheduler.runSync(() -> {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            String msg = session.request().validationFailMessage() != null
                                    ? session.request().validationFailMessage().replace("{error}", finalError)
                                    : "&cEntrada inválida: &f" + finalError;
                            p.sendMessage(DefaultInputService.colorize(msg));
                        }
                    });
                }
                return;
            }
        }

        // Success
        String finalInput = input;
        scheduler.runSync(() -> service.completeSession(uuid, finalInput));
    }
}
