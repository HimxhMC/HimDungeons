package com.him.dungeons.storage;

import java.util.UUID;

/** 排行榜单条：某玩家在指定地牢的聚合值。 */
public final class RankEntry {
    public final int rank;
    public final UUID uuid;
    public final String name;
    public final long value;   // 时间(ms) / 胜利数 / 最高分

    public RankEntry(int rank, UUID uuid, String name, long value) {
        this.rank = rank;
        this.uuid = uuid;
        this.name = name;
        this.value = value;
    }
}