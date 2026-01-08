package com.afterlands.core.inventory.shared;

import com.afterlands.core.inventory.InventoryContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contexto compartilhado de um inventário acessado por múltiplos players.
 *
 * <p>Record imutável que encapsula o estado de uma sessão compartilhada.
 * Usa copy-on-write para atualizações de sharedData.</p>
 *
 * <p><b>Thread Safety:</b> Imutável (record), sharedData usa ConcurrentHashMap.</p>
 *
 * @param sessionId ID único da sessão compartilhada
 * @param playerIds Lista de UUIDs dos players participantes
 * @param baseContext Contexto base (placeholders, variáveis)
 * @param sharedData Dados compartilhados (thread-safe)
 * @param createdAt Timestamp de criação da sessão
 */
public record SharedInventoryContext(
        @NotNull String sessionId,
        @NotNull List<UUID> playerIds,
        @NotNull InventoryContext baseContext,
        @NotNull ConcurrentHashMap<String, Object> sharedData,
        @NotNull Instant createdAt
) {

    /**
     * Construtor compacto com validação.
     */
    public SharedInventoryContext {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be null or blank");
        }
        if (playerIds == null) {
            playerIds = new ArrayList<>();
        }
        if (baseContext == null) {
            throw new IllegalArgumentException("baseContext cannot be null");
        }
        if (sharedData == null) {
            sharedData = new ConcurrentHashMap<>();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Cria novo contexto com dado compartilhado atualizado (copy-on-write).
     *
     * @param key Chave do dado
     * @param value Valor
     * @return Novo contexto com dado atualizado
     */
    @NotNull
    public SharedInventoryContext withSharedData(@NotNull String key, @NotNull Object value) {
        ConcurrentHashMap<String, Object> newSharedData = new ConcurrentHashMap<>(sharedData);
        newSharedData.put(key, value);
        return new SharedInventoryContext(sessionId, playerIds, baseContext, newSharedData, createdAt);
    }

    /**
     * Obtém dado compartilhado.
     *
     * @param key Chave do dado
     * @return Optional com valor (vazio se não existir)
     */
    @NotNull
    public Optional<Object> getSharedData(@NotNull String key) {
        return Optional.ofNullable(sharedData.get(key));
    }

    /**
     * Atualiza sharedData in-place (muta o map, mas record continua imutável).
     *
     * <p>Útil para updates que não requerem nova instância do record.</p>
     *
     * @param key Chave
     * @param value Valor
     */
    public void putSharedData(@NotNull String key, @NotNull Object value) {
        sharedData.put(key, value);
    }

    /**
     * Remove dado compartilhado in-place.
     *
     * @param key Chave
     */
    public void removeSharedData(@NotNull String key) {
        sharedData.remove(key);
    }

    /**
     * Verifica se contém player.
     *
     * @param playerId UUID do player
     * @return true se player está na sessão
     */
    public boolean containsPlayer(@NotNull UUID playerId) {
        return playerIds.contains(playerId);
    }

    /**
     * Obtém número de players na sessão.
     *
     * @return Quantidade de players
     */
    public int getPlayerCount() {
        return playerIds.size();
    }

    /**
     * Cria nova instância com player adicionado (copy-on-write).
     *
     * @param playerId UUID do player
     * @return Novo contexto com player adicionado
     */
    @NotNull
    public SharedInventoryContext withPlayer(@NotNull UUID playerId) {
        if (playerIds.contains(playerId)) {
            return this; // Já está na lista
        }

        List<UUID> newPlayerIds = new ArrayList<>(playerIds);
        newPlayerIds.add(playerId);
        return new SharedInventoryContext(sessionId, newPlayerIds, baseContext, sharedData, createdAt);
    }

    /**
     * Cria nova instância com player removido (copy-on-write).
     *
     * @param playerId UUID do player
     * @return Novo contexto com player removido
     */
    @NotNull
    public SharedInventoryContext withoutPlayer(@NotNull UUID playerId) {
        if (!playerIds.contains(playerId)) {
            return this; // Não está na lista
        }

        List<UUID> newPlayerIds = new ArrayList<>(playerIds);
        newPlayerIds.remove(playerId);
        return new SharedInventoryContext(sessionId, newPlayerIds, baseContext, sharedData, createdAt);
    }

    /**
     * Cria contexto inicial.
     *
     * @param baseContext Contexto base
     * @param initialPlayers Players iniciais
     * @return Novo contexto compartilhado
     */
    @NotNull
    public static SharedInventoryContext create(
            @NotNull InventoryContext baseContext,
            @NotNull List<UUID> initialPlayers
    ) {
        String sessionId = UUID.randomUUID().toString();
        return new SharedInventoryContext(
                sessionId,
                new ArrayList<>(initialPlayers),
                baseContext,
                new ConcurrentHashMap<>(),
                Instant.now()
        );
    }

    /**
     * Cria contexto inicial com session ID customizado.
     *
     * @param sessionId ID da sessão
     * @param baseContext Contexto base
     * @param initialPlayers Players iniciais
     * @return Novo contexto compartilhado
     */
    @NotNull
    public static SharedInventoryContext createWithId(
            @NotNull String sessionId,
            @NotNull InventoryContext baseContext,
            @NotNull List<UUID> initialPlayers
    ) {
        return new SharedInventoryContext(
                sessionId,
                new ArrayList<>(initialPlayers),
                baseContext,
                new ConcurrentHashMap<>(),
                Instant.now()
        );
    }
}
