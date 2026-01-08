package com.afterlands.core.protocol.impl;

import com.afterlands.core.spatial.ChunkKey;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Debouncer/batcher para chunks sujos por jogador.
 * 
 * <p>
 * Coalesce múltiplos marks de chunk em uma única aplicação após janela de
 * tempo.
 * </p>
 * 
 * <p>
 * Inspirado no pattern do AfterBlockState StateApplicationScheduler.
 * </p>
 */
public final class ChunkDebounceBatcher {

    private final Plugin plugin;
    private final Logger logger;
    private final long batchIntervalMs;
    private final int maxChunksPerBatch;
    private final boolean debug;

    // player UUID -> set of dirty chunk keys (world|chunkKey)
    private final Map<UUID, Set<DirtyChunk>> dirtyChunks = new ConcurrentHashMap<>();

    // player UUID -> scheduled task ID (-1 = none)
    private final Map<UUID, Integer> scheduledTasks = new ConcurrentHashMap<>();

    // Métricas
    private final AtomicLong chunksQueued = new AtomicLong();
    private final AtomicLong batchesProcessed = new AtomicLong();

    public ChunkDebounceBatcher(@NotNull Plugin plugin,
            long batchIntervalMs,
            int maxChunksPerBatch,
            boolean debug) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.batchIntervalMs = batchIntervalMs;
        this.maxChunksPerBatch = maxChunksPerBatch;
        this.debug = debug;
    }

    /**
     * Marcar um chunk como "dirty" para um jogador.
     * Agendará aplicação após a janela de debounce.
     * 
     * @param player    Jogador
     * @param world     Nome do mundo
     * @param chunkX    Coordenada X do chunk
     * @param chunkZ    Coordenada Z do chunk
     * @param processor Callback que será chamado com (player, lista de chunks)
     */
    public void markDirty(@NotNull Player player,
            @NotNull String world,
            int chunkX,
            int chunkZ,
            @NotNull BiConsumer<Player, List<DirtyChunk>> processor) {
        UUID playerId = player.getUniqueId();

        Set<DirtyChunk> chunks = dirtyChunks.computeIfAbsent(playerId,
                k -> ConcurrentHashMap.newKeySet());

        DirtyChunk dirty = new DirtyChunk(world, chunkX, chunkZ, System.currentTimeMillis());
        chunks.add(dirty);
        chunksQueued.incrementAndGet();

        if (debug) {
            logger.info(
                    "[Batcher] Marked dirty: " + world + " [" + chunkX + "," + chunkZ + "] for " + player.getName());
        }

        // Agendar processamento se ainda não agendado
        scheduledTasks.computeIfAbsent(playerId, k -> {
            long delayTicks = Math.max(1, batchIntervalMs / 50);
            return Bukkit.getScheduler().runTaskLater(plugin, () -> {
                processBatch(player, processor);
            }, delayTicks).getTaskId();
        });
    }

    /**
     * Processar batch de chunks para um jogador.
     */
    private void processBatch(@NotNull Player player,
            @NotNull BiConsumer<Player, List<DirtyChunk>> processor) {
        UUID playerId = player.getUniqueId();
        scheduledTasks.remove(playerId);

        if (!player.isOnline()) {
            dirtyChunks.remove(playerId);
            return;
        }

        Set<DirtyChunk> chunks = dirtyChunks.remove(playerId);
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        // Converter para lista e ordenar por distância ao jogador
        List<DirtyChunk> chunkList = new ArrayList<>(chunks);
        int playerChunkX = player.getLocation().getBlockX() >> 4;
        int playerChunkZ = player.getLocation().getBlockZ() >> 4;

        chunkList.sort(
                Comparator.comparingInt(c -> Math.abs(c.chunkX - playerChunkX) + Math.abs(c.chunkZ - playerChunkZ)));

        // Limitar tamanho do batch
        if (chunkList.size() > maxChunksPerBatch) {
            // Re-queue chunks excedentes
            List<DirtyChunk> excess = chunkList.subList(maxChunksPerBatch, chunkList.size());
            Set<DirtyChunk> remaining = dirtyChunks.computeIfAbsent(playerId,
                    k -> ConcurrentHashMap.newKeySet());
            remaining.addAll(excess);

            // Agendar próximo batch
            long delayTicks = Math.max(1, batchIntervalMs / 50);
            scheduledTasks.put(playerId,
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        processBatch(player, processor);
                    }, delayTicks).getTaskId());

            chunkList = chunkList.subList(0, maxChunksPerBatch);
        }

        if (debug) {
            logger.info("[Batcher] Processing batch of " + chunkList.size() +
                    " chunks for " + player.getName());
        }

        batchesProcessed.incrementAndGet();
        processor.accept(player, chunkList);
    }

    /**
     * Cancelar pending batches para um jogador (ex: no quit).
     */
    public void cancelForPlayer(@NotNull UUID playerId) {
        dirtyChunks.remove(playerId);
        Integer taskId = scheduledTasks.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    /**
     * Limpar tudo no shutdown.
     */
    public void shutdown() {
        for (Integer taskId : scheduledTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        scheduledTasks.clear();
        dirtyChunks.clear();
    }

    public long getChunksQueued() {
        return chunksQueued.get();
    }

    public long getBatchesProcessed() {
        return batchesProcessed.get();
    }

    /**
     * Representa um chunk marcado como dirty.
     */
    public record DirtyChunk(
            @NotNull String world,
            int chunkX,
            int chunkZ,
            long markedAt) {
        public long chunkKey() {
            return ChunkKey.pack(chunkX, chunkZ);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof DirtyChunk that))
                return false;
            return chunkX == that.chunkX &&
                    chunkZ == that.chunkZ &&
                    world.equalsIgnoreCase(that.world);
        }

        @Override
        public int hashCode() {
            return Objects.hash(world.toLowerCase(Locale.ROOT), chunkX, chunkZ);
        }
    }
}
