package com.afterlands.core.input.impl;

import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.input.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Implementação principal do {@link InputService}.
 *
 * <p>Mantém um registry de sessões ativas ({@code ConcurrentHashMap}),
 * garante no máximo 1 sessão por jogador e gerencia timeouts via {@link BukkitTask}.</p>
 */
public final class DefaultInputService implements InputService, Listener {

    private final Plugin plugin;
    private final SchedulerService scheduler;
    private final boolean debug;
    private final Logger logger;
    private final boolean protocolLibAvailable;

    private final ConcurrentHashMap<UUID, ActiveInputSession> sessions = new ConcurrentHashMap<>();

    private final ChatInputHandler chatHandler;
    private final AnvilInputHandler anvilHandler;
    private final @Nullable SignInputHandler signHandler;

    public DefaultInputService(@NotNull Plugin plugin,
                               @NotNull SchedulerService scheduler,
                               boolean debug) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.debug = debug;
        this.logger = plugin.getLogger();
        this.protocolLibAvailable = Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;

        this.chatHandler  = new ChatInputHandler(this, scheduler);
        this.anvilHandler = new AnvilInputHandler(this, plugin);
        this.signHandler  = protocolLibAvailable ? new SignInputHandler(this, plugin) : null;

        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(chatHandler, plugin);
        Bukkit.getPluginManager().registerEvents(anvilHandler, plugin);

        if (debug) {
            logger.info("[InputService] Initialized (ProtocolLib=" + protocolLibAvailable + ")");
        }
    }

    // ==================== InputService API ====================

    @Override
    public @NotNull CompletableFuture<InputResult> requestInput(@NotNull Player player,
                                                                @NotNull InputRequest request) {
        UUID uuid = player.getUniqueId();

        // Cancel existing session silently (not sending cancel message)
        cancelInputSilent(uuid);

        // Sign input requires ProtocolLib
        if (request.type() == InputType.SIGN && !protocolLibAvailable) {
            if (debug) {
                logger.info("[InputService] SIGN input unavailable for " + player.getName()
                        + " (ProtocolLib missing)");
            }
            return CompletableFuture.completedFuture(InputResult.unavailable(InputType.SIGN));
        }

        CompletableFuture<InputResult> future = new CompletableFuture<>();

        // Schedule timeout task
        BukkitTask timeoutTask = null;
        if (request.timeoutTicks() > 0) {
            timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ActiveInputSession session = sessions.remove(uuid);
                if (session == null) return;
                cancelBukkitTask(session.timeoutTask()); // no-op, but safe
                String msg = request.timeoutMessage();
                Player p = Bukkit.getPlayer(uuid);
                if (msg != null && p != null && p.isOnline()) {
                    p.sendMessage(colorize(msg));
                }
                session.future().complete(InputResult.timedOut(request.type()));
            }, request.timeoutTicks());
        }

        ActiveInputSession session = new ActiveInputSession(uuid, request, future, timeoutTask);
        sessions.put(uuid, session);

        // Delegate to correct handler
        switch (request.type()) {
            case CHAT  -> deliverChatPrompt(player, request);
            case SIGN  -> scheduler.runSync(() -> {
                if (signHandler != null) signHandler.openSign(player, request);
            });
            case ANVIL -> scheduler.runSync(() -> anvilHandler.openAnvil(player, request));
        }

        return future;
    }

    @Override
    public void cancelInput(@NotNull Player player) {
        cancelInputWithMessage(player.getUniqueId());
    }

    @Override
    public boolean hasActiveInput(@NotNull Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    @Override
    public boolean isSignInputAvailable() {
        return protocolLibAvailable;
    }

    // ==================== Internal API (used by handlers) ====================

    /** Returns the active session for a player, or null. */
    @Nullable ActiveInputSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    /** Completes the session successfully and removes it. */
    void completeSession(UUID playerId, String value) {
        ActiveInputSession session = sessions.remove(playerId);
        if (session == null) return;
        cancelBukkitTask(session.timeoutTask());
        session.future().complete(InputResult.success(value, session.request().type()));
        if (debug) {
            logger.info("[InputService] Session completed for " + playerId + " -> \"" + value + "\"");
        }
    }

    /** Cancels a session and sends the cancel message (if configured). */
    void cancelInputWithMessage(UUID playerId) {
        cancelInternal(playerId, true);
    }

    /** Cancels a session silently (no cancel message sent). */
    void cancelInputSilent(UUID playerId) {
        cancelInternal(playerId, false);
    }

    SchedulerService getScheduler() { return scheduler; }
    Logger getLogger() { return logger; }
    boolean isDebug() { return debug; }

    // ==================== Private ====================

    private void cancelInternal(UUID playerId, boolean sendMessage) {
        ActiveInputSession session = sessions.remove(playerId);
        if (session == null) return;
        cancelBukkitTask(session.timeoutTask());
        if (sendMessage) {
            String msg = session.request().cancelMessage();
            Player player = Bukkit.getPlayer(playerId);
            if (msg != null && player != null && player.isOnline()) {
                player.sendMessage(colorize(msg));
            }
        }
        session.future().complete(InputResult.cancelled(session.request().type()));
    }

    private void deliverChatPrompt(Player player, InputRequest request) {
        scheduler.runSync(() -> {
            if (!player.isOnline()) return;
            if (request.promptMessage() != null) {
                player.sendMessage(colorize(request.promptMessage()));
            }
            if (request.promptTitle() != null || request.promptSubtitle() != null) {
                player.sendTitle(
                        request.promptTitle()    != null ? colorize(request.promptTitle())    : "",
                        request.promptSubtitle() != null ? colorize(request.promptSubtitle()) : "");
            }
            if (request.promptActionBar() != null) {
                // ActionBar via NMS for 1.8.8 (same pattern used elsewhere in the codebase)
                sendActionBar(player, colorize(request.promptActionBar()));
            }
        });
    }

    private void sendActionBar(Player player, String text) {
        try {
            String v = plugin.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> packetClass = Class.forName("net.minecraft.server." + v + ".PacketPlayOutChat");
            Class<?> chatClass   = Class.forName("net.minecraft.server." + v + ".ChatComponentText");
            Class<?> packetBase  = Class.forName("net.minecraft.server." + v + ".Packet");
            Object chatComponent = chatClass.getConstructor(String.class).newInstance(text);
            Object packet = packetClass.getConstructor(
                    Class.forName("net.minecraft.server." + v + ".IChatBaseComponent"), byte.class)
                    .newInstance(chatComponent, (byte) 2);
            Object nmsPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Object conn = nmsPlayer.getClass().getDeclaredField("playerConnection").get(nmsPlayer);
            conn.getClass().getMethod("sendPacket", packetBase).invoke(conn, packet);
        } catch (Exception ignored) {
            // ActionBar fallback: no-op — prompt message was already sent
        }
    }

    private static void cancelBukkitTask(@Nullable BukkitTask task) {
        if (task == null) return;
        try { task.cancel(); } catch (Exception ignored) {}
    }

    // ==================== Event Listeners ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ActiveInputSession session = sessions.remove(uuid);
        if (session == null) return;
        cancelBukkitTask(session.timeoutTask());
        session.future().complete(InputResult.disconnected(session.request().type()));
    }

    // ==================== Lifecycle ====================

    public void shutdown() {
        for (ActiveInputSession session : sessions.values()) {
            cancelBukkitTask(session.timeoutTask());
            session.future().complete(InputResult.cancelled(session.request().type()));
        }
        sessions.clear();

        if (signHandler != null) {
            signHandler.unregister();
        }

        if (debug) {
            logger.info("[InputService] Shutdown complete");
        }
    }

    // ==================== Utility ====================

    static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
