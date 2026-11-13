package com.him.dungeons.service;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.storage.RunMember;
import com.him.dungeons.storage.RunRecord;
import com.him.dungeons.storage.RunStorage;
import com.him.dungeons.util.LeaderboardCache;
import com.him.dungeons.util.LanguageManager;
import com.him.dungeons.util.RunStatsAccumulator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 通关记录服务：把 RunRecord 写入 SQLite。
 *
 * <p>写入完全异步——通关时塞队列，后台每 40 tick drain 一次批量事务写。
 * 峰值丢 ≤ 2 秒数据，完全可接受。</p>
 */
public class RunRecordService {

    private static final long DRAIN_INTERVAL_TICKS = 40L;

    private final HimDungeons plugin;
    private final RunStorage storage;
    private final LeaderboardCache cache;
    private final ConcurrentLinkedQueue<RunRecord> queue = new ConcurrentLinkedQueue<>();
    private final AtomicLong droppedCount = new AtomicLong();

    public RunRecordService(HimDungeons plugin, RunStorage storage, LeaderboardCache cache) {
        this.plugin = plugin;
        this.storage = storage;
        this.cache = cache;
        startDrainTask();
    }

    public RunStorage storage() { return storage; }
    public LeaderboardCache cache() { return cache; }

    private void startDrainTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try { drain(); }
            catch (Throwable t) {
                plugin.getLogger().warning(LanguageManager.get("log.run-record.drain-fail",
        "error", t.getMessage()));
            }
        }, DRAIN_INTERVAL_TICKS, DRAIN_INTERVAL_TICKS);
    }

    private void drain() {
        List<RunRecord> batch = new ArrayList<>();
        RunRecord r;
        while ((r = queue.poll()) != null) batch.add(r);
        if (batch.isEmpty()) return;
        storage.insertBatch(batch);
    }

    /**
     * 构造并提交一条通关记录。
     * 线程安全；调用方可以在主线程 / 异步回调任意位置调用。
     */
    public void record(HimDungeons.DungeonInstance instance, boolean success, String failReason) {
        if (instance == null) return;
        if (instance.runRecorded) return;
        instance.runRecorded = true;

        RunRecord record = buildRecord(instance, success, failReason);
        queue.offer(record);

        // 立即失效缓存（玩家下次查看到的就是最新数据）
        cache.invalidate(record.dungeon);
        for (RunMember m : record.members) {
            cache.invalidatePlayer(m.uuid);
        }
    }

    private RunRecord buildRecord(HimDungeons.DungeonInstance instance,
                                  boolean success, String failReason) {
        long now = System.currentTimeMillis();
        long startedAt = instance.startTime > 0 ? instance.startTime : now;

        List<RunMember> members = new ArrayList<>();
        RunStatsAccumulator stats = instance.runStats;
        Set<UUID> allPlayers = new HashSet<>(instance.players);
        if (stats != null) allPlayers.addAll(stats.allTrackedUuids());

        for (UUID uuid : allPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            String name = p != null ? p.getName() : uuid.toString().substring(0, 8);
            members.add(new RunMember(
                    uuid, name,
                    stats != null ? stats.getDeaths(uuid) : 0,
                    stats != null ? stats.getDamageDealt(uuid) : 0L,
                    stats != null ? stats.getDamageTaken(uuid) : 0L,
                    stats != null ? stats.getMobsKilled(uuid) : 0
            ));
        }

        String typesUsed = collectTypesUsed(instance);

        return new RunRecord(
                instance.dungeonName,
                startedAt,
                now,
                success,
                failReason,
                null,
                typesUsed,
                true,
                members
        );
    }

    private String collectTypesUsed(HimDungeons.DungeonInstance instance) {
        if (instance.placedRooms == null) return "[]";
        Set<String> types = new TreeSet<>();
        for (var room : instance.placedRooms) {
            if (room.type != null) types.add(room.type);
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String t : types) {
            if (!first) sb.append(',');
            sb.append('"').append(t.replace("\"", "\\\"")).append('"');
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }

    public void shutdown() {
        drain();
    }
}