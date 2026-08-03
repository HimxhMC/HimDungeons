package com.him.dungeons.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;

/**
 * 解析 door.yml / chest.yml / spawn.yml
 * 
 * door.yml 格式：{ { x= y= z= facing= } { …… } }
 * facing 取值：x+、x-、y+、y-、z+、z-
 */
public final class YamlDoorUtil {

    private YamlDoorUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 门锚点数据类
     */
    public static class DoorAnchor {
        public final int x, y, z;
        public final String facing; // "x+"、"x-"、"y+"、"y-"、"z+"、"z-"

        public DoorAnchor(int x, int y, int z, String facing) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.facing = facing;
        }

        public Vector getDirectionVector() {
            return TransformUtil.facingToVector(facing);
        }

        /**
         * 判断该门是否为垂直方向（y+ / y-）
         */
        public boolean isVertical() {
            return TransformUtil.isVerticalFacing(facing);
        }

        /**
         * 判断该门是否为水平方向（x+ / x- / z+ / z-）
         */
        public boolean isHorizontal() {
            return TransformUtil.isHorizontalFacing(facing);
        }

        @Override
        public String toString() {
            return "DoorAnchor{x=" + x + ", y=" + y + ", z=" + z + ", facing=" + facing + "}";
        }
    }

    /**
     * 解析 door.yml
     * 
     * @param doorFile door.yml 文件
     * @return 门锚点列表
     * @throws IllegalArgumentException 若文件不存在或格式错误
     */
    public static List<DoorAnchor> parseDoorAnchors(File doorFile) {
        if (doorFile == null || !doorFile.exists()) {
            throw new IllegalArgumentException("door.yml not found: " + (doorFile == null ? "null" : doorFile.getPath()));
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(doorFile);
        List<DoorAnchor> anchors = new ArrayList<>();
        
        // 兼容两种格式：根节点是 "doors" 列表，或根节点直接是列表
        List<?> list = yaml.getList("doors");
        if (list == null) list = yaml.getList("");
        if (list == null) return anchors;

        for (Object obj : list) {
            if (!(obj instanceof Map)) continue;
            Map<?, ?> map = (Map<?, ?>) obj;
            try {
                int x = ((Number) map.get("x")).intValue();
                int y = ((Number) map.get("y")).intValue();
                int z = ((Number) map.get("z")).intValue();
                String facing = map.get("facing").toString();
                
                // 校验 facing 格式是否合法
                if (!TransformUtil.isValidFacing(facing)) {
                    // TODO: 日志记录警告，跳过非法门锚点
                    continue;
                }
                anchors.add(new DoorAnchor(x, y, z, facing));
            } catch (Exception ignored) {
                // TODO: 日志记录解析失败
            }
        }
        return anchors;
    }

    /**
 * 解析 spawn.yml 为出生点偏移向量
 *
 * @param spawnFile spawn.yml 文件
 * @return 出生点偏移向量（相对于房间锚点），若不存在或格式错误返回 null
 */
public static org.bukkit.util.Vector parseSpawnPoint(File spawnFile) {
    if (spawnFile == null || !spawnFile.exists()) {
        return null;
    }
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(spawnFile);
    // 支持两种格式：根节点直接是 x,y,z，或使用 "spawn" 节点
    if (yaml.contains("x") && yaml.contains("y") && yaml.contains("z")) {
        double x = yaml.getDouble("x");
        double y = yaml.getDouble("y");
        double z = yaml.getDouble("z");
        return new org.bukkit.util.Vector(x, y, z);
    }
    if (yaml.contains("spawn")) {
        ConfigurationSection spawnSec = yaml.getConfigurationSection("spawn");
        if (spawnSec != null) {
            double x = spawnSec.getDouble("x");
            double y = spawnSec.getDouble("y");
            double z = spawnSec.getDouble("z");
            return new org.bukkit.util.Vector(x, y, z);
        }
    }
    return null;
}
/**
 * 解析 chest.yml 为战利品列表
 * 
 * 符合文档规范：chest.yml { { pos:[,,] loot: <概率> DIAMOND 50 } { …… } }
 * 
 * 每个条目包含：
 *   - pos: [x, y, z] 原理图内相对坐标（整数数组）
 *   - loot: "<概率> <物品ID> [数量]" 例如 "50 DIAMOND 1"
 * 
 * @param chestFile chest.yml 文件
 * @return 宝箱列表，每个元素包含 pos（List<Integer>）和 loot（String）
 *         若文件不存在或格式错误返回空列表
 */
public static List<Map<String, Object>> parseChestLoot(File chestFile) {
    List<Map<String, Object>> result = new ArrayList<>();
    if (chestFile == null || !chestFile.exists()) {
        return result;
    }
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(chestFile);

    // 读取根列表
    List<?> list = yaml.getList("");
    if (list == null) return result;

    for (Object obj : list) {
        if (!(obj instanceof Map)) continue;
        Map<?, ?> map = (Map<?, ?>) obj;
        Map<String, Object> entry = new LinkedHashMap<>();

        // 解析 pos: [x, y, z]
        Object posObj = map.get("pos");
        if (posObj instanceof List) {
            List<?> posList = (List<?>) posObj;
            if (posList.size() >= 3) {
                try {
                    int x = ((Number) posList.get(0)).intValue();
                    int y = ((Number) posList.get(1)).intValue();
                    int z = ((Number) posList.get(2)).intValue();
                    entry.put("pos", Arrays.asList(x, y, z));
                } catch (Exception ignored) {
                    continue; // pos 格式错误，跳过该条目
                }
            } else {
                continue; // pos 数组长度不足
            }
        } else {
            continue; // 缺少 pos 字段
        }

        // 解析 loot: "<概率> <物品ID> [数量]"
        Object lootObj = map.get("loot");
        if (lootObj != null) {
            entry.put("loot", lootObj.toString());
        }

        result.add(entry);
    }
    return result;
}
}