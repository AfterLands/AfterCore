package com.afterlands.core.config.impl;

import com.afterlands.core.config.ConfigService;
import com.afterlands.core.config.MessageService;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class DefaultMessageService implements MessageService {

    private final Plugin plugin;
    private final ConfigService config;
    private final boolean debug;

    public DefaultMessageService(@NotNull Plugin plugin, @NotNull ConfigService config, boolean debug) {
        this.plugin = plugin;
        this.config = config;
        this.debug = debug;
    }

    @Override
    public void send(@NotNull CommandSender sender, @NotNull String path) {
        String prefix = config.messages().getString("prefix", "");
        String msg = config.messages().getString(path, "");
        if (msg == null || msg.isEmpty()) {
            if (debug) {
                plugin.getLogger().warning("[AfterCore] Mensagem não encontrada: " + path);
            }
            return;
        }
        sendRaw(sender, prefix + msg);
    }

    @Override
    public void sendRaw(@NotNull CommandSender sender, @NotNull String raw) {
        sender.sendMessage(format(raw));
    }

    @Override
    public @NotNull String format(@NotNull String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw);
    }
}

