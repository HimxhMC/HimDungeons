package com.him.dungeons.util;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class RoomInstance {
    public final String roomType;
    public final String baseName; // 新增：自定义基础文件名
    public final Location anchor;
    public final int rotationTimes;
    public final BlockVector3 size;
    public final AABBUtil.AABB aabb;
    public final List<YamlDoorUtil.DoorAnchor> doors;
    public final List<Integer> usedDoorIndices;
    public final boolean isFallback;

    public RoomInstance(String roomType, String baseName, Location anchor, int rotationTimes,
                        BlockVector3 size, AABBUtil.AABB aabb,
                        List<YamlDoorUtil.DoorAnchor> doors) {
        this(roomType, baseName, anchor, rotationTimes, size, aabb, doors, false);
    }

    public RoomInstance(String roomType, String baseName, Location anchor, int rotationTimes,
                        BlockVector3 size, AABBUtil.AABB aabb,
                        List<YamlDoorUtil.DoorAnchor> doors, boolean isFallback) {
        this.roomType = roomType;
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