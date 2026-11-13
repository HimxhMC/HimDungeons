package com.him.dungeons.storage;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.LanguageManager;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class SqliteRunStorage implements RunStorage {

    private final HimDungeons plugin;
    private Connection conn;

    public SqliteRunStorage(HimDungeons plugin) {
        this.plugin = plugin;
        init();
    }

    @Override
    public synchronized void init() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();
            File dbFile = new File(dataFolder, "stats.db");
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("""
                    CREATE TABLE IF NOT EXISTS run_record (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        dungeon TEXT NOT NULL,
                        started_at INTEGER NOT NULL,
                        finished_at INTEGER NOT NULL,
                        duration_ms INTEGER NOT NULL,
                        team_size INTEGER NOT NULL,
                        success INTEGER NOT NULL,
                        fail_reason TEXT,
                        damage_dealt INTEGER DEFAULT 0,
                        damage_taken INTEGER DEFAULT 0,
                        mobs_killed INTEGER DEFAULT 0,
                        deaths INTEGER DEFAULT 0,
                        score INTEGER DEFAULT 0,
                        season TEXT,
                        types_used TEXT,
                        validator_ok INTEGER DEFAULT 1
                    )
                """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS run_member (
                        run_id INTEGER NOT NULL,
                        uuid TEXT NOT NULL,
                        name TEXT NOT NULL,
                        deaths INTEGER DEFAULT 0,
                        damage_dealt INTEGER DEFAULT 0,
                        damage_taken INTEGER DEFAULT 0,
                        mobs_killed INTEGER DEFAULT 0,
                        PRIMARY KEY (run_id, uuid)
                    )
                """);
                st.execute("CREATE INDEX IF NOT EXISTS idx_lb_fastest ON run_record(dungeon, success, team_size, duration_ms)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_lb_score ON run_record(dungeon, success, team_size, score)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_member_uuid ON run_member(uuid, run_id)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_record_dungeon ON run_record(dungeon, finished_at)");
            }
            plugin.getLogger().info(LanguageManager.get("log.sqlite.run-init-ok",
                    "path", dbFile.getAbsolutePath()));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    LanguageManager.get("log.sqlite.run-init-fail"), e);
        }
    }

    private synchronized Connection getConn() throws SQLException {
        if (conn == null || conn.isClosed()) init();
        return conn;
    }

    // ==================== 写入 ====================

    @Override
    public synchronized void insertBatch(List<RunRecord> records) {
        if (records == null || records.isEmpty()) return;
        try {
            Connection c = getConn();
            c.setAutoCommit(false);
            try (PreparedStatement rs = c.prepareStatement("""
                        INSERT INTO run_record
                            (dungeon, started_at, finished_at, duration_ms,
                             team_size, success, fail_reason, damage_dealt,
                             damage_taken, mobs_killed, deaths, score,
                             season, types_used, validator_ok)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement ms = c.prepareStatement("""
                        INSERT INTO run_member
                            (run_id, uuid, name, deaths, damage_dealt, damage_taken, mobs_killed)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {

                for (RunRecord r : records) {
                    rs.setString(1, r.dungeon);
                    rs.setLong(2, r.startedAt);
                    rs.setLong(3, r.finishedAt);
                    rs.setLong(4, r.durationMs());
                    rs.setInt(5, r.teamSize());
                    rs.setInt(6, r.success ? 1 : 0);
                    rs.setString(7, r.failReason);
                    rs.setLong(8, r.totalDamageDealt());
                    rs.setLong(9, r.totalDamageTaken());
                    rs.setInt(10, r.totalMobsKilled());
                    rs.setInt(11, r.totalDeaths());
                    rs.setInt(12, r.computeScore());
                    rs.setString(13, r.season);
                    rs.setString(14, r.typesUsed);
                    rs.setInt(15, r.validatorOk ? 1 : 0);
                    rs.executeUpdate();

                    long runId;
                    try (ResultSet keys = rs.getGeneratedKeys()) {
                        if (!keys.next()) continue;
                        runId = keys.getLong(1);
                    }
                    for (RunMember m : r.members) {
                        ms.setLong(1, runId);
                        ms.setString(2, m.uuid.toString());
                        ms.setString(3, m.name);
                        ms.setInt(4, m.deaths);
                        ms.setLong(5, m.damageDealt);
                        ms.setLong(6, m.damageTaken);
                        ms.setInt(7, m.mobsKilled);
                        ms.addBatch();
                    }
                }
                ms.executeBatch();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    LanguageManager.get("log.sqlite.run-insert-fail"), e);
        }
    }

    // ==================== 聚合查询 ====================

    @Override
    public List<RankEntry> top(String dungeon, LeaderboardMode mode,
                               TeamSizeBucket bucket, Period period, int limit) {
        List<RankEntry> out = new ArrayList<>();
        String aggColumn = switch (mode) {
            case FASTEST -> "MIN(r.duration_ms)";
            case WINS    -> "COUNT(*)";
            case SCORE   -> "MAX(r.score)";
        };
        String order = switch (mode) {
            case FASTEST -> "ASC";
            case WINS, SCORE -> "DESC";
        };
        String sql = """
                SELECT m.uuid, m.name, %s AS v
                FROM run_record r
                JOIN run_member m ON m.run_id = r.id
                WHERE r.dungeon = ? AND r.success = 1
                  AND r.team_size BETWEEN ? AND ?
                  AND r.finished_at >= ?
                GROUP BY m.uuid
                ORDER BY v %s
                LIMIT ?
                """.formatted(aggColumn, order);

        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setString(1, dungeon);
            ps.setInt(2, bucket.min);
            ps.setInt(3, bucket.max);
            ps.setLong(4, period.since());
            ps.setInt(5, limit);
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 0;
                while (rs.next()) {
                    rank++;
                    out.add(new RankEntry(rank,
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("name"),
                            rs.getLong("v")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    LanguageManager.get("log.sqlite.run-query-fail"), e);
        }
        return out;
    }

    @Override
    public int playerRank(UUID uuid, String dungeon, LeaderboardMode mode,
                          TeamSizeBucket bucket, Period period) {
        List<RankEntry> list = top(dungeon, mode, bucket, period, 1000);
        for (RankEntry e : list) if (e.uuid.equals(uuid)) return e.rank;
        return -1;
    }

    @Override
    public PlayerStats playerStats(UUID uuid, String dungeon, Period period) {
        PlayerStats stats = new PlayerStats(dungeon);
        String sql = """
                SELECT r.success, r.duration_ms, r.deaths
                FROM run_record r
                JOIN run_member m ON m.run_id = r.id
                WHERE m.uuid = ? AND r.dungeon = ? AND r.finished_at >= ?
                """;
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, dungeon);
            ps.setLong(3, period.since());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean success = rs.getInt("success") == 1;
                    if (success) {
                        stats.successCount++;
                        long d = rs.getLong("duration_ms");
                        stats.totalMs += d;
                        if (d < stats.fastestMs) stats.fastestMs = d;
                    } else {
                        stats.failCount++;
                    }
                    stats.totalDeaths += rs.getInt("deaths");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    LanguageManager.get("log.sqlite.run-query-fail"), e);
        }
        return stats;
    }

    @Override
    public DungeonSummary dungeonSummary(String dungeon, Period period) {
        DungeonSummary s = new DungeonSummary(dungeon);
        long since = period.since();

        // 1. 基础汇总
        String summarySql = """
                SELECT success, duration_ms, team_size, fail_reason,
                       damage_dealt, mobs_killed, deaths
                FROM run_record WHERE dungeon = ? AND finished_at >= ?
                """;
        try (PreparedStatement ps = getConn().prepareStatement(summarySql)) {
            ps.setString(1, dungeon);
            ps.setLong(2, since);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    s.totalRuns++;
                    if (rs.getInt("success") == 1) {
                        s.successCount++;
                        long d = rs.getLong("duration_ms");
                        s.totalMs += d;
                        if (d < s.fastestMs) s.fastestMs = d;
                    } else {
                        s.failCount++;
                        String reason = rs.getString("fail_reason");
                        s.failReasons.merge(reason == null ? "unknown" : reason, 1, Integer::sum);
                    }
                    s.totalDeaths      += rs.getInt("deaths");
                    s.totalDamageDealt += rs.getLong("damage_dealt");
                    s.totalMobsKilled  += rs.getInt("mobs_killed");

                    int teamSize = rs.getInt("team_size");
                    String bucket = switch (teamSize) {
                        case 1 -> "solo";
                        case 2 -> "duo";
                        case 3 -> "trio";
                        case 4 -> "quad";
                        default -> "large";
                    };
                    s.teamSizeBuckets.merge(bucket, 1, Integer::sum);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    LanguageManager.get("log.sqlite.run-query-fail"), e);
        }
        if (s.successCount > 0) s.averageMs = s.totalMs / s.successCount;
        if (s.fastestMs == Long.MAX_VALUE) s.fastestMs = -1;

        s.topFastest.addAll(top(dungeon, LeaderboardMode.FASTEST,
                TeamSizeBucket.ALL, period, 5));
        s.topWins.addAll(top(dungeon, LeaderboardMode.WINS,
                TeamSizeBucket.ALL, period, 5));
        return s;
    }

    @Override
    public List<RunRecord> exportAll(String dungeon) {
        List<RunRecord> out = new ArrayList<>();
        String sql = """
                SELECT * FROM run_record WHERE dungeon = ? ORDER BY id
                """;
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setString(1, dungeon);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRecord(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    LanguageManager.get("log.sqlite.run-query-fail"), e);
        }
        // 填充 members
        Map<Long, RunRecord> byId = new HashMap<>();
        for (RunRecord r : out) byId.put(r.id, r);
        String memSql = "SELECT * FROM run_member WHERE run_id IN "
                + "(SELECT id FROM run_record WHERE dungeon = ?)";
        try (PreparedStatement ps = getConn().prepareStatement(memSql)) {
            ps.setString(1, dungeon);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RunRecord parent = byId.get(rs.getLong("run_id"));
                    if (parent == null) continue;
                    parent.members.add(mapMember(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    LanguageManager.get("log.sqlite.run-query-fail"), e);
        }
        return out;
    }

private RunRecord mapRecord(ResultSet rs) throws SQLException {
    RunRecord r = new RunRecord(
            rs.getString("dungeon"),
            rs.getLong("started_at"),
            rs.getLong("finished_at"),
            rs.getInt("success") == 1,
            rs.getString("fail_reason"),
            rs.getString("season"),
            rs.getString("types_used"),
            rs.getInt("validator_ok") == 1,
            new ArrayList<>()
    );
    r.id = rs.getLong("id");
    return r;
}

    private RunMember mapMember(ResultSet rs) throws SQLException {
        return new RunMember(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("name"),
                rs.getInt("deaths"),
                rs.getLong("damage_dealt"),
                rs.getLong("damage_taken"),
                rs.getInt("mobs_killed")
        );
    }

    @Override
    public synchronized void close() {
        try {
            if (conn != null && !conn.isClosed()) conn.close();
        } catch (SQLException ignored) {}
        conn = null;
    }
}