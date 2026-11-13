package com.him.dungeons.util;
import com.him.dungeons.util.Pos3;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.List;

public final class DoorMatcherUtil {

    private DoorMatcherUtil() {}

    /**
     * 核心配对检测：P1 + D1 = P2 且 P2 + D2 = P1
     */
    public static boolean isMatching(Pos3 p1, String f1,
                                     Pos3 p2, String f2) {
        if (p1 == null || p2 == null || f1 == null || f2 == null) return false;
        Vector d1 = TransformUtil.facingToVector(f1);
        Vector d2 = TransformUtil.facingToVector(f2);
        if (d1.lengthSquared() == 0 || d2.lengthSquared() == 0) return false;

        Pos3 expectedP2 = p1.add(Pos3.at(d1.getX(), d1.getY(), d1.getZ()));
        Pos3 expectedP1 = p2.add(Pos3.at(d2.getX(), d2.getY(), d2.getZ()));
        return expectedP2.equals(p2) && expectedP1.equals(p1);
    }

    /**
     * 将门的相对坐标转为世界坐标（使用旋转）
     */
    public static Pos3 toWorldDoorPosition(YamlDoorUtil.DoorAnchor door,
                                                   Location roomAnchor,
                                                   int rotationTimes) {
        if (door == null || roomAnchor == null) return null;
        Pos3 rotated = TransformUtil.rotatePos(
            Pos3.at(door.x, door.y, door.z), rotationTimes
        );
        return Pos3.at(
            roomAnchor.getX() + rotated.getX(),
            roomAnchor.getY() + rotated.getY(),
            roomAnchor.getZ() + rotated.getZ()
        );
    }
}