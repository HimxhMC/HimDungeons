package com.him.dungeons.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

public final class SoftDependencyUtil {

    private SoftDependencyUtil() {}

    // ==================== Slimefun ====================
    public static boolean isSlimefunItem(ItemStack item) {
        if (item == null || Bukkit.getPluginManager().getPlugin("Slimefun") == null) {
            return false;
        }
        try {
            Class<?> slimefunItemClass = Class.forName(
                    "io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem");
            Object sfItem = slimefunItemClass.getMethod("getByItem", ItemStack.class)
                    .invoke(null, item);
            return sfItem != null;
        } catch (ClassNotFoundException e) {
            DebugUtil.debug(null, "Slimefun 类未找到，Slimefun 集成降级为 false");
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== MythicMobs ====================

    private static Boolean mythicV5 = null;

    private static boolean isMythicV5() {
        if (mythicV5 != null) return mythicV5;
        try {
            Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            mythicV5 = true;
        } catch (ClassNotFoundException e) {
            mythicV5 = false;
        }
        return mythicV5;
    }

    /** 判断物品是否由 MythicMobs 管理 */
    public static boolean isMythicMobsItem(ItemStack item) {
        if (item == null || Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            return false;
        }
        try {
            if (isMythicV5()) {
                Object bukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit")
                        .getMethod("inst").invoke(null);
                Object itemManager = bukkit.getClass().getMethod("getItemManager").invoke(bukkit);
                Object result = itemManager.getClass()
                        .getMethod("getMythicItem", ItemStack.class)
                        .invoke(itemManager, item);
                return result != null;
            } else {
                Object mythic = Class.forName("io.lumine.xikage.mythicmobs.MythicMobs")
                        .getMethod("inst").invoke(null);
                Object helper = mythic.getClass().getMethod("getAPIHelper").invoke(mythic);
                Object result = helper.getClass()
                        .getMethod("isMythicItem", ItemStack.class)
                        .invoke(helper, item);
                return Boolean.TRUE.equals(result);
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** 判断实体是否为 MythicMobs 生物 */
    public static boolean isMythicMobsEntity(Entity entity) {
        if (entity == null || Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            return false;
        }
        try {
            if (isMythicV5()) {
                Object bukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit")
                        .getMethod("inst").invoke(null);
                Object mobManager = bukkit.getClass().getMethod("getMobManager").invoke(bukkit);
                Object result = mobManager.getClass()
                        .getMethod("isMythicMob", Entity.class)
                        .invoke(mobManager, entity);
                return Boolean.TRUE.equals(result);
            } else {
                Object mythic = Class.forName("io.lumine.xikage.mythicmobs.MythicMobs")
                        .getMethod("inst").invoke(null);
                Object helper = mythic.getClass().getMethod("getAPIHelper").invoke(mythic);
                Object result = helper.getClass()
                        .getMethod("isMythicMob", Entity.class)
                        .invoke(helper, entity);
                return Boolean.TRUE.equals(result);
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 生成 MythicMobs 生物。
     * v5 返回 ActiveMob.getEntity().getBukkitEntity()；
     * v4 直接返回 Bukkit Entity。
     */
    public static LivingEntity spawnMythicMob(String mobName, Location location, int level) {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) return null;
        try {
            if (isMythicV5()) {
                Object bukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit")
                        .getMethod("inst").invoke(null);
                Object mobManager = bukkit.getClass().getMethod("getMobManager").invoke(bukkit);
                Object optional = mobManager.getClass()
                        .getMethod("getMythicMob", String.class)
                        .invoke(mobManager, mobName);
                Object mythicMob = optional.getClass()
                        .getMethod("orElse", Object.class)
                        .invoke(optional, null);
                if (mythicMob == null) return null;

                Object activeMob = mythicMob.getClass()
                        .getMethod("spawn", Location.class, int.class)
                        .invoke(mythicMob, location, level);
                Object abstractEntity = activeMob.getClass().getMethod("getEntity").invoke(activeMob);
                Object bukkitEntity = abstractEntity.getClass().getMethod("getBukkitEntity")
                        .invoke(abstractEntity);
                return bukkitEntity instanceof LivingEntity ? (LivingEntity) bukkitEntity : null;
            } else {
                Object mythic = Class.forName("io.lumine.xikage.mythicmobs.MythicMobs")
                        .getMethod("inst").invoke(null);
                Object helper = mythic.getClass().getMethod("getAPIHelper").invoke(mythic);

                // v4 常见签名：spawnMythicMob(String, Location, int)
                try {
                    Object entity = helper.getClass()
                            .getMethod("spawnMythicMob", String.class, Location.class, int.class)
                            .invoke(helper, mobName, location, level);
                    return entity instanceof LivingEntity ? (LivingEntity) entity : null;
                } catch (NoSuchMethodException e) {
                    // 降级：spawnMythicMob(String, Location)
                    Object entity = helper.getClass()
                            .getMethod("spawnMythicMob", String.class, Location.class)
                            .invoke(helper, mobName, location);
                    return entity instanceof LivingEntity ? (LivingEntity) entity : null;
                }
            }
        } catch (Exception e) {
            DebugUtil.debug(null, "MythicMobs 生成失败: " + e.getMessage());
            return null;
        }
    }
}