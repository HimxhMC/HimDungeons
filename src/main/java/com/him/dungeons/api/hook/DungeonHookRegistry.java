package com.him.dungeons.api.hook;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.LanguageManager;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * 地牢钩子注册中心。
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
                String msg = LanguageManager.get("log.hook.execution-error");
                if (p != null) p.getLogger().log(Level.WARNING, msg, t);
                else { System.err.println(msg); t.printStackTrace(); }
            }
        }
    }

    // ==================== 管理 API ====================

    /**
     * 钩子类型枚举。
     *
     * <p>{@link #id} 与 {@link #group} 为固定字段；显示名通过
     * {@link #displayName()} 从语言文件读取（key 为
     * {@code hook.type.<kebab-case>}）。</p>
     */
    public enum HookType {
        // L1 实例级
        BEFORE_CREATE_WORLD("BeforeCreateWorld", "L1"),
        AFTER_CREATE_WORLD ("AfterCreateWorld",  "L1"),
        BEFORE_GENERATE    ("BeforeGenerate",    "L1"),
        AFTER_GENERATE     ("AfterGenerate",     "L1"),
        BEFORE_PASTE       ("BeforePaste",       "L1"),
        AFTER_PASTE        ("AfterPaste",        "L1"),
        DUNGEON_READY      ("DungeonReady",      "L1"),
        BEFORE_DESTROY     ("BeforeDestroy",     "L1"),
        AFTER_DESTROY      ("AfterDestroy",      "L1"),
        // L2 房间级
        ROOM_SELECT        ("RoomSelect",        "L2"),
        ROOM_PLACED        ("RoomPlaced",        "L2"),
        ROOM_PASTED        ("RoomPasted",        "L2"),
        CHEST_FILL         ("ChestFill",         "L2"),
        // L3 玩家级
        BEFORE_ENTER       ("BeforeEnter",       "L3"),
        AFTER_ENTER        ("AfterEnter",        "L3"),
        BEFORE_LEAVE       ("BeforeLeave",       "L3"),
        AFTER_LEAVE        ("AfterLeave",        "L3"),
        FINISH             ("Finish",            "L3"),
        OFFLINE            ("Offline",           "L3"),
        ONLINE             ("Online",            "L3"),
        // L4 Boss 级
        BOSS_SPAWN         ("BossSpawn",         "L4"),
        BOSS_DEFEAT        ("BossDefeat",        "L4"),
        ;

        public final String id;
        public final String group;

        HookType(String id, String group) {
            this.id = id;
            this.group = group;
        }

        /** 从语言文件读取显示名；缺失时回退为 {@link #id} */
        public String displayName() {
            String key = "hook.type." + name().toLowerCase(Locale.ROOT).replace('_', '-');
            String val = LanguageManager.get(key);
            // 未翻译时 LanguageManager 会返回 key 原文，用 id 兜底保证可读
            return val.equals(key) ? id : val;
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

    public int count(HookType type) { return listOf(type).size(); }

    public List<?> getHooks(HookType type) {
        return new java.util.ArrayList<>(listOf(type));
    }

    public boolean removeHook(HookType type, int index) {
        List<?> list = listOf(type);
        if (index < 0 || index >= list.size()) return false;
        list.remove(index);
        return true;
    }

    public int clearType(HookType type) {
        List<?> list = listOf(type);
        int n = list.size();
        list.clear();
        return n;
    }

    public int countAll() {
        int n = 0;
        for (HookType t : HookType.values()) n += count(t);
        return n;
    }

    public int clearAllTypes() {
        int n = 0;
        for (HookType t : HookType.values()) n += clearType(t);
        return n;
    }
}