package com.afterlands.core.input.impl;

import com.afterlands.core.input.InputRequest;
import com.afterlands.core.input.InputType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Captura input via bigorna (rename de item).
 *
 * <p><b>Abertura:</b> Usa NMS reflection para criar um ContainerAnvil virtual.</p>
 * <p><b>Captura:</b> {@code InventoryClickEvent} no slot 2 (resultado) —
 * lê o display name do item renomeado.</p>
 * <p><b>Cancelamento:</b> {@code InventoryCloseEvent} sem clicar no slot de resultado.</p>
 *
 * <p><b>Thread Safety:</b> Deve ser chamado na main thread.</p>
 */
final class AnvilInputHandler implements Listener {

    private final DefaultInputService service;
    private final Plugin plugin;
    private final String nmsVersion;

    AnvilInputHandler(@NotNull DefaultInputService service, @NotNull Plugin plugin) {
        this.service = service;
        this.plugin = plugin;
        this.nmsVersion = detectNmsVersion();
    }

    private String detectNmsVersion() {
        try {
            return plugin.getServer().getClass().getPackage().getName().split("\\.")[3];
        } catch (Exception e) {
            return "v1_8_R3";
        }
    }

    // ==================== Open ====================

    void openAnvil(@NotNull Player player, @NotNull InputRequest request) {
        try {
            openAnvilNMS(player, request);
        } catch (Exception e) {
            service.getLogger().log(Level.WARNING,
                    "[InputService] Failed to open anvil GUI for " + player.getName() + ": " + e.getMessage());
            if (service.isDebug()) {
                e.printStackTrace();
            }
            service.cancelInputSilent(player.getUniqueId());
        }
    }

    private void openAnvilNMS(@NotNull Player player, @NotNull InputRequest request) throws Exception {
        String v = nmsVersion;

        // Resolve NMS classes
        Class<?> blockPosClass     = nmsClass(v, "BlockPosition");
        Class<?> worldClass        = nmsClass(v, "World");
        Class<?> entityHumanClass  = nmsClass(v, "EntityHuman");
        Class<?> iInventoryClass   = nmsClass(v, "IInventory");
        Class<?> containerAnvilClass = nmsClass(v, "ContainerAnvil");

        // NMS player handle
        Object nmsPlayer = player.getClass().getMethod("getHandle").invoke(player);

        // NMS World (field in Entity hierarchy)
        Object nmsWorld = getFieldFromHierarchy(nmsPlayer, "world");

        // Player inventory
        Object playerInventory = getFieldFromHierarchy(nmsPlayer, "inventory");

        // Block position (virtual — far below the world)
        Object pos = blockPosClass.getConstructor(int.class, int.class, int.class)
                .newInstance(0, 0, 0);

        // Create ContainerAnvil(IInventory, World, BlockPosition, EntityHuman)
        Constructor<?> anvilCtor = containerAnvilClass.getConstructor(
                iInventoryClass, worldClass, blockPosClass, entityHumanClass);
        Object anvil = anvilCtor.newInstance(playerInventory, nmsWorld, pos, nmsPlayer);

        // Disable reachability check so the container works without a real anvil block
        setFieldInHierarchy(anvil, "checkReachable", false);

        // Increment container window counter
        Field counterField = getFieldFromHierarchyRaw(nmsPlayer.getClass(), "containerCounter");
        counterField.setAccessible(true);
        int windowId = (int) counterField.get(nmsPlayer) + 1;
        if (windowId > 100) windowId = 1;
        counterField.set(nmsPlayer, windowId);

        // Set window ID on container
        setFieldInHierarchy(anvil, "windowId", windowId);

        // Register slot listener (ICrafting in 1.8.8)
        addSlotListener(v, anvil, nmsPlayer);

        // Set player's active container
        Field activeContainerField = getFieldFromHierarchyRaw(nmsPlayer.getClass(), "activeContainer");
        activeContainerField.setAccessible(true);
        activeContainerField.set(nmsPlayer, anvil);

        // Get the Bukkit InventoryView from the container
        Method getBukkitViewMethod = anvil.getClass().getMethod("getBukkitView");
        Object craftView = getBukkitViewMethod.invoke(anvil);
        Inventory topInventory = ((org.bukkit.inventory.InventoryView) craftView).getTopInventory();

        // Place prompt item in slot 0
        topInventory.setItem(0, createPromptItem(request));

        // Send PacketPlayOutOpenWindow to the client
        sendOpenWindowPacket(v, nmsPlayer, windowId);

        if (service.isDebug()) {
            service.getLogger().info("[InputService] Opened anvil for " + player.getName()
                    + " (windowId=" + windowId + ", version=" + v + ")");
        }
    }

    private void addSlotListener(String v, Object anvil, Object nmsPlayer) {
        // ICrafting was renamed to IContainerListener in some versions
        for (String listenerName : new String[]{"ICrafting", "IContainerListener"}) {
            try {
                Class<?> listenerClass = nmsClass(v, listenerName);
                anvil.getClass().getMethod("addSlotListener", listenerClass).invoke(anvil, nmsPlayer);
                return;
            } catch (Exception ignored) {}
        }
        service.getLogger().warning("[InputService] Could not register slot listener on ContainerAnvil");
    }

    private void sendOpenWindowPacket(String v, Object nmsPlayer, int windowId) throws Exception {
        Class<?> packetClass        = nmsClass(v, "PacketPlayOutOpenWindow");
        Class<?> iChatClass         = nmsClass(v, "IChatBaseComponent");
        Class<?> chatTextClass      = nmsClass(v, "ChatComponentText");
        Class<?> packetBaseClass    = nmsClass(v, "Packet");

        Object title = chatTextClass.getConstructor(String.class).newInstance("Rename");
        Object packet = packetClass.getConstructor(int.class, String.class, iChatClass, int.class)
                .newInstance(windowId, "minecraft:anvil", title, 0);

        Object playerConnection = getFieldFromHierarchyRaw(nmsPlayer.getClass(), "playerConnection")
                .get(nmsPlayer);
        playerConnection.getClass().getMethod("sendPacket", packetBaseClass)
                .invoke(playerConnection, packet);
    }

    private ItemStack createPromptItem(@NotNull InputRequest request) {
        String promptText = request.promptMessage() != null
                ? ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', request.promptMessage()))
                : "Digite aqui";
        ItemStack item = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(promptText);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ==================== Events ====================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        ActiveInputSession session = service.getSession(uuid);
        if (session == null || session.request().type() != InputType.ANVIL) return;

        // Only handle clicks on the result slot (slot 2)
        if (event.getRawSlot() != 2) return;

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) return;

        event.setCancelled(true);

        // Extract renamed text
        String input = "";
        if (result.hasItemMeta() && result.getItemMeta().hasDisplayName()) {
            input = result.getItemMeta().getDisplayName();
        }

        player.closeInventory();

        // Validate
        if (session.request().validator() != null) {
            String error = session.request().validator().validate(input);
            if (error != null) {
                int attempt = session.getAndIncrementRetry();
                if (attempt >= session.request().maxRetries()) {
                    player.sendMessage(DefaultInputService.colorize("&cNúmero máximo de tentativas excedido."));
                    service.cancelInputSilent(uuid);
                } else {
                    String msg = session.request().validationFailMessage() != null
                            ? session.request().validationFailMessage().replace("{error}", error)
                            : "&cEntrada inválida: &f" + error;
                    player.sendMessage(DefaultInputService.colorize(msg));
                    // Re-open after 2 ticks
                    service.getScheduler().runLaterSync(() -> openAnvil(player, session.request()), 2L);
                }
                return;
            }
        }

        String finalInput = input;
        service.completeSession(uuid, finalInput);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        ActiveInputSession session = service.getSession(uuid);
        if (session == null || session.request().type() != InputType.ANVIL) return;

        // Defer 1 tick so click handler (which calls closeInventory) can complete first
        service.getScheduler().runLaterSync(() -> {
            if (service.hasActiveInput(player)) {
                service.cancelInputWithMessage(uuid);
            }
        }, 1L);
    }

    // ==================== NMS Reflection Helpers ====================

    private static Class<?> nmsClass(String version, String name) throws ClassNotFoundException {
        return Class.forName("net.minecraft.server." + version + "." + name);
    }

    private static Object getFieldFromHierarchy(Object obj, String fieldName) throws Exception {
        Field f = getFieldFromHierarchyRaw(obj.getClass(), fieldName);
        f.setAccessible(true);
        return f.get(obj);
    }

    private static Field getFieldFromHierarchyRaw(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field '" + fieldName + "' not found in class hierarchy");
    }

    private static void setFieldInHierarchy(Object obj, String fieldName, Object value) throws Exception {
        Field f = getFieldFromHierarchyRaw(obj.getClass(), fieldName);
        f.setAccessible(true);
        f.set(obj, value);
    }
}
