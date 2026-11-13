package com.him.dungeons.util;

import com.him.dungeons.HimDungeons;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 胜利数据存储（YAML，可读文件）。
 * 路径：plugins/HimDungeons/<地牢名>/stats.yml
 *
 * <pre>
 * players:
 *   &lt;uuid&gt;:
 *     name: "Steve"
 *     wins: 5
 *     firstWinTime: 1699999999000
 *     lastWinTime: 1700000000000
 * </pre>
 *
 * 与背包备份完全解耦 —— 恢复/删除背包永远不会影响此文件。
 */
public final class WinStorage {

    private static final String FILE_NAME = "stats.yml";

    private WinStorage() {}

    private static File getFile(String dungeonName) {
        return new File(HimDungeons.getInstance().getDataFolder(), dungeonName + "/" + FILE_NAME);
    }

    private static YamlConfiguration load(String dungeonName) {
        File f = getFile(dungeonName);
        return f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
    }

    private static void save(String dungeonName, YamlConfiguration yaml) {
        File f = getFile(dungeonName);
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            HimDungeons.getInstance().getLogger().warning(
        LanguageManager.get("log.win.mkdir-fail", "path", parent.getPath()));
        }
        try {
            yaml.save(f);
        } catch (IOException e) {
            HimDungeons.getInstance().getLogger().warning(
        LanguageManager.get("log.win.save-fail", "error", e.getMessage()));
        }
    }

    /** 增加一次胜利（同步写，通关频率极低，无需异步） */
    public static void incrementWin(String dungeonName, UUID uuid, String playerName) {
        if (dungeonName == null || uuid == null) return;
        YamlConfiguration yaml = load(dungeonName);
        String base = "players." + uuid;
        int wins = yaml.getInt(base + ".wins", 0) + 1;
        yaml.set(base + ".wins", wins);
        yaml.set(base + ".name", playerName == null ? "" : playerName);
        yaml.set(base + ".lastWinTime", System.currentTimeMillis());
        if (!yaml.contains(base + ".firstWinTime")) {
            yaml.set(base + ".firstWinTime", System.currentTimeMillis());
        }
        save(dungeonName, yaml);
    }

    public static int getWins(String dungeonName, UUID uuid) {
        if (dungeonName == null || uuid == null) return 0;
        return load(dungeonName).getInt("players." + uuid + ".wins", 0);
    }

    public static String getName(String dungeonName, UUID uuid) {
        if (dungeonName == null || uuid == null) return "";
        return load(dungeonName).getString("players." + uuid + ".name", "");
    }
    /** 记录最快通关时间（毫秒），只在比已有记录更短时更新 */
    public static void recordFastestTime(String dungeonName, UUID uuid, long millis) {
        if (dungeonName == null || uuid == null || millis <= 0) return;
        YamlConfiguration yaml = load(dungeonName);
        String base = "players." + uuid;
        long existing = yaml.getLong(base + ".fastestTime", Long.MAX_VALUE);
        if (millis < existing) {
            yaml.set(base + ".fastestTime", millis);
            save(dungeonName, yaml);
        }
    }

    public static long getFastestTime(String dungeonName, UUID uuid) {
        if (dungeonName == null || uuid == null) return -1;
        return load(dungeonName).getLong("players." + uuid + ".fastestTime", -1);
    }
    public static Map<UUID, Integer> getAllWins(String dungeonName) {
        Map<UUID, Integer> result = new HashMap<>();
        YamlConfiguration yaml = load(dungeonName);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return result;
        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int wins = players.getInt(key + ".wins", 0);
                if (wins > 0) result.put(uuid, wins);
            } catch (Exception ignored) {}
        }
        return result;
    }

    public static Map<UUID, Long> getAllFastestTimes(String dungeonName) {
        Map<UUID, Long> result = new HashMap<>();
        YamlConfiguration yaml = load(dungeonName);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return result;
        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long t = players.getLong(key + ".fastestTime", -1);
                if (t > 0) result.put(uuid, t);
            } catch (Exception ignored) {}
        }
        return result;
    }
}