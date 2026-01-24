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

        if (config.messages().isList(path)) {
            java.util.List<String> lines = config.messages().getStringList(path);
            if (lines.isEmpty()) {
                if (debug)
                    plugin.getLogger().warning("[AfterCore] Lista de mensagens vazia: " + path);
                return;
            }
            for (String line : lines) {
                sendRaw(sender, prefix + line);
            }
            return;
        }

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
    public void send(@NotNull CommandSender sender, @NotNull String path, @NotNull String... replacements) {
        String prefix = config.messages().getString("prefix", "");

        if (config.messages().isList(path)) {
            java.util.List<String> lines = config.messages().getStringList(path);
            if (lines.isEmpty())
                return;
            for (String line : lines) {
                sendRaw(sender, prefix + applyReplacements(line, replacements));
            }
            return;
        }

        String msg = config.messages().getString(path, "");
        if (msg == null || msg.isEmpty()) {
            if (debug)
                plugin.getLogger().warning("[AfterCore] Mensagem não encontrada: " + path);
            return;
        }
        sendRaw(sender, prefix + applyReplacements(msg, replacements));
    }

    @Override
    public void sendRaw(@NotNull CommandSender sender, @NotNull String raw) {
        sender.sendMessage(format(raw));
    }

    @Override
    public @NotNull String get(@NotNull String path) {
        return config.messages().getString(path, "");
    }

    @Override
    public @NotNull String get(@NotNull String path, @NotNull String... replacements) {
        return applyReplacements(get(path), replacements);
    }

    private String applyReplacements(String msg, String... replacements) {
        if (replacements.length % 2 != 0) {
            plugin.getLogger().warning("[AfterCore] Replacements devem ser pares (key, value). Ignorando último.");
        }
        String result = msg;
        for (int i = 0; i < replacements.length - 1; i += 2) {
            String key = replacements[i];
            String value = replacements[i + 1];
            if (key != null && value != null) {
                result = result.replace(key, value);
            }
        }
        return result;
    }

    @Override
    public @NotNull java.util.List<String> getList(@NotNull String path) {
        return config.messages().getStringList(path);
    }

    @Override
    public @NotNull String format(@NotNull String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw);
    }
}
