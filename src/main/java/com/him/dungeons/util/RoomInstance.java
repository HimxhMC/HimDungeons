package com.him.dungeons.util;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import com.him.dungeons.util.Pos3;

public class RoomInstance {
    public final String roomType;   // 用途：start / normal / boss / fallback
    public final String type;       // 房间身份：combat / treasure / ...
    public final String baseName;
    public final Location anchor;
    public final int rotationTimes;
    public final Pos3 size;
    public final AABBUtil.AABB aabb;
    public final List<YamlDoorUtil.DoorAnchor> doors;
    public final List<Integer> usedDoorIndices;
    public final boolean isFallback;

    // 旧构造（type = roomType，向后兼容）
    public RoomInstance(String roomType, String baseName, Location anchor, int rotationTimes,
                        Pos3 size, AABBUtil.AABB aabb, List<YamlDoorUtil.DoorAnchor> doors) {
        this(roomType, roomType, baseName, anchor, rotationTimes, size, aabb, doors, false);
    }
    public RoomInstance(String roomType, String baseName, Location anchor, int rotationTimes,
                        Pos3 size, AABBUtil.AABB aabb, List<YamlDoorUtil.DoorAnchor> doors,
                        boolean isFallback) {
        this(roomType, roomType, baseName, anchor, rotationTimes, size, aabb, doors, isFallback);
    }

    // 新构造（带 type）
    public RoomInstance(String roomType, String type, String baseName, Location anchor, int rotationTimes,
                        Pos3 size, AABBUtil.AABB aabb, List<YamlDoorUtil.DoorAnchor> doors,
                        boolean isFallback) {
        this.roomType = roomType;
        this.type = type != null ? type : roomType;
        this.baseName = baseName;
        this.anchor = anchor;
        this.rotationTimes = rotationTimes;
        this.size = size;
        this.aabb = aabb;
        this.doors = new ArrayList<>(doors);
        this.usedDoorIndices = new ArrayList<>();
        this.isFallback = isFallback;
    }

public List<Integer> getAvailableDoorIndices() {
    List<Integer> available = new ArrayList<>();
    for (int i = 0; i < doors.size(); i++) {
        if (!usedDoorIndices.contains(i)) available.add(i);
    }
    return available;
}

public void markDoorUsed(int idx) {
    if (!usedDoorIndices.contains(idx)) usedDoorIndices.add(idx);
}
}