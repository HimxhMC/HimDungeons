package com.him.dungeons.api.event;

import com.him.dungeons.api.hook.BlockInteractContext;
import com.him.dungeons.api.hook.DungeonContext;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;

/** 玩家在地牢内左/右键方块（BlockInteract）。取消 = 阻止交互。 */
public class DungeonBlockInteractEvent extends CancellableHimDungeonsEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final BlockInteractContext ctx;

    public DungeonBlockInteractEvent(BlockInteractContext ctx) { this.ctx = ctx; }

    public Player getPlayer()           { return ctx.player; }
    public Block getBlock()             { return ctx.block; }
    public Action getAction()           { return ctx.action; }
    public DungeonContext getDungeon()  { return ctx.dungeon; }
    public BlockInteractContext getContext() { return ctx; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()  { return HANDLERS; }
}