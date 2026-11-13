package com.him.dungeons.api;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.HimDungeons.DungeonInstance;
import com.him.dungeons.api.hook.DungeonContext;
import com.him.dungeons.api.hook.ObjectiveContext;
import com.him.dungeons.listener.DungeonBossListener;
import com.him.dungeons.util.AABBUtil;
import com.him.dungeons.generator.ConfigApplier;
import com.him.dungeons.util.Pos3;
import com.him.dungeons.util.RoomInstance;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地牢 API 静态便捷方法：查询、目标控制、方块统计与标记。
 *
 * <p>所有方法对非地牢玩家安全返回（null / false / 0），无需额外判空。</p>
 */
public final class DungeonAPI {

    private DungeonAPI() {}

    // ==================== 查询 ====================

    public static boolean inDungeon(Player p) {
        return p != null && instance(p) != null;
    }

    public static DungeonInstance instance(Player p) {
        if (p == null) return null;
        HimDungeons plugin = HimDungeons.getInstance();
        if (plugin == null) return null;
        String worldName = plugin.getPlayerDungeonWorld(p.getUniqueId());
        if (worldName == null) return null;
        return plugin.getDungeonInstanceByWorld(worldName);
    }

    public static DungeonContext context(Player p) {
        DungeonInstance inst = instance(p);
        return inst == null ? null : inst.hookContext;
    }

    // ==================== 房间查询 ====================

    /** 玩家当前所在房间；不在任何房间返回 null */
    public static RoomInstance currentRoom(Player p) {
        if (p == null) return null;
        DungeonInstance inst = instance(p);
        if (inst == null || inst.placedRooms == null) return null;
        Location loc = p.getLocation();
        for (RoomInstance r : inst.placedRooms) {
            if (AABBUtil.contains(r.aabb, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
                return r;
            }
        }
        return null;
    }

    /** 玩家是否在指定 RoomInstance 内 */
    public static boolean atRoom(Player p, RoomInstance room) {
        if (p == null || room == null) return false;
        Location loc = p.getLocation();
        return AABBUtil.contains(room.aabb, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /** 玩家是否在指定 baseName 的房间内（支持多个同 baseName 房间） */
    public static boolean atRoom(Player p, String roomBaseName) {
        if (p == null || roomBaseName == null) return false;
        DungeonInstance inst = instance(p);
        if (inst == null || inst.placedRooms == null) return false;
        Location loc = p.getLocation();
        for (RoomInstance r : inst.placedRooms) {
            if (r.baseName.equals(roomBaseName)
                    && AABBUtil.contains(r.aabb, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
                return true;
            }
        }
        return false;
    }

    /** 玩家是否在指定类型的房间内（start / boss / fallback / normal） */
    public static boolean atRoomType(Player p, String roomType) {
        RoomInstance r = currentRoom(p);
        return r != null && r.roomType.equalsIgnoreCase(roomType);
    }

    // ==================== 目标控制 ====================

    /** 主目标完成。Boss 已死时立即结算，否则等待 Boss 死亡。 */
    public static void markObjectiveComplete(Player p) {
        DungeonContext ctx = context(p);
        if (ctx == null) return;
        ctx.completeObjective();
        HimDungeons plugin = HimDungeons.getInstance();
        if (plugin == null) return;
        plugin.getHookRegistry().fireObjectiveComplete(
                new ObjectiveContext(ctx, p, ObjectiveContext.Type.COMPLETE, "manual"));
        if (ctx.bossDefeated) {
            DungeonInstance inst = instance(p);
            if (inst != null) tryFinishIfReady(plugin, inst);
        }
    }

    /** 记录一个子目标完成，返回是否为首次完成 */
    public static boolean completeSubObjective(Player p, String id) {
        DungeonContext ctx = context(p);
        return ctx != null && ctx.completeSubObjective(id);
    }

    public static boolean isSubObjectiveComplete(Player p, String id) {
        DungeonContext ctx = context(p);
        return ctx != null && ctx.completedSubObjectives.contains(id);
    }

    public static int completedSubObjectiveCount(Player p) {
        DungeonContext ctx = context(p);
        return ctx == null ? 0 : ctx.completedSubObjectives.size();
    }

    /** 让当前地牢失败（触发惩罚并结算）。 */
    public static void failDungeon(Player p, String reason) {
        DungeonContext ctx = context(p);
        if (ctx == null) return;
        ctx.failObjective(reason);
        HimDungeons plugin = HimDungeons.getInstance();
        if (plugin == null) return;
        plugin.getHookRegistry().fireObjectiveFail(
                new ObjectiveContext(ctx, p, ObjectiveContext.Type.FAIL, reason));
        DungeonInstance inst = instance(p);
        if (inst != null) plugin.forceFinishDungeon(inst);
    }

    private static void tryFinishIfReady(HimDungeons plugin, DungeonInstance inst) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (inst.isFinished) return;
            ConfigApplier applier = plugin.configAppliers.get(inst.worldName);
            if (applier == null) return;
            DungeonBossListener bl = plugin.getBossListener();
            if (bl != null) bl.finishDungeon(inst, applier);
        });
    }

    // ==================== 方块统计 ====================

    /** 玩家当前房间内某方块的数量 */
    public static int countBlocks(Player p, Material mat) {
        RoomInstance room = currentRoom(p);
        if (room == null) return 0;
        return countBlocksInRoom(p, room, mat);
    }

    /** 指定房间内某方块的数量 */
    public static int countBlocksInRoom(Player p, RoomInstance room, Material mat) {
        if (p == null || room == null || mat == null) return 0;
        World world = p.getWorld();
        AABBUtil.AABB a = room.aabb;
        int count = 0;
        int minX = (int) Math.floor(a.minX), maxX = (int) Math.ceil(a.maxX);
        int minY = (int) Math.floor(a.minY), maxY = (int) Math.ceil(a.maxY);
        int minZ = (int) Math.floor(a.minZ), maxZ = (int) Math.ceil(a.maxZ);
        for (int y = minY; y <= maxY; y++)
            for (int z = minZ; z <= maxZ; z++)
                for (int x = minX; x <= maxX; x++)
                    if (world.getBlockAt(x, y, z).getType() == mat) count++;
        return count;
    }

    /** 整个地牢内某方块的数量（按房间并集，不含房间之间空隙） */
    public static int countBlocksInDungeon(Player p, Material mat) {
        DungeonInstance inst = instance(p);
        if (inst == null || inst.placedRooms == null || mat == null) return 0;
        World world = p.getWorld();
        int count = 0;
        java.util.Set<Long> visited = new java.util.HashSet<>();
        for (RoomInstance r : inst.placedRooms) {
            AABBUtil.AABB a = r.aabb;
            int minX = (int) Math.floor(a.minX), maxX = (int) Math.ceil(a.maxX);
            int minY = (int) Math.floor(a.minY), maxY = (int) Math.ceil(a.maxY);
            int minZ = (int) Math.floor(a.minZ), maxZ = (int) Math.ceil(a.maxZ);
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    for (int x = minX; x <= maxX; x++) {
                        long key = ((long) x << 42) ^ ((long) z << 21) ^ y;
                        if (!visited.add(key)) continue;
                        if (world.getBlockAt(x, y, z).getType() == mat) count++;
                    }
        }
        return count;
    }

    // ==================== 方块标记（"点亮红石灯"式玩法） ====================

    /** 把一个方块坐标标记到 tag 下（去重） */
    public static void markBlock(Player p, Location loc, String tag) {
        DungeonContext ctx = context(p);
        if (ctx == null || loc == null || tag == null) return;
        Pos3 pos = Pos3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        ctx.blockMarks.computeIfAbsent(tag, k -> ConcurrentHashMap.newKeySet()).add(pos);
    }

    public static boolean isBlockMarked(Player p, Location loc, String tag) {
        DungeonContext ctx = context(p);
        if (ctx == null || loc == null || tag == null) return false;
        Set<Pos3> marks = ctx.blockMarks.get(tag);
        return marks != null && marks.contains(
                Pos3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }

    public static int countMarkedBlocks(Player p, String tag) {
        DungeonContext ctx = context(p);
        if (ctx == null || tag == null) return 0;
        Set<Pos3> marks = ctx.blockMarks.get(tag);
        return marks == null ? 0 : marks.size();
    }

    public static Set<Pos3> getMarkedBlocks(Player p, String tag) {
        DungeonContext ctx = context(p);
        if (ctx == null || tag == null) return Collections.emptySet();
        Set<Pos3> marks = ctx.blockMarks.get(tag);
        return marks == null ? Collections.emptySet() : Collections.unmodifiableSet(marks);
    }
/** 外部汇报：本次运行对玩家造成的伤害（累计）。 */
public static void addDamageDealt(Player p, long amount) {
    DungeonInstance inst = instance(p);
    if (inst != null && inst.runStats != null) inst.runStats.recordDamageDealt(p.getUniqueId(), amount);
}

public static void addDamageTaken(Player p, long amount) {
    DungeonInstance inst = instance(p);
    if (inst != null && inst.runStats != null) inst.runStats.recordDamageTaken(p.getUniqueId(), amount);
}

public static void addMobsKilled(Player p, int n) {
    DungeonInstance inst = instance(p);
    if (inst != null && inst.runStats != null) inst.runStats.recordMobsKilled(p.getUniqueId(), n);
}
}