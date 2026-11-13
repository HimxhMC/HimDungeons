package com.him.dungeons.api.event;

import com.him.dungeons.api.hook.BlockPlaceContext;
import com.him.dungeons.api.hook.DungeonContext;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/** 玩家在地牢内放置方块（BlockPlace）。取消 = 阻止放置。 */
public class DungeonBlockPlaceEvent extends CancellableHimDungeonsEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final BlockPlaceContext ctx;

    public DungeonBlockPlaceEvent(BlockPlaceContext ctx) { this.ctx = ctx; }

    public Player getPlayer()           { return ctx.player; }
    public Block getBlock()             { return ctx.block; }
    public ItemStack getItem()          { return ctx.item; }
    public DungeonContext getDungeon()  { return ctx.dungeon; }
    public BlockPlaceContext getContext() { return ctx; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()  { return HANDLERS; }
}