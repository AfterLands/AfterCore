package com.afterlands.core.config;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * API padronizada de mensagens.
 */
public interface MessageService {

    void send(@NotNull CommandSender sender, @NotNull String path);

    void sendRaw(@NotNull CommandSender sender, @NotNull String raw);

    @NotNull String format(@NotNull String raw);
}

