package com.him.dungeons.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;

public final class YamlDoorUtil {

    private YamlDoorUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 门锚点 ====================

    public static class DoorAnchor {
        public final int x, y, z;
        public final String facing;
        public final String type;
        public final int weight;
        /** 门自定义 id；未配置时等于列表索引 */
        public final int id;
        /** 连接约束；null 表示无约束 */
        public final ConnectRule connect;

        /** 旧 constructor（向后兼容），id 由列表索引补 */
        public DoorAnchor(int x, int y, int z, String facing, String type, int weight) {
            this(x, y, z, facing, type, weight, -1, null);
        }

        public DoorAnchor(int x, int y, int z, String facing, String type, int weight,
                          int id, ConnectRule connect) {
            this.x = x; this.y = y; this.z = z;
            this.facing = facing;
            this.type = type != null ? type : "default";
            this.weight = Math.max(weight, 1);
            this.id = id;
            this.connect = connect;
        }

        public Vector getDirectionVector() { return TransformUtil.facingToVector(facing); }
        public boolean isVertical()   { return TransformUtil.isVerticalFacing(facing); }
        public boolean isHorizontal() { return TransformUtil.isHorizontalFacing(facing); }

        @Override public String toString() {
            return "DoorAnchor{id=" + id + ", x=" + x + ", y=" + y + ", z=" + z
                    + ", facing=" + facing + "}";
        }
    }

    // ==================== 连接约束 ====================

    /**
     * 单条 allow / deny 条目。
     * <ul>
     *   <li>{@code baseName} 精确匹配对方房间路径（如 {@code combat/combat2}）</li>
     *   <li>{@code type} 匹配对方房间 type</li>
     *   <li>{@code doorIds} 为 null 表示通配，否则必须是对方门的 {@link DoorAnchor#id}</li>
     * </ul>
     * <p>任一条件命中即视为匹配（未配置的字段视为通配）。</p>
     */
    public static class ConnectEntry {
        public final String baseName;
        public final String type;
        public final Set<Integer> doorIds;

        public ConnectEntry(String baseName, String type, Set<Integer> doorIds) {
            this.baseName = baseName;
            this.type = type;
            this.doorIds = doorIds;
        }

        public boolean matches(String otherBaseName, String otherType, int otherDoorId) {
            if (baseName != null && !baseName.equals(otherBaseName)) return false;
            if (type != null && !type.equals(otherType)) return false;
            if (doorIds != null && !doorIds.contains(otherDoorId)) return false;
            return true;
        }
    }

    /**
     * 单扇门的连接规则。
     *
     * <p>语义约定：</p>
     * <ul>
     *   <li>{@code connect} 段缺失 / allow/deny 都为空 → 无约束</li>
     *   <li>{@code allow} 为空但 {@code deny} 非空 → 纯 deny 模式（只禁止列出的）</li>
     *   <li>{@code allow} 非空 → 白名单模式（只允许列出的）</li>
     *   <li>{@code deny} 永远覆盖 {@code allow}</li>
     * </ul>
     */
    public static class ConnectRule {
        public final List<ConnectEntry> allow;
        public final List<ConnectEntry> deny;

        public ConnectRule(List<ConnectEntry> allow, List<ConnectEntry> deny) {
            this.allow = allow == null ? List.of() : allow;
            this.deny  = deny  == null ? List.of() : deny;
        }

        public boolean isEmpty() { return allow.isEmpty() && deny.isEmpty(); }

        /**
         * 判断"这扇门是否允许连接对方"。
         *
         * @param otherBaseName 对方房间路径
         * @param otherType     对方房间 type
         * @param otherDoorId   对方门的 id
         */
        public boolean permits(String otherBaseName, String otherType, int otherDoorId) {
            if (isEmpty()) return true;
            for (ConnectEntry e : deny) {
                if (e.matches(otherBaseName, otherType, otherDoorId)) return false;
            }
            if (allow.isEmpty()) return true;   // 纯 deny 模式
            for (ConnectEntry e : allow) {
                if (e.matches(otherBaseName, otherType, otherDoorId)) return true;
            }
            return false;
        }
    }

    // ==================== 解析：door.yml ====================

    /**
     * 解析门锚点列表（兼容旧格式）。
     * <p>每扇门的 {@link DoorAnchor#id} 在解析时即确定：yml 显式配置优先，
     * 缺失则取列表索引。</p>
     */
    public static List<DoorAnchor> parseDoorAnchors(File doorFile) {
        if (doorFile == null || !doorFile.exists()) {
            throw new IllegalArgumentException("door.yml not found: "
                    + (doorFile == null ? "null" : doorFile.getPath()));
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(doorFile);
        List<?> list = yaml.getList("doors");
        if (list == null) list = yaml.getList("");
        if (list == null) return new ArrayList<>();
        return parseDoorList(list);
    }

    /**
     * 从 YAML 列表解析门锚点（内部使用，供 {@link #parseRoomConfig} 复用）。
     */
    public static List<DoorAnchor> parseDoorList(List<?> list) {
        List<DoorAnchor> anchors = new ArrayList<>();
        if (list == null) return anchors;

        for (int i = 0; i < list.size(); i++) {
            Object obj = list.get(i);
            if (!(obj instanceof Map<?, ?> map)) continue;
            try {
                int x = ((Number) map.get("x")).intValue();
                int y = ((Number) map.get("y")).intValue();
                int z = ((Number) map.get("z")).intValue();
                String facing = String.valueOf(map.get("facing"));
                String type = map.containsKey("type") ? String.valueOf(map.get("type")) : "default";
                int weight = map.containsKey("weight")
                        ? ((Number) map.get("weight")).intValue() : 10;
                int id = map.containsKey("id")
                        ? ((Number) map.get("id")).intValue() : i;

                if (!TransformUtil.isValidFacing(facing)) continue;

                ConnectRule connect = map.containsKey("connect")
                        ? parseConnectRule(map.get("connect")) : null;

                anchors.add(new DoorAnchor(x, y, z, facing, type, weight, id, connect));
            } catch (Exception ignored) {
                // 格式错误的门直接跳过，不阻塞其他门
            }
        }
        return anchors;
    }

    @SuppressWarnings("unchecked")
    private static ConnectRule parseConnectRule(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) return null;
        List<ConnectEntry> allow = parseConnectList(map.get("allow"));
        List<ConnectEntry> deny  = parseConnectList(map.get("deny"));
        return new ConnectRule(allow, deny);
    }

    @SuppressWarnings("unchecked")
    private static List<ConnectEntry> parseConnectList(Object raw) {
        List<ConnectEntry> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return out;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String baseName = m.containsKey("baseName") ? String.valueOf(m.get("baseName")) : null;
            String type     = m.containsKey("type")     ? String.valueOf(m.get("type"))     : null;
            Set<Integer> doorIds = parseDoorIds(m.get("doorid"));
            out.add(new ConnectEntry(baseName, type, doorIds));
        }
        return out;
    }

    private static Set<Integer> parseDoorIds(Object raw) {
        if (raw == null) return null;    // 通配
        if (raw instanceof Number n) return Set.of(n.intValue());
        if (raw instanceof List<?> list) {
            Set<Integer> ids = new HashSet<>();
            for (Object o : list) if (o instanceof Number num) ids.add(num.intValue());
            return ids.isEmpty() ? null : ids;
        }
        return null;
    }

    // ==================== 解析：spawn.yml / chest.yml（保持旧签名） ====================

    public static org.bukkit.util.Vector parseSpawnPoint(File spawnFile) {
        if (spawnFile == null || !spawnFile.exists()) return null;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(spawnFile);
        if (yaml.contains("x") && yaml.contains("y") && yaml.contains("z")) {
            return new Vector(yaml.getDouble("x"), yaml.getDouble("y"), yaml.getDouble("z"));
        }
        if (yaml.contains("spawn")) {
            ConfigurationSection spawnSec = yaml.getConfigurationSection("spawn");
            if (spawnSec != null) {
                return new Vector(spawnSec.getDouble("x"),
                        spawnSec.getDouble("y"), spawnSec.getDouble("z"));
            }
        }
        return null;
    }

    public static List<Map<String, Object>> parseChestLoot(File chestFile) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (chestFile == null || !chestFile.exists()) return result;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(chestFile);
        List<?> list = yaml.getList("chests");
        if (list == null) list = yaml.getList("");
        if (list == null) return result;
        return parseChestList(list);
    }

    /** 从 YAML 列表解析箱子（内部使用）。 */
    public static List<Map<String, Object>> parseChestList(List<?> list) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (list == null) return result;
        for (Object obj : list) {
            if (!(obj instanceof Map<?, ?> map)) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            Object posObj = map.get("pos");
            if (posObj instanceof List<?> posList && posList.size() >= 3) {
                try {
                    int x = ((Number) posList.get(0)).intValue();
                    int y = ((Number) posList.get(1)).intValue();
                    int z = ((Number) posList.get(2)).intValue();
                    entry.put("pos", Arrays.asList(x, y, z));
                } catch (Exception ignored) { continue; }
            } else {
                continue;
            }
            Object lootObj = map.get("loot");
            if (lootObj != null) entry.put("loot", lootObj.toString());
            result.add(entry);
        }
        return result;
    }
}