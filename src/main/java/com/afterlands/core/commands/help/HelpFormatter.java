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

        // Build help entries: either groups or individual subcommands
        List<HelpEntry> entries = buildHelpEntries(sender, root);

        int totalItems = entries.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) pageSize));
        page = Math.max(1, Math.min(page, totalPages));

        // Header
        sendHeader(sender, root, path);

        if (entries.isEmpty()) {
            String usage = root.generateUsage(path);
            messages.send(sender, "commands.help.usage", "usage", usage);
        } else {
            int startIndex = (page - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalItems);
            List<HelpEntry> pageItems = entries.subList(startIndex, endIndex);

            for (HelpEntry entry : pageItems) {
                sendHelpEntry(sender, path, entry);
            }
        }

        messages.sendRaw(sender, " ");
        // Footer (only if multiple pages)
        if (totalPages > 1) {
            sendPaginationFooter(sender, path, page, totalPages);
            messages.sendRaw(sender, " ");
        }
    }

    /**
     * Sends help for a specific command group.
     * Shows only subcommands that start with the group prefix.
     *
     * @param sender      The command sender
     * @param root        The root command node
     * @param label       The command label (e.g., "animations")
     * @param groupPrefix The group prefix (e.g., "placement")
     * @param page        The page number
     */
    public void sendGroupHelp(@NotNull CommandSender sender, @NotNull RootNode root,
            @NotNull String label, @NotNull String groupPrefix, int page) {
        int pageSize = config.getInt("commands.help.page-size", DEFAULT_PAGE_SIZE);
        String groupPath = label + " " + groupPrefix;

        // Build entries for this group only
        List<HelpEntry> entries = new ArrayList<>();
        for (SubNode child : root.children().values()) {
            // Check permission
            if (child.permission() != null && !sender.hasPermission(child.permission())) {
                continue;
            }

            String childName = child.name();
            // Only include subcommands that start with "groupPrefix "
            if (childName.startsWith(groupPrefix + " ")) {
                // Remove the group prefix from the display name
                String shortName = childName.substring(groupPrefix.length() + 1);
                entries.add(new HelpEntry(shortName, child.description(), child.usage(), child.usageHelp(), false, 0));
            }
        }

        // Sort alphabetically
        entries.sort(Comparator.comparing(e -> e.name));

        int totalItems = entries.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) pageSize));
        page = Math.max(1, Math.min(page, totalPages));

        // Header
        sendGroupHeader(sender, root, groupPrefix, groupPath);

        if (entries.isEmpty()) {
            messages.sendRaw(sender, " &7Nenhum subcomando encontrado.");
        } else {
            int startIndex = (page - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalItems);
            List<HelpEntry> pageItems = entries.subList(startIndex, endIndex);

            for (HelpEntry entry : pageItems) {
                sendHelpEntry(sender, groupPath, entry);
            }
        }

        messages.sendRaw(sender, " ");
        // Footer: "← Voltar" link + pagination
        sendBackLink(sender, label);
        if (totalPages > 1) {
            sendPaginationFooter(sender, groupPath, page, totalPages);
        }
        messages.sendRaw(sender, " ");
    }

    /**
     * Sends a clickable "← Voltar" link to return to root help.
     */
    private void sendBackLink(@NotNull CommandSender sender, @NotNull String rootLabel) {
        if (sender instanceof Player player) {
            String text = messages.format(" &8← &7Voltar para &b/" + rootLabel + " help");
            String hoverText = messages.format("&a► Clique para voltar");

            BaseComponent[] components = TextComponent.fromLegacyText(text);
            BaseComponent[] hoverComponents = TextComponent.fromLegacyText(hoverText);

            for (BaseComponent component : components) {
                component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponents));
                component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + rootLabel + " help"));
            }

            player.spigot().sendMessage(components);
        } else {
            messages.sendRaw(sender, " &8← &7Voltar para &b/" + rootLabel + " help");
        }
    }

    private void sendGroupHeader(@NotNull CommandSender sender, @NotNull RootNode root,
            @NotNull String groupName, @NotNull String path) {
        String plugin = root.helpPrefix() != null ? root.helpPrefix() : root.name().toUpperCase(Locale.ROOT);
        String capitalizedGroup = capitalize(groupName);
        String rootLabel = root.name();

        // Spacer
        messages.sendRaw(sender, " ");

        // Breadcrumb line with clickable "Ajuda" link
        if (sender instanceof Player player) {
            // Build: " PLUGIN ┃ Ajuda > groupName - description"
            TextComponent prefix = new TextComponent(messages.format(" &b&l" + plugin + " &8┃ "));

            String breadcrumbText = messages.format("&bAjuda");
            BaseComponent[] breadcrumbComponents = TextComponent.fromLegacyText(breadcrumbText);
            BaseComponent[] breadcrumbHover = TextComponent
                    .fromLegacyText(messages.format("&a► Voltar para ajuda principal"));
            for (BaseComponent bc : breadcrumbComponents) {
                bc.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, breadcrumbHover));
                bc.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + rootLabel + " help"));
            }

            TextComponent separator = new TextComponent(messages.format(" &8» "));
            TextComponent groupPart = new TextComponent(messages.format("&f" + capitalizedGroup));

            List<BaseComponent> all = new ArrayList<>();
            all.add(prefix);
            Collections.addAll(all, breadcrumbComponents);
            all.add(separator);
            all.add(groupPart);

            player.spigot().sendMessage(all.toArray(new BaseComponent[0]));
        } else {
            messages.sendRaw(sender, " &b&l" + plugin + " &8┃ &bAjuda &8» &f" + capitalizedGroup);
        }

        messages.sendRaw(sender, " &c<> &7obrigatório &8┃ &d[] &7opcional");
        messages.sendRaw(sender, " ");
    }

    /**
     * Builds help entries, collapsing groups into single entries.
     */
    private List<HelpEntry> buildHelpEntries(@NotNull CommandSender sender, @NotNull RootNode root) {
        Map<String, String> groups = root.groups();
        Set<String> groupedPrefixes = groups.keySet();
        Set<String> processedGroups = new LinkedHashSet<>();
        List<HelpEntry> entries = new ArrayList<>();

        for (SubNode child : root.children().values()) {
            // Check permission
            if (child.permission() != null && !sender.hasPermission(child.permission())) {
                continue;
            }

            String childName = child.name();

            // Check if this child belongs to a group
            String matchedGroup = null;
            for (String prefix : groupedPrefixes) {
                if (childName.startsWith(prefix + " ") || childName.equals(prefix)) {
                    matchedGroup = prefix;
                    break;
                }
            }

            if (matchedGroup != null) {
                // Add group entry only once
                if (!processedGroups.contains(matchedGroup)) {
                    processedGroups.add(matchedGroup);
                    // Count subcommands in this group
                    int subCount = countGroupSubcommands(sender, root, matchedGroup);
                    entries.add(new HelpEntry(
                            matchedGroup,
                            groups.get(matchedGroup),
                            null, // no usage for groups
                            null, // no usageHelp for groups
                            true, // is group
                            subCount));
                }
            } else {
                // Regular subcommand
                entries.add(new HelpEntry(
                        childName,
                        child.description(),
                        child.usage(),
                        child.usageHelp(),
                        false,
                        0));
            }
        }

        // Sort: groups first, then alphabetically
        entries.sort((a, b) -> {
            if (a.isGroup != b.isGroup) {
                return a.isGroup ? -1 : 1;
            }
            return a.name.compareTo(b.name);
        });

        return entries;
    }

    /**
     * Represents a help entry (either a group or a subcommand).
     */
    private record HelpEntry(String name, String description, String usage, String usageHelp, boolean isGroup,
            int subCount) {
    }

    /**
     * Sends a single help entry.
     */
    private void sendHelpEntry(@NotNull CommandSender sender, @NotNull String path, @NotNull HelpEntry entry) {
        String baseCommand = "/" + path + " " + entry.name;
        String displayCommand = baseCommand;
        String suggestCommand = baseCommand;

        if (entry.isGroup) {
            // Show [N cmds] or [!] based on config
            boolean showSubcount = config.getBoolean("commands.help.show-group-subcount", false);
            String marker;
            if (showSubcount && entry.subCount > 0) {
                marker = "&a[" + entry.subCount + " cmds]";
            } else {
                marker = "&a[!]";
            }
            displayCommand += " " + marker;
            suggestCommand += " help"; // Suggest help for the group
        } else if (entry.usageHelp != null && !entry.usageHelp.isEmpty()) {
            // Use colored usageHelp for display
            displayCommand += " " + entry.usageHelp;
            suggestCommand += " " + stripColorCodes(entry.usageHelp);
        } else if (entry.usage != null && !entry.usage.isEmpty()) {
            // Fallback to plain usage
            displayCommand += " " + entry.usage;
            suggestCommand += " " + stripColorCodes(entry.usage);
        }

        String description = entry.description != null ? entry.description : "";

        String entryFormat = config.getString("commands.help.subcommand-entry", " &b▪ &f{command} &7- {description}");
        String hoverText = entry.isGroup
                ? config.getString("commands.help.group-hover", "&a► Clique para ver subcomandos")
                : config.getString("commands.help.subcommand-hover", "&a► Clique para sugerir");

        String formatted = entryFormat.replace("{command}", displayCommand).replace("{description}", description);

        if (sender instanceof Player player) {
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

    private void sendHeader(@NotNull CommandSender sender, @NotNull RootNode root, @NotNull String path) {
        String plugin = root.helpPrefix() != null ? root.helpPrefix() : root.name().toUpperCase(Locale.ROOT);

        List<String> headerLines = config.getStringList("commands.help.header");
        if (headerLines.isEmpty()) {
            boolean showSubcount = config.getBoolean("commands.help.show-group-subcount", false);
            String legend = showSubcount
                    ? " &a[N] &7sub-cmds &8┃ &c<> &7obrigatório &8┃ &d[] &7opcional"
                    : " &a[!] &7sub-cmds &8┃ &c<> &7obrigatório &8┃ &d[] &7opcional";
            headerLines = List.of(
                    " ",
                    " &b&l{plugin} &8┃ &fAjuda - &b{command}",
                    legend,
                    " ");
        }

        for (String line : headerLines) {
            String formatted = line.replace("{plugin}", plugin).replace("{command}", "/" + path);
            messages.sendRaw(sender, formatted);
        }
    }

    /**
     * Counts the number of subcommands belonging to a group
     * that the sender has permission to see.
     */
    private int countGroupSubcommands(@NotNull CommandSender sender, @NotNull RootNode root,
            @NotNull String groupPrefix) {
        int count = 0;
        for (SubNode child : root.children().values()) {
            if (child.permission() != null && !sender.hasPermission(child.permission())) {
                continue;
            }
            if (child.name().startsWith(groupPrefix + " ")) {
                count++;
            }
        }
        return count;
    }

    private String stripColorCodes(String text) {
        return text.replaceAll("&[0-9a-fk-or]", "");
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty())
            return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
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
