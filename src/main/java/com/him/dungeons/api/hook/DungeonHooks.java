package com.him.dungeons.api.hook;

/**
 * 全部钩子函数式接口定义。
 *
 * <p>统一约定：</p>
 * <ul>
 *   <li>通过 {@link DungeonHookRegistry} 注册与触发</li>
 *   <li>同一事件多监听器按<b>注册顺序</b>依次执行</li>
 *   <li>任意监听器抛出的异常会被隔离，不影响其他监听器与主流程</li>
 *   <li>"否决"语义通过修改对应 ctx 的 {@code cancelled} / {@code rejected} 字段实现</li>
 * </ul>
 */
public final class DungeonHooks {

    private DungeonHooks() {}

    // ===== L1 实例级 =====
    /** 世界创建之前（主线程） */
    @FunctionalInterface public interface BeforeCreateWorld { void on(DungeonContext ctx); }
    /** 世界创建并应用设置之后（主线程） */
    @FunctionalInterface public interface AfterCreateWorld { void on(DungeonContext ctx); }
    /** 异步生成任务开始、房间池扫描之前（异步线程） */
    @FunctionalInterface public interface BeforeGenerate { void on(DungeonContext ctx); }
    /** 全部房间放置完成、尚未粘贴（异步线程） */
    @FunctionalInterface public interface AfterGenerate { void on(DungeonContext ctx); }
    /** 即将开始粘贴（异步线程） */
    @FunctionalInterface public interface BeforePaste { void on(DungeonContext ctx); }
    /** 粘贴全部完成（异步线程） */
    @FunctionalInterface public interface AfterPaste { void on(DungeonContext ctx); }
    /** 实例就绪、玩家传送前（主线程） */
    @FunctionalInterface public interface DungeonReady { void on(DungeonContext ctx); }
    /** 世界销毁之前（主线程） */
    @FunctionalInterface public interface BeforeDestroy { void on(DungeonContext ctx); }
    /** 世界销毁并清理完毕（主线程） */
    @FunctionalInterface public interface AfterDestroy { void on(DungeonContext ctx); }

    // ===== L2 房间级 =====
    /** 每次从候选池选中模板时（异步线程） */
    @FunctionalInterface public interface RoomSelect { void on(RoomSelectContext ctx); }
    /** 房间被加入 placedRooms 时（异步线程） */
    @FunctionalInterface public interface RoomPlaced { void on(RoomContext ctx); }
    /** 单个房间粘贴完成时（主线程） */
    @FunctionalInterface public interface RoomPasted { void on(RoomContext ctx); }
    /** 单条战利品填充前（主线程） */
    @FunctionalInterface public interface ChestFill { void on(ChestFillContext ctx); }

    // ===== L3 玩家级 =====
    /** 玩家进入前；cancelled=true 否决进入（主线程） */
    @FunctionalInterface public interface BeforeEnter { void on(PlayerHookContext ctx); }
    /** 玩家进入后（主线程） */
    @FunctionalInterface public interface AfterEnter { void on(PlayerHookContext ctx); }
    /** 玩家离开前；cancelled=true 否决离开（主线程） */
    @FunctionalInterface public interface BeforeLeave { void on(PlayerHookContext ctx); }
    /** 玩家离开后（主线程） */
    @FunctionalInterface public interface AfterLeave { void on(PlayerHookContext ctx); }
    /** 玩家通关结算完成、奖励发放后（主线程） */
    @FunctionalInterface public interface Finish { void on(PlayerHookContext ctx); }
    /** 玩家离线（主线程） */
    @FunctionalInterface public interface Offline { void on(PlayerHookContext ctx); }
    /** 玩家重新上线并成功重入（主线程） */
    @FunctionalInterface public interface Online { void on(PlayerHookContext ctx); }

    // ===== L4 Boss 级 =====
    /** Boss 生成完成、注册前；可替换 ctx.boss（主线程） */
    @FunctionalInterface public interface BossSpawn { void on(BossHookContext ctx); }
    /** Boss 死亡；cancelled=true 阻止结算（主线程） */
    @FunctionalInterface public interface BossDefeat { void on(BossHookContext ctx); }
}