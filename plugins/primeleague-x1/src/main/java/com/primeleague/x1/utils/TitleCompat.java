package com.primeleague.x1.utils;

import org.bukkit.entity.Player;

/**
 * Minimal title sender compatible with 1.8.8.
 * Uses NMS v1_8_R3 packets when available; otherwise no-ops.
 */
public final class TitleCompat {

	private TitleCompat() {}

	public static void send(Player player, String title, String subtitle) {
		if (player == null || !player.isOnline()) return;
		try {
			// Try Paper/Spigot newer API via reflection (if present at runtime)
			try {
				player.getClass().getMethod("sendTitle", String.class, String.class).invoke(player, title, subtitle);
				return;
			} catch (NoSuchMethodException ignored) {
				// Fall through to NMS for 1.8.8
			}

			// NMS v1_8_R3 title packets
			Class<?> ichat = Class.forName("net.minecraft.server.v1_8_R3.IChatBaseComponent");
			Class<?> serializer = Class.forName("net.minecraft.server.v1_8_R3.IChatBaseComponent$ChatSerializer");
			Class<?> enumTitle = Class.forName("net.minecraft.server.v1_8_R3.PacketPlayOutTitle$EnumTitleAction");
			Class<?> packetTitle = Class.forName("net.minecraft.server.v1_8_R3.PacketPlayOutTitle");
			Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer");
			Class<?> entityPlayer = Class.forName("net.minecraft.server.v1_8_R3.EntityPlayer");
			Class<?> playerConnection = Class.forName("net.minecraft.server.v1_8_R3.PlayerConnection");
			Class<?> packet = Class.forName("net.minecraft.server.v1_8_R3.Packet");

			Object handle = craftPlayer.getMethod("getHandle").invoke(player);
			Object connection = entityPlayer.getField("playerConnection").get(handle);

			// Times (fadeIn, stay, fadeOut) - optional
			try {
				Object timesPacket = packetTitle
					.getConstructor(enumTitle, ichat, int.class, int.class, int.class)
					.newInstance(enumTitle.getField("TIMES").get(null), null, 5, 40, 5);
				playerConnection.getMethod("sendPacket", packet).invoke(connection, timesPacket);
			} catch (Throwable ignored) {
			}

			if (title != null && !title.isEmpty()) {
				Object titleJson = serializer.getMethod("a", String.class)
					.invoke(null, "{\"text\":\"" + escape(title) + "\"}");
				Object titlePacket = packetTitle
					.getConstructor(enumTitle, ichat)
					.newInstance(enumTitle.getField("TITLE").get(null), titleJson);
				playerConnection.getMethod("sendPacket", packet).invoke(connection, titlePacket);
			}

			if (subtitle != null && !subtitle.isEmpty()) {
				Object subtitleJson = serializer.getMethod("a", String.class)
					.invoke(null, "{\"text\":\"" + escape(subtitle) + "\"}");
				Object subtitlePacket = packetTitle
					.getConstructor(enumTitle, ichat)
					.newInstance(enumTitle.getField("SUBTITLE").get(null), subtitleJson);
				playerConnection.getMethod("sendPacket", packet).invoke(connection, subtitlePacket);
			}
		} catch (Throwable ignored) {
			// Silently ignore on unsupported servers
		}
	}

	private static String escape(String input) {
		return input.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}


