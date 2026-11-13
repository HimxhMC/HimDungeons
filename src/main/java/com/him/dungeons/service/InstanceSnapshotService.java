package com.him.dungeons.service;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.AABBUtil;
import com.him.dungeons.util.ConfigUtil;
import com.him.dungeons.util.InstanceSnapshot;
import com.him.dungeons.util.RoomInstance;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每 20 tick 扫描一次 {@code dungeonInstances}，生成不可变快照供 GUI 读取。
 *
 * <p>主线程执行；20 tick 一次的全量扫描开销可忽略（< 0.5ms）。</p>
 * <p>离线玩家名通过 {@code Bukkit.getOfflinePlayer} 回填（首次会查磁盘，缓存后极快）。</p>
 */
public final class InstanceSnapshotService {

    private final HimDungeons plugin;
    private volatile Map<String, InstanceSnapshot> snapshots = Collections.emptyMap();
    private BukkitTask task;
    private final Map<UUID, String> nameCache = new ConcurrentHashMap<>();

    public InstanceSnapshotService(HimDungeons plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 20L, 20L);
        refresh();
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        snapshots = Collections.emptyMap();
        nameCache.clear();
    }

    public Map<String, InstanceSnapshot> snapshots() { return snapshots; }
    public InstanceSnapshot get(String worldName)    { return snapshots.get(worldName); }

    // ==================== 内部 ====================

    private void refresh() {
        Map<String, InstanceSnapshot> next = new HashMap<>();
        long now = System.currentTimeMillis();
        for (HimDungeons.DungeonInstance inst : plugin.dungeonInstances.values()) {
            try {
                next.put(inst.worldName, build(inst, now));
            } catch (Throwable t) {
                plugin.getLogger().warning(
                        "[Snapshot] " + inst.worldName + " 快照失败: " + t.getMessage());
            }
        }
        snapshots = Collections.unmodifiableMap(next);
    }

    private InstanceSnapshot build(HimDungeons.DungeonInstance inst, long now) {
        List<UUID> uuids = new ArrayList<>(inst.players);
        Map<UUID, String> names = new HashMap<>(uuids.size());
        for (UUID uuid : uuids) {
            names.put(uuid, resolveName(uuid));
        }

        int total = inst.placedRooms == null ? 0 : inst.placedRooms.size();
        int done = 0;
        for (UUID uuid : uuids) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            int blockX = p.getLocation().getBlockX();
            int blockY = p.getLocation().getBlockY();
            int blockZ = p.getLocation().getBlockZ();
            for (int i = 0; i < total; i++) {
                RoomInstance r = inst.placedRooms.get(i);
                if (AABBUtil.contains(r.aabb, blockX, blockY, blockZ)) {
                    done = Math.max(done, i + 1);
                    break;
                }
            }
        }

        int remaining = -1;
        FileConfiguration cfg = ConfigUtil.getDungeonConfig(inst.dungeonName);
        if (cfg != null && inst.startTime > 0) {
            int totalSec = ConfigUtil.getInstanceTime(cfg);
            if (totalSec > 0) {
                long elapsed = (now - inst.startTime) / 1000L;
                remaining = (int) Math.max(0, totalSec - elapsed);
            }
        }

        double bossHp = -1;
        HimDungeons.BossData bd = plugin.getBossData(inst.worldName);
        if (bd != null && bd.entity != null && bd.entity.isValid() && !bd.entity.isDead()) {
            double max = bd.entity.getMaxHealth();
            if (max > 0) bossHp = Math.min(100.0, bd.entity.getHealth() / max * 100);
        }

        return new InstanceSnapshot(
                inst.worldName,
                inst.dungeonName,
                Collections.unmodifiableList(uuids),
                Collections.unmodifiableMap(names),
                done, total, remaining, bossHp,
                inst.startTime > 0 ? inst.startTime : now,
                now,
                inst.isFinished
        );
    }

    private String resolveName(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            nameCache.put(uuid, p.getName());
            return p.getName();
        }
        String cached = nameCache.get(uuid);
        if (cached != null) return cached;
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String name = op.getName();
            if (name != null) {
                nameCache.put(uuid, name);
                return name;
            }
        } catch (Throwable ignored) {}
        return uuid.toString().substring(0, 8);
    }
}