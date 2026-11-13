package com.him.dungeons.api.event;

import com.him.dungeons.api.hook.BlockBreakContext;
import com.him.dungeons.api.hook.DungeonContext;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/** 玩家在地牢内破坏方块（BlockBreak）。取消 = 阻止破坏。 */
public class DungeonBlockBreakEvent extends CancellableHimDungeonsEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final BlockBreakContext ctx;

    public DungeonBlockBreakEvent(BlockBreakContext ctx) { this.ctx = ctx; }

    public Player getPlayer()           { return ctx.player; }
    public Block getBlock()             { return ctx.block; }
    public DungeonContext getDungeon()  { return ctx.dungeon; }
    public BlockBreakContext getContext() { return ctx; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()  { return HANDLERS; }
}