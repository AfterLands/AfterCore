package com.afterlands.core.inventory.action;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Representa uma action parseada de uma string de configuração.
 *
 * <p>Formato esperado: "action_type: arguments"
 * <p>Exemplos:
 * <ul>
 *   <li>"switch_tab: shop"</li>
 *   <li>"next_page"</li>
 *   <li>"play_sound: CLICK"</li>
 *   <li>"message: &aOlá, {player}!"</li>
 * </ul>
 *
 * @param actionType Tipo da action (ex: "switch_tab", "next_page")
 * @param arguments Argumentos da action (pode ser vazio)
 * @param parameters Parâmetros adicionais parseados (para futuras extensões)
 */
public record ParsedAction(
    String actionType,
    String arguments,
    Map<String, String> parameters
) {
    // Pattern para parse de action: "action_type: arguments"
    private static final Pattern ACTION_PATTERN = Pattern.compile("^([a-z_]+)(?:\\s*:\\s*(.*))?$");

    /**
     * Construtor compacto com validação.
     */
    public ParsedAction {
        if (actionType == null || actionType.isBlank()) {
            throw new IllegalArgumentException("Action type cannot be null or empty");
        }
        if (arguments == null) {
            arguments = "";
        }
        if (parameters == null) {
            parameters = Map.of();
        }
    }

    /**
     * Parse de uma string de action.
     *
     * @param actionString String no formato "action_type: arguments"
     * @return ParsedAction com tipo e argumentos parseados
     * @throws IllegalArgumentException se o formato for inválido
     */
    public static ParsedAction parse(String actionString) {
        if (actionString == null || actionString.isBlank()) {
            throw new IllegalArgumentException("Action string cannot be null or empty");
        }

        String trimmed = actionString.trim();
        Matcher matcher = ACTION_PATTERN.matcher(trimmed);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid action format: " + actionString);
        }

        String actionType = matcher.group(1);
        String arguments = matcher.group(2);

        // Normalize arguments
        if (arguments == null) {
            arguments = "";
        } else {
            arguments = arguments.trim();
        }

        return new ParsedAction(actionType, arguments, new HashMap<>());
    }

    /**
     * Verifica se a action tem argumentos.
     *
     * @return true se arguments não está vazio
     */
    public boolean hasArguments() {
        return !arguments.isBlank();
    }

    /**
     * Cria uma ParsedAction sem argumentos.
     *
     * @param actionType Tipo da action
     * @return ParsedAction sem argumentos
     */
    public static ParsedAction simple(String actionType) {
        return new ParsedAction(actionType, "", Map.of());
    }

    /**
     * Cria uma ParsedAction com argumentos.
     *
     * @param actionType Tipo da action
     * @param arguments Argumentos da action
     * @return ParsedAction com argumentos
     */
    public static ParsedAction withArgs(String actionType, String arguments) {
        return new ParsedAction(actionType, arguments, Map.of());
    }

    @Override
    public String toString() {
        if (arguments.isBlank()) {
            return actionType;
        }
        return actionType + ": " + arguments;
    }
}
