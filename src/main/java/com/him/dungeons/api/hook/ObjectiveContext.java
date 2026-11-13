package com.him.dungeons.api.hook;

import org.bukkit.entity.Player;

/** 目标完成 / 失败。 */
public final class ObjectiveContext {
    public enum Type { COMPLETE, FAIL }

    public final DungeonContext dungeon;
    /** 触发者（可能 null：例如超时失败由系统触发） */
    public final Player initiator;
    public final Type type;
    /** COMPLETE 时是 objectiveId；FAIL 时是失败原因 */
    public final String reason;

    public ObjectiveContext(DungeonContext dungeon, Player initiator, Type type, String reason) {
        this.dungeon = dungeon;
        this.initiator = initiator;
        this.type = type;
        this.reason = reason;
    }
}