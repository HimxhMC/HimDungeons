package com.him.dungeons.storage;

public enum LeaderboardMode {
    FASTEST("fastest"), WINS("wins"), SCORE("score");
    public final String id;
    LeaderboardMode(String id) { this.id = id; }
}