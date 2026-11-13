package com.him.dungeons.util;

import com.him.dungeons.storage.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 排行榜查询缓存（30 秒 TTL）。
 *
 * <p>通关时由 {@code RunRecordService} 主动失效对应地牢 + 玩家，无需等待 TTL。</p>
 */
public final class LeaderboardCache {

    private static final long TTL_MS = 30_000;

    private final RunStorage storage;
    private final Map<String, CachedTop>   topCache   = new ConcurrentHashMap<>();
    private final Map<UUID, CachedStats>   statsCache = new ConcurrentHashMap<>();

    public LeaderboardCache(RunStorage storage) { this.storage = storage; }

    private record CachedTop(List<RankEntry> data, long expireAt) {
        boolean valid() { return System.currentTimeMillis() < expireAt; }
    }
    private record CachedStats(PlayerStats data, long expireAt) {
        boolean valid() { return System.currentTimeMillis() < expireAt; }
    }

    public List<RankEntry> top(String dungeon, LeaderboardMode mode,
                               TeamSizeBucket bucket, Period period, int limit) {
        String key = dungeon + "|" + mode + "|" + bucket + "|" + period + "|" + limit;
        CachedTop c = topCache.get(key);
        if (c != null && c.valid()) return c.data();
        List<RankEntry> data = storage.top(dungeon, mode, bucket, period, limit);
        topCache.put(key, new CachedTop(data, System.currentTimeMillis() + TTL_MS));
        return data;
    }

    public PlayerStats playerStats(UUID uuid, String dungeon, Period period) {
        CachedStats c = statsCache.get(uuid);
        if (c != null && c.valid() && c.data().dungeon.equals(dungeon)) {
            return c.data();
        }
        PlayerStats s = storage.playerStats(uuid, dungeon, period);
        statsCache.put(uuid, new CachedStats(s, System.currentTimeMillis() + TTL_MS));
        return s;
    }

    public void invalidate(String dungeon) {
        String prefix = dungeon + "|";
        topCache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public void invalidatePlayer(UUID uuid) {
        statsCache.remove(uuid);
    }

    public void invalidateAll() {
        topCache.clear();
        statsCache.clear();
    }
}