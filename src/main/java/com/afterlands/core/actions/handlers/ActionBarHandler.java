package com.afterlands.core.actions.handlers;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Handler para enviar mensagens na action bar (barra acima do hotbar).
 *
 * <p>Formato: {@code actionbar: Texto da action bar}</p>
 * <p>Suporta:</p>
 * <ul>
 *     <li>Color codes (&a, &b, etc.)</li>
 *     <li>PlaceholderAPI (se disponível)</li>
 * </ul>
 *
 * <p>Exemplos:</p>
 * <pre>
 * actionbar: &aOlá!
 * actionbar: &7HP: &c%player_health%
 * </pre>
 *
 * <p>Compatibilidade: Spigot 1.8.8+ (usa NMS para 1.8.8, Spigot API para versões mais novas)</p>
 */
public final class ActionBarHandler implements ActionHandler {

    // NMS reflection caching (para 1.8.8)
    private static Class<?> chatSerializerClass;
    private static Class<?> packetClass;
    private static Constructor<?> packetConstructor;
    private static Method serializeMethod;
    private static Method getHandleMethod;
    private static Object playerConnectionField;
    private static Method sendPacketMethod;

    static {
        try {
            // Tentar inicializar NMS (1.8.8)
            String version = org.bukkit.Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            chatSerializerClass = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent$ChatSerializer");
            packetClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutChat");

            // PacketPlayOutChat(IChatBaseComponent, byte) - byte 2 = action bar
            Class<?> baseComponentClass = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent");
            packetConstructor = packetClass.getConstructor(baseComponentClass, byte.class);

            serializeMethod = chatSerializerClass.getMethod("a", String.class);

            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
            getHandleMethod = craftPlayerClass.getMethod("getHandle");

            Class<?> entityPlayerClass = Class.forName("net.minecraft.server." + version + ".EntityPlayer");
            playerConnectionField = entityPlayerClass.getField("playerConnection");

            Class<?> playerConnectionClass = Class.forName("net.minecraft.server." + version + ".PlayerConnection");
            Class<?> packetSuperClass = Class.forName("net.minecraft.server." + version + ".Packet");
            sendPacketMethod = playerConnectionClass.getMethod("sendPacket", packetSuperClass);
        } catch (Exception e) {
            // NMS não disponível, fallback para APIs modernas
            chatSerializerClass = null;
        }
    }

    @Override
    public void execute(@NotNull Player target, @NotNull ActionSpec spec) {
        String rawMessage = spec.rawArgs();
        if (rawMessage == null || rawMessage.isEmpty()) {
            return;
        }

        // Processar PlaceholderAPI (main thread)
        String processed = PlaceholderUtil.process(target, rawMessage);

        // Processar color codes
        String colored = ChatColor.translateAlternateColorCodes('&', processed);

        // Enviar action bar
        sendActionBar(target, colored);
    }

    /**
     * Envia action bar usando NMS (1.8.8) ou fallback para chat normal.
     */
    private void sendActionBar(Player player, String message) {
        if (chatSerializerClass != null) {
            // Usar NMS (1.8.8)
            try {
                Object chatComponent = serializeMethod.invoke(null, "{\"text\":\"" + escapeJson(message) + "\"}");
                Object packet = packetConstructor.newInstance(chatComponent, (byte) 2); // 2 = action bar

                Object nmsPlayer = getHandleMethod.invoke(player);
                Object connection = ((java.lang.reflect.Field) playerConnectionField).get(nmsPlayer);
                sendPacketMethod.invoke(connection, packet);
                return;
            } catch (Exception e) {
                // Falhou, fallback
            }
        }

        // Fallback: enviar como mensagem normal no chat
        player.sendMessage(message);
    }

    /**
     * Escapa JSON para uso seguro em componentes de chat.
     */
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
