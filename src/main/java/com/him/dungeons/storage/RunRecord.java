package com.him.dungeons.storage;

import java.util.*;

public final class RunRecord {
    public long id;
    public final String dungeon;
    public final long startedAt;
    public final long finishedAt;
    public final boolean success;
    public final String failReason;
    public final String season;        // 预留，本期 null
    public final String typesUsed;     // JSON 数组
    public final boolean validatorOk;  // 预留，本期恒 true
    public final List<RunMember> members;

    public RunRecord(String dungeon, long startedAt, long finishedAt,
                     boolean success, String failReason, String season,
                     String typesUsed, boolean validatorOk, List<RunMember> members) {
        this.dungeon = dungeon;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.success = success;
        this.failReason = failReason;
        this.season = season;
        this.typesUsed = typesUsed;
        this.validatorOk = validatorOk;
        this.members = members != null ? members : List.of();
    }

    public long durationMs() { return Math.max(0, finishedAt - startedAt); }
    public int teamSize()    { return members.size(); }
    public int totalDeaths() { return members.stream().mapToInt(m -> m.deaths).sum(); }
    public long totalDamageDealt() { return members.stream().mapToLong(m -> m.damageDealt).sum(); }
    public long totalDamageTaken() { return members.stream().mapToLong(m -> m.damageTaken).sum(); }
    public int totalMobsKilled()   { return members.stream().mapToInt(m -> m.mobsKilled).sum(); }

    /**
     * 默认计分公式。外部可通过 ConfigApplier 覆盖（本期仅内置）。
     * 1000 基础通关分 + 击杀奖励 - 死亡惩罚 - 时间惩罚 + 多人奖励
     */
    public int computeScore() {
        if (!success) return 0;
        int score = 1000;
        score += totalMobsKilled() * 10;
        score -= totalDeaths() * 50;
        score -= (int) Math.min(500, durationMs() / 1000 / 10);
        score += teamSize() > 1 ? teamSize() * 50 : 0;
        return Math.max(0, score);
    }
}