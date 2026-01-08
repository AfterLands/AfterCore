package com.afterlands.core.protocol.impl;

import com.afterlands.core.protocol.BlockMutation;
import com.afterlands.core.protocol.BlockPosKey;
import com.afterlands.core.protocol.ChunkMutationProvider;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Merge determinístico de mutations de múltiplos providers.
 * 
 * <p>
 * Regra: "último ganha" — providers são processados em ordem de prioridade
 * (ascendente),
 * então maior prioridade sobrescreve menor.
 * </p>
 */
public final class ChunkMutationMerger {

    // Métricas por provider
    private final Map<String, AtomicLong> mutationsByProvider = new HashMap<>();
    private final Map<String, AtomicLong> conflictsByProvider = new HashMap<>();
    private final AtomicLong totalConflicts = new AtomicLong();
    private final AtomicLong totalMutations = new AtomicLong();

    /**
     * Merge mutations de todos os providers para um chunk.
     * 
     * @param providers Lista de providers (já ordenada por prioridade ascendente)
     * @param player    Jogador que receberá as mutations
     * @param world     Mundo do chunk
     * @param chunkX    Coordenada X do chunk
     * @param chunkZ    Coordenada Z do chunk
     * @return Lista de mutations merged (sem duplicatas de posição)
     */
    @NotNull
    public List<BlockMutation> merge(@NotNull List<ChunkMutationProvider> providers,
            @NotNull Player player,
            @NotNull World world,
            int chunkX,
            int chunkZ) {
        if (providers.isEmpty()) {
            return Collections.emptyList();
        }

        // Map de posição -> mutation (último ganha)
        Map<Long, BlockMutation> merged = new HashMap<>();

        for (ChunkMutationProvider provider : providers) {
            List<BlockMutation> mutations = provider.mutationsForChunk(player, world, chunkX, chunkZ);

            if (mutations.isEmpty()) {
                continue;
            }

            // Track métricas
            String providerId = provider.id();
            mutationsByProvider.computeIfAbsent(providerId, k -> new AtomicLong())
                    .addAndGet(mutations.size());
            totalMutations.addAndGet(mutations.size());

            for (BlockMutation mutation : mutations) {
                BlockPosKey key = new BlockPosKey(mutation.x(), mutation.y(), mutation.z());
                long packedKey = key.packed();

                BlockMutation existing = merged.put(packedKey, mutation);
                if (existing != null) {
                    // Conflito detectado
                    conflictsByProvider.computeIfAbsent(providerId, k -> new AtomicLong())
                            .incrementAndGet();
                    totalConflicts.incrementAndGet();
                }
            }
        }

        return new ArrayList<>(merged.values());
    }

    /**
     * Obter métricas de um provider específico.
     */
    public long getMutationsForProvider(@NotNull String providerId) {
        AtomicLong counter = mutationsByProvider.get(providerId);
        return counter != null ? counter.get() : 0;
    }

    public long getConflictsForProvider(@NotNull String providerId) {
        AtomicLong counter = conflictsByProvider.get(providerId);
        return counter != null ? counter.get() : 0;
    }

    public long getTotalConflicts() {
        return totalConflicts.get();
    }

    public long getTotalMutations() {
        return totalMutations.get();
    }

    /**
     * Reset todas as métricas.
     */
    public void resetMetrics() {
        mutationsByProvider.clear();
        conflictsByProvider.clear();
        totalConflicts.set(0);
        totalMutations.set(0);
    }

    /**
     * Obter IDs de todos os providers que já produziram mutations.
     */
    @NotNull
    public Set<String> getActiveProviderIds() {
        return new HashSet<>(mutationsByProvider.keySet());
    }
}
