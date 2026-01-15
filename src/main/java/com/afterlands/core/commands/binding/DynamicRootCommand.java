package com.afterlands.core.commands.binding;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Dynamic command implementation that delegates to a CommandDispatcher.
 *
 * <p>
 * This class serves as the bridge between Bukkit's command system and
 * the AfterCore command framework. It:
 * </p>
 * <ul>
 * <li>Extends Bukkit's Command class for CommandMap registration</li>
 * <li>Implements PluginIdentifiableCommand for plugin ownership tracking</li>
 * <li>Delegates execution and tab-completion to CommandDispatcher</li>
 * </ul>
 *
 * <p>
 * This class is lightweight and performs no logic itself - all work
 * is delegated to the dispatcher to keep the Bukkit integration minimal.
 * </p>
 */
public final class DynamicRootCommand extends Command implements PluginIdentifiableCommand {

    private final Plugin owner;
    private final CommandDispatcher dispatcher;

    /**
     * Creates a new DynamicRootCommand.
     *
     * @param name         Primary command name
     * @param description  Command description
     * @param usageMessage Usage message
     * @param aliases      Command aliases
     * @param owner        Owner plugin
     * @param dispatcher   The command dispatcher
     */
    public DynamicRootCommand(@NotNull String name,
            @NotNull String description,
            @NotNull String usageMessage,
            @NotNull List<String> aliases,
            @NotNull Plugin owner,
            @NotNull CommandDispatcher dispatcher) {
        super(name, description, usageMessage, aliases);
        this.owner = Objects.requireNonNull(owner, "owner");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");

        // Initialize timings (needed for Spigot/Paper servers)
        // We use reflection to avoid compile-time dependency issues with spigot-api
        // versions
        try {
            java.lang.reflect.Field timingsField = org.bukkit.command.Command.class.getDeclaredField("timings");
            timingsField.setAccessible(true);

            // Try to find Aikar's Timings class (modern Spigot/Paper)
            Class<?> timingsClass = Class.forName("co.aikar.timings.Timings");
            java.lang.reflect.Method ofMethod = timingsClass.getMethod("of", Plugin.class, String.class);
            Object timing = ofMethod.invoke(null, owner, "Command: " + name);

            timingsField.set(this, timing);
        } catch (Throwable ignored) {
            // Fallback: Field doesn't exist or Timings class not found
            // This is fine for vanilla Bukkit or very old versions
        }
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        // Delegate to dispatcher - all logic is handled there
        return dispatcher.dispatch(sender, commandLabel, args);
    }

    @NotNull
    @Override
    public List<String> tabComplete(@NotNull CommandSender sender,
            @NotNull String alias,
            @NotNull String[] args) throws IllegalArgumentException {
        // Delegate to dispatcher
        return dispatcher.tabComplete(sender, alias, args);
    }

    @NotNull
    @Override
    public Plugin getPlugin() {
        return owner;
    }

    /**
     * Gets the command dispatcher.
     */
    @NotNull
    public CommandDispatcher dispatcher() {
        return dispatcher;
    }
}
