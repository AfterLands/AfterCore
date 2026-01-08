package com.afterlands.core.inventory.animation;

import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.inventory.InventoryContext;
import com.afterlands.core.inventory.item.GuiItem;
import com.afterlands.core.inventory.item.ItemCompiler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Motor de animações de items em inventários.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *     <li>Gerenciar animações ativas em todos os inventários</li>
 *     <li>Tick periódico para avançar frames</li>
 *     <li>Atualizar items nos inventários na main thread</li>
 *     <li>Cleanup de animações órfãs</li>
 *     <li>Batch updates para performance</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> Thread-safe via ConcurrentHashMap.
 * Updates de inventário executam SEMPRE na main thread.</p>
 *
 * <p><b>Performance:</b></p>
 * <ul>
 *     <li>Tick rate configurável (default: 1 tick = 50ms)</li>
 *     <li>Batch updates: múltiplos items por tick</li>
 *     <li>Rate limiting: max 50 items/tick</li>
 *     <li>Skip updates se player offline ou inventário fechado</li>
 * </ul>
 */
public class InventoryAnimator {

    private final SchedulerService scheduler;
    private final ItemCompiler itemCompiler;
    private final Logger logger;
    private final boolean debug;

    // Animações ativas: key = animationId
    private final Map<String, ActiveAnimation> activeAnimations;

    // Tick task
    private BukkitTask animationTask;

    // Configurações
    private final long tickInterval; // ticks entre cada update
    private final int maxUpdatesPerTick; // max items atualizados por tick

    // Estatísticas
    private long totalFrameUpdates = 0;
    private long totalSkippedUpdates = 0;

    /**
     * Cria animator com configuração padrão.
     *
     * @param scheduler Scheduler service
     * @param itemCompiler Item compiler
     * @param logger Logger
     * @param debug Habilita debug logging
     */
    public InventoryAnimator(
            @NotNull SchedulerService scheduler,
            @NotNull ItemCompiler itemCompiler,
            @NotNull Logger logger,
            boolean debug
    ) {
        this(scheduler, itemCompiler, logger, debug, 1L, 50);
    }

    /**
     * Cria animator com configuração customizada.
     *
     * @param scheduler Scheduler service
     * @param itemCompiler Item compiler
     * @param logger Logger
     * @param debug Habilita debug logging
     * @param tickInterval Ticks entre cada update (1 = 50ms)
     * @param maxUpdatesPerTick Max items atualizados por tick
     */
    public InventoryAnimator(
            @NotNull SchedulerService scheduler,
            @NotNull ItemCompiler itemCompiler,
            @NotNull Logger logger,
            boolean debug,
            long tickInterval,
            int maxUpdatesPerTick
    ) {
        this.scheduler = scheduler;
        this.itemCompiler = itemCompiler;
        this.logger = logger;
        this.debug = debug;
        this.tickInterval = tickInterval;
        this.maxUpdatesPerTick = maxUpdatesPerTick;
        this.activeAnimations = new ConcurrentHashMap<>();
    }

    /**
     * Inicia animação para um item específico.
     *
     * @param inventoryId ID do inventário
     * @param playerId UUID do player
     * @param slot Slot do item
     * @param animation Configuração da animação
     */
    public void startAnimation(
            @NotNull String inventoryId,
            @NotNull UUID playerId,
            int slot,
            @NotNull AnimationConfig animation
    ) {
        ActiveAnimation active = ActiveAnimation.create(inventoryId, playerId, slot, animation);

        String key = active.getKey();
        activeAnimations.put(key, active);

        if (debug) {
            logger.fine("Started animation: " + animation.animationId() + " for " + playerId +
                    " at slot " + slot + " (total: " + activeAnimations.size() + ")");
        }
    }

    /**
     * Para animação específica.
     *
     * @param animationId ID único da animação ativa
     */
    public void stopAnimation(@NotNull String animationId) {
        ActiveAnimation removed = activeAnimations.remove(animationId);

        if (removed != null && debug) {
            logger.fine("Stopped animation: " + animationId);
        }
    }

    /**
     * Para todas as animações de um inventário específico.
     *
     * @param inventoryId ID do inventário
     * @param playerId UUID do player
     */
    public void stopAllAnimations(@NotNull String inventoryId, @NotNull UUID playerId) {
        String prefix = inventoryId + ":" + playerId + ":";

        List<String> toRemove = activeAnimations.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .collect(Collectors.toList());

        toRemove.forEach(activeAnimations::remove);

        if (debug && !toRemove.isEmpty()) {
            logger.fine("Stopped " + toRemove.size() + " animations for " + playerId +
                    " in inventory " + inventoryId);
        }
    }

    /**
     * Para todas as animações de um player (ao deslogar).
     *
     * @param playerId UUID do player
     */
    public void stopAllAnimations(@NotNull UUID playerId) {
        String playerStr = ":" + playerId + ":";

        List<String> toRemove = activeAnimations.keySet().stream()
                .filter(key -> key.contains(playerStr))
                .collect(Collectors.toList());

        toRemove.forEach(activeAnimations::remove);

        if (debug && !toRemove.isEmpty()) {
            logger.fine("Stopped " + toRemove.size() + " animations for player " + playerId);
        }
    }

    /**
     * Tick do motor de animações.
     *
     * <p>Executado periodicamente (a cada tickInterval ticks).</p>
     * <p>Avança frames e atualiza items nos inventários.</p>
     */
    public void tick() {
        if (activeAnimations.isEmpty()) {
            return;
        }

        long currentTick = getCurrentTick();
        List<ActiveAnimation> toUpdate = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();

        // 1. Identifica animações que precisam atualizar
        for (Map.Entry<String, ActiveAnimation> entry : activeAnimations.entrySet()) {
            ActiveAnimation animation = entry.getValue();

            // Verifica se player ainda está online
            Player player = Bukkit.getPlayer(animation.playerId());
            if (player == null || !player.isOnline()) {
                toRemove.add(entry.getKey());
                totalSkippedUpdates++;
                continue;
            }

            // Verifica se deve atualizar
            if (animation.shouldUpdate(currentTick)) {
                toUpdate.add(animation);
            }

            // Verifica se terminou (non-loop)
            if (animation.isFinished()) {
                toRemove.add(entry.getKey());
            }
        }

        // 2. Remove animações finalizadas/órfãs
        toRemove.forEach(activeAnimations::remove);

        if (debug && !toRemove.isEmpty()) {
            logger.fine("Removed " + toRemove.size() + " finished/orphan animations");
        }

        // 3. Rate limiting: max updates por tick
        if (toUpdate.size() > maxUpdatesPerTick) {
            if (debug) {
                logger.warning("Rate limiting: " + toUpdate.size() + " updates, limiting to " + maxUpdatesPerTick);
            }
            toUpdate = toUpdate.subList(0, maxUpdatesPerTick);
        }

        // 4. Batch update (main thread)
        if (!toUpdate.isEmpty()) {
            batchUpdateAnimations(toUpdate, currentTick);
        }
    }

    /**
     * Batch update de animações.
     *
     * <p>Agrupa updates e executa na main thread.</p>
     *
     * @param animations Animações a atualizar
     * @param currentTick Tick atual
     */
    private void batchUpdateAnimations(@NotNull List<ActiveAnimation> animations, long currentTick) {
        // Agrupa por player para otimizar
        Map<UUID, List<ActiveAnimation>> byPlayer = animations.stream()
                .collect(Collectors.groupingBy(ActiveAnimation::playerId));

        // Executa update na main thread
        scheduler.runSync(() -> {
            for (Map.Entry<UUID, List<ActiveAnimation>> entry : byPlayer.entrySet()) {
                UUID playerId = entry.getKey();
                List<ActiveAnimation> playerAnimations = entry.getValue();

                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    continue;
                }

                Inventory inventory = player.getOpenInventory().getTopInventory();
                if (inventory == null) {
                    continue;
                }

                // Atualiza cada animação
                for (ActiveAnimation animation : playerAnimations) {
                    updateAnimation(player, inventory, animation, currentTick);
                }
            }
        });
    }

    /**
     * Atualiza uma animação específica.
     *
     * <p><b>CRITICAL: Deve rodar na main thread.</b></p>
     *
     * @param player Player
     * @param inventory Inventário aberto
     * @param animation Animação a atualizar
     * @param currentTick Tick atual
     */
    private void updateAnimation(
            @NotNull Player player,
            @NotNull Inventory inventory,
            @NotNull ActiveAnimation animation,
            long currentTick
    ) {
        try {
            // Avança frame
            ActiveAnimation advanced = animation.advanceFrame();

            // Atualiza no map
            activeAnimations.put(advanced.getKey(), advanced);

            // Obtém frame atual
            AnimationFrame frame = advanced.getCurrentFrameData();

            // Converte frame para GuiItem
            GuiItem frameItem = frame.toGuiItem(animation.slot());

            // Compila item (sem cache para frames de animação)
            InventoryContext context = new InventoryContext(animation.playerId(), animation.inventoryId());

            // Compila e aplica ao inventário
            itemCompiler.compile(frameItem, player, context).thenAccept(itemStack -> {
                if (animation.slot() >= 0 && animation.slot() < inventory.getSize()) {
                    inventory.setItem(animation.slot(), itemStack);
                    totalFrameUpdates++;
                }
            });

        } catch (Exception e) {
            logger.warning("Failed to update animation " + animation.animationId() + ": " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Inicia o scheduler de animações.
     *
     * <p>Chama {@link #tick()} a cada N ticks (configurável).</p>
     */
    public void start() {
        if (animationTask != null) {
            logger.warning("InventoryAnimator already started");
            return;
        }

        animationTask = Bukkit.getScheduler().runTaskTimer(
                scheduler.plugin(),
                this::tick,
                tickInterval,        // Delay inicial
                tickInterval         // Intervalo
        );

        logger.info("InventoryAnimator started (tick interval: " + tickInterval + "t)");
    }

    /**
     * Para o scheduler e limpa todas animações.
     */
    public void shutdown() {
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }

        int count = activeAnimations.size();
        activeAnimations.clear();

        logger.info("InventoryAnimator stopped (" + count + " animations cleared)");
        logger.info("Stats: " + totalFrameUpdates + " updates, " + totalSkippedUpdates + " skipped");
    }

    /**
     * Obtém animações ativas para um inventário.
     *
     * @param inventoryId ID do inventário
     * @param playerId UUID do player
     * @return Lista de animações ativas
     */
    @NotNull
    public List<ActiveAnimation> getActiveAnimations(@NotNull String inventoryId, @NotNull UUID playerId) {
        String prefix = inventoryId + ":" + playerId + ":";

        return activeAnimations.values().stream()
                .filter(anim -> anim.getKey().startsWith(prefix))
                .collect(Collectors.toList());
    }

    /**
     * Obtém número de animações ativas.
     *
     * @return Contagem de animações
     */
    public int getActiveCount() {
        return activeAnimations.size();
    }

    /**
     * Obtém estatísticas do animator.
     *
     * @return String formatada com stats
     */
    @NotNull
    public String getStats() {
        return String.format(
                "InventoryAnimator Stats:\n" +
                "  Active: %d animations\n" +
                "  Total Updates: %d\n" +
                "  Skipped Updates: %d\n" +
                "  Tick Interval: %dt (%.1fms)",
                activeAnimations.size(),
                totalFrameUpdates,
                totalSkippedUpdates,
                tickInterval,
                tickInterval * 50.0
        );
    }

    /**
     * Obtém tick atual do servidor.
     *
     * @return Tick count atual
     */
    private long getCurrentTick() {
        try {
            return Bukkit.getServer().getWorlds().get(0).getFullTime();
        } catch (Exception e) {
            return System.currentTimeMillis() / 50L;
        }
    }
}
