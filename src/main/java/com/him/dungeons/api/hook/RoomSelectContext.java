package com.him.dungeons.api.hook;

import com.him.dungeons.util.RoomInstance;

/**
 * 房间模板选择上下文。
 *
 * <p>在 {@code tryExtend} 中，从候选池抽到模板、尚未读取原理图之前触发。
 * 外部可以：</p>
 * <ul>
 *   <li>设置 {@link #rejected} = true 否决当前候选（插件继续尝试下一个）</li>
 *   <li>修改 {@link #baseName} 替换模板（例如按外部规则重定向）</li>
 * </ul>
 *
 * <p><b>运行线程：</b>异步生成线程。</p>
 */
public final class RoomSelectContext {

    public final DungeonContext dungeon;
    public final RoomInstance fromRoom;
    public final int doorIdx;
    /** 候选池类型："normal" / "boss" */
    public final String poolType;
    public final String originalBaseName;

    /** 外部可写：新模板名。默认 {@link #originalBaseName} */
    public String baseName;
    /** 外部可写：true 否决当前候选 */
    public boolean rejected;

    public RoomSelectContext(DungeonContext dungeon, RoomInstance fromRoom, int doorIdx,
                             String poolType, String originalBaseName) {
        this.dungeon = dungeon;
        this.fromRoom = fromRoom;
        this.doorIdx = doorIdx;
        this.poolType = poolType;
        this.originalBaseName = originalBaseName;
        this.baseName = originalBaseName;
    }
}