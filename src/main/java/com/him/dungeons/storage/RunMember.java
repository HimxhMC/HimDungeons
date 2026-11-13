package com.him.dungeons.storage;

import java.util.UUID;

public final class RunMember {
    public final UUID uuid;
    public final String name;
    public final int deaths;
    public final long damageDealt;
    public final long damageTaken;
    public final int mobsKilled;

    public RunMember(UUID uuid, String name, int deaths, long damageDealt,
                     long damageTaken, int mobsKilled) {
        this.uuid = uuid;
        this.name = name;
        this.deaths = deaths;
        this.damageDealt = damageDealt;
        this.damageTaken = damageTaken;
        this.mobsKilled = mobsKilled;
    }
}