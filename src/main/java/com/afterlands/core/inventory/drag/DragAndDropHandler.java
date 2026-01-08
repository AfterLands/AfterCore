package com.afterlands.core.inventory.drag;

import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.inventory.InventoryContext;
import com.afterlands.core.inventory.action.InventoryActionHandler;
import com.afterlands.core.inventory.item.GuiItem;
import com.afterlands.core.inventory.view.InventoryViewHolder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handler de drag-and-drop para inventários do AfterCore.
 *
 * <p>Gerencia sessões de drag com validação server-side para prevenir
 * item duplication (anti-dupe protection).
 *
 * <p>Thread safety: Todos os métodos são thread-safe.
 * Drag events sempre rodam na main thread (garantido pelo Bukkit).
 */
public class DragAndDropHandler {

    private final SchedulerService scheduler;
    private final InventoryActionHandler actionHandler;
    private final Logger logger;
    private final boolean debug;

    // Active drag sessions (player UUID -> session)
    private final Map<UUID, DragSession> activeDragSessions;

    // Cleanup task (remove expired sessions)
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);
    private BukkitTask cleanupTask;

    public DragAndDropHandler(
        SchedulerService scheduler,
        InventoryActionHandler actionHandler,
        Logger logger,
        boolean debug
    ) {
        this.scheduler = scheduler;
        this.actionHandler = actionHandler;
        this.logger = logger;
        this.debug = debug;
        this.activeDragSessions = new ConcurrentHashMap<>();

        // Schedule cleanup task (every 60 seconds)
        long intervalTicks = 20L * 60; // 60 seconds
        this.cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            scheduler.plugin(),
            this::cleanupExpiredSessions,
            intervalTicks,
            intervalTicks
        );
    }

    /**
     * Inicia sessão de drag.
     *
     * <p>IMPORTANTE: Este método sempre roda na main thread (garantido pelo Bukkit).
     *
     * @param event InventoryDragEvent do Bukkit
     * @param item GuiItem configurado (pode ser null se slot não tem GuiItem)
     * @return true se drag permitido
     */
    public boolean startDrag(InventoryDragEvent event, GuiItem item) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return false;
        }

        // Check if item allows drag
        if (item != null && !item.isAllowDrag()) {
            if (debug) {
                logger.fine("[DragDrop] Drag not allowed for item at slot " + event.getRawSlots());
            }
            return false;
        }

        ItemStack cursor = event.getOldCursor();
        if (cursor == null || cursor.getType().name().equals("AIR")) {
            return false;
        }

        // Get inventory holder
        InventoryViewHolder holder = InventoryViewHolder.get(player);
        if (holder == null) {
            return false;
        }

        // Create drag session
        DragSession session = DragSession.create(
            player.getUniqueId(),
            cursor,
            -1, // Drag doesn't have a single source slot
            holder.getInventoryConfig().id()
        );

        activeDragSessions.put(player.getUniqueId(), session);

        if (debug) {
            logger.info("[DragDrop] Started drag session: " + session);
        }

        return true;
    }

    /**
     * Processa fim de drag.
     *
     * <p>IMPORTANTE: Este método sempre roda na main thread (garantido pelo Bukkit).
     *
     * @param event InventoryDragEvent
     * @param targetSlot Slot de destino (-1 se múltiplos slots)
     * @return true se drag válido
     */
    public boolean completeDrag(InventoryDragEvent event, int targetSlot) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return false;
        }

        DragSession session = activeDragSessions.get(player.getUniqueId());
        if (session == null) {
            if (debug) {
                logger.fine("[DragDrop] No active drag session for player " + player.getName());
            }
            return false;
        }

        // Validate session
        ValidationResult validation = validateDrag(session, event.getOldCursor());
        if (validation.isInvalid()) {
            if (debug) {
                logger.warning("[DragDrop] Drag validation failed: " + validation);
            }
            cancelDrag(player.getUniqueId());
            return false;
        }

        // Get target item (if single slot drag)
        ItemStack targetItem = null;
        if (targetSlot >= 0 && event.getView().getTopInventory().getSize() > targetSlot) {
            targetItem = event.getView().getTopInventory().getItem(targetSlot);
        }

        // Additional validation for target
        ValidationResult targetValidation = validateDragTarget(session, targetItem, targetSlot);
        if (targetValidation.isInvalid()) {
            if (debug) {
                logger.warning("[DragDrop] Target validation failed: " + targetValidation);
            }
            cancelDrag(player.getUniqueId());
            return false;
        }

        if (debug) {
            logger.info("[DragDrop] Completed drag session: " + session);
        }

        // Execute drag action if configured
        InventoryViewHolder holder = InventoryViewHolder.get(player);
        if (holder != null && targetSlot >= 0) {
            GuiItem targetGuiItem = holder.getGuiItemAt(targetSlot);
            if (targetGuiItem != null && targetGuiItem.getDragAction() != null) {
                InventoryContext context = holder.getContext();
                executeDragAction(targetGuiItem, player, context);
            }
        }

        // Remove session
        activeDragSessions.remove(player.getUniqueId());

        return true;
    }

    /**
     * Cancela drag em progresso.
     *
     * @param playerId UUID do player
     */
    public void cancelDrag(UUID playerId) {
        DragSession removed = activeDragSessions.remove(playerId);
        if (debug && removed != null) {
            logger.fine("[DragDrop] Cancelled drag session: " + removed);
        }
    }

    /**
     * Valida drag (anti-dupe protection).
     *
     * @param session Sessão de drag
     * @param currentItem Item atual no cursor
     * @return ValidationResult
     */
    public ValidationResult validateDrag(DragSession session, ItemStack currentItem) {
        // Check session expiration
        if (session.isExpired()) {
            return ValidationResult.error(
                ValidationResult.SESSION_EXPIRED,
                "Drag session expired (timeout: 30s)"
            );
        }

        // Validate item hasn't been modified (anti-dupe)
        if (!session.isValid(currentItem)) {
            return ValidationResult.error(
                ValidationResult.CHECKSUM_MISMATCH,
                "Item was modified during drag (possible dupe attempt)"
            );
        }

        return ValidationResult.ok();
    }

    /**
     * Valida target slot do drag.
     *
     * @param session Sessão de drag
     * @param targetItem Item no slot de destino
     * @param targetSlot Slot de destino
     * @return ValidationResult
     */
    private ValidationResult validateDragTarget(DragSession session, ItemStack targetItem, int targetSlot) {
        if (targetSlot < 0) {
            // Multi-slot drag is allowed
            return ValidationResult.ok();
        }

        // Additional validations can be added here
        // For example: check if target slot accepts the item type

        return ValidationResult.ok();
    }

    /**
     * Executa action de drag (se configurada).
     *
     * <p>IMPORTANTE: drag-action é executada na main thread.
     *
     * @param item GuiItem com dragAction
     * @param player Player
     * @param context Context
     * @return CompletableFuture que completa quando a action foi executada
     */
    public CompletableFuture<Void> executeDragAction(GuiItem item, Player player, InventoryContext context) {
        String dragAction = item.getDragAction();
        if (dragAction == null || dragAction.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        if (debug) {
            logger.info("[DragDrop] Executing drag action: " + dragAction);
        }

        // Execute action via InventoryActionHandler
        return actionHandler.executeActions(List.of(dragAction), player, context)
            .exceptionally(ex -> {
                logger.log(Level.WARNING, "Failed to execute drag action: " + dragAction, ex);
                return null;
            });
    }

    /**
     * Retorna sessão ativa de um player (se existir).
     *
     * @param playerId UUID do player
     * @return Optional com a sessão ativa
     */
    public Optional<DragSession> getActiveSession(UUID playerId) {
        return Optional.ofNullable(activeDragSessions.get(playerId));
    }

    /**
     * Verifica se um player tem sessão ativa.
     *
     * @param playerId UUID do player
     * @return true se tem sessão ativa
     */
    public boolean hasActiveSession(UUID playerId) {
        return activeDragSessions.containsKey(playerId);
    }

    /**
     * Cleanup de sessões expiradas (executado periodicamente).
     */
    private void cleanupExpiredSessions() {
        Instant now = Instant.now();
        int removed = 0;

        Iterator<Map.Entry<UUID, DragSession>> iterator = activeDragSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, DragSession> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                removed++;
            }
        }

        if (debug && removed > 0) {
            logger.fine("[DragDrop] Cleaned up " + removed + " expired drag sessions");
        }
    }

    /**
     * Limpa todas as sessões (usado no shutdown).
     */
    public void shutdown() {
        // Cancel cleanup task
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        int size = activeDragSessions.size();
        activeDragSessions.clear();

        if (debug && size > 0) {
            logger.info("[DragDrop] Cleared " + size + " active drag sessions on shutdown");
        }
    }

    /**
     * Retorna estatísticas de drag sessions.
     *
     * @return Map com estatísticas
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("active_sessions", activeDragSessions.size());

        // Count expired sessions (without removing them)
        long expired = activeDragSessions.values().stream()
            .filter(DragSession::isExpired)
            .count();
        stats.put("expired_sessions", expired);

        return stats;
    }
}
