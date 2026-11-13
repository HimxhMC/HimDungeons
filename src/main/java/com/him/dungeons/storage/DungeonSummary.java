package com.him.dungeons.storage;

import java.util.*;

/** 地牢全局汇总（用于 /dg stats admin 和日报）。 */
public final class DungeonSummary {
    public final String dungeon;
    public int totalRuns;
    public int successCount;
    public int failCount;
    public long fastestMs = Long.MAX_VALUE;
    public long averageMs;
    public long totalMs;
    public int totalDeaths;
    public long totalDamageDealt;
    public int totalMobsKilled;
    public final Map<String, Integer> failReasons = new LinkedHashMap<>();
    public final Map<String, Integer> teamSizeBuckets = new LinkedHashMap<>();
    public final List<RankEntry> topFastest = new ArrayList<>();
    public final List<RankEntry> topWins = new ArrayList<>();

    public DungeonSummary(String dungeon) { this.dungeon = dungeon; }
}