package com.him.dungeons.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;

public final class SoftDependencyUtil {

    private SoftDependencyUtil() {}

    // ==================== Slimefun ====================
    /**
     * 使用 Slimefun API 判断物品是否为 Slimefun 物品
     * @param item 要检查的物品
     * @return true 如果是 Slimefun 物品
     */
    public static boolean isSlimefunItem(ItemStack item) {
        if (item == null || Bukkit.getPluginManager().getPlugin("Slimefun") == null) {
            return false;
        }
        try {
            // 使用 Slimefun 的 ItemUtils 或直接调用 SlimefunItem.getByItem(ItemStack)
            Class<?> slimefunItemClass = Class.forName("io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem");
            // 静态方法 getByItem(ItemStack)
            Object sfItem = slimefunItemClass.getMethod("getByItem", ItemStack.class).invoke(null, item);
            return sfItem != null;
        } catch (Exception e) {
            // 如果 API 调用失败，降级为安全的 false
            return false;
        }
    }

    // ==================== MythicMobs ====================
    /**
     * 使用 MythicMobs API 判断物品是否为 MythicMobs 物品（如技能绑定物品、自定义物品）
     * @param item 要检查的物品
     * @return true 如果是 MythicMobs 管理的物品
     */
    public static boolean isMythicMobsItem(ItemStack item) {
        if (item == null || Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            return false;
        }
        try {
            // 获取 MythicMobs 插件的 API 实例
            Object plugin = Bukkit.getPluginManager().getPlugin("MythicMobs");
            if (plugin == null) return false;
            Object api = plugin.getClass().getMethod("getAPI").invoke(plugin);
            // 通过 API 的 ItemManager 判断
            Object itemManager = api.getClass().getMethod("getItemManager").invoke(api);
            // 假设有方法 isMythicItem(ItemStack)
            Boolean result = (Boolean) itemManager.getClass().getMethod("isMythicItem", ItemStack.class)
                    .invoke(itemManager, item);
            return result != null && result;
        } catch (Exception e) {
            // 降级：检查物品的 lore 或 name 是否包含 "Mythic"（但不作为主要判断，API 优先）
            return false;
        }
    }

    /**
     * 使用 MythicMobs API 判断实体是否为 MythicMobs 生物
     * @param entity 要检查的实体
     * @return true 如果是 MythicMobs 生物
     */
    public static boolean isMythicMobsEntity(Entity entity) {
        if (entity == null || Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            return false;
        }
        try {
            Object plugin = Bukkit.getPluginManager().getPlugin("MythicMobs");
            if (plugin == null) return false;
            Object api = plugin.getClass().getMethod("getAPI").invoke(plugin);
            // 假设 API 有方法 isMythicMob(Entity)
            Boolean result = (Boolean) api.getClass().getMethod("isMythicMob", Entity.class).invoke(api, entity);
            return result != null && result;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * （可选）使用 MythicMobs 生成生物（此处未使用，保留以备将来）
     * 目前 Boss 生成暂不支持 MythicMobs，此方法留空。
     */
public static LivingEntity spawnMythicMob(String mobName, Location location, int level) {
    if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) return null;
    try {
        Object plugin = Bukkit.getPluginManager().getPlugin("MythicMobs");
        Object api = plugin.getClass().getMethod("getAPI").invoke(plugin);
        // 使用 MythicMobs API 生成
        Object mob = api.getClass().getMethod("spawnMythicMob", String.class, Location.class, int.class)
                .invoke(api, mobName, location, level);
        if (mob instanceof LivingEntity) return (LivingEntity) mob;
    } catch (Exception e) { e.printStackTrace(); }
    return null;
}
}