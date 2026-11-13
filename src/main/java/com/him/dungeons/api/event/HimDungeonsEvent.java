package com.him.dungeons.api.event;

import org.bukkit.event.Event;

/**
 * 所有 HimDungeons 事件的抽象基类。
 * 子类必须自行定义 {@code HandlerList} 并实现 {@code getHandlers()} / 静态 {@code getHandlerList()}。
 */
public abstract class HimDungeonsEvent extends Event {
    protected HimDungeonsEvent() { super(); }
    protected HimDungeonsEvent(boolean isAsync) { super(isAsync); }
}