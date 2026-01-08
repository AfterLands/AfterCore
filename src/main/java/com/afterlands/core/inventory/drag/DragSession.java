package com.afterlands.core.inventory.drag;

import org.bukkit.inventory.ItemStack;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Representa uma sessão ativa de drag-and-drop.
 *
 * <p>Contém informações sobre o item sendo arrastado e validação
 * para prevenir item duplication (anti-dupe protection).
 *
 * @param playerId UUID do player executando o drag
 * @param draggedItem ItemStack sendo arrastado (snapshot)
 * @param sourceSlot Slot de origem do item
 * @param inventoryId ID do inventário de origem
 * @param startTime Timestamp de início do drag
 * @param itemChecksum Checksum do item para validação anti-dupe
 */
public record DragSession(
    UUID playerId,
    ItemStack draggedItem,
    int sourceSlot,
    String inventoryId,
    Instant startTime,
    String itemChecksum
) {
    // Session timeout: 30 segundos
    private static final Duration SESSION_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Construtor compacto com validação.
     */
    public DragSession {
        if (playerId == null) {
            throw new IllegalArgumentException("Player ID cannot be null");
        }
        if (draggedItem == null) {
            throw new IllegalArgumentException("Dragged item cannot be null");
        }
        if (inventoryId == null || inventoryId.isBlank()) {
            throw new IllegalArgumentException("Inventory ID cannot be null or empty");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("Start time cannot be null");
        }
        if (itemChecksum == null || itemChecksum.isBlank()) {
            throw new IllegalArgumentException("Item checksum cannot be null or empty");
        }

        // Clone item para prevenir modificações externas
        draggedItem = draggedItem.clone();
    }

    /**
     * Calcula checksum de um ItemStack para validação anti-dupe.
     *
     * <p>O checksum inclui:
     * <ul>
     *   <li>Material type</li>
     *   <li>Amount</li>
     *   <li>Durability</li>
     *   <li>Serialized NBT data</li>
     * </ul>
     *
     * @param item ItemStack para calcular checksum
     * @return Checksum em Base64 (SHA-256)
     */
    public static String calculateChecksum(ItemStack item) {
        if (item == null) {
            return "NULL";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Material
            digest.update(item.getType().name().getBytes());

            // Amount
            digest.update((byte) item.getAmount());

            // Durability (1.8.8 compatibility)
            digest.update((byte) (item.getDurability() & 0xFF));
            digest.update((byte) ((item.getDurability() >> 8) & 0xFF));

            // ItemMeta serialization
            if (item.hasItemMeta()) {
                String metaString = item.getItemMeta().toString();
                digest.update(metaString.getBytes());
            }

            byte[] hash = digest.digest();
            return Base64.getEncoder().encodeToString(hash);

        } catch (NoSuchAlgorithmException e) {
            // Fallback: usar toString() do item
            return String.valueOf(item.hashCode());
        }
    }

    /**
     * Valida se o item atual corresponde ao item original da sessão.
     *
     * <p>Previne item duplication checando se o item foi modificado
     * desde o início do drag.
     *
     * @param currentItem Item atual para validar
     * @return true se o item não foi modificado
     */
    public boolean isValid(ItemStack currentItem) {
        if (currentItem == null) {
            return false;
        }

        String currentChecksum = calculateChecksum(currentItem);
        return itemChecksum.equals(currentChecksum);
    }

    /**
     * Verifica se a sessão expirou (timeout).
     *
     * @return true se a sessão expirou
     */
    public boolean isExpired() {
        return Duration.between(startTime, Instant.now()).compareTo(SESSION_TIMEOUT) > 0;
    }

    /**
     * Retorna o tempo restante até o timeout.
     *
     * @return Duration restante (pode ser negativo se expirado)
     */
    public Duration remainingTime() {
        Duration elapsed = Duration.between(startTime, Instant.now());
        return SESSION_TIMEOUT.minus(elapsed);
    }

    /**
     * Cria uma nova DragSession para um item.
     *
     * @param playerId UUID do player
     * @param item ItemStack sendo arrastado
     * @param sourceSlot Slot de origem
     * @param inventoryId ID do inventário
     * @return Nova DragSession
     */
    public static DragSession create(UUID playerId, ItemStack item, int sourceSlot, String inventoryId) {
        return new DragSession(
            playerId,
            item.clone(),
            sourceSlot,
            inventoryId,
            Instant.now(),
            calculateChecksum(item)
        );
    }

    @Override
    public String toString() {
        return "DragSession{" +
            "player=" + playerId +
            ", item=" + draggedItem.getType() +
            " x" + draggedItem.getAmount() +
            ", slot=" + sourceSlot +
            ", inventory=" + inventoryId +
            ", age=" + Duration.between(startTime, Instant.now()).toMillis() + "ms" +
            "}";
    }
}
