package com.afterlands.core.commands.impl;

import com.afterlands.core.commands.CommandService;
import com.afterlands.core.config.MessageService;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Implementação mínima (placeholder).
 *
 * <p>Uma versão completa exigirá integração com o CommandMap do Bukkit e parsing de args.</p>
 */
public final class DefaultCommandService implements CommandService {

    private final Plugin plugin;
    private final Logger logger;
    @SuppressWarnings("unused")
    private final MessageService messages;
    private final boolean debug;

    // reservado para futuro: comandos registrados dinamicamente
    private final List<Object> handlers = new ArrayList<>();

    public DefaultCommandService(@NotNull Plugin plugin, @NotNull MessageService messages, boolean debug) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.messages = messages;
        this.debug = debug;
    }

    @Override
    public void register(@NotNull Object commandHandler) {
        handlers.add(commandHandler);
        if (debug) {
            logger.info("[AfterCore] Command handler registrado (stub): " + commandHandler.getClass().getName());
        }
        // TODO: refletir @Command/@Subcommand e registrar no CommandMap
    }

    @Override
    public void unregisterAll() {
        handlers.clear();
        // TODO: remover do CommandMap quando implementado
    }
}

