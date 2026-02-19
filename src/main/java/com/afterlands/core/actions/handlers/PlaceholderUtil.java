package com.afterlands.core.actions.handlers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Utilitário para processar PlaceholderAPI de forma segura.
 *
 * <p>
 * Graceful degradation: se PAPI não estiver disponível, retorna texto literal.
 * </p>
 * <p>
 * Thread safety: SEMPRE deve ser chamado na main thread.
 * </p>
 */
final class PlaceholderUtil {

    private static final boolean PAPI_AVAILABLE = checkPlaceholderAPI();

    private PlaceholderUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Processa placeholders se PAPI estiver disponível.
     *
     * <p>
     * <b>CRITICAL:</b> Deve ser chamado APENAS na main thread!
     * </p>
     */
    @NotNull
    static String process(@NotNull Player player, @NotNull String text) {
        // Pre-process legacy {player} placeholder (used by older ABA animations)
        if (text.contains("{player}")) {
            text = text.replace("{player}", player.getName());
        }

        if (text.contains("%player%")) {
            text = text.replace("%player%", player.getName());
        }

        if (!PAPI_AVAILABLE) {
            return text; // Sem PAPI, retorna literal
        }

        // PlaceholderAPI só funciona na main thread
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("PlaceholderAPI must be called on main thread!");
        }

        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable t) {
            // Fallback se algo der errado
            return text;
        }
    }

    /**
     * Verifica se PlaceholderAPI está disponível no servidor.
     */
    private static boolean checkPlaceholderAPI() {
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Retorna se PlaceholderAPI está disponível.
     */
    static boolean isAvailable() {
        return PAPI_AVAILABLE;
    }
}
