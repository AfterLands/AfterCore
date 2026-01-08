package com.afterlands.core.spatial;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Índice espacial genérico: lookup O(1) por (world, chunk).
 *
 * <p>
 * Design inspirado no `ChunkSpatialIndex` do AfterBlockState, porém desacoplado
 * de StateTarget.
 * </p>
 */
public final class ChunkSpatialIndex<T> {

    // world -> chunkKey -> list
    private final Map<String, Map<Long, List<T>>> worldIndex = new ConcurrentHashMap<>();

    /**
     * Registra um valor em um range de chunks (inclusive).
     */
    public void register(@NotNull String world, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
            @NotNull T value) {
        String w = world.toLowerCase(Locale.ROOT);
        Map<Long, List<T>> chunkIndex = worldIndex.computeIfAbsent(w, k -> new ConcurrentHashMap<>());
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long key = ChunkKey.pack(cx, cz);
                chunkIndex.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                        .add(value);
            }
        }
    }

    /**
     * Registra um valor em um único chunk.
     */
    public void register(@NotNull String world, int chunkX, int chunkZ, @NotNull T value) {
        register(world, chunkX, chunkX, chunkZ, chunkZ, value);
    }

    /**
     * Remove todos os valores que satisfazem o predicado em todos os mundos e
     * chunks.
     * 
     * @param predicate Predicado para identificar valores a remover
     * @return Número de valores removidos
     */
    public int unregister(@NotNull Predicate<T> predicate) {
        int removed = 0;
        for (Map<Long, List<T>> chunkIndex : worldIndex.values()) {
            Iterator<Map.Entry<Long, List<T>>> it = chunkIndex.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, List<T>> entry = it.next();
                List<T> list = entry.getValue();
                synchronized (list) {
                    int before = list.size();
                    list.removeIf(predicate);
                    removed += before - list.size();
                    if (list.isEmpty()) {
                        it.remove();
                    }
                }
            }
        }
        return removed;
    }

    /**
     * Remove um valor específico de um range de chunks.
     * 
     * @param world     Mundo
     * @param minChunkX Mínimo X do chunk (inclusive)
     * @param maxChunkX Máximo X do chunk (inclusive)
     * @param minChunkZ Mínimo Z do chunk (inclusive)
     * @param maxChunkZ Máximo Z do chunk (inclusive)
     * @param value     Valor a remover (usa equals para comparação)
     * @return true se pelo menos um valor foi removido
     */
    public boolean unregister(@NotNull String world, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
            @NotNull T value) {
        String w = world.toLowerCase(Locale.ROOT);
        Map<Long, List<T>> chunkIndex = worldIndex.get(w);
        if (chunkIndex == null)
            return false;

        boolean removed = false;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long key = ChunkKey.pack(cx, cz);
                List<T> list = chunkIndex.get(key);
                if (list != null) {
                    synchronized (list) {
                        if (list.remove(value)) {
                            removed = true;
                        }
                        if (list.isEmpty()) {
                            chunkIndex.remove(key);
                        }
                    }
                }
            }
        }
        return removed;
    }

    /**
     * Remove um valor específico de um único chunk.
     */
    public boolean unregister(@NotNull String world, int chunkX, int chunkZ, @NotNull T value) {
        return unregister(world, chunkX, chunkX, chunkZ, chunkZ, value);
    }

    /**
     * Limpa tudo.
     */
    public void clear() {
        worldIndex.clear();
    }

    @NotNull
    public List<T> getInChunk(@NotNull String world, int chunkX, int chunkZ) {
        Map<Long, List<T>> chunkIndex = worldIndex.get(world.toLowerCase(Locale.ROOT));
        if (chunkIndex == null)
            return Collections.emptyList();

        List<T> list = chunkIndex.get(ChunkKey.pack(chunkX, chunkZ));
        if (list == null)
            return Collections.emptyList();
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    /**
     * Verifica se um chunk contém valores.
     * 
     * @param world  Nome do mundo
     * @param chunkX Coordenada X do chunk
     * @param chunkZ Coordenada Z do chunk
     * @return true se o chunk contém pelo menos um valor
     */
    public boolean hasInChunk(@NotNull String world, int chunkX, int chunkZ) {
        Map<Long, List<T>> chunkIndex = worldIndex.get(world.toLowerCase(Locale.ROOT));
        if (chunkIndex == null)
            return false;

        List<T> list = chunkIndex.get(ChunkKey.pack(chunkX, chunkZ));
        return list != null && !list.isEmpty();
    }

    @NotNull
    public Set<Long> getIndexedChunks(@NotNull String world) {
        Map<Long, List<T>> chunkIndex = worldIndex.get(world.toLowerCase(Locale.ROOT));
        if (chunkIndex == null)
            return Collections.emptySet();
        return new HashSet<>(chunkIndex.keySet());
    }

    /**
     * Retorna todos os mundos que têm chunks indexados.
     */
    @NotNull
    public Set<String> getIndexedWorlds() {
        return new HashSet<>(worldIndex.keySet());
    }

    /**
     * Retorna o número total de chunks indexados (soma de todos os mundos).
     */
    public int getTotalIndexedChunks() {
        return worldIndex.values().stream()
                .mapToInt(Map::size)
                .sum();
    }

    /**
     * Retorna o número total de associações (valor-chunk).
     * Um valor registrado em N chunks conta N vezes.
     */
    public int getTotalAssociations() {
        return worldIndex.values().stream()
                .flatMap(m -> m.values().stream())
                .mapToInt(List::size)
                .sum();
    }

    /**
     * Retorna estatísticas do índice para diagnóstico.
     */
    @NotNull
    public IndexStats getStats() {
        return new IndexStats(
                getIndexedWorlds().size(),
                getTotalIndexedChunks(),
                getTotalAssociations());
    }

    /**
     * Estatísticas do índice espacial.
     */
    public record IndexStats(int worlds, int chunks, int associations) {
    }
}
