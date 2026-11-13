package com.him.dungeons.api.hook;

import org.bukkit.entity.Player;

/**
 * 玩家生命周期钩子上下文。
 *
 * <p>{@link #cancelled} 仅在 {@code BeforeEnter} / {@code BeforeLeave} 中生效。</p>
 */
public final class PlayerHookContext {
    public final DungeonContext dungeon;
    public final Player player;
    public boolean cancelled;
    public PlayerHookContext(DungeonContext dungeon, Player player) {
        this.dungeon = dungeon;
        this.player = player;
    }
}