package com.him.dungeons.api.event;

import org.bukkit.event.Cancellable;

public abstract class CancellableHimDungeonsEvent extends HimDungeonsEvent implements Cancellable {
    private boolean cancelled;
    @Override public final boolean isCancelled() { return cancelled; }
    @Override public final void setCancelled(boolean cancel) { this.cancelled = cancel; }
}