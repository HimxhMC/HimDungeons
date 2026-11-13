package com.him.dungeons.api.hook;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** 玩家在地牢内放置方块。 */
public final class BlockPlaceContext {
    public final DungeonContext dungeon;
    public final Player player;
    public final Block block;
    public final ItemStack item;
    public boolean cancelled;

    public BlockPlaceContext(DungeonContext dungeon, Player player, Block block, ItemStack item) {
        this.dungeon = dungeon;
        this.player = player;
        this.block = block;
        this.item = item;
    }
}