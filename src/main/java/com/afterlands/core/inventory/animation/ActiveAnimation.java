package com.afterlands.core.inventory.animation;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Representa uma animação ativa (em execução) em um inventário.
 *
 * <p>Este record é imutável - cada avanço de frame cria uma nova instância
 * com estado atualizado (padrão funcional).</p>
 *
 * <p><b>Lifecycle:</b></p>
 * <ol>
 *     <li>Criada via {@link #create(String, UUID, int, AnimationConfig)}</li>
 *     <li>Atualizada via {@link #advanceFrame()} a cada intervalo</li>
 *     <li>Removida quando {@link #isFinished()} retorna true</li>
 * </ol>
 *
 * <p>Thread Safety: Imutável, thread-safe.</p>
 */
public record ActiveAnimation(
        @NotNull String animationId,      // UUID único da animação ativa
        @NotNull String inventoryId,      // ID do inventário
        @NotNull UUID playerId,           // UUID do jogador
        int slot,                         // Slot do item sendo animado
        @NotNull AnimationConfig config,  // Configuração da animação
        int currentFrame,                 // Índice do frame atual (0-based)
        long lastFrameTime,               // Timestamp do último frame (server ticks)
        long ticksElapsed                 // Ticks desde início da animação
) {

    /**
     * Compact constructor com validação.
     */
    public ActiveAnimation {
        if (animationId == null || animationId.isBlank()) {
            throw new IllegalArgumentException("animationId cannot be null or blank");
        }
        if (inventoryId == null || inventoryId.isBlank()) {
            throw new IllegalArgumentException("inventoryId cannot be null or blank");
        }
        if (playerId == null) {
            throw new IllegalArgumentException("playerId cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        if (currentFrame < 0) {
            currentFrame = 0;
        }
    }

    /**
     * Avança para o próximo frame.
     *
     * <p>Se a animação está em loop, volta para o frame 0 ao chegar no fim.
     * Se não está em loop, permanece no último frame.</p>
     *
     * @return Nova instância com frame atualizado
     */
    @NotNull
    public ActiveAnimation advanceFrame() {
        if (config.type() != AnimationConfig.AnimationType.FRAME_BASED) {
            // STATE_WATCH não usa frame advancement
            return this;
        }

        if (config.frames().isEmpty()) {
            return this;
        }

        int nextFrame = currentFrame + 1;
        long currentTick = getCurrentTick();

        // Verifica se chegou no fim
        if (nextFrame >= config.frames().size()) {
            if (config.loop()) {
                // Loop: volta para o início
                nextFrame = 0;
            } else {
                // Não-loop: permanece no último frame
                nextFrame = config.frames().size() - 1;
            }
        }

        return new ActiveAnimation(
                animationId,
                inventoryId,
                playerId,
                slot,
                config,
                nextFrame,
                currentTick,
                ticksElapsed + 1
        );
    }

    /**
     * Verifica se é hora de atualizar o frame.
     *
     * <p>Compara o tempo atual com lastFrameTime + frame duration.</p>
     *
     * @param currentTick Tick atual do servidor
     * @return true se deve atualizar
     */
    public boolean shouldUpdate(long currentTick) {
        if (config.type() != AnimationConfig.AnimationType.FRAME_BASED) {
            return false;
        }

        if (config.frames().isEmpty()) {
            return false;
        }

        // Obtém duração do frame atual
        AnimationFrame currentFrameData = getCurrentFrameData();
        if (currentFrameData == null) {
            return false;
        }

        long frameDuration = currentFrameData.durationTicks();
        return (currentTick - lastFrameTime) >= frameDuration;
    }

    /**
     * Verifica se a animação terminou (non-loop).
     *
     * <p>Animações em loop nunca terminam.</p>
     *
     * @return true se terminou
     */
    public boolean isFinished() {
        if (config.loop()) {
            return false; // Loop nunca termina
        }

        if (config.type() != AnimationConfig.AnimationType.FRAME_BASED) {
            return false; // STATE_WATCH controlado externamente
        }

        // Verifica se está no último frame
        return currentFrame >= config.frames().size() - 1;
    }

    /**
     * Obtém o frame atual.
     *
     * @return AnimationFrame atual ou null se inválido
     */
    @NotNull
    public AnimationFrame getCurrentFrameData() {
        if (config.type() != AnimationConfig.AnimationType.FRAME_BASED) {
            throw new IllegalStateException("Cannot get frame data for non-FRAME_BASED animation");
        }

        if (config.frames().isEmpty()) {
            throw new IllegalStateException("Animation has no frames");
        }

        int frameIndex = Math.min(currentFrame, config.frames().size() - 1);
        return config.frames().get(frameIndex);
    }

    /**
     * Cria nova animação ativa.
     *
     * @param inventoryId ID do inventário
     * @param playerId UUID do jogador
     * @param slot Slot do item
     * @param config Configuração da animação
     * @return ActiveAnimation criada
     */
    @NotNull
    public static ActiveAnimation create(
            @NotNull String inventoryId,
            @NotNull UUID playerId,
            int slot,
            @NotNull AnimationConfig config
    ) {
        String animationId = UUID.randomUUID().toString();
        long currentTick = getCurrentTick();

        return new ActiveAnimation(
                animationId,
                inventoryId,
                playerId,
                slot,
                config,
                0,                  // Inicia no frame 0
                currentTick,        // Timestamp inicial
                0L                  // Ticks elapsed = 0
        );
    }

    /**
     * Obtém tick atual do servidor.
     *
     * @return Tick count atual
     */
    private static long getCurrentTick() {
        try {
            return Bukkit.getServer().getWorlds().get(0).getFullTime();
        } catch (Exception e) {
            // Fallback para System.currentTimeMillis() / 50
            return System.currentTimeMillis() / 50L;
        }
    }

    /**
     * Cria chave única para esta animação (para maps).
     *
     * @return String chave única
     */
    @NotNull
    public String getKey() {
        return inventoryId + ":" + playerId + ":" + slot + ":" + config.animationId();
    }

    @Override
    public String toString() {
        return "ActiveAnimation{" +
                "id='" + animationId + '\'' +
                ", inventory='" + inventoryId + '\'' +
                ", slot=" + slot +
                ", frame=" + currentFrame + "/" + (config.frames().size() - 1) +
                ", elapsed=" + ticksElapsed +
                "t}";
    }
}
