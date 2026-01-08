package com.afterlands.core.util.ratelimit;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serviço de cooldowns para players e outras chaves.
 *
 * <p>Casos de uso:
 * <ul>
 *   <li>Cooldown de comandos per-player</li>
 *   <li>Rate limiting de interações (blocos, NPCs, etc.)</li>
 *   <li>Anti-spam de mensagens</li>
 * </ul>
 * </p>
 *
 * <p>Thread-safe.</p>
 */
public final class CooldownService {

    private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    /**
     * Verifica se player pode executar ação (consome token se permitido).
     *
     * @param player     Player
     * @param actionKey  Chave da ação (ex: "command:tpa", "interact:npc")
     * @param cooldown   Cooldown entre ações
     * @return true se ação é permitida
     */
    public boolean tryAcquire(@NotNull Player player, @NotNull String actionKey, @NotNull Duration cooldown) {
        return tryAcquire(player.getUniqueId(), actionKey, cooldown);
    }

    /**
     * Verifica se UUID pode executar ação (consome token se permitido).
     *
     * @param uuid       UUID (player ou outra entidade)
     * @param actionKey  Chave da ação
     * @param cooldown   Cooldown entre ações
     * @return true se ação é permitida
     */
    public boolean tryAcquire(@NotNull UUID uuid, @NotNull String actionKey, @NotNull Duration cooldown) {
        String key = actionKey + ":" + uuid;
        RateLimiter limiter = getOrCreateLimiter(actionKey, cooldown);
        return limiter.tryAcquire(key);
    }

    /**
     * Verifica tempo restante até próxima ação.
     *
     * @param player     Player
     * @param actionKey  Chave da ação
     * @param cooldown   Cooldown entre ações
     * @return resultado com tempo restante
     */
    @NotNull
    public RateLimiter.AcquireResult tryAcquireWithRemaining(@NotNull Player player,
                                                             @NotNull String actionKey,
                                                             @NotNull Duration cooldown) {
        return tryAcquireWithRemaining(player.getUniqueId(), actionKey, cooldown);
    }

    /**
     * Verifica tempo restante até próxima ação.
     *
     * @param uuid       UUID
     * @param actionKey  Chave da ação
     * @param cooldown   Cooldown entre ações
     * @return resultado com tempo restante
     */
    @NotNull
    public RateLimiter.AcquireResult tryAcquireWithRemaining(@NotNull UUID uuid,
                                                             @NotNull String actionKey,
                                                             @NotNull Duration cooldown) {
        String key = actionKey + ":" + uuid;
        RateLimiter limiter = getOrCreateLimiter(actionKey, cooldown);
        return limiter.tryAcquireWithRemaining(key);
    }

    /**
     * Reseta cooldown de um player para uma ação específica.
     *
     * @param player    Player
     * @param actionKey Chave da ação
     */
    public void reset(@NotNull Player player, @NotNull String actionKey) {
        reset(player.getUniqueId(), actionKey);
    }

    /**
     * Reseta cooldown de um UUID para uma ação específica.
     *
     * @param uuid      UUID
     * @param actionKey Chave da ação
     */
    public void reset(@NotNull UUID uuid, @NotNull String actionKey) {
        RateLimiter limiter = limiters.get(actionKey);
        if (limiter != null) {
            String key = actionKey + ":" + uuid;
            limiter.reset(key);
        }
    }

    /**
     * Limpa todos os cooldowns.
     */
    public void clearAll() {
        limiters.values().forEach(RateLimiter::clear);
        limiters.clear();
    }

    private RateLimiter getOrCreateLimiter(@NotNull String actionKey, @NotNull Duration cooldown) {
        return limiters.computeIfAbsent(actionKey,
                k -> TokenBucketRateLimiter.simpleCooldown(cooldown));
    }

    /**
     * Formata duração em formato legível (ex: "5s", "2m 30s", "1h 15m").
     */
    @NotNull
    public static String formatDuration(@NotNull Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return "0s";
        }

        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 || sb.length() == 0) {
            sb.append(seconds).append("s");
        }

        return sb.toString().trim();
    }
}
