package com.afterlands.core.input.impl;

import com.afterlands.core.input.InputType;
import com.afterlands.core.input.InputRequest;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Captura input via placa virtual usando ProtocolLib.
 *
 * <p><b>Requer:</b> ProtocolLib no servidor.</p>
 *
 * <p><b>Fluxo:</b></p>
 * <ol>
 *     <li>Envia {@code BLOCK_CHANGE} para criar uma placa virtual em Y=255</li>
 *     <li>Envia {@code UPDATE_SIGN} com linhas pré-preenchidas</li>
 *     <li>Envia {@code OPEN_SIGN_ENTITY} para abrir o editor de placa</li>
 *     <li>Escuta {@code UPDATE_SIGN} do cliente com o texto digitado</li>
 *     <li>Restaura o bloco original com {@code BLOCK_CHANGE} para AIR</li>
 * </ol>
 */
final class SignInputHandler {

    private final DefaultInputService service;
    private final Plugin plugin;
    private final PacketAdapter packetListener;

    SignInputHandler(@NotNull DefaultInputService service, @NotNull Plugin plugin) {
        this.service = service;
        this.plugin = plugin;
        this.packetListener = createPacketListener();
        ProtocolLibrary.getProtocolManager().addPacketListener(packetListener);
    }

    // ==================== Open ====================

    void openSign(@NotNull Player player, @NotNull InputRequest request) {
        try {
            Location loc = player.getLocation().clone();
            int x = loc.getBlockX();
            int y = 255;
            int z = loc.getBlockZ();

            sendSignBlock(player, x, y, z);
            sendUpdateSign(player, x, y, z, request.signLines());
            sendOpenSignEditor(player, x, y, z);

            if (service.isDebug()) {
                service.getLogger().info("[InputService] Opened sign for " + player.getName()
                        + " at " + x + "," + y + "," + z);
            }
        } catch (Exception e) {
            service.getLogger().log(Level.WARNING,
                    "[InputService] Failed to open sign for " + player.getName(), e);
            service.cancelInputSilent(player.getUniqueId());
        }
    }

    private void sendSignBlock(Player player, int x, int y, int z) throws Exception {
        PacketContainer packet = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.BLOCK_CHANGE);
        packet.getBlockPositionModifier().write(0, new BlockPosition(x, y, z));
        // SIGN_POST is the standing sign material in 1.8.8
        packet.getBlockData().write(0,
                com.comphenix.protocol.wrappers.WrappedBlockData.createData(Material.SIGN_POST));
        ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
    }

    private void sendUpdateSign(Player player, int x, int y, int z, String[] lines) throws Exception {
        PacketContainer packet = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.UPDATE_SIGN);
        packet.getBlockPositionModifier().write(0, new BlockPosition(x, y, z));

        WrappedChatComponent[] components = new WrappedChatComponent[4];
        for (int i = 0; i < 4; i++) {
            String text = (lines != null && i < lines.length) ? lines[i] : "";
            components[i] = WrappedChatComponent.fromText(text);
        }
        packet.getChatComponentArrays().write(0, components);
        ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
    }

    private void sendOpenSignEditor(Player player, int x, int y, int z) throws Exception {
        PacketContainer packet = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.OPEN_SIGN_ENTITY);
        packet.getBlockPositionModifier().write(0, new BlockPosition(x, y, z));
        ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
    }

    private void restoreBlock(Player player, int x, int y, int z) {
        try {
            PacketContainer packet = ProtocolLibrary.getProtocolManager()
                    .createPacket(PacketType.Play.Server.BLOCK_CHANGE);
            packet.getBlockPositionModifier().write(0, new BlockPosition(x, y, z));
            packet.getBlockData().write(0,
                    com.comphenix.protocol.wrappers.WrappedBlockData.createData(Material.AIR));
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception e) {
            service.getLogger().log(Level.WARNING, "[InputService] Failed to restore sign block", e);
        }
    }

    // ==================== Packet Listener ====================

    private PacketAdapter createPacketListener() {
        return new PacketAdapter(plugin, ListenerPriority.LOWEST, PacketType.Play.Client.UPDATE_SIGN) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                UUID uuid = player.getUniqueId();

                ActiveInputSession session = service.getSession(uuid);
                if (session == null || session.request().type() != InputType.SIGN) return;

                event.setCancelled(true);

                // Extract sign lines and concatenate non-empty ones
                String input = extractSignText(event.getPacket());

                // Restore virtual sign block
                Location loc = player.getLocation().clone();
                restoreBlock(player, loc.getBlockX(), 255, loc.getBlockZ());

                // Check cancel keyword
                if (input.equalsIgnoreCase(session.request().cancelKeyword())) {
                    service.getScheduler().runSync(() -> service.cancelInputWithMessage(uuid));
                    return;
                }

                // Validate
                if (session.request().validator() != null) {
                    String error = session.request().validator().validate(input);
                    if (error != null) {
                        int attempt = session.getAndIncrementRetry();
                        String finalError = error;
                        if (attempt >= session.request().maxRetries()) {
                            service.getScheduler().runSync(() -> {
                                player.sendMessage(DefaultInputService.colorize(
                                        "&cNúmero máximo de tentativas excedido."));
                                service.cancelInputSilent(uuid);
                            });
                        } else {
                            service.getScheduler().runSync(() -> {
                                String msg = session.request().validationFailMessage() != null
                                        ? session.request().validationFailMessage().replace("{error}", finalError)
                                        : "&cEntrada inválida: &f" + finalError;
                                player.sendMessage(DefaultInputService.colorize(msg));
                                openSign(player, session.request());
                            });
                        }
                        return;
                    }
                }

                String finalInput = input;
                service.getScheduler().runSync(() -> service.completeSession(uuid, finalInput));
            }
        };
    }

    private String extractSignText(com.comphenix.protocol.events.PacketContainer packet) {
        StringBuilder sb = new StringBuilder();

        // In 1.8.8, client sign packet may send string array or chat component array
        // Try string array first
        try {
            String[] lines = packet.getStringArrays().read(0);
            if (lines != null) {
                for (String line : lines) {
                    if (line != null && !line.isEmpty()) {
                        if (sb.length() > 0) sb.append(" ");
                        sb.append(line);
                    }
                }
                return sb.toString().trim();
            }
        } catch (Exception ignored) {}

        // Fallback: try chat component array
        try {
            WrappedChatComponent[] components = packet.getChatComponentArrays().read(0);
            if (components != null) {
                for (WrappedChatComponent c : components) {
                    if (c != null) {
                        String text = extractPlainText(c.getJson());
                        if (!text.isEmpty()) {
                            if (sb.length() > 0) sb.append(" ");
                            sb.append(text);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return sb.toString().trim();
    }

    /**
     * Extracts plain text from a simple ProtocolLib JSON chat component.
     * Handles the common format: {@code {"text":"content"}}.
     */
    private String extractPlainText(String json) {
        if (json == null || json.isEmpty()) return "";
        // Handle {"text":"content"} format
        int textIdx = json.indexOf("\"text\":\"");
        if (textIdx >= 0) {
            int start = textIdx + 8;
            int end = json.indexOf('"', start);
            if (end > start) {
                return json.substring(start, end);
            }
        }
        // Fallback: strip JSON syntax
        return json.replaceAll("[{}\"]", "").replace("text:", "").trim();
    }

    void unregister() {
        try {
            ProtocolLibrary.getProtocolManager().removePacketListener(packetListener);
        } catch (Exception ignored) {}
    }
}
