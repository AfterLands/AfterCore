package com.afterlands.core.inventory;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Serviço principal de inventários do AfterCore.
 *
 * <p>Fornece APIs para gerenciamento de GUIs configuráveis via YAML,
 * com suporte a paginação, tabs, animações, drag-and-drop e persistência.</p>
 *
 * <p><b>Thread Safety:</b> Métodos de abertura de inventário devem ser chamados
 * na main thread. Operações de DB são sempre async.</p>
 *
 * <p><b>Performance:</b> Cache inteligente de itens estáticos, resolução async
 * de placeholders quando possível, batch updates para animações.</p>
 */
public interface InventoryService {

    /**
     * Abre um inventário para um player específico.
     *
     * <p>O inventário será carregado da configuração (inventories.yml)
     * e renderizado com os itens definidos. Se houver estado salvo no DB,
     * será carregado automaticamente.</p>
     *
     * <p><b>Thread:</b> MAIN THREAD ONLY</p>
     *
     * @param player Jogador alvo
     * @param inventoryId ID do inventário configurado em inventories.yml
     * @param context Dados de contexto (placeholders, estado inicial)
     * @throws IllegalArgumentException se inventoryId não existir
     * @throws IllegalStateException se chamado fora da main thread
     */
    void openInventory(@NotNull Player player, @NotNull String inventoryId, @NotNull InventoryContext context);

    /**
     * Abre um inventário compartilhado (mesmo estado para múltiplos players).
     *
     * <p>Todos os players verão o mesmo inventário e mudanças serão
     * refletidas em tempo real para todos. Útil para GUIs de leilão,
     * baús compartilhados, etc.</p>
     *
     * <p><b>Thread:</b> MAIN THREAD ONLY</p>
     *
     * <p><b>Performance:</b> Usa copy-on-write para evitar race conditions,
     * mas pode ter overhead maior que inventários individuais.</p>
     *
     * @param players Lista de jogadores que abrirão o inventário
     * @param inventoryId ID do inventário configurado
     * @param context Dados de contexto compartilhado
     * @return Context ID único para referenciar esta instância compartilhada
     * @throws IllegalArgumentException se inventoryId não existir ou lista vazia
     * @throws IllegalStateException se chamado fora da main thread
     */
    @NotNull
    String openSharedInventory(@NotNull List<Player> players, @NotNull String inventoryId, @NotNull InventoryContext context);

    /**
     * Fecha um inventário para um player.
     *
     * <p>Se o inventário tiver persistência habilitada, o estado
     * será salvo automaticamente no DB.</p>
     *
     * <p><b>Thread:</b> MAIN THREAD ONLY</p>
     *
     * @param player Jogador alvo
     */
    void closeInventory(@NotNull Player player);

    /**
     * Salva o estado de um inventário no banco de dados.
     *
     * <p>Operação assíncrona que não bloqueia a main thread.
     * Caso já exista estado salvo, será sobrescrito.</p>
     *
     * <p><b>Thread:</b> Async (CompletableFuture)</p>
     *
     * @param playerId UUID do jogador
     * @param inventoryId ID do inventário
     * @param state Estado serializável
     * @return CompletableFuture que completa quando salvo
     */
    @NotNull
    CompletableFuture<Void> saveInventoryState(@NotNull UUID playerId, @NotNull String inventoryId, @NotNull InventoryState state);

    /**
     * Carrega o estado de um inventário do banco de dados.
     *
     * <p>Operação assíncrona. Se não houver estado salvo,
     * retorna estado inicial vazio.</p>
     *
     * <p><b>Thread:</b> Async (CompletableFuture)</p>
     *
     * @param playerId UUID do jogador
     * @param inventoryId ID do inventário
     * @return CompletableFuture com o estado (ou estado inicial se não existir)
     */
    @NotNull
    CompletableFuture<InventoryState> loadInventoryState(@NotNull UUID playerId, @NotNull String inventoryId);

    /**
     * Recarrega as configurações de inventários.
     *
     * <p>Apenas novos inventários abertos usarão a nova configuração.
     * Inventários já abertos não são afetados.</p>
     *
     * <p><b>Thread:</b> Async (operação pode levar tempo)</p>
     *
     * @return CompletableFuture que completa quando reload finalizado
     */
    @NotNull
    CompletableFuture<Void> reloadConfigurations();

    /**
     * Limpa o cache de itens compilados.
     *
     * <p>Útil para forçar recompilação após mudanças em resource packs
     * ou PlaceholderAPI expansions.</p>
     *
     * <p><b>Thread:</b> Thread-safe</p>
     */
    void clearCache();

    /**
     * Verifica se um inventário está registrado.
     *
     * @param inventoryId ID do inventário
     * @return true se existir, false caso contrário
     */
    boolean isInventoryRegistered(@NotNull String inventoryId);

    /**
     * Registra um inventário customizado programaticamente.
     *
     * <p>Permite registrar inventários sem YAML, útil para
     * extensões customizadas de outros plugins.</p>
     *
     * <p><b>Thread:</b> Thread-safe</p>
     *
     * @param config Configuração do inventário
     */
    void registerInventory(@NotNull InventoryConfig config);
}
