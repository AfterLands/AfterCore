package com.afterlands.core.config;

import com.afterlands.core.api.messages.MessageKey;
import com.afterlands.core.api.messages.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unified messaging API with i18n support via Provider Pattern.
 *
 * <p>
 * <b>Legacy Methods (Path-based):</b> Simple message resolution from
 * messages.yml
 * </p>
 * <p>
 * <b>I18n Methods (MessageKey-based):</b> Advanced i18n with per-player
 * language support
 * </p>
 *
 * <p>
 * When AfterLanguage plugin is installed, i18n methods provide full translation
 * capabilities.
 * Otherwise, they fall back to simple message resolution.
 * </p>
 *
 * <h3>Migration Path:</h3>
 * 
 * <pre>{@code
 * // Old (deprecated)
 * messages.send(player, "quest.started", "{quest}", questName);
 *
 * // New (i18n-ready)
 * messages.send(player, MessageKey.of("afterjournal", "quest.started"),
 *         Placeholder.of("quest", questName));
 * }</pre>
 */
public interface MessageService {

    // ══════════════════════════════════════════════
    // LEGACY METHODS (Path-based, messages.yml)
    // ══════════════════════════════════════════════

    /**
     * Sends message from messages.yml using path.
     *
     * @deprecated Use {@link #send(Player, MessageKey, Placeholder...)} for i18n
     *             support
     */
    @Deprecated
    void send(@NotNull CommandSender sender, @NotNull String path);

    /**
     * Sends message with simple placeholder replacements.
     *
     * @param replacements Pairs of key/value (e.g., "{player}", "Steve")
     * @deprecated Use {@link #send(Player, MessageKey, Placeholder...)} for i18n
     *             support
     */
    @Deprecated
    void send(@NotNull CommandSender sender, @NotNull String path, @NotNull String... replacements);

    /**
     * Sends raw formatted message.
     *
     * @deprecated Use i18n methods for better internationalization
     */
    @Deprecated
    void sendRaw(@NotNull CommandSender sender, @NotNull String raw);

    /**
     * Gets message from messages.yml.
     *
     * @param path YAML path (e.g., "game.error")
     * @return Message or empty string if not found
     * @deprecated Use {@link #get(Player, MessageKey, Placeholder...)} for i18n
     *             support
     */
    @Deprecated
    @NotNull
    String get(@NotNull String path);

    /**
     * Gets message with simple placeholder replacements.
     *
     * @deprecated Use {@link #get(Player, MessageKey, Placeholder...)} for i18n
     *             support
     */
    @Deprecated
    @NotNull
    String get(@NotNull String path, @NotNull String... replacements);

    /**
     * Gets list of messages from messages.yml.
     *
     * @deprecated Use {@link #get(Player, MessageKey, Placeholder...)} for i18n
     *             support
     */
    @Deprecated
    @NotNull
    List<String> getList(@NotNull String path);

    /**
     * Formats string with color codes.
     *
     * @param raw Raw string with & color codes
     * @return Formatted string
     */
    @NotNull
    String format(@NotNull String raw);

    // ══════════════════════════════════════════════
    // I18N METHODS (MessageKey-based, AfterLanguage)
    // ══════════════════════════════════════════════

    /**
     * Sends translated message to player in their language.
     *
     * <p>
     * If AfterLanguage is installed, uses player's configured language.
     * Otherwise, falls back to simple message resolution.
     * </p>
     *
     * @param player       Player recipient
     * @param key          Translation key
     * @param placeholders Placeholders to replace in message
     */
    void send(@NotNull Player player, @NotNull MessageKey key, @NotNull Placeholder... placeholders);

    /**
     * Sends translated message with pluralization support.
     *
     * <p>
     * Selects between .one and .other variants based on count.
     * </p>
     *
     * @param player       Player recipient
     * @param key          Translation key (should have .one and .other variants)
     * @param count        Count for pluralization
     * @param placeholders Additional placeholders
     */
    void send(@NotNull Player player, @NotNull MessageKey key, int count, @NotNull Placeholder... placeholders);

    /**
     * Gets translated string for player in their language.
     *
     * @param player       Player for language resolution
     * @param key          Translation key
     * @param placeholders Placeholders to replace
     * @return Translated string with placeholders resolved
     */
    @NotNull
    String get(@NotNull Player player, @NotNull MessageKey key, @NotNull Placeholder... placeholders);

    /**
     * Gets translated string with pluralization support.
     *
     * @param player       Player for language resolution
     * @param key          Translation key
     * @param count        Count for pluralization
     * @param placeholders Placeholders to replace
     * @return Translated string
     */
    @NotNull
    String get(@NotNull Player player, @NotNull MessageKey key, int count, @NotNull Placeholder... placeholders);

    /**
     * Gets translated string with fallback if key doesn't exist.
     *
     * <p>
     * Useful for config scanner integration where fallback to original text is
     * needed.
     * </p>
     *
     * @param player       Player for language resolution
     * @param key          Translation key
     * @param defaultValue Fallback value if translation doesn't exist (nullable)
     * @param placeholders Placeholders to replace
     * @return Translated string or defaultValue
     */
    @NotNull
    String getOrDefault(@NotNull Player player, @NotNull MessageKey key, @Nullable String defaultValue,
            @NotNull Placeholder... placeholders);

    /**
     * Broadcasts message to all online players (each in their language).
     *
     * @param key          Translation key
     * @param placeholders Placeholders (same for all players)
     */
    void broadcast(@NotNull MessageKey key, @NotNull Placeholder... placeholders);

    /**
     * Broadcasts message to players with specific permission.
     *
     * @param key          Translation key
     * @param permission   Required permission
     * @param placeholders Placeholders
     */
    void broadcast(@NotNull MessageKey key, @NotNull String permission, @NotNull Placeholder... placeholders);

    /**
     * Sends multiple messages with shared placeholders.
     *
     * @param player             Player recipient
     * @param keys               Message keys
     * @param sharedPlaceholders Placeholders applied to all messages
     */
    void sendBatch(@NotNull Player player, @NotNull List<MessageKey> keys,
            @NotNull Map<String, Object> sharedPlaceholders);

    // ══════════════════════════════════════════════
    // PLAYER LANGUAGE MANAGEMENT
    // ══════════════════════════════════════════════

    /**
     * Gets player's configured language code.
     *
     * @param playerId Player UUID
     * @return Language code (e.g., "pt_br", "en_us")
     */
    @NotNull
    String getPlayerLanguage(@NotNull UUID playerId);

    /**
     * Sets player's language preference.
     *
     * @param playerId Player UUID
     * @param language Language code
     */
    void setPlayerLanguage(@NotNull UUID playerId, @NotNull String language);

    /**
     * Gets list of available language codes.
     *
     * @return List of language codes
     */
    @NotNull
    List<String> getAvailableLanguages();

    /**
     * Gets the default/fallback language code.
     *
     * @return Default language code
     */
    @NotNull
    String getDefaultLanguage();

    // ══════════════════════════════════════════════
    // NAMESPACE REGISTRATION
    // ══════════════════════════════════════════════

    /**
     * Registers an i18n namespace for a plugin.
     *
     * <p>
     * When AfterLanguage is installed, extracts default translation files from the
     * plugin's JAR ({@code languages/{lang}/{namespace}/*.yml}) and registers
     * the namespace for translation resolution.
     * </p>
     *
     * <p>
     * When AfterLanguage is not installed, this is a no-op — the plugin
     * falls back to its own messages.yml.
     * </p>
     *
     * @param plugin    The plugin registering the namespace
     * @param namespace Namespace identifier (e.g., "aftertemplate")
     */
    void registerNamespace(@NotNull Plugin plugin, @NotNull String namespace);
}
