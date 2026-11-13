package com.him.dungeons.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 玩家进入地牢的冷却（按 dungeonName 分别计算）。 */
public final class PlayerCooldownManager {

    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new ConcurrentHashMap<>();

    private PlayerCooldownManager() {}

    public static void set(UUID uuid, String dungeon, int seconds) {
        if (uuid == null || dungeon == null || seconds <= 0) return;
        COOLDOWNS.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(dungeon, System.currentTimeMillis() + seconds * 1000L);
    }

    public static int getRemaining(UUID uuid, String dungeon) {
        Map<String, Long> m = COOLDOWNS.get(uuid);
        if (m == null) return 0;
        Long expire = m.get(dungeon);
        if (expire == null) return 0;
        long remain = (expire - System.currentTimeMillis()) / 1000L;
        if (remain <= 0) { m.remove(dungeon); return 0; }
        return (int) remain;
    }

    public static void clear(UUID uuid) {
        if (uuid != null) COOLDOWNS.remove(uuid);
    }
}