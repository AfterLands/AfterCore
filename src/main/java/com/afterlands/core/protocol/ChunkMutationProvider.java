package com.afterlands.core.protocol;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Provider para alterações de bloco por chunk (pipeline MAP_CHUNK).
 */
public interface ChunkMutationProvider {

    @NotNull String id();

    /**
     * Maior = aplicado por último (último ganha em conflito).
     */
    int priority();

    /**
     * Calcula mutations para um chunk específico.
     *
     * <p>Regra: não bloquear main thread. Se precisar de I/O, retornar lista vazia e agendar update depois.</p>
     */
    @NotNull List<BlockMutation> mutationsForChunk(@NotNull Player player, @NotNull World world, int chunkX, int chunkZ);
}

