package com.him.dungeons.generator;

import com.him.dungeons.util.RoomUse;
import com.him.dungeons.util.YamlDoorUtil;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;

/**
 * 单个房间的合并配置（从单文件 &lt;baseName&gt;.yml 或旧 _*.yml 读取）。
 *
 * <p>字段语义见 {@code docs/room-format.md}（如有）。</p>
 */
public final class RoomConfig {

    public final String baseName;
    /** 房间类型（自由文本）；缺失时回退为 baseName 前缀 */
    public final String type;
    /** 用途集合；缺失时全部 true */
    public final RoomUse use;

    public final List<YamlDoorUtil.DoorAnchor> doors;
    public final List<Map<String, Object>> chests;
    public final Vector spawn;
    public final Vector checkpoint;

    public RoomConfig(String baseName, String type, RoomUse use,
                      List<YamlDoorUtil.DoorAnchor> doors,
                      List<Map<String, Object>> chests,
                      Vector spawn, Vector checkpoint) {
        this.baseName = baseName;
        this.type = type;
        this.use = use != null ? use : RoomUse.ALL;
        this.doors = doors != null ? doors : List.of();
        this.chests = chests != null ? chests : List.of();
        this.spawn = spawn;
        this.checkpoint = checkpoint;
    }
}