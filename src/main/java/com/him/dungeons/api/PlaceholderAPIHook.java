package com.him.dungeons.api;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.storage.LeaderboardMode;
import com.him.dungeons.storage.Period;
import com.him.dungeons.storage.PlayerStats;
import com.him.dungeons.storage.TeamSizeBucket;
import com.him.dungeons.util.InstanceSnapshot;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * HimDungeons PlaceholderAPI 扩展。
 *
 * <p>高频占位符直接读 {@code InstanceSnapshotService} 快照，完全不卡主线程。</p>
 */
public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final HimDungeons plugin;

    public PlaceholderAPIHook(HimDungeons plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "himdungeons";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Him";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** 让 PAPI 在重载时不要丢弃此扩展 */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) return "";
        UUID uuid = offlinePlayer.getUniqueId();
        String p = params.toLowerCase();

        // ==================== 高频：直接查内存快照 ====================
        // 不依赖玩家在线，但需要在地牢中
        if (p.equals("in_dungeon")) {
            return plugin.getPlayerDungeonWorld(uuid) != null ? "true" : "false";
        }

        String worldName = plugin.getPlayerDungeonWorld(uuid);
        InstanceSnapshot snap = worldName != null ? plugin.getInstanceSnapshotService().get(worldName) : null;

        if (p.equals("dungeon_name")) {
            return snap != null ? snap.dungeonName : "";
        }
        if (p.equals("remaining_seconds")) {
            return snap != null ? String.valueOf(snap.remainingSec) : "-1";
        }
        if (p.equals("room_progress")) {
            if (snap == null) return "0/0";
            return snap.roomsDone + "/" + snap.roomsTotal;
        }
        if (p.equals("team_size")) {
            return snap != null ? String.valueOf(snap.teamSize()) : "0";
        }
        if (p.equals("boss_hp_percent")) {
            if (snap == null || snap.bossHpPercent < 0) return "-1";
            return String.format("%.0f", snap.bossHpPercent);
        }

        // ==================== 中低频：查带缓存的统计 ====================
        // 格式: wins_<dungeon>, fastest_<dungeon>, rank_fastest_<dungeon>
        
        if (p.startsWith("wins_")) {
            String dungeon = params.substring(5);
            PlayerStats stats = plugin.getLeaderboardCache().playerStats(uuid, dungeon, Period.ALL);
            return String.valueOf(stats.successCount);
        }

        if (p.startsWith("runs_")) {
            String dungeon = params.substring(5);
            PlayerStats stats = plugin.getLeaderboardCache().playerStats(uuid, dungeon, Period.ALL);
            return String.valueOf(stats.totalRuns());
        }

        if (p.startsWith("fastest_")) {
            String dungeon = params.substring(8);
            PlayerStats stats = plugin.getLeaderboardCache().playerStats(uuid, dungeon, Period.ALL);
            if (stats.fastestMs == Long.MAX_VALUE) return "-";
            return formatTime(stats.fastestMs);
        }

        if (p.startsWith("rank_fastest_")) {
            String dungeon = params.substring(13);
            int rank = plugin.getRunStorage().playerRank(uuid, dungeon, LeaderboardMode.FASTEST, TeamSizeBucket.ALL, Period.ALL);
            return rank > 0 ? String.valueOf(rank) : "-";
        }

        if (p.startsWith("rank_wins_")) {
            String dungeon = params.substring(10);
            int rank = plugin.getRunStorage().playerRank(uuid, dungeon, LeaderboardMode.WINS, TeamSizeBucket.ALL, Period.ALL);
            return rank > 0 ? String.valueOf(rank) : "-";
        }

        // 未匹配到的占位符返回 null，让 PAPI 处理默认值
        return null;
    }

    // ==================== 注册辅助 ====================

    /**
     * 尝试注册 PAPI。如果未安装则静默跳过。
     */
    public static void register(HimDungeons plugin) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIHook(plugin).register();
            plugin.getLogger().info("[PlaceholderAPI] 扩展已注册。");
        }
    }

    // ==================== 内部工具 ====================

    private static String formatTime(long ms) {
        long sec = Math.max(0, ms) / 1000;
        long m = sec / 60;
        long s = sec % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }
}