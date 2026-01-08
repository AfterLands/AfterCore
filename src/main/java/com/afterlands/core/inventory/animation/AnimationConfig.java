package com.afterlands.core.inventory.animation;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuração de animação de item.
 *
 * <p>Suporta dois tipos:</p>
 * <ul>
 *     <li><b>FRAME_BASED:</b> Animações sequenciais por frames (estilo AfterBlockAnimations)</li>
 *     <li><b>STATE_WATCH:</b> Reativo a mudanças de estado (MutableIntState)</li>
 * </ul>
 */
public record AnimationConfig(
        @NotNull String animationId,
        @NotNull AnimationType type,
        long intervalTicks,
        @NotNull List<AnimationFrame> frames,
        boolean loop,
        @NotNull String stateKey
) {

    /**
     * Construtor compacto com validação.
     */
    public AnimationConfig {
        if (animationId == null || animationId.isBlank()) {
            throw new IllegalArgumentException("animationId cannot be null or blank");
        }
        if (type == null) {
            type = AnimationType.FRAME_BASED;
        }
        if (intervalTicks <= 0) {
            intervalTicks = 20L; // 1 segundo padrão
        }
        if (frames == null) {
            frames = List.of();
        }
        if (stateKey == null) {
            stateKey = "";
        }
    }

    /**
     * Tipo de animação.
     */
    public enum AnimationType {
        /**
         * Animação baseada em frames sequenciais.
         *
         * <p>Troca frames em loop/sequência com timing configurável.</p>
         */
        FRAME_BASED,

        /**
         * Animação baseada em estado (watch MutableIntState).
         *
         * <p>Atualiza item quando estado muda (reativo).</p>
         */
        STATE_WATCH,

        /**
         * Animação sequencial simples.
         *
         * <p>Troca frames em sequência linear (não loop).</p>
         */
        SEQUENTIAL
    }

    /**
     * Parse de ConfigurationSection.
     *
     * <p>Formato YAML esperado:</p>
     * <pre>
     * - id: "pulse"
     *   type: FRAME_BASED
     *   interval: 10
     *   loop: true
     *   frames:
     *     - material: DIAMOND_SWORD
     *       duration: 5
     *       enchanted: true
     *     - material: DIAMOND_SWORD
     *       duration: 5
     *       enchanted: false
     * </pre>
     *
     * @param section ConfigurationSection
     * @return AnimationConfig parseada
     */
    @NotNull
    public static AnimationConfig fromConfig(@NotNull ConfigurationSection section) {
        String id = section.getString("id", "anim_" + System.currentTimeMillis());

        // Parse type
        String typeStr = section.getString("type", "FRAME_BASED").toUpperCase();
        AnimationType type;
        try {
            type = AnimationType.valueOf(typeStr);
            // SEQUENTIAL é alias para FRAME_BASED
            if (type == AnimationType.SEQUENTIAL) {
                type = AnimationType.FRAME_BASED;
            }
        } catch (IllegalArgumentException e) {
            type = AnimationType.FRAME_BASED;
        }

        long interval = section.getLong("interval", 20L);
        boolean loop = section.getBoolean("loop", false);
        String stateKey = section.getString("state-key", "");

        // Parse frames
        List<AnimationFrame> frames = new ArrayList<>();
        if (section.contains("frames")) {
            List<?> framesList = section.getList("frames");
            if (framesList != null) {
                for (Object frameObj : framesList) {
                    if (frameObj instanceof Map<?, ?>) {
                        try {
                            ConfigurationSection frameSection = toConfigSection((Map<?, ?>) frameObj);
                            AnimationFrame frame = AnimationFrame.fromConfig(frameSection);
                            frames.add(frame);
                        } catch (Exception e) {
                            // Skip invalid frame
                        }
                    }
                }
            }
        }

        return new AnimationConfig(id, type, interval, frames, loop, stateKey);
    }

    /**
     * Cria animação frame-based.
     */
    public static AnimationConfig frameBased(@NotNull String id, long interval, @NotNull List<AnimationFrame> frames, boolean loop) {
        return new AnimationConfig(id, AnimationType.FRAME_BASED, interval, frames, loop, "");
    }

    /**
     * Cria animação state-watch.
     */
    public static AnimationConfig stateWatch(@NotNull String id, @NotNull String stateKey, long interval) {
        return new AnimationConfig(id, AnimationType.STATE_WATCH, interval, List.of(), false, stateKey);
    }

    /**
     * Valida configuração.
     *
     * @return true se válida
     */
    public boolean isValid() {
        if (animationId == null || animationId.isBlank()) {
            return false;
        }

        if (type == AnimationType.FRAME_BASED) {
            // FRAME_BASED precisa ter pelo menos 1 frame
            if (frames == null || frames.isEmpty()) {
                return false;
            }

            // Todos os frames devem ter duration > 0
            for (AnimationFrame frame : frames) {
                if (frame.durationTicks() <= 0) {
                    return false;
                }
            }
        }

        if (type == AnimationType.STATE_WATCH) {
            // STATE_WATCH precisa ter stateKey
            if (stateKey == null || stateKey.isBlank()) {
                return false;
            }
        }

        return intervalTicks > 0;
    }

    /**
     * Converte Map para ConfigurationSection.
     *
     * @param map Map a converter
     * @return ConfigurationSection
     */
    @NotNull
    private static ConfigurationSection toConfigSection(@NotNull Map<?, ?> map) {
        org.bukkit.configuration.MemoryConfiguration memoryConfig = new org.bukkit.configuration.MemoryConfiguration();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            memoryConfig.set(key, value);
        }
        return memoryConfig;
    }

    @Override
    public String toString() {
        return "AnimationConfig{" +
                "id='" + animationId + '\'' +
                ", type=" + type +
                ", interval=" + intervalTicks +
                ", frames=" + frames.size() +
                ", loop=" + loop +
                '}';
    }
}
