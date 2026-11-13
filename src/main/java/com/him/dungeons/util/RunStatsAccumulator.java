package com.him.dungeons.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一次地牢运行中的运行统计（线程安全）。
 *
 * <p>插件内部采集：deaths（PlayerDeathEvent）。</p>
 * <p>外部通过 {@code DungeonAPI} 汇报：damageDealt / damageTaken / mobsKilled。</p>
 */
public final class RunStatsAccumulator {

    private final Map<UUID, Integer> deaths       = new ConcurrentHashMap<>();
    private final Map<UUID, Long>    damageDealt  = new ConcurrentHashMap<>();
    private final Map<UUID, Long>    damageTaken  = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> mobsKilled   = new ConcurrentHashMap<>();

    public void recordDeath(UUID uuid)                 { deaths.merge(uuid, 1, Integer::sum); }
    public void recordDamageDealt(UUID uuid, long a)   { if (a > 0) damageDealt.merge(uuid, a, Long::sum); }
    public void recordDamageTaken(UUID uuid, long a)   { if (a > 0) damageTaken.merge(uuid, a, Long::sum); }
    public void recordMobsKilled(UUID uuid, int n)     { if (n > 0) mobsKilled.merge(uuid, n, Integer::sum); }

    public int getDeaths(UUID uuid)       { return deaths.getOrDefault(uuid, 0); }
    public long getDamageDealt(UUID uuid) { return damageDealt.getOrDefault(uuid, 0L); }
    public long getDamageTaken(UUID uuid) { return damageTaken.getOrDefault(uuid, 0L); }
    public int getMobsKilled(UUID uuid)   { return mobsKilled.getOrDefault(uuid, 0); }

    public Set<UUID> allTrackedUuids() {
        Set<UUID> all = new HashSet<>();
        all.addAll(deaths.keySet());
        all.addAll(damageDealt.keySet());
        all.addAll(damageTaken.keySet());
        all.addAll(mobsKilled.keySet());
        return all;
    }
}