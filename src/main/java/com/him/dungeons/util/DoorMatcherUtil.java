package com.him.dungeons.util;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.List;

public final class DoorMatcherUtil {

    private DoorMatcherUtil() {}

    /**
     * 核心配对检测：P1 + D1 = P2 且 P2 + D2 = P1
     */
    public static boolean isMatching(BlockVector3 p1, String f1,
                                     BlockVector3 p2, String f2) {
        if (p1 == null || p2 == null || f1 == null || f2 == null) return false;
        Vector d1 = TransformUtil.facingToVector(f1);
        Vector d2 = TransformUtil.facingToVector(f2);
        if (d1.lengthSquared() == 0 || d2.lengthSquared() == 0) return false;

        BlockVector3 expectedP2 = p1.add(BlockVector3.at(d1.getX(), d1.getY(), d1.getZ()));
        BlockVector3 expectedP1 = p2.add(BlockVector3.at(d2.getX(), d2.getY(), d2.getZ()));
        return expectedP2.equals(p2) && expectedP1.equals(p1);
    }

    /**
     * 将门的相对坐标转为世界坐标（使用旋转）
     */
    public static BlockVector3 toWorldDoorPosition(YamlDoorUtil.DoorAnchor door,
                                                   Location roomAnchor,
                                                   int rotationTimes) {
        if (door == null || roomAnchor == null) return null;
        BlockVector3 rotated = TransformUtil.rotateBlockVector(
            BlockVector3.at(door.x, door.y, door.z), rotationTimes
        );
        return BlockVector3.at(
            roomAnchor.getX() + rotated.getX(),
            roomAnchor.getY() + rotated.getY(),
            roomAnchor.getZ() + rotated.getZ()
        );
    }

    // 其他方法（findAvailableMatchingDoor等）可省略，因为我们直接在生成器中实现配对
}