package com.afterlands.core.inventory.drag;

/**
 * Resultado de validação de drag-and-drop.
 *
 * <p>Usado para comunicar se uma operação de drag é válida
 * e fornecer informações sobre erros quando não é.
 *
 * @param valid true se a validação passou
 * @param errorCode Código do erro (null se valid = true)
 * @param errorMessage Mensagem de erro legível (null se valid = true)
 */
public record ValidationResult(
    boolean valid,
    String errorCode,
    String errorMessage
) {
    /**
     * Construtor compacto com validação.
     */
    public ValidationResult {
        if (!valid && errorCode == null) {
            throw new IllegalArgumentException("Error code must be provided when validation fails");
        }
    }

    /**
     * Cria um resultado de validação bem-sucedida.
     *
     * @return ValidationResult válido
     */
    public static ValidationResult ok() {
        return new ValidationResult(true, null, null);
    }

    /**
     * Cria um resultado de validação com erro.
     *
     * @param code Código do erro
     * @param message Mensagem de erro
     * @return ValidationResult inválido
     */
    public static ValidationResult error(String code, String message) {
        return new ValidationResult(false, code, message);
    }

    /**
     * Verifica se a validação falhou.
     *
     * @return true se invalid
     */
    public boolean isInvalid() {
        return !valid;
    }

    @Override
    public String toString() {
        if (valid) {
            return "ValidationResult[valid]";
        }
        return "ValidationResult[invalid: " + errorCode + " - " + errorMessage + "]";
    }

    // Common error codes
    public static final String DRAG_NOT_ALLOWED = "DRAG_NOT_ALLOWED";
    public static final String ITEM_MODIFIED = "ITEM_MODIFIED";
    public static final String INVALID_TARGET = "INVALID_TARGET";
    public static final String SESSION_EXPIRED = "SESSION_EXPIRED";
    public static final String CHECKSUM_MISMATCH = "CHECKSUM_MISMATCH";
    public static final String INVENTORY_MISMATCH = "INVENTORY_MISMATCH";
}
