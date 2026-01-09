package com.afterlands.core.inventory.click;

import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Container imutável de handlers por tipo de click.
 *
 * <p>Suporta tanto handlers programáticos (ClickHandler) quanto
 * listas de actions (List&lt;String&gt;) para YAML.</p>
 *
 * <p>Exemplo YAML:</p>
 * <pre>{@code
 * items:
 *   shop_item:
 *     on_left_click:
 *       - "message: &aBought item!"
 *     on_right_click:
 *       - "message: &eSold item!"
 *     actions:  # fallback
 *       - "message: &cUse left or right click!"
 * }</pre>
 *
 * <p>Exemplo programático:</p>
 * <pre>{@code
 * ClickHandlers handlers = ClickHandlers.builder()
 *     .onLeftClick(ctx -> ctx.sendMessage("&aBought!"))
 *     .onRightClick(ctx -> ctx.sendMessage("&eSold!"))
 *     .defaultHandler(ctx -> ctx.sendMessage("&cInvalid click!"))
 *     .build();
 * }</pre>
 */
public class ClickHandlers {

    // Actions por tipo de click (YAML)
    private final Map<ClickType, List<String>> actionsByType;

    // Handlers programáticos (API Java)
    private final Map<ClickType, ClickHandler> handlersByType;

    // Fallback actions (executadas se tipo específico não definido)
    private final List<String> defaultActions;

    // Fallback handler
    private final ClickHandler defaultHandler;

    private ClickHandlers(Builder builder) {
        this.actionsByType = Map.copyOf(builder.actionsByType);
        this.handlersByType = Map.copyOf(builder.handlersByType);
        this.defaultActions = builder.defaultActions != null ? List.copyOf(builder.defaultActions) : List.of();
        this.defaultHandler = builder.defaultHandler;
    }

    /**
     * Obtém actions para um tipo de click.
     *
     * @param clickType Tipo do click
     * @return Lista de actions ou defaultActions se não definido
     */
    @NotNull
    public List<String> getActions(@NotNull ClickType clickType) {
        return actionsByType.getOrDefault(clickType, defaultActions);
    }

    /**
     * Obtém handler para um tipo de click.
     *
     * @param clickType Tipo do click
     * @return Handler ou defaultHandler se não definido (pode ser null)
     */
    @Nullable
    public ClickHandler getHandler(@NotNull ClickType clickType) {
        return handlersByType.getOrDefault(clickType, defaultHandler);
    }

    /**
     * Verifica se tem handler ou actions para um tipo de click.
     *
     * @param clickType Tipo do click
     * @return true se tem handler ou actions
     */
    public boolean hasHandlerFor(@NotNull ClickType clickType) {
        return handlersByType.containsKey(clickType)
            || actionsByType.containsKey(clickType)
            || defaultHandler != null
            || !defaultActions.isEmpty();
    }

    /**
     * Verifica se usa handlers programáticos.
     *
     * @return true se tem pelo menos um handler programático
     */
    public boolean hasProgrammaticHandlers() {
        return !handlersByType.isEmpty() || defaultHandler != null;
    }

    /**
     * Cria builder vazio.
     *
     * @return Builder para configurar handlers
     */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Cria ClickHandlers com apenas actions default (compatibilidade).
     *
     * @param actions Lista de actions padrão
     * @return ClickHandlers configurado
     */
    @NotNull
    public static ClickHandlers ofDefault(@NotNull List<String> actions) {
        return builder().defaultActions(actions).build();
    }

    /**
     * Builder para construir ClickHandlers de forma fluente.
     */
    public static class Builder {
        private final Map<ClickType, List<String>> actionsByType = new EnumMap<>(ClickType.class);
        private final Map<ClickType, ClickHandler> handlersByType = new EnumMap<>(ClickType.class);
        private List<String> defaultActions;
        private ClickHandler defaultHandler;

        // ========== YAML-style setters (actions) ==========

        @NotNull
        public Builder onLeftClick(@NotNull List<String> actions) {
            actionsByType.put(ClickType.LEFT, actions);
            return this;
        }

        @NotNull
        public Builder onRightClick(@NotNull List<String> actions) {
            actionsByType.put(ClickType.RIGHT, actions);
            return this;
        }

        @NotNull
        public Builder onShiftLeftClick(@NotNull List<String> actions) {
            actionsByType.put(ClickType.SHIFT_LEFT, actions);
            return this;
        }

        @NotNull
        public Builder onShiftRightClick(@NotNull List<String> actions) {
            actionsByType.put(ClickType.SHIFT_RIGHT, actions);
            return this;
        }

        @NotNull
        public Builder onMiddleClick(@NotNull List<String> actions) {
            actionsByType.put(ClickType.MIDDLE, actions);
            return this;
        }

        @NotNull
        public Builder onDoubleClick(@NotNull List<String> actions) {
            actionsByType.put(ClickType.DOUBLE_CLICK, actions);
            return this;
        }

        @NotNull
        public Builder onDrop(@NotNull List<String> actions) {
            actionsByType.put(ClickType.DROP, actions);
            return this;
        }

        @NotNull
        public Builder onControlDrop(@NotNull List<String> actions) {
            actionsByType.put(ClickType.CONTROL_DROP, actions);
            return this;
        }

        @NotNull
        public Builder onNumberKey(@NotNull List<String> actions) {
            actionsByType.put(ClickType.NUMBER_KEY, actions);
            return this;
        }

        @NotNull
        public Builder onClickType(@NotNull ClickType type, @NotNull List<String> actions) {
            actionsByType.put(type, actions);
            return this;
        }

        // ========== API-style setters (handlers) ==========

        @NotNull
        public Builder onLeftClick(@NotNull ClickHandler handler) {
            handlersByType.put(ClickType.LEFT, handler);
            return this;
        }

        @NotNull
        public Builder onRightClick(@NotNull ClickHandler handler) {
            handlersByType.put(ClickType.RIGHT, handler);
            return this;
        }

        @NotNull
        public Builder onShiftLeftClick(@NotNull ClickHandler handler) {
            handlersByType.put(ClickType.SHIFT_LEFT, handler);
            return this;
        }

        @NotNull
        public Builder onShiftRightClick(@NotNull ClickHandler handler) {
            handlersByType.put(ClickType.SHIFT_RIGHT, handler);
            return this;
        }

        @NotNull
        public Builder onMiddleClick(@NotNull ClickHandler handler) {
            handlersByType.put(ClickType.MIDDLE, handler);
            return this;
        }

        @NotNull
        public Builder onDoubleClick(@NotNull ClickHandler handler) {
            handlersByType.put(ClickType.DOUBLE_CLICK, handler);
            return this;
        }

        @NotNull
        public Builder onDrop(@NotNull ClickHandler handler) {
            handlersByType.put(ClickType.DROP, handler);
            return this;
        }

        @NotNull
        public Builder onControlDrop(@NotNull ClickHandler handler) {
            handlersByType.put(ClickType.CONTROL_DROP, handler);
            return this;
        }

        @NotNull
        public Builder onNumberKey(@NotNull ClickHandler handler) {
            handlersByType.put(ClickType.NUMBER_KEY, handler);
            return this;
        }

        @NotNull
        public Builder onClickType(@NotNull ClickType type, @NotNull ClickHandler handler) {
            handlersByType.put(type, handler);
            return this;
        }

        // ========== Default fallbacks ==========

        @NotNull
        public Builder defaultActions(@NotNull List<String> actions) {
            this.defaultActions = actions;
            return this;
        }

        @NotNull
        public Builder defaultHandler(@NotNull ClickHandler handler) {
            this.defaultHandler = handler;
            return this;
        }

        // Aliases for compatibility

        @NotNull
        public Builder actions(@NotNull List<String> actions) {
            return defaultActions(actions);
        }

        @NotNull
        public Builder onClick(@NotNull ClickHandler handler) {
            return defaultHandler(handler);
        }

        @NotNull
        public ClickHandlers build() {
            return new ClickHandlers(this);
        }
    }
}
