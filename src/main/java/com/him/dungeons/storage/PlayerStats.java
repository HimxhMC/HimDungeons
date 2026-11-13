package com.him.dungeons.storage;

/** 玩家个人统计。 */
public final class PlayerStats {
    public final String dungeon;
    public int successCount;
    public int failCount;
    public long fastestMs = Long.MAX_VALUE;
    public long totalMs;
    public int totalDeaths;

    public PlayerStats(String dungeon) { this.dungeon = dungeon; }

    public int totalRuns() { return successCount + failCount; }
    public double winRate() {
        int t = totalRuns();
        return t == 0 ? 0 : (double) successCount / t;
    }
    public long averageMs() {
        return successCount == 0 ? 0 : totalMs / successCount;
    }
}