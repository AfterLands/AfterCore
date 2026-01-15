package com.afterlands.core.commands.help;

import com.afterlands.core.commands.messages.MessageFacade;
import com.afterlands.core.commands.registry.nodes.RootNode;
import com.afterlands.core.commands.registry.nodes.SubNode;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Formats command help with pagination and clickable text.
 */
public final class HelpFormatter {

    private static final int DEFAULT_PAGE_SIZE = 7;

    private final MessageFacade messages;
    private final FileConfiguration config;

    public HelpFormatter(@NotNull MessageFacade messages, @NotNull FileConfiguration config) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Sends formatted help to a sender.
     */
    public void sendHelp(@NotNull CommandSender sender, @NotNull RootNode root,
            @NotNull String path, int page) {
        int pageSize = config.getInt("commands.help.page-size", DEFAULT_PAGE_SIZE);

        List<SubNode> visibleChildren = root.children().values().stream()
                .filter(child -> child.permission() == null || sender.hasPermission(child.permission()))
                .sorted(Comparator.comparing(SubNode::name))
                .collect(Collectors.toList());

        int totalItems = visibleChildren.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) pageSize));
        page = Math.max(1, Math.min(page, totalPages));

        // Header
        sendHeader(sender, root, path);

        if (visibleChildren.isEmpty()) {
            String usage = root.generateUsage(path);
            messages.send(sender, "commands.help.usage", "usage", usage);
        } else {
            int startIndex = (page - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalItems);
            List<SubNode> pageItems = visibleChildren.subList(startIndex, endIndex);

            for (SubNode sub : pageItems) {
                sendSubcommandEntry(sender, path, sub);
            }
        }

        messages.sendRaw(sender, " ");
        // Footer (only if multiple pages)
        if (totalPages > 1) {
            sendPaginationFooter(sender, path, page, totalPages);
            messages.sendRaw(sender, " ");
        }
    }

    private void sendHeader(@NotNull CommandSender sender, @NotNull RootNode root, @NotNull String path) {
        String plugin = root.helpPrefix() != null ? root.helpPrefix() : root.name().toUpperCase(Locale.ROOT);

        List<String> headerLines = config.getStringList("commands.help.header");
        if (headerLines.isEmpty()) {
            headerLines = List.of(
                    " ",
                    " &b&l{plugin} &8┃ &fAjuda - &b{command}",
                    " &a[!] &7sub-cmds &8┃ &c<> &7obrigatório &8┃ &d[] &7opcional",
                    " ");
        }

        for (String line : headerLines) {
            String formatted = line.replace("{plugin}", plugin).replace("{command}", "/" + path);
            messages.sendRaw(sender, formatted);
        }
    }

    private void sendSubcommandEntry(@NotNull CommandSender sender, @NotNull String path, @NotNull SubNode sub) {
        String baseCommand = "/" + path + " " + sub.name();
        String displayCommand = baseCommand;
        String suggestCommand = baseCommand; // For click suggestion (no colors)

        // Append usage hint if available
        if (sub.usage() != null && !sub.usage().isEmpty()) {
            displayCommand += " " + sub.usage(); // Keep colors for display
            suggestCommand += " " + stripColorCodes(sub.usage()); // Strip colors for suggestion
        }
        String description = sub.description() != null ? sub.description() : "";

        String entryFormat = config.getString("commands.help.subcommand-entry", " &b▪ &f{command} &7- {description}");
        String hoverText = config.getString("commands.help.subcommand-hover", "&a► Clique para sugerir");

        String formatted = entryFormat.replace("{command}", displayCommand).replace("{description}", description);

        if (sender instanceof Player player) {
            // Use sendMessage directly with BaseComponent array - avoids nesting issues
            BaseComponent[] components = TextComponent.fromLegacyText(messages.format(formatted));
            BaseComponent[] hoverComponents = TextComponent.fromLegacyText(messages.format(hoverText));

            for (BaseComponent component : components) {
                component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponents));
                component.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestCommand));
            }

            player.spigot().sendMessage(components);
        } else {
            messages.sendRaw(sender, formatted);
        }
    }

    private String stripColorCodes(String text) {
        return text.replaceAll("&[0-9a-fk-or]", "");
    }

    private void sendPaginationFooter(@NotNull CommandSender sender, @NotNull String path, int currentPage,
            int totalPages) {
        if (sender instanceof Player player) {
            List<BaseComponent> allComponents = new ArrayList<>();

            // Page indicator
            String pageIndicator = config.getString("commands.help.footer.page-indicator",
                    " &7Página &b{current}&7/&b{total}");
            pageIndicator = pageIndicator.replace("{current}", String.valueOf(currentPage)).replace("{total}",
                    String.valueOf(totalPages));
            Collections.addAll(allComponents, TextComponent.fromLegacyText(messages.format(pageIndicator)));

            String separator = config.getString("commands.help.footer.separator", " &8┃");

            // Previous page (only if not on first)
            if (currentPage > 1) {
                Collections.addAll(allComponents, TextComponent.fromLegacyText(messages.format(separator)));

                String prevText = config.getString("commands.help.footer.prev-page", " &7⇦ Anterior");
                String prevHover = config.getString("commands.help.footer.prev-hover", "&7Clique para página {page}");
                prevHover = prevHover.replace("{page}", String.valueOf(currentPage - 1));

                BaseComponent[] prevComponents = TextComponent.fromLegacyText(messages.format(prevText));
                BaseComponent[] prevHoverComponents = TextComponent.fromLegacyText(messages.format(prevHover));

                for (BaseComponent component : prevComponents) {
                    component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, prevHoverComponents));
                    component.setClickEvent(
                            new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + path + " help " + (currentPage - 1)));
                }

                Collections.addAll(allComponents, prevComponents);
            }

            // Next page (only if not on last)
            if (currentPage < totalPages) {
                Collections.addAll(allComponents, TextComponent.fromLegacyText(messages.format(separator)));

                String nextText = config.getString("commands.help.footer.next-page", " &7Próxima ⇨");
                String nextHover = config.getString("commands.help.footer.next-hover", "&7Clique para página {page}");
                nextHover = nextHover.replace("{page}", String.valueOf(currentPage + 1));

                BaseComponent[] nextComponents = TextComponent.fromLegacyText(messages.format(nextText));
                BaseComponent[] nextHoverComponents = TextComponent.fromLegacyText(messages.format(nextHover));

                for (BaseComponent component : nextComponents) {
                    component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, nextHoverComponents));
                    component.setClickEvent(
                            new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + path + " help " + (currentPage + 1)));
                }

                Collections.addAll(allComponents, nextComponents);
            }

            player.spigot().sendMessage(allComponents.toArray(new BaseComponent[0]));
        } else {
            String pageIndicator = config.getString("commands.help.footer.page-indicator",
                    " &7Página &b{current}&7/&b{total}");
            messages.sendRaw(sender, pageIndicator.replace("{current}", String.valueOf(currentPage)).replace("{total}",
                    String.valueOf(totalPages)));
        }
    }
}
