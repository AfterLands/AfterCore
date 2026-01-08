package com.afterlands.core.protocol;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Centraliza a integração com ProtocolLib e expõe um pipeline de modificações.
 *
 * <p>
 * Fase inicial: estrutura + registro; implementação completa do pipeline vem em
 * releases posteriores.
 * </p>
 */
public interface ProtocolService {

    void start();

    void stop();

    void registerChunkProvider(@NotNull ChunkMutationProvider provider);

    /**
     * Remove um provider do pipeline.
     * 
     * @param id ID do provider a remover
     * @return true se foi removido, false se não existia
     */
    boolean unregisterChunkProvider(@NotNull String id);

    /**
     * Retorna lista ordenada de providers (por prioridade ascendente).
     */
    @NotNull
    List<ChunkMutationProvider> getProviders();

    /**
     * Retorna snapshot das estatísticas do pipeline.
     */
    @NotNull
    ProtocolStats getStats();
}
