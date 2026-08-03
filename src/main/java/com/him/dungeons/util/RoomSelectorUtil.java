package com.him.dungeons.util;

import java.util.*;

/**
 * 房间类型权重选择器（含排除逻辑和Fallback）
 */
public final class RoomSelectorUtil {

    public RoomSelectorUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 按权重随机选择房间类型
     *
     * @param weightMap      房间类型 -> 权重（来自 ConfigUtil）
     * @param excludeTypes   要排除的类型集合（如 start, boss）
     * @param fallbackType   保底类型（如 fallback）
     * @return 选中的类型
     */
    public static String selectByWeight(Map<String, Integer> weightMap,
                                        Set<String> excludeTypes,
                                        String fallbackType) {
        if (weightMap == null || weightMap.isEmpty()) return fallbackType;

        List<String> candidates = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, Integer> entry : weightMap.entrySet()) {
            String type = entry.getKey();
            int weight = entry.getValue();
            if (weight > 0 && !excludeTypes.contains(type)) {
                candidates.add(type);
                total += weight;
            }
        }
        if (candidates.isEmpty()) return fallbackType;

        int rand = new Random().nextInt(total);
        int cum = 0;
        for (String type : candidates) {
            cum += weightMap.get(type);
            if (rand < cum) return type;
        }
        return candidates.get(candidates.size() - 1);
    }
}