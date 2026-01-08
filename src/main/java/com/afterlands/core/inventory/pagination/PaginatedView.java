package com.afterlands.core.inventory.pagination;

import com.afterlands.core.inventory.item.GuiItem;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Representação de uma página paginada.
 *
 * <p>Imutável (record) para thread safety.</p>
 *
 * <p><b>Estrutura:</b></p>
 * <ul>
 *     <li><b>pageItems:</b> Items da página atual (slot -> item)</li>
 *     <li><b>navigationItems:</b> Botões de navegação (next/prev)</li>
 *     <li><b>decorationItems:</b> Bordas, backgrounds, etc.</li>
 * </ul>
 *
 * <p><b>Uso:</b></p>
 * <pre>
 * PaginatedView page = engine.createPage(config, 1, allItems);
 *
 * // Renderizar no inventário
 * page.pageItems().forEach((slot, item) -> {
 *     inventory.setItem(slot, item.build(player, context));
 * });
 *
 * page.navigationItems().forEach((slot, item) -> {
 *     inventory.setItem(slot, item.build(player, context));
 * });
 * </pre>
 */
public record PaginatedView(
        int currentPage,
        int totalPages,
        @NotNull List<Integer> contentSlots,
        @NotNull Map<Integer, GuiItem> pageItems,
        @NotNull Map<Integer, GuiItem> navigationItems,
        @NotNull Map<Integer, GuiItem> decorationItems
) {

    /**
     * Construtor compacto com validação.
     */
    public PaginatedView {
        if (currentPage < 1) {
            currentPage = 1;
        }
        if (totalPages < 1) {
            totalPages = 1;
        }
        if (contentSlots == null) {
            contentSlots = List.of();
        }
        if (pageItems == null) {
            pageItems = Map.of();
        }
        if (navigationItems == null) {
            navigationItems = Map.of();
        }
        if (decorationItems == null) {
            decorationItems = Map.of();
        }
    }

    /**
     * Verifica se é a primeira página.
     *
     * @return true se currentPage == 1
     */
    public boolean isFirstPage() {
        return currentPage == 1;
    }

    /**
     * Verifica se é a última página.
     *
     * @return true se currentPage == totalPages
     */
    public boolean isLastPage() {
        return currentPage >= totalPages;
    }

    /**
     * Verifica se há página anterior.
     *
     * @return true se currentPage > 1
     */
    public boolean hasPreviousPage() {
        return currentPage > 1;
    }

    /**
     * Verifica se há próxima página.
     *
     * @return true se currentPage < totalPages
     */
    public boolean hasNextPage() {
        return currentPage < totalPages;
    }

    /**
     * Obtém número da página anterior.
     *
     * @return Número da página anterior ou currentPage se já é a primeira
     */
    public int getPreviousPageNumber() {
        return Math.max(1, currentPage - 1);
    }

    /**
     * Obtém número da próxima página.
     *
     * @return Número da próxima página ou currentPage se já é a última
     */
    public int getNextPageNumber() {
        return Math.min(totalPages, currentPage + 1);
    }

    /**
     * Obtém total de items na página atual.
     *
     * @return Número de items (tamanho do pageItems map)
     */
    public int getItemCount() {
        return pageItems.size();
    }

    /**
     * Verifica se a página está vazia (sem items).
     *
     * @return true se pageItems está vazio
     */
    public boolean isEmpty() {
        return pageItems.isEmpty();
    }

    /**
     * Combina todos os items (page + navigation + decoration) em um único map.
     *
     * <p><b>Precedência:</b> pageItems > navigationItems > decorationItems</p>
     * <p>Se houver conflito de slots, pageItems tem prioridade.</p>
     *
     * @return Map combinado de todos os items
     */
    @NotNull
    public Map<Integer, GuiItem> getAllItems() {
        Map<Integer, GuiItem> combined = new java.util.HashMap<>(decorationItems);
        combined.putAll(navigationItems);
        combined.putAll(pageItems); // Page items override navigation/decoration
        return combined;
    }

    /**
     * Cria cópia com página diferente.
     *
     * <p>Útil para navegação entre páginas.</p>
     *
     * @param newPage Novo número de página
     * @return Nova PaginatedView com página atualizada
     */
    @NotNull
    public PaginatedView withPage(int newPage) {
        return new PaginatedView(
                Math.max(1, Math.min(newPage, totalPages)),
                totalPages,
                contentSlots,
                pageItems,
                navigationItems,
                decorationItems
        );
    }

    /**
     * Obtém progresso da paginação (para UI).
     *
     * @return String no formato "Page X/Y"
     */
    @NotNull
    public String getProgressText() {
        return "Page " + currentPage + "/" + totalPages;
    }

    /**
     * Obtém progresso da paginação em porcentagem.
     *
     * @return Porcentagem (0.0 a 1.0)
     */
    public double getProgressPercentage() {
        if (totalPages <= 1) {
            return 1.0;
        }
        return (double) currentPage / totalPages;
    }

    @Override
    public String toString() {
        return "PaginatedView{" +
                "page=" + currentPage + "/" + totalPages +
                ", items=" + pageItems.size() +
                ", navigation=" + navigationItems.size() +
                ", decoration=" + decorationItems.size() +
                ", contentSlots=" + contentSlots.size() +
                '}';
    }
}
