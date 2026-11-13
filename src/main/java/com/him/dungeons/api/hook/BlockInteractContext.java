package com.him.dungeons.api.hook;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;

/** 玩家在地牢内左键/右键方块。 */
public final class BlockInteractContext {
    public final DungeonContext dungeon;
    public final Player player;
    public final Block block;
    public final Action action;
    /** true = 取消本次交互（阻止打开容器/触发按钮等） */
    public boolean cancelled;

    public BlockInteractContext(DungeonContext dungeon, Player player, Block block, Action action) {
        this.dungeon = dungeon;
        this.player = player;
        this.block = block;
        this.action = action;
    }
}