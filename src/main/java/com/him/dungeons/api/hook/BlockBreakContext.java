package com.him.dungeons.api.hook;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** 玩家在地牢内破坏方块。 */
public final class BlockBreakContext {
    public final DungeonContext dungeon;
    public final Player player;
    public final Block block;
    public boolean cancelled;

    public BlockBreakContext(DungeonContext dungeon, Player player, Block block) {
        this.dungeon = dungeon;
        this.player = player;
        this.block = block;
    }
}