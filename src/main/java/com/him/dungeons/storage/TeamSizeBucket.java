package com.him.dungeons.storage;

public enum TeamSizeBucket {
    ALL(0, 999), SOLO(1, 1), DUO(2, 2), TRIO(3, 3), QUAD(4, 4), LARGE(5, 999);
    public final int min;
    public final int max;
    TeamSizeBucket(int min, int max) { this.min = min; this.max = max; }
    public boolean matches(int n) { return n >= min && n <= max; }
}