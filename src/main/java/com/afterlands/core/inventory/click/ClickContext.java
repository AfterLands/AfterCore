package com.afterlands.core.inventory.click;

import com.afterlands.core.inventory.InventoryContext;
import com.afterlands.core.inventory.item.GuiItem;
import com.afterlands.core.inventory.view.InventoryViewHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Contexto imutável de um click em inventário.
 *
 * <p>Fornece acesso a todas as informações relevantes do click
 * para handlers customizados.</p>
 */
public record ClickContext(
    @NotNull Player player,
    @NotNull InventoryViewHolder holder,
    @NotNull GuiItem item,
    @NotNull InventoryContext inventoryContext,
    @NotNull InventoryClickEvent event,
    @NotNull ClickType clickType,
    int slot,
    @Nullable ItemStack cursor,
    @Nullable ItemStack currentItem
) {
    /**
     * Cria ClickContext a partir de um InventoryClickEvent.
     *
     * @param event Evento de click
     * @param holder View holder do inventário
     * @param item Item clicado
     * @param context Contexto do inventário
     * @return Contexto de click construído
     */
    @NotNull
    public static ClickContext from(
        @NotNull InventoryClickEvent event,
        @NotNull InventoryViewHolder holder,
        @NotNull GuiItem item,
        @NotNull InventoryContext context
    ) {
        return new ClickContext(
            (Player) event.getWhoClicked(),
            holder,
            item,
            context,
            event,
            event.getClick(),
            event.getRawSlot(),
            event.getCursor(),
            event.getCurrentItem()
        );
    }

    // Convenience methods for click type checking

    /**
     * Verifica se é click esquerdo.
     */
    public boolean isLeftClick() {
        return clickType == ClickType.LEFT;
    }

    /**
     * Verifica se é click direito.
     */
    public boolean isRightClick() {
        return clickType == ClickType.RIGHT;
    }

    /**
     * Verifica se é Shift + Click Esquerdo.
     */
    public boolean isShiftLeftClick() {
        return clickType == ClickType.SHIFT_LEFT;
    }

    /**
     * Verifica se é Shift + Click Direito.
     */
    public boolean isShiftRightClick() {
        return clickType == ClickType.SHIFT_RIGHT;
    }

    /**
     * Verifica se é click do meio (roda do mouse).
     */
    public boolean isMiddleClick() {
        return clickType == ClickType.MIDDLE;
    }

    /**
     * Verifica se é double click.
     */
    public boolean isDoubleClick() {
        return clickType == ClickType.DOUBLE_CLICK;
    }

    /**
     * Verifica se é drop (tecla Q).
     */
    public boolean isDrop() {
        return clickType == ClickType.DROP;
    }

    /**
     * Verifica se é Control + Drop (tecla Q).
     */
    public boolean isControlDrop() {
        return clickType == ClickType.CONTROL_DROP;
    }

    /**
     * Verifica se é tecla numérica (hotbar 1-9).
     */
    public boolean isNumberKey() {
        return clickType == ClickType.NUMBER_KEY;
    }

    /**
     * Verifica se é qualquer tipo de shift click.
     */
    public boolean isShiftClick() {
        return clickType.isShiftClick();
    }

    // Navigation helpers

    /**
     * Vai para a próxima página.
     */
    public void nextPage() {
        holder.nextPage();
    }

    /**
     * Vai para a página anterior.
     */
    public void previousPage() {
        holder.previousPage();
    }

    /**
     * Troca para uma aba específica.
     *
     * @param tabId ID da aba
     */
    public void switchTab(@NotNull String tabId) {
        holder.switchTab(tabId);
    }

    /**
     * Fecha o inventário.
     */
    public void close() {
        holder.close();
    }

    /**
     * Atualiza o inventário.
     */
    public void refresh() {
        holder.refresh();
    }

    // Message helpers

    /**
     * Envia mensagem para o jogador com suporte a color codes.
     *
     * @param message Mensagem com color codes (&a, &b, etc.)
     */
    public void sendMessage(@NotNull String message) {
        player.sendMessage(message.replace("&", "§"));
    }
}
