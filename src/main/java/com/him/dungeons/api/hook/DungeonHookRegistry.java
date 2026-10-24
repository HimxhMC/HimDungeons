package com.him.dungeons.api.hook;

import com.him.dungeons.HimDungeons;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * 地牢钩子注册中心。
 *
 * <p>外部插件通过 {@code HimDungeons.getInstance().getHookRegistry()} 获取，
 * 用 {@code onXxx(...)} 注册、{@code offXxx(...)} 取消、{@code clearAll()} 清空。</p>
 *
 * <p>所有 {@code fireXxx} 均线程安全、异常隔离：单个监听器抛异常不会
 * 中断其他监听器或地牢主流程。</p>
 */
public final class DungeonHookRegistry {

    private final List<DungeonHooks.BeforeCreateWorld> beforeCreateWorld = new CopyOnWriteArrayList<>();
    private final List<DungeonHooks.AfterCreateWorld>  afterCreateWorld  = new CopyOnWriteArrayList<>();
    private final List<DungeonHooks.BeforeGenerate>    beforeGenerate    = new CopyOnWriteArrayList<>();
    private final List<DungeonHooks.AfterGenerate>     afterGenerate     = new CopyOnWriteArrayList<>();
    private final List<DungeonHooks.BeforePaste>       beforePaste       = new CopyOnWriteArrayList<>();
    private final List<DungeonHooks.AfterPaste>        afterPaste        = new CopyOnWriteArrayList<>();
    private final List<DungeonHooks.DungeonReady>      dungeonReady      = new CopyOnWriteArrayList<>();
    private final List<DungeonHooks.BeforeDestroy>     beforeDestroy     = new CopyOnWriteArrayList<>();
    private final List<DungeonHooks.AfterDestroy>      afterDestroy      = new CopyOnWriteArrayList<>();

    private final List<DungeonHooks.RoomSelect>        roomSelect        = new CopyOnWriteArrayList<>();
    private final List<DungeonHooks.RoomPlaced>        roomPlaced        = new CopyOnWriteArrayList<>();
    private final List<DungeonHooks.RoomPasted>        roomPasted        = new CopyOnWriteArrayList<>();
    private final List<DungeonHooks.ChestFill>         chestFill         = new CopyOnWriteArrayList<>();

    private final List<DungeonHooks.BeforeEnter>       beforeEnter       = new CopyOnWriteArrayList<>();
    private final List<DungeonHooks.AfterEnter>        afterEnter        = new CopyOnWriteArrayList<>();
    private final List<DungeonHooks.BeforeLeave>       beforeLeave       = new CopyOnWriteArrayList<>();
    private final List<DungeonHooks.AfterLeave>        afterLeave        = new CopyOnWriteArrayList<>();
    private final List<DungeonHooks.Finish>            finish            = new CopyOnWriteArrayList<>();
    private final List<DungeonHooks.Offline>           offline           = new CopyOnWriteArrayList<>();
    private final List<DungeonHooks.Online>            online            = new CopyOnWriteArrayList<>();

    private final List<DungeonHooks.BossSpawn>         bossSpawn         = new CopyOnWriteArrayList<>();
    private final List<DungeonHooks.BossDefeat>        bossDefeat        = new CopyOnWriteArrayList<>();

    // ==================== 注册 ====================
    public void onBeforeCreateWorld(DungeonHooks.BeforeCreateWorld h) { beforeCreateWorld.add(h); }
    public void onAfterCreateWorld (DungeonHooks.AfterCreateWorld  h) { afterCreateWorld.add(h); }
    public void onBeforeGenerate   (DungeonHooks.BeforeGenerate    h) { beforeGenerate.add(h); }
    public void onAfterGenerate    (DungeonHooks.AfterGenerate     h) { afterGenerate.add(h); }
    public void onBeforePaste      (DungeonHooks.BeforePaste       h) { beforePaste.add(h); }
    public void onAfterPaste       (DungeonHooks.AfterPaste        h) { afterPaste.add(h); }
    public void onDungeonReady     (DungeonHooks.DungeonReady      h) { dungeonReady.add(h); }
    public void onBeforeDestroy    (DungeonHooks.BeforeDestroy     h) { beforeDestroy.add(h); }
    public void onAfterDestroy     (DungeonHooks.AfterDestroy      h) { afterDestroy.add(h); }

    public void onRoomSelect(DungeonHooks.RoomSelect h) { roomSelect.add(h); }
    public void onRoomPlaced(DungeonHooks.RoomPlaced h) { roomPlaced.add(h); }
    public void onRoomPasted(DungeonHooks.RoomPasted h) { roomPasted.add(h); }
    public void onChestFill (DungeonHooks.ChestFill  h) { chestFill.add(h); }

    public void onBeforeEnter(DungeonHooks.BeforeEnter h) { beforeEnter.add(h); }
    public void onAfterEnter (DungeonHooks.AfterEnter  h) { afterEnter.add(h); }
    public void onBeforeLeave(DungeonHooks.BeforeLeave h) { beforeLeave.add(h); }
    public void onAfterLeave (DungeonHooks.AfterLeave  h) { afterLeave.add(h); }
    public void onFinish     (DungeonHooks.Finish      h) { finish.add(h); }
    public void onOffline    (DungeonHooks.Offline     h) { offline.add(h); }
    public void onOnline     (DungeonHooks.Online      h) { online.add(h); }

    public void onBossSpawn (DungeonHooks.BossSpawn  h) { bossSpawn.add(h); }
    public void onBossDefeat(DungeonHooks.BossDefeat h) { bossDefeat.add(h); }

    // ==================== 取消注册 ====================
    public void offBeforeCreateWorld(DungeonHooks.BeforeCreateWorld h) { beforeCreateWorld.remove(h); }
    public void offAfterCreateWorld (DungeonHooks.AfterCreateWorld  h) { afterCreateWorld.remove(h); }
    public void offBeforeGenerate   (DungeonHooks.BeforeGenerate    h) { beforeGenerate.remove(h); }
    public void offAfterGenerate    (DungeonHooks.AfterGenerate     h) { afterGenerate.remove(h); }
    public void offBeforePaste      (DungeonHooks.BeforePaste       h) { beforePaste.remove(h); }
    public void offAfterPaste       (DungeonHooks.AfterPaste        h) { afterPaste.remove(h); }
    public void offDungeonReady     (DungeonHooks.DungeonReady      h) { dungeonReady.remove(h); }
    public void offBeforeDestroy    (DungeonHooks.BeforeDestroy     h) { beforeDestroy.remove(h); }
    public void offAfterDestroy     (DungeonHooks.AfterDestroy      h) { afterDestroy.remove(h); }

    public void offRoomSelect(DungeonHooks.RoomSelect h) { roomSelect.remove(h); }
    public void offRoomPlaced(DungeonHooks.RoomPlaced h) { roomPlaced.remove(h); }
    public void offRoomPasted(DungeonHooks.RoomPasted h) { roomPasted.remove(h); }
    public void offChestFill (DungeonHooks.ChestFill  h) { chestFill.remove(h); }

    public void offBeforeEnter(DungeonHooks.BeforeEnter h) { beforeEnter.remove(h); }
    public void offAfterEnter (DungeonHooks.AfterEnter  h) { afterEnter.remove(h); }
    public void offBeforeLeave(DungeonHooks.BeforeLeave h) { beforeLeave.remove(h); }
    public void offAfterLeave (DungeonHooks.AfterLeave  h) { afterLeave.remove(h); }
    public void offFinish     (DungeonHooks.Finish      h) { finish.remove(h); }
    public void offOffline    (DungeonHooks.Offline     h) { offline.remove(h); }
    public void offOnline     (DungeonHooks.Online      h) { online.remove(h); }

    public void offBossSpawn (DungeonHooks.BossSpawn  h) { bossSpawn.remove(h); }
    public void offBossDefeat(DungeonHooks.BossDefeat h) { bossDefeat.remove(h); }

    // ==================== fire ====================
    public void fireBeforeCreateWorld(DungeonContext ctx) { fireAll(beforeCreateWorld, h -> h.on(ctx)); }
    public void fireAfterCreateWorld (DungeonContext ctx) { fireAll(afterCreateWorld,  h -> h.on(ctx)); }
    public void fireBeforeGenerate   (DungeonContext ctx) { fireAll(beforeGenerate,    h -> h.on(ctx)); }
    public void fireAfterGenerate    (DungeonContext ctx) { fireAll(afterGenerate,     h -> h.on(ctx)); }
    public void fireBeforePaste      (DungeonContext ctx) { fireAll(beforePaste,       h -> h.on(ctx)); }
    public void fireAfterPaste       (DungeonContext ctx) { fireAll(afterPaste,        h -> h.on(ctx)); }
    public void fireDungeonReady     (DungeonContext ctx) { fireAll(dungeonReady,      h -> h.on(ctx)); }
    public void fireBeforeDestroy    (DungeonContext ctx) { fireAll(beforeDestroy,     h -> h.on(ctx)); }
    public void fireAfterDestroy     (DungeonContext ctx) { fireAll(afterDestroy,      h -> h.on(ctx)); }

    public void fireRoomSelect(RoomSelectContext ctx) { fireAll(roomSelect, h -> h.on(ctx)); }
    public void fireRoomPlaced(RoomContext ctx)       { fireAll(roomPlaced, h -> h.on(ctx)); }
    public void fireRoomPasted(RoomContext ctx)       { fireAll(roomPasted, h -> h.on(ctx)); }
    public void fireChestFill (ChestFillContext ctx)  { fireAll(chestFill,  h -> h.on(ctx)); }

    public void fireBeforeEnter(PlayerHookContext ctx) { fireAll(beforeEnter, h -> h.on(ctx)); }
    public void fireAfterEnter (PlayerHookContext ctx) { fireAll(afterEnter,  h -> h.on(ctx)); }
    public void fireBeforeLeave(PlayerHookContext ctx) { fireAll(beforeLeave, h -> h.on(ctx)); }
    public void fireAfterLeave (PlayerHookContext ctx) { fireAll(afterLeave,  h -> h.on(ctx)); }
    public void fireFinish     (PlayerHookContext ctx) { fireAll(finish,      h -> h.on(ctx)); }
    public void fireOffline    (PlayerHookContext ctx) { fireAll(offline,     h -> h.on(ctx)); }
    public void fireOnline     (PlayerHookContext ctx) { fireAll(online,      h -> h.on(ctx)); }

    public void fireBossSpawn (BossHookContext ctx) { fireAll(bossSpawn,  h -> h.on(ctx)); }
    public void fireBossDefeat(BossHookContext ctx) { fireAll(bossDefeat, h -> h.on(ctx)); }

    // ==================== 内部 ====================
    public void clearAll() {
        beforeCreateWorld.clear(); afterCreateWorld.clear();
        beforeGenerate.clear(); afterGenerate.clear();
        beforePaste.clear(); afterPaste.clear();
        dungeonReady.clear(); beforeDestroy.clear(); afterDestroy.clear();
        roomSelect.clear(); roomPlaced.clear(); roomPasted.clear(); chestFill.clear();
        beforeEnter.clear(); afterEnter.clear(); beforeLeave.clear(); afterLeave.clear();
        finish.clear(); offline.clear(); online.clear();
        bossSpawn.clear(); bossDefeat.clear();
    }

    private static <T> void fireAll(List<T> hooks, Consumer<T> invoker) {
        if (hooks.isEmpty()) return;
        for (T h : hooks) {
            try { invoker.accept(h); }
            catch (Throwable t) {
                HimDungeons p = HimDungeons.getInstance();
                if (p != null) p.getLogger().log(Level.WARNING, "[Hook] 钩子执行异常", t);
                else { System.err.println("[Hook] 钩子执行异常"); t.printStackTrace(); }
            }
        }
    }
    // ==================== 管理 API ====================

    /**
     * 钩子类型枚举，供 GUI 与外部插件管理使用。
     * {@link #id} 与 {@link #displayName} 用于展示，{@link #group} 用于分组配色。
     */
    public enum HookType {
        // L1 实例级
        BEFORE_CREATE_WORLD("BeforeCreateWorld", "世界创建前", "L1"),
        AFTER_CREATE_WORLD ("AfterCreateWorld",  "世界创建后", "L1"),
        BEFORE_GENERATE    ("BeforeGenerate",    "开始生成前", "L1"),
        AFTER_GENERATE     ("AfterGenerate",     "房间放置完", "L1"),
        BEFORE_PASTE       ("BeforePaste",       "开始粘贴前", "L1"),
        AFTER_PASTE        ("AfterPaste",        "粘贴完成",   "L1"),
        DUNGEON_READY      ("DungeonReady",      "实例就绪",   "L1"),
        BEFORE_DESTROY     ("BeforeDestroy",     "世界销毁前", "L1"),
        AFTER_DESTROY      ("AfterDestroy",      "世界销毁后", "L1"),
        // L2 房间级
        ROOM_SELECT        ("RoomSelect",        "房间选择",   "L2"),
        ROOM_PLACED        ("RoomPlaced",        "房间放置",   "L2"),
        ROOM_PASTED        ("RoomPasted",        "房间粘贴",   "L2"),
        CHEST_FILL         ("ChestFill",         "战利品填充", "L2"),
        // L3 玩家级
        BEFORE_ENTER       ("BeforeEnter",       "进入前",   "L3"),
        AFTER_ENTER        ("AfterEnter",        "进入后",   "L3"),
        BEFORE_LEAVE       ("BeforeLeave",       "离开前",   "L3"),
        AFTER_LEAVE        ("AfterLeave",        "离开后",   "L3"),
        FINISH             ("Finish",            "通关结算", "L3"),
        OFFLINE            ("Offline",           "玩家离线", "L3"),
        ONLINE             ("Online",            "重新上线", "L3"),
        // L4 Boss 级
        BOSS_SPAWN         ("BossSpawn",         "Boss 生成", "L4"),
        BOSS_DEFEAT        ("BossDefeat",        "Boss 击杀", "L4"),
        ;

        public final String id;
        public final String displayName;
        public final String group;

        HookType(String id, String displayName, String group) {
            this.id = id;
            this.displayName = displayName;
            this.group = group;
        }
    }

    private List<?> listOf(HookType type) {
        return switch (type) {
            case BEFORE_CREATE_WORLD -> beforeCreateWorld;
            case AFTER_CREATE_WORLD  -> afterCreateWorld;
            case BEFORE_GENERATE     -> beforeGenerate;
            case AFTER_GENERATE      -> afterGenerate;
            case BEFORE_PASTE        -> beforePaste;
            case AFTER_PASTE         -> afterPaste;
            case DUNGEON_READY       -> dungeonReady;
            case BEFORE_DESTROY      -> beforeDestroy;
            case AFTER_DESTROY       -> afterDestroy;
            case ROOM_SELECT         -> roomSelect;
            case ROOM_PLACED         -> roomPlaced;
            case ROOM_PASTED         -> roomPasted;
            case CHEST_FILL          -> chestFill;
            case BEFORE_ENTER        -> beforeEnter;
            case AFTER_ENTER         -> afterEnter;
            case BEFORE_LEAVE        -> beforeLeave;
            case AFTER_LEAVE         -> afterLeave;
            case FINISH              -> finish;
            case OFFLINE             -> offline;
            case ONLINE              -> online;
            case BOSS_SPAWN          -> bossSpawn;
            case BOSS_DEFEAT         -> bossDefeat;
        };
    }

    /** 当前某类钩子的注册数量 */
    public int count(HookType type) { return listOf(type).size(); }

    /** 获取某类钩子的快照（顺序与注册顺序一致，索引可用于 {@link #removeHook}） */
    public List<?> getHooks(HookType type) {
        return new java.util.ArrayList<>(listOf(type));
    }

    /** 按索引移除某类中的一个钩子（索引基于注册顺序，与 {@link #getHooks} 一致） */
    public boolean removeHook(HookType type, int index) {
        List<?> list = listOf(type);
        if (index < 0 || index >= list.size()) return false;
        list.remove(index);
        return true;
    }

    /** 清空某类钩子，返回清空前的数量 */
    public int clearType(HookType type) {
        List<?> list = listOf(type);
        int n = list.size();
        list.clear();
        return n;
    }

    /** 所有类型钩子的总数 */
    public int countAll() {
        int n = 0;
        for (HookType t : HookType.values()) n += count(t);
        return n;
    }

    /** 清空所有类型钩子，返回清空前的总数 */
    public int clearAllTypes() {
        int n = 0;
        for (HookType t : HookType.values()) n += clearType(t);
        return n;
    }
}