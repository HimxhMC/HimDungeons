package com.him.dungeons.storage;

import java.util.*;

public interface RunStorage {

    void init();

    /** 批量插入（单事务）。 */
    void insertBatch(List<RunRecord> records);

    /** Top 列表（聚合到玩家）。 */
    List<RankEntry> top(String dungeon, LeaderboardMode mode,
                        TeamSizeBucket bucket, Period period, int limit);

    /** 玩家在某地牢的排名（1-based，未上榜返回 -1）。 */
    int playerRank(UUID uuid, String dungeon, LeaderboardMode mode,
                   TeamSizeBucket bucket, Period period);

    /** 玩家个人统计。 */
    PlayerStats playerStats(UUID uuid, String dungeon, Period period);

    /** 地牢全局汇总。 */
    DungeonSummary dungeonSummary(String dungeon, Period period);

    /** 导出全部记录。 */
    List<RunRecord> exportAll(String dungeon);

    void close();
}