package com.him.dungeons.util;

import com.him.dungeons.HimDungeons;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public final class DebugUtil {
    private static boolean debugMode = false;
    private static long lastCheck = 0;
    private static final long CHECK_INTERVAL = 2000; // 2秒

    private DebugUtil() {}

    public static boolean isDebugMode() {
        long now = System.currentTimeMillis();
        if (now - lastCheck > CHECK_INTERVAL) {
            lastCheck = now;
            File debugFile = new File(HimDungeons.getInstance().getDataFolder(), "generatorDebug.txt");
            if (debugFile.exists()) {
                try {
                    String content = Files.readString(debugFile.toPath()).trim();
                    debugMode = content.equalsIgnoreCase("true");
                } catch (IOException e) {
                    debugMode = false;
                }
            } else {
                debugMode = false;
            }
        }
        return debugMode;
    }

    public static void debug(Player player, String message, Object... args) {
        if (!isDebugMode()) return;
        String formatted = String.format(message, args);
        if (player != null) {
            player.sendMessage("§7[Debug] " + formatted);
        }
        HimDungeons.getInstance().getLogger().info("[Debug] " + formatted);
    }
}