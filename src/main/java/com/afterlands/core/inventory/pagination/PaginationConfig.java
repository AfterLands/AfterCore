package com.afterlands.core.inventory.pagination;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Configuração de paginação híbrida.
 *
 * <p>Suporta três modos:</p>
 * <ul>
 *     <li><b>NATIVE_ONLY:</b> Paginação automática do framework</li>
 *     <li><b>LAYOUT_ONLY:</b> Layout configurável (estilo AfterBlockAnimations)</li>
 *     <li><b>HYBRID:</b> Combinação de ambos (recomendado)</li>
 * </ul>
 */
public record PaginationConfig(
        @NotNull PaginationMode mode,
        @NotNull List<String> layout,
        @NotNull List<Integer> paginationSlots,
        int itemsPerPage,
        boolean showNavigation
) {

    /**
     * Construtor compacto com validação.
     */
    public PaginationConfig {
        if (mode == null) {
            mode = PaginationMode.HYBRID;
        }
        if (layout == null) {
            layout = List.of();
        }
        if (paginationSlots == null) {
            paginationSlots = List.of();
        }
        if (itemsPerPage <= 0) {
            itemsPerPage = 9; // Padrão: 9 itens por página
        }
    }

    /**
     * Modo de paginação.
     */
    public enum PaginationMode {
        /**
         * Apenas paginação nativa (automática).
         */
        NATIVE_ONLY,

        /**
         * Apenas layout configurável (manual).
         */
        LAYOUT_ONLY,

        /**
         * Híbrido: combina ambos (recomendado).
         */
        HYBRID
    }

    /**
     * Cria configuração nativa (padrão).
     */
    public static PaginationConfig nativeOnly(List<Integer> slots, int itemsPerPage) {
        return new PaginationConfig(PaginationMode.NATIVE_ONLY, List.of(), slots, itemsPerPage, true);
    }

    /**
     * Cria configuração layout (manual).
     */
    public static PaginationConfig layoutOnly(List<String> layout) {
        return new PaginationConfig(PaginationMode.LAYOUT_ONLY, layout, List.of(), 0, false);
    }

    /**
     * Cria configuração híbrida (recomendado).
     */
    public static PaginationConfig hybrid(List<String> layout, List<Integer> slots, int itemsPerPage) {
        return new PaginationConfig(PaginationMode.HYBRID, layout, slots, itemsPerPage, true);
    }

    @Override
    public String toString() {
        return "PaginationConfig{" +
                "mode=" + mode +
                ", itemsPerPage=" + itemsPerPage +
                ", showNavigation=" + showNavigation +
                '}';
    }
}
