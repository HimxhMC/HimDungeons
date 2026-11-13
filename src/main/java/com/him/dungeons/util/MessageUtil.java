package com.him.dungeons.util;

import com.him.dungeons.HimDungeons;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MessageUtil {

    private MessageUtil() {}

    private static String prefixFor(Player player) {
        if (player != null && player.getWorld() != null) {
            HimDungeons p = HimDungeons.getInstance();
            if (p != null) {
                String worldPrefix = p.getWorldPrefix(player.getWorld().getName());
                if (worldPrefix != null) return worldPrefix;
            }
        }
        return LanguageManager.getPrefix();
    }

    public static void sendMessage(Player player, String key, Object... kv) {
        if (player == null) return;
        player.sendMessage(prefixFor(player) + ChatColor.GREEN + LanguageManager.get(player, key, kv));
    }

    public static void sendError(Player player, String key, Object... kv) {
        if (player == null) return;
        player.sendMessage(prefixFor(player) + ChatColor.RED + LanguageManager.get(player, key, kv));
    }

    public static void sendColored(Player player, ChatColor color, String key, Object... kv) {
        if (player == null) return;
        String c = (color == null) ? "" : color.toString();
        player.sendMessage(prefixFor(player) + c + LanguageManager.get(player, key, kv));
    }

    public static void sendMessage(CommandSender sender, String key, Object... kv) {
        if (sender == null) return;
        if (sender instanceof Player p) { sendMessage(p, key, kv); return; }
        sender.sendMessage(LanguageManager.getPrefix() + ChatColor.GREEN + LanguageManager.get(key, kv));
    }

    public static void sendError(CommandSender sender, String key, Object... kv) {
        if (sender == null) return;
        if (sender instanceof Player p) { sendError(p, key, kv); return; }
        sender.sendMessage(LanguageManager.getPrefix() + ChatColor.RED + LanguageManager.get(key, kv));
    }

    public static void sendRaw(Player player, String raw) {
        if (player == null || raw == null) return;
        player.sendMessage(raw);
    }

    public static void sendRawError(Player player, String raw) {
        if (player == null || raw == null) return;
        player.sendMessage(ChatColor.RED + raw);
    }
}