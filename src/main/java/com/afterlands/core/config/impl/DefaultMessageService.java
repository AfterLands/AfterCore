package com.afterlands.core.config.impl;

import com.afterlands.core.api.messages.MessageKey;
import com.afterlands.core.api.messages.Placeholder;
import com.afterlands.core.config.ConfigService;
import com.afterlands.core.config.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default fallback implementation of MessageService.
 *
 * <p>
 * Provides basic message resolution from messages.yml without i18n support.
 * When AfterLanguage is installed, it registers as provider and overrides these
 * methods.
 * </p>
 */
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
                    plugin.getLogger().warning("Lista de mensagens vazia: " + path);
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
                plugin.getLogger().warning("Mensagem não encontrada: " + path);
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
                plugin.getLogger().warning("Mensagem não encontrada: " + path);
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
            plugin.getLogger().warning("Replacements devem ser pares (key, value). Ignorando último.");
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

    // ══════════════════════════════════════════════
    // I18N METHODS (Fallback implementation)
    // ══════════════════════════════════════════════

    @Override
    public void send(@NotNull Player player, @NotNull MessageKey key, @NotNull Placeholder... placeholders) {
        MessageService delegate = getDelegate();
        if (delegate != null) {
            delegate.send(player, key, placeholders);
            return;
        }

        // Fallback: try to resolve from messages.yml using full key
        String message = get(key.fullKey());
        if (message.isEmpty()) {
            // Try just the path without namespace
            message = get(key.path());
        }

        if (!message.isEmpty()) {
            String resolved = Placeholder.replaceAll(message, placeholders);
            sendRaw(player, resolved);
        } else if (debug) {
            plugin.getLogger().warning("[MessageService] Message key not found (fallback mode): " + key.fullKey());
        }
    }

    @Override
    public void send(@NotNull Player player, @NotNull MessageKey key, int count, @NotNull Placeholder... placeholders) {
        MessageService delegate = getDelegate();
        if (delegate != null) {
            delegate.send(player, key, count, placeholders);
            return;
        }

        // Fallback: no pluralization support, just send with count as placeholder
        Placeholder[] withCount = new Placeholder[placeholders.length + 1];
        withCount[0] = Placeholder.of("count", count);
        System.arraycopy(placeholders, 0, withCount, 1, placeholders.length);
        send(player, key, withCount);
    }

    @Override
    public @NotNull String get(@NotNull Player player, @NotNull MessageKey key, @NotNull Placeholder... placeholders) {
        MessageService delegate = getDelegate();
        if (delegate != null) {
            return delegate.get(player, key, placeholders);
        }

        // Fallback: try to resolve from messages.yml
        String message = get(key.fullKey());
        if (message.isEmpty()) {
            message = get(key.path());
        }

        if (!message.isEmpty()) {
            return format(Placeholder.replaceAll(message, placeholders));
        }

        // Ultimate fallback: return key literal
        return "&c[Missing: " + key.fullKey() + "]";
    }

    @Override
    public @NotNull String getOrDefault(@NotNull Player player, @NotNull MessageKey key, @NotNull String defaultValue,
            @NotNull Placeholder... placeholders) {
        MessageService delegate = getDelegate();
        if (delegate != null) {
            return delegate.getOrDefault(player, key, defaultValue, placeholders);
        }

        String message = get(key.fullKey());
        if (message.isEmpty()) {
            message = get(key.path());
        }

        if (!message.isEmpty()) {
            return format(Placeholder.replaceAll(message, placeholders));
        }

        // Use provided default
        return format(Placeholder.replaceAll(defaultValue, placeholders));
    }

    @Override
    public void broadcast(@NotNull MessageKey key, @NotNull Placeholder... placeholders) {
        MessageService delegate = getDelegate();
        if (delegate != null) {
            delegate.broadcast(key, placeholders);
            return;
        }

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            send(onlinePlayer, key, placeholders);
        }
    }

    @Override
    public void broadcast(@NotNull MessageKey key, @NotNull String permission, @NotNull Placeholder... placeholders) {
        MessageService delegate = getDelegate();
        if (delegate != null) {
            delegate.broadcast(key, permission, placeholders);
            return;
        }

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.hasPermission(permission)) {
                send(onlinePlayer, key, placeholders);
            }
        }
    }

    @Override
    public void sendBatch(@NotNull Player player, @NotNull List<MessageKey> keys,
            @NotNull Map<String, Object> sharedPlaceholders) {
        MessageService delegate = getDelegate();
        if (delegate != null) {
            delegate.sendBatch(player, keys, sharedPlaceholders);
            return;
        }

        Placeholder[] placeholders = Placeholder.fromMap(sharedPlaceholders);
        for (MessageKey key : keys) {
            send(player, key, placeholders);
        }
    }

    // ══════════════════════════════════════════════
    // PLAYER LANGUAGE MANAGEMENT (No-op fallback)
    // ══════════════════════════════════════════════

    @Override
    public @NotNull String getPlayerLanguage(@NotNull UUID playerId) {
        MessageService delegate = getDelegate();
        if (delegate != null) {
            return delegate.getPlayerLanguage(playerId);
        }
        return getDefaultLanguage();
    }

    @Override
    public void setPlayerLanguage(@NotNull UUID playerId, @NotNull String language) {
        MessageService delegate = getDelegate();
        if (delegate != null) {
            delegate.setPlayerLanguage(playerId, language);
            return;
        }

        // No-op in fallback mode (no persistence)
        if (debug) {
            plugin.getLogger()
                    .info("[MessageService] Language change ignored in fallback mode (AfterLanguage not installed)");
        }
    }

    @Override
    public @NotNull List<String> getAvailableLanguages() {
        MessageService delegate = getDelegate();
        if (delegate != null) {
            return delegate.getAvailableLanguages();
        }
        return List.of(getDefaultLanguage());
    }

    @Override
    public @NotNull String getDefaultLanguage() {
        MessageService delegate = getDelegate();
        if (delegate != null) {
            return delegate.getDefaultLanguage();
        }
        String lang = config.main().getString("language.default");
        return lang != null && !lang.isEmpty() ? lang : "pt_br";
    }

    /**
     * Tries to find a registered MessageService provider (e.g. AfterLanguage).
     * Returns null if none found or if the provider is this instance.
     */
    private MessageService getDelegate() {
        try {
            var registration = Bukkit.getServicesManager().getRegistration(MessageService.class);
            if (registration != null) {
                MessageService provider = registration.getProvider();
                // Avoid self-delegation if this instance is somehow registered
                if (provider != this) {
                    return provider;
                }
            }
        } catch (Throwable t) {
            // Ignore errors during lookup (e.g. if Bukkit isn't fully initialized)
        }
        return null;
    }
}
