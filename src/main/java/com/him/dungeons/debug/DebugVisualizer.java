package com.him.dungeons.debug;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.generator.RoomConfig;
import com.him.dungeons.generator.RoomConfigReader;
import com.him.dungeons.util.LanguageManager;
import com.him.dungeons.util.MessageUtil;
import com.him.dungeons.util.SelectionManager;
import com.him.dungeons.util.YamlDoorUtil;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DebugVisualizer {

    private static final float MARKER_SIZE = 0.6f;

    private final HimDungeons plugin;
    private final Map<UUID, VisualizeSession> sessions = new ConcurrentHashMap<>();

    public DebugVisualizer(HimDungeons plugin) {
        this.plugin = plugin;
    }

    // ==================== 选区 ====================

    public void showSelection(Player p, SelectionManager.Selection sel) {
        if (p == null || sel == null || sel.pos1.getWorld() == null) return;

        VisualizeSession session = sessions.computeIfAbsent(p.getUniqueId(), k -> new VisualizeSession());

        if (session.lastMinX == sel.minX && session.lastMinY == sel.minY
                && session.lastMinZ == sel.minZ && session.lastMaxX == sel.maxX
                && session.lastMaxY == sel.maxY && session.lastMaxZ == sel.maxZ) {
            return;
        }

        session.clear();
        session.lastMinX = sel.minX; session.lastMinY = sel.minY; session.lastMinZ = sel.minZ;
        session.lastMaxX = sel.maxX; session.lastMaxY = sel.maxY; session.lastMaxZ = sel.maxZ;

        World world = sel.pos1.getWorld();
        
        // 这里的边界跨度精确到整数方块
        float sx = sel.maxX - sel.minX + 1;
        float sy = sel.maxY - sel.minY + 1;
        float sz = sel.maxZ - sel.minZ + 1;

        // 直接传入最小角坐标 (minX, minY, minZ)，无需任何 +/- 0.5 偏移
        session.add(DisplayEntityHelper.spawnBlock(world,
                sel.minX, sel.minY, sel.minZ,
                sx, sy, sz,
                Material.WHITE_STAINED_GLASS, false));

        MessageUtil.sendMessage(p, "debug.selection-shown",
                "w", sel.width, "h", sel.height, "l", sel.length);
    }

    // ==================== 房间元素 ====================

    public boolean showRoomElements(Player p, String dungeon, String baseName) {
        SelectionManager.Selection sel = SelectionManager.getSelection(p.getUniqueId());
        if (sel == null) {
            MessageUtil.sendMessage(p, "debug.no-selection");
            return false;
        }
        if (sel.pos1.getWorld() == null) return false;

        // 确保大白框存在
        showSelection(p, sel);

        VisualizeSession session = sessions.computeIfAbsent(p.getUniqueId(), k -> new VisualizeSession());
        RoomConfig cfg = RoomConfigReader.read(dungeon, baseName);
        if (cfg == null) return false;

        World world = sel.pos1.getWorld();

        // ---- 门（改用淡蓝色玻璃，不用绿色了） ----
        for (YamlDoorUtil.DoorAnchor door : cfg.doors) {
            double wx = sel.minX + door.x + 0.5;
            double wy = sel.minY + door.y + 0.5;
            double wz = sel.minZ + door.z + 0.5;

            session.add(DisplayEntityHelper.spawnBlock(world,
                    wx - MARKER_SIZE / 2.0, wy - MARKER_SIZE / 2.0, wz - MARKER_SIZE / 2.0,
                    MARKER_SIZE, MARKER_SIZE, MARKER_SIZE,
                    Material.LIGHT_BLUE_STAINED_GLASS, true)); // ★ 改为淡蓝色

            String label = "§b#" + door.id + " " + door.facing
                    + (door.type != null && !"default".equals(door.type) ? " §7[" + door.type + "]" : "");
            session.add(DisplayEntityHelper.spawnText(world,
                    wx, wy + 0.8, wz, label));
        }

        // ---- 箱子 ----
        for (Map<String, Object> chest : cfg.chests) {
            Object posRaw = chest.get("pos");
            if (!(posRaw instanceof List<?> pos) || pos.size() < 3) continue;
            try {
                double wx = sel.minX + ((Number) pos.get(0)).doubleValue() + 0.5;
                double wy = sel.minY + ((Number) pos.get(1)).doubleValue() + 0.5;
                double wz = sel.minZ + ((Number) pos.get(2)).doubleValue() + 0.5;
                session.add(DisplayEntityHelper.spawnBlock(world,
                        wx - MARKER_SIZE / 2.0, wy - MARKER_SIZE / 2.0, wz - MARKER_SIZE / 2.0,
                        MARKER_SIZE, MARKER_SIZE, MARKER_SIZE,
                        Material.BROWN_STAINED_GLASS, true));
            } catch (Exception ignored) {}
        }

        // ---- 出生点 ----
        if (cfg.spawn != null) {
            double wx = sel.minX + cfg.spawn.getX() + 0.5;
            double wy = sel.minY + cfg.spawn.getY() + 0.5;
            double wz = sel.minZ + cfg.spawn.getZ() + 0.5;
            float s = MARKER_SIZE + 0.2f;
            session.add(DisplayEntityHelper.spawnBlock(world,
                    wx - s / 2.0, wy - s / 2.0, wz - s / 2.0,
                    s, s, s, Material.GOLD_BLOCK, true));
            session.add(DisplayEntityHelper.spawnText(world,
                    wx, wy + 0.9, wz, "§6出生点"));
        }

        // ---- 存档点 ----
        if (cfg.checkpoint != null) {
            double wx = sel.minX + cfg.checkpoint.getX() + 0.5;
            double wy = sel.minY + cfg.checkpoint.getY() + 0.5;
            double wz = sel.minZ + cfg.checkpoint.getZ() + 0.5;
            float s = MARKER_SIZE + 0.2f;
            session.add(DisplayEntityHelper.spawnBlock(world,
                    wx - s / 2.0, wy - s / 2.0, wz - s / 2.0,
                    s, s, s, Material.DIAMOND_BLOCK, true));
            session.add(DisplayEntityHelper.spawnText(world,
                    wx, wy + 0.9, wz, "§b存档点"));
        }

        MessageUtil.sendMessage(p, "debug.room-shown", "room", baseName);
        return true;
    }

    // ==================== 清理 ====================

    public void clear(Player p) {
        if (p == null) return;
        VisualizeSession s = sessions.remove(p.getUniqueId());
        if (s != null) s.clear();
    }

    public void clear(UUID uuid) {
        if (uuid == null) return;
        VisualizeSession s = sessions.remove(uuid);
        if (s != null) s.clear();
    }

    public void clearAll() {
        for (VisualizeSession s : sessions.values()) s.clear();
        sessions.clear();
    }
}