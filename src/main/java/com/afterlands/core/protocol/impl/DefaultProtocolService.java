package com.afterlands.core.protocol.impl;

import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.metrics.MetricsService;
import com.afterlands.core.protocol.BlockMutation;
import com.afterlands.core.protocol.ChunkMutationProvider;
import com.afterlands.core.protocol.ProtocolService;
import com.afterlands.core.protocol.ProtocolStats;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.ChunkCoordIntPair;
import com.comphenix.protocol.wrappers.MultiBlockChangeInfo;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Implementação completa do ProtocolService com pipeline MAP_CHUNK.
 *
 * <p>
 * Features:
 * <ul>
 * <li>Listener único para MAP_CHUNK e MAP_CHUNK_BULK</li>
 * <li>Debounce/batching por player</li>
 * <li>Merge determinístico de mutations (último por prioridade ganha)</li>
 * <li>Applier via MULTI_BLOCK_CHANGE</li>
 * <li>Métricas integradas</li>
 * </ul>
 * </p>
 */
public final class DefaultProtocolService implements ProtocolService, Listener {
    private final Plugin plugin;
    private final Logger logger;
    private final SchedulerService scheduler;
    private final MetricsService metrics;
    private final boolean debug;

    // Config
    private final long batchIntervalMs;
    private final int maxChunksPerBatch;

    // Providers ordenados por prioridade (menor -> maior)
    private final List<ChunkMutationProvider> providers = new CopyOnWriteArrayList<>();

    // Pipeline components
    private ChunkDebounceBatcher batcher;
    private ChunkMutationMerger merger;
    private PacketAdapter chunkPacketListener;
    private ProtocolManager protocolManager;

    // State
    private volatile boolean started;
    private volatile boolean protocolLibAvailable;

    // Métricas
    private final AtomicLong chunksProcessed = new AtomicLong();
    private final AtomicLong packetsQueued = new AtomicLong();

    public DefaultProtocolService(@NotNull Plugin plugin,
            @NotNull SchedulerService scheduler,
            @NotNull MetricsService metrics,
            boolean debug,
            long batchIntervalMs,
            int maxChunksPerBatch) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.scheduler = scheduler;
        this.metrics = metrics;
        this.debug = debug;
        this.batchIntervalMs = batchIntervalMs;
        this.maxChunksPerBatch = maxChunksPerBatch;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void start() {
        if (started)
            return;
        started = true;

        // Check ProtocolLib availability
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            logger.info("[AfterCore] ProtocolLib não encontrado (ProtocolService desativado).");
            protocolLibAvailable = false;
            return;
        }

        protocolLibAvailable = true;
        protocolManager = ProtocolLibrary.getProtocolManager();

        // Initialize components
        batcher = new ChunkDebounceBatcher(plugin, batchIntervalMs, maxChunksPerBatch, debug);
        merger = new ChunkMutationMerger();

        // Register packet listener
        chunkPacketListener = new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.MAP_CHUNK,
                PacketType.Play.Server.MAP_CHUNK_BULK) {
            @Override
            public void onPacketSending(PacketEvent event) {
                handleChunkPacket(event);
            }
        };
        protocolManager.addPacketListener(chunkPacketListener);

        // Register Bukkit listener for cleanup
        Bukkit.getPluginManager().registerEvents(this, plugin);

        logger.info("[AfterCore] ProtocolService iniciado (pipeline MAP_CHUNK ativo).");
    }

    @Override
    public void stop() {
        if (!started)
            return;
        started = false;

        if (protocolLibAvailable && protocolManager != null && chunkPacketListener != null) {
            protocolManager.removePacketListener(chunkPacketListener);
        }

        if (batcher != null) {
            batcher.shutdown();
        }
    }

    @Override
    public void registerChunkProvider(@NotNull ChunkMutationProvider provider) {
        providers.add(provider);
        sortProviders();

        if (debug) {
            logger.info("[AfterCore] ChunkProvider registrado: " + provider.id() + " prio=" + provider.priority());
        }
    }

    @Override
    public boolean unregisterChunkProvider(@NotNull String id) {
        boolean removed = providers.removeIf(p -> p.id().equals(id));
        if (removed && debug) {
            logger.info("[AfterCore] ChunkProvider removido: " + id);
        }
        return removed;
    }

    @Override
    @NotNull
    public List<ChunkMutationProvider> getProviders() {
        return Collections.unmodifiableList(new ArrayList<>(providers));
    }

    @Override
    @NotNull
    public ProtocolStats getStats() {
        List<ProtocolStats.ProviderStat> providerStats = new ArrayList<>();

        if (merger != null) {
            for (ChunkMutationProvider provider : providers) {
                providerStats.add(new ProtocolStats.ProviderStat(
                        provider.id(),
                        provider.priority(),
                        merger.getMutationsForProvider(provider.id()),
                        merger.getConflictsForProvider(provider.id())));
            }
        }

        return new ProtocolStats(
                providers.size(),
                chunksProcessed.get(),
                merger != null ? merger.getTotalMutations() : 0,
                merger != null ? merger.getTotalConflicts() : 0,
                packetsQueued.get(),
                providerStats);
    }

    // --- Private methods ---

    private void sortProviders() {
        List<ChunkMutationProvider> sorted = new ArrayList<>(providers);
        sorted.sort(Comparator.comparingInt(ChunkMutationProvider::priority));
        providers.clear();
        providers.addAll(sorted);
    }

    @SuppressWarnings("deprecation")
    private void handleChunkPacket(PacketEvent event) {
        if (event.isCancelled())
            return;
        if (providers.isEmpty())
            return;

        Player player = event.getPlayer();
        if (player == null || !player.isOnline())
            return;

        PacketType type = event.getPacketType();

        try {
            if (type == PacketType.Play.Server.MAP_CHUNK_BULK) {
                handleMapChunkBulk(event, player);
            } else if (type == PacketType.Play.Server.MAP_CHUNK) {
                handleMapChunk(event, player);
            }
        } catch (Exception e) {
            logger.warning("[AfterCore] Erro ao processar chunk packet: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
        }
    }

    private void handleMapChunk(PacketEvent event, Player player) {
        int chunkX = event.getPacket().getIntegers().read(0);
        int chunkZ = event.getPacket().getIntegers().read(1);
        String world = player.getWorld().getName();

        if (debug) {
            logger.info("[Protocol] MAP_CHUNK [" + chunkX + "," + chunkZ + "] -> " + player.getName());
        }

        queueChunk(player, world, chunkX, chunkZ);
    }

    @SuppressWarnings("deprecation")
    private void handleMapChunkBulk(PacketEvent event, Player player) {
        try {
            int[] chunkXArray = event.getPacket().getIntegerArrays().read(0);
            int[] chunkZArray = event.getPacket().getIntegerArrays().read(1);

            if (chunkXArray == null || chunkZArray == null) {
                return;
            }

            String world = player.getWorld().getName();

            if (debug) {
                logger.info("[Protocol] MAP_CHUNK_BULK " + chunkXArray.length + " chunks -> " + player.getName());
            }

            for (int i = 0; i < chunkXArray.length && i < chunkZArray.length; i++) {
                queueChunk(player, world, chunkXArray[i], chunkZArray[i]);
            }
        } catch (Exception e) {
            if (debug) {
                logger.warning("[Protocol] Erro reading MAP_CHUNK_BULK: " + e.getMessage());
            }
        }
    }

    private void queueChunk(Player player, String world, int chunkX, int chunkZ) {
        packetsQueued.incrementAndGet();
        batcher.markDirty(player, world, chunkX, chunkZ, this::processBatch);
    }

    private void processBatch(Player player, List<ChunkDebounceBatcher.DirtyChunk> chunks) {
        if (!player.isOnline() || providers.isEmpty())
            return;

        World world = player.getWorld();

        for (ChunkDebounceBatcher.DirtyChunk chunk : chunks) {
            // Só processar chunks do mundo atual do player
            if (!chunk.world().equalsIgnoreCase(world.getName()))
                continue;

            chunksProcessed.incrementAndGet();
            metrics.increment("protocol.chunks_processed");

            // Merge mutations de todos providers
            List<BlockMutation> mutations = merger.merge(
                    providers, player, world, chunk.chunkX(), chunk.chunkZ());

            if (mutations.isEmpty())
                continue;

            // Aplicar via MULTI_BLOCK_CHANGE
            sendMultiBlockChange(player, chunk.chunkX(), chunk.chunkZ(), mutations);
        }
    }

    @SuppressWarnings("deprecation")
    private void sendMultiBlockChange(Player player, int chunkX, int chunkZ, List<BlockMutation> mutations) {
        if (!protocolLibAvailable || protocolManager == null)
            return;

        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.MULTI_BLOCK_CHANGE);

        // Set chunk coordinates
        packet.getChunkCoordIntPairs().write(0, new ChunkCoordIntPair(chunkX, chunkZ));

        // Build MultiBlockChangeInfo array
        MultiBlockChangeInfo[] changes = new MultiBlockChangeInfo[mutations.size()];
        for (int i = 0; i < mutations.size(); i++) {
            BlockMutation m = mutations.get(i);
            Material material = Material.getMaterial(m.blockId());
            if (material == null)
                material = Material.AIR;

            WrappedBlockData blockData = WrappedBlockData.createData(material, m.blockData());
            changes[i] = new MultiBlockChangeInfo(
                    new Location(null, m.x(), m.y(), m.z()),
                    blockData);
        }

        packet.getMultiBlockChangeInfoArrays().write(0, changes);

        try {
            // filters=false to avoid processing our own packet
            protocolManager.sendServerPacket(player, packet, false);
            metrics.increment("protocol.packets_sent");

            if (debug) {
                logger.info("[Protocol] Sent " + mutations.size() + " blocks to " +
                        player.getName() + " at [" + chunkX + "," + chunkZ + "]");
            }
        } catch (Exception e) {
            logger.warning("[AfterCore] Falha ao enviar MULTI_BLOCK_CHANGE: " + e.getMessage());
        }
    }

    // --- Event handlers ---

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (batcher != null) {
            batcher.cancelForPlayer(event.getPlayer().getUniqueId());
        }
    }
}
