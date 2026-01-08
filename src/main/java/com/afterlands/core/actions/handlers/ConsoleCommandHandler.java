package com.afterlands.core.actions.handlers;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handler para executar comandos como console.
 *
 * <p>Formato: {@code console: <comando>}</p>
 * <p>Suporta:</p>
 * <ul>
 *     <li>Qualquer comando do servidor</li>
 *     <li>PlaceholderAPI para substituir variáveis do player</li>
 *     <li>Execução com permissões de console (OP)</li>
 * </ul>
 *
 * <p>Exemplos:</p>
 * <pre>
 * console: give %player_name% diamond 1
 * console: gamemode creative %player_name%
 * console: broadcast Jogador %player_name% completou quest!
 * </pre>
 *
 * <p><b>ATENÇÃO:</b> Comandos são executados com permissões de console.
 * Use com cautela e sempre valide inputs se vierem de players.</p>
 */
public final class ConsoleCommandHandler implements ActionHandler {

    @Override
    public void execute(@NotNull Player target, @NotNull ActionSpec spec) {
        String command = spec.rawArgs();
        if (command == null || command.isEmpty()) {
            return;
        }

        // Processar PlaceholderAPI para substituir variáveis do player
        String processed = PlaceholderUtil.process(target, command);

        // Remover barra inicial se presente
        if (processed.startsWith("/")) {
            processed = processed.substring(1);
        }

        // Executar como console
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processed);
    }
}
