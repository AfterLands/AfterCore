package com.afterlands.core.config.validate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Resultado da validação de configuração.
 *
 * @param errors Lista de erros e avisos encontrados
 */
public record ValidationResult(@NotNull List<ValidationError> errors) {

    public static final ValidationResult VALID = new ValidationResult(Collections.emptyList());

    public ValidationResult {
        errors = new ArrayList<>(errors); // defensive copy
    }

    public static ValidationResult of(@NotNull List<ValidationError> errors) {
        return errors.isEmpty() ? VALID : new ValidationResult(errors);
    }

    public static ValidationResult single(@NotNull ValidationError error) {
        return new ValidationResult(List.of(error));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public boolean hasErrors() {
        return errors.stream().anyMatch(ValidationError::isError);
    }

    public boolean hasWarnings() {
        return errors.stream().anyMatch(ValidationError::isWarning);
    }

    public List<ValidationError> getErrors() {
        return errors.stream()
                .filter(ValidationError::isError)
                .collect(Collectors.toList());
    }

    public List<ValidationError> getWarnings() {
        return errors.stream()
                .filter(ValidationError::isWarning)
                .collect(Collectors.toList());
    }

    public int errorCount() {
        return (int) errors.stream().filter(ValidationError::isError).count();
    }

    public int warningCount() {
        return (int) errors.stream().filter(ValidationError::isWarning).count();
    }

    /**
     * Combina múltiplos resultados de validação.
     */
    public static ValidationResult combine(@NotNull ValidationResult... results) {
        List<ValidationError> allErrors = new ArrayList<>();
        for (ValidationResult result : results) {
            allErrors.addAll(result.errors);
        }
        return ValidationResult.of(allErrors);
    }

    /**
     * Retorna uma mensagem formatada com todos os erros e avisos.
     */
    public String formatMessages() {
        if (isValid()) {
            return "Configuração válida";
        }

        StringBuilder sb = new StringBuilder();
        if (hasErrors()) {
            sb.append("Erros encontrados:\n");
            getErrors().forEach(e -> sb.append("  ").append(e.toString()).append("\n"));
        }
        if (hasWarnings()) {
            sb.append("Avisos encontrados:\n");
            getWarnings().forEach(w -> sb.append("  ").append(w.toString()).append("\n"));
        }
        return sb.toString();
    }
}
