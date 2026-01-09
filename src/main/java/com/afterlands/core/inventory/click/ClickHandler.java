package com.afterlands.core.inventory.click;

import org.jetbrains.annotations.NotNull;

/**
 * Handler funcional para clicks em inventário.
 *
 * <p>Usado para API programática com lambdas.</p>
 *
 * <p>Exemplo de uso:</p>
 * <pre>{@code
 * GuiItem item = new GuiItem.Builder()
 *     .onLeftClick(ctx -> {
 *         ctx.sendMessage("&aVocê clicou com o botão esquerdo!");
 *         ctx.nextPage();
 *     })
 *     .onRightClick(ctx -> {
 *         ctx.sendMessage("&cVocê clicou com o botão direito!");
 *         ctx.close();
 *     })
 *     .build();
 * }</pre>
 */
@FunctionalInterface
public interface ClickHandler {
    /**
     * Processa o click.
     *
     * @param context Contexto completo do click com acesso ao player,
     *                holder, item, e métodos de navegação
     */
    void handle(@NotNull ClickContext context);
}
