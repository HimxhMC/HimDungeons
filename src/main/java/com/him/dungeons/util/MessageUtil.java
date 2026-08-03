package com.him.dungeons.util;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * 消息发送工具类
 * 符合 mczfw.com/blog/26408.html 规范
 */
public final class MessageUtil {

    private MessageUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 向玩家发送普通消息（绿色前缀）
     *
     * @param player 目标玩家
     * @param msg    消息内容
     */
    public static void sendMessage(Player player, String msg) {
        if (player == null) return;
        player.sendMessage(ChatColor.GREEN + msg);
    }

    /**
     * 向玩家发送错误消息（红色前缀）
     *
     * @param player 目标玩家
     * @param msg    错误内容
     */
    public static void sendError(Player player, String msg) {
        if (player == null) return;
        player.sendMessage(ChatColor.RED + msg);
    }

    /**
     * 向玩家发送带自定义颜色的消息
     *
     * @param player 目标玩家
     * @param color  颜色
     * @param msg    消息内容
     */
    public static void sendColored(Player player, ChatColor color, String msg) {
        if (player == null) return;
        player.sendMessage(color + msg);
    }
}