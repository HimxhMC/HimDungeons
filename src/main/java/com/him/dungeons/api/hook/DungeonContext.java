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
}