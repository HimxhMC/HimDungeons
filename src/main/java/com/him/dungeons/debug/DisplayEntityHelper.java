package com.him.dungeons.debug;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.joml.Matrix4f;

public final class DisplayEntityHelper {

    private DisplayEntityHelper() {}

    /**
     * 生成拉伸方块。
     * 将实体位置直接定在方块左下角（min角），仅应用缩放，彻底消除大规模缩放时的累计误差。
     */
    public static BlockDisplay spawnBlock(World world,
                                          double minX, double minY, double minZ,
                                          float sx, float sy, float sz,
                                          Material mat, boolean glowing) {
        // 1. 实体坐标直接放在目标区域的最小角
        Location loc = new Location(world, minX, minY, minZ);

        return world.spawn(loc, BlockDisplay.class, d -> {
            d.setBlock(mat.createBlockData());

            // 2. 仅设置缩放矩阵（无平移，轴心直接对准方块的左下角）
            Matrix4f matrix = new Matrix4f().scale(sx, sy, sz);
            d.setTransformationMatrix(matrix);

            // 3. 基础渲染参数
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.setSilent(true);
            d.setGravity(false);
            d.setGlowing(glowing);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(2.0f);
            d.setBillboard(Display.Billboard.FIXED);
        });
    }

    public static TextDisplay spawnText(World world,
                                        double x, double y, double z,
                                        String text) {
        Location loc = new Location(world, x, y, z);
        return world.spawn(loc, TextDisplay.class, d -> {
            d.setText(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.setSilent(true);
            d.setGravity(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(2.0f);
            d.setBackgroundColor(Color.fromARGB(160, 0, 0, 0));
            d.setSeeThrough(true);
        });
    }
}