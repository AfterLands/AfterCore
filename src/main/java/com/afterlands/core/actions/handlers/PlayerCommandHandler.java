package com.afterlands.core.actions.handlers;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handler para fazer o player executar um comando.
 *
 * <p>Formato: {@code player_command: <comando>}</p>
 * <p>Suporta:</p>
 * <ul>
 *     <li>Qualquer comando que o player poderia executar</li>
 *     <li>PlaceholderAPI para substituir variáveis</li>
 *     <li>Execução com permissões do player</li>
 * </ul>
 *
 * <p>Exemplos:</p>
 * <pre>
 * player_command: spawn
 * player_command: menu principal
 * player_command: warp cidade
 * </pre>
 *
 * <p><b>Diferença de {@code console}:</b></p>
 * <ul>
 *     <li>console: Executa com permissões de OP</li>
 *     <li>player_command: Executa com permissões do player</li>
 * </ul>
 */
public final class PlayerCommandHandler implements ActionHandler {

    @Override
    public void execute(@NotNull Player target, @NotNull ActionSpec spec) {
        String command = spec.rawArgs();
        if (command == null || command.isEmpty()) {
            return;
        }

        // Processar PlaceholderAPI
        String processed = PlaceholderUtil.process(target, command);

        // Remover barra inicial se presente
        if (processed.startsWith("/")) {
            processed = processed.substring(1);
        }

        // Executar como player (performCommand já verifica permissões)
        target.performCommand(processed);
    }
}
