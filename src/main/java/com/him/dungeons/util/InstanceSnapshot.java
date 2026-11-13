package com.him.dungeons.util;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 单个运行实例的不可变快照。
 *
 * <p>由 {@code InstanceSnapshotService} 每 20 tick 生成一次，GUI 只读快照，
 * 不直接遍历 {@code dungeonInstances}——避免每次渲染做 45 次 getPlayer。</p>
 */
public final class InstanceSnapshot {

    public final String worldName;
    public final String dungeonName;

    /** 按 inst.players 迭代顺序，稳定 */
    public final List<UUID> playerUuids;
    /** UUID → 名字（离线时保留最后一次已知名字） */
    public final Map<UUID, String> playerNames;

    public final int roomsDone;
    public final int roomsTotal;
    /** -1 表示无限 */
    public final int remainingSec;
    /** 0~100；-1 表示无 Boss */
    public final double bossHpPercent;

    public final long startedAt;
    public final long capturedAt;
    public final boolean finished;

    public InstanceSnapshot(String worldName, String dungeonName,
                            List<UUID> playerUuids, Map<UUID, String> playerNames,
                            int roomsDone, int roomsTotal, int remainingSec,
                            double bossHpPercent, long startedAt, long capturedAt,
                            boolean finished) {
        this.worldName = worldName;
        this.dungeonName = dungeonName;
        this.playerUuids = playerUuids;
        this.playerNames = playerNames;
        this.roomsDone = roomsDone;
        this.roomsTotal = roomsTotal;
        this.remainingSec = remainingSec;
        this.bossHpPercent = bossHpPercent;
        this.startedAt = startedAt;
        this.capturedAt = capturedAt;
        this.finished = finished;
    }

    public int teamSize() { return playerUuids.size(); }
}