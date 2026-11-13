package com.him.dungeons.api.hook;

import com.him.dungeons.util.RoomInstance;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一次完整地牢实例生命周期的上下文对象。
 *
 * <p>由 {@code DungeonGenerator} 在生成开始时创建，随 {@code DungeonInstance}
 * 一起存活到世界销毁。所有实例 / 房间 / 玩家 / Boss 级钩子都通过它传递地牢公共信息。</p>
 *
 * <p>{@link #attrs} 是钩子之间唯一推荐的共享状态通道；不可变字段请勿替换。</p>
 */
public final class DungeonContext {

    public final String dungeonName;
    public final Player initiator;
    public final boolean testMode;
    public final FileConfiguration config;

    /** 钩子间共享状态（线程安全） */
    public final Map<String, Object> attrs = new ConcurrentHashMap<>();

    /** 虚空世界创建完成后填充 */
    public volatile World world;
    /** 世界名（世界创建后填充） */
    public volatile String worldName;
    /** 出生点（玩家传送前填充） */
    public volatile Location spawnLocation;
    /** 全部已放置的房间（生成完成时填充，可直接增删） */
    public volatile List<RoomInstance> placedRooms;

    public DungeonContext(String dungeonName, Player initiator, boolean testMode,
                          FileConfiguration config) {
        this.dungeonName = dungeonName;
        this.initiator = initiator;
        this.testMode = testMode;
        this.config = config;
    }
    /** Boss 是否已被击败（BossDefeat 钩子后自动置 true） */
    public volatile boolean bossDefeated;
    /** 主目标是否完成（外部通过 DungeonAPI.markObjectiveComplete 设置） */
    public volatile boolean objectiveComplete;
    /** 是否已失败 */
    public volatile boolean objectiveFailed;
    /** 失败原因 */
    public volatile String failureReason;

    /** 已完成的子目标 id 集合（线程安全） */
    public final java.util.Set<String> completedSubObjectives =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 方块标记：tag → Set&lt;Pos3&gt;（线程安全） */
    public final Map<String, java.util.Set<com.him.dungeons.util.Pos3>> blockMarks =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 标记子目标完成，返回是否为首次完成 */
    public boolean completeSubObjective(String id) {
        return id != null && completedSubObjectives.add(id);
    }

    /** 主目标完成 */
    public void completeObjective() {
        this.objectiveComplete = true;
    }

    /** 目标失败 */
    public void failObjective(String reason) {
        this.objectiveFailed = true;
        this.failureReason = reason;
    }
}