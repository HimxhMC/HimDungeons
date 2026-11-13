package com.him.dungeons.storage;

public enum Period {
    ALL(0L),
    WEEK(7L * 86400000L),
    MONTH(30L * 86400000L);
    public final long millis;
    Period(long m) { this.millis = m; }
    /** 返回查询起点时间戳（0 = 不限） */
    public long since() { return millis == 0 ? 0 : System.currentTimeMillis() - millis; }
}