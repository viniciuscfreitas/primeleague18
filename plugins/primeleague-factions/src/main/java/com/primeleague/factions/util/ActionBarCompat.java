package com.primeleague.factions.util;

import org.bukkit.entity.Player;

/**
 * ActionBar sender compatible with Paper 1.8.8
 * Grug Brain: Usa NMS v1_8_R3 quando necessário, fallback silencioso
 */
public final class ActionBarCompat {

    private ActionBarCompat() {}

    /**
     * Envia ActionBar para player (compatível com Paper 1.8.8)
     */
    public static void send(Player player, String message) {
        if (player == null || !player.isOnline()) return;

        try {
            // NMS v1_8_R3 para ActionBar (PacketPlayOutChat com byte 2 = GAME_INFO)
            // Grug Brain: Paper 1.8.8 não tem API para ActionBar, usar NMS diretamente
            Class<?> ichat = Class.forName("net.minecraft.server.v1_8_R3.IChatBaseComponent");
            Class<?> serializer = Class.forName("net.minecraft.server.v1_8_R3.IChatBaseComponent$ChatSerializer");
            Class<?> packetChat = Class.forName("net.minecraft.server.v1_8_R3.PacketPlayOutChat");
            Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer");
            Class<?> entityPlayer = Class.forName("net.minecraft.server.v1_8_R3.EntityPlayer");
            Class<?> playerConnection = Class.forName("net.minecraft.server.v1_8_R3.PlayerConnection");
            Class<?> packet = Class.forName("net.minecraft.server.v1_8_R3.Packet");

            Object handle = craftPlayer.getMethod("getHandle").invoke(player);
            Object connection = entityPlayer.getField("playerConnection").get(handle);

            // Converter mensagem para JSON
            String json = "{\"text\":\"" + escape(message) + "\"}";
            Object chatComponent = serializer.getMethod("a", String.class).invoke(null, json);

            // Criar packet com byte 2 = GAME_INFO (ActionBar)
            // PacketPlayOutChat(IChatBaseComponent, byte) - byte 2 = ActionBar
            Object chatPacket = packetChat.getConstructor(ichat, byte.class)
                .newInstance(chatComponent, (byte) 2);

            // Enviar packet
            playerConnection.getMethod("sendPacket", packet).invoke(connection, chatPacket);
        } catch (Throwable ignored) {
            // Fallback: enviar mensagem normal no chat (melhor que nada)
            player.sendMessage(message);
        }
    }

    private static String escape(String input) {
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
    }
}

