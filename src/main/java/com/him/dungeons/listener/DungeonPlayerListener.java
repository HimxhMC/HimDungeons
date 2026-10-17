package com.him.dungeons.listener;
import com.him.dungeons.HimDungeons.DungeonInstance;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.generator.ConfigApplier;
import com.him.dungeons.util.SoftDependencyUtil;
import com.him.dungeons.util.ResourceUtil;
import com.him.dungeons.util.YamlDoorUtil;
import com.him.dungeons.util.TransformUtil;
import com.him.dungeons.util.AABBUtil;
import com.him.dungeons.util.RoomInstance;
import com.him.dungeons.util.Pos3;

import java.io.File;

public class DungeonPlayerListener implements Listener {
    
    private final HimDungeons plugin;
    private BukkitTask bossBoundaryTask;
    
    public DungeonPlayerListener(HimDungeons plugin) {
        this.plugin = plugin;
        startBossBoundaryTask();
    }
    
    private void startBossBoundaryTask() {
        bossBoundaryTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (String worldName : plugin.dungeonBosses.keySet()) {
                    HimDungeons.BossData data = plugin.getBossData(worldName);
                    if (data == null || data.bossRoom == null) continue;
                    LivingEntity boss = data.entity;
                    if (boss == null || !boss.isValid() || boss.isDead()) continue;
                    checkBossBoundary(boss, data.bossRoom);
                }
            }
        }.runTaskTimer(plugin, 20L, 2L); // 1 秒后启动，之后每 2 tick 巡检一次
    }
    
    private void checkBossBoundary(LivingEntity entity, RoomInstance bossRoom) {
        AABBUtil.AABB aabb = bossRoom.aabb;
        Location loc = entity.getLocation();
        double margin = 0.5;
        if (loc.getX() < aabb.minX + margin || loc.getX() > aabb.maxX - margin ||
        loc.getY() < aabb.minY          || loc.getY() > aabb.maxY ||
        loc.getZ() < aabb.minZ + margin || loc.getZ() > aabb.maxZ - margin) {
            
            Location center = new Location(entity.getWorld(),
            (aabb.minX + aabb.maxX) / 2,
            (aabb.minY + aabb.maxY) / 2,
            (aabb.minZ + aabb.maxZ) / 2);
            entity.teleport(center);
        }
    }
    
    
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String worldName = plugin.getPlayerDungeonWorld(p.getUniqueId());
        if (worldName == null) return;
        ConfigApplier applier = plugin.getConfigApplier(worldName);
        if (applier == null) return;
        String fullCmd = e.getMessage();
        if (!applier.isCommandAllowed(fullCmd)) {
            e.setCancelled(true);
            p.sendMessage("§c该命令在地牢中被禁止");
        }
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        String worldName = plugin.getPlayerDungeonWorld(p.getUniqueId());
        if (worldName == null) return;
        ConfigApplier applier = plugin.getConfigApplier(worldName);
        if (applier == null) return;
        
        // 1. 检查建筑白名单
        Material mat = e.getBlockPlaced().getType();
        if (!applier.isBuildAllowed(mat)) {
            e.setCancelled(true);
            p.sendMessage("§c该方块在地牢中不可放置");
            return;
        }
        
        // 2. 检查 Slimefun 物品（如果禁用）
        if (!applier.isSlimefunAllowed()) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (SoftDependencyUtil.isSlimefunItem(hand)) {
                e.setCancelled(true);
                p.sendMessage("§c该地牢禁止使用 Slimefun 物品");
            }
        }
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        String worldName = plugin.getPlayerDungeonWorld(p.getUniqueId());
        if (worldName == null) return;
        ConfigApplier applier = plugin.getConfigApplier(worldName);
        if (applier == null) return;
        
        // 1. 检查破坏白名单
        Material mat = e.getBlock().getType();
        if (!applier.isBreakAllowed(mat)) {
            e.setCancelled(true);
            p.sendMessage("§c该方块在地牢中不可破坏");
            return;
        }
        
        // 2. 检查 Slimefun 方块（如果禁用）
        if (!applier.isSlimefunAllowed()) {
            ItemStack blockItem = new ItemStack(e.getBlock().getType());
            if (SoftDependencyUtil.isSlimefunItem(blockItem)) {
                e.setCancelled(true);
                p.sendMessage("§c该地牢禁止破坏 Slimefun 方块");
            }
        }
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        String worldName = plugin.getPlayerDungeonWorld(p.getUniqueId());
        if (worldName == null) return;
        ConfigApplier applier = plugin.getConfigApplier(worldName);
        if (applier == null) return;
        
        ItemStack item = e.getItem();
        if (item == null) return;
        
        // 检查 Slimefun 物品使用（右击）
        if (!applier.isSlimefunAllowed() && SoftDependencyUtil.isSlimefunItem(item)) {
            e.setCancelled(true);
            p.sendMessage("§c该地牢禁止使用 Slimefun 物品");
            return;
        }
        
        // 检查 MythicMobs 物品使用（如果禁用）
        if (!applier.isMythicMobsAllowed() && SoftDependencyUtil.isMythicMobsItem(item)) {
            e.setCancelled(true);
            p.sendMessage("§c该地牢禁止使用 MythicMobs 物品");
        }
    }
    
    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent e) {
        Entity entity = e.getEntity();
        World world = entity.getWorld();
        String worldName = world.getName();
        if (!plugin.isDungeonWorld(worldName)) return;
        ConfigApplier applier = plugin.getConfigApplier(worldName);
        if (applier == null) return;
        
        // 如果 MythicMobs 被禁用，阻止 MythicMobs 生物生成
        if (!applier.isMythicMobsAllowed() && SoftDependencyUtil.isMythicMobsEntity(entity)) {
            e.setCancelled(true);
        }
    }
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        String worldName = plugin.getPlayerDungeonWorld(p.getUniqueId());
        if (worldName == null) return;
        Location to = e.getTo();
        Location from = e.getFrom();
        // 仅在移动到一个新方块时检查（减少开销）
        if (to.getBlockX() == from.getBlockX() && 
        to.getBlockY() == from.getBlockY() && 
        to.getBlockZ() == from.getBlockZ()) return;
        
        DungeonInstance instance = plugin.getDungeonInstanceByWorld(worldName);
        if (instance == null || instance.placedRooms == null) return;
        
        // 获取玩家所在房间
        RoomInstance currentRoom = null;
        for (RoomInstance room : instance.placedRooms) {
            if (AABBUtil.contains(room.aabb, to.getBlockX(), to.getBlockY(), to.getBlockZ())) {
                currentRoom = room;
                break;
            }
        }
        if (currentRoom == null) return;
        
        // 检查是否有 checkpoint 文件
        File checkFile = ResourceUtil.getRoomCheckpointYaml(instance.dungeonName, currentRoom.baseName);
        if (checkFile == null || !checkFile.exists()) return;
        
        Vector cpVec = YamlDoorUtil.parseSpawnPoint(checkFile);
        if (cpVec == null) return;
        
        // 计算世界坐标（考虑房间锚点和旋转）
        Pos3 rel = Pos3.at(cpVec.getX(), cpVec.getY(), cpVec.getZ());
        Pos3 rotated = TransformUtil.rotatePos(rel, currentRoom.rotationTimes);
        Location cpLoc = currentRoom.anchor.clone().add(rotated.getX(), rotated.getY(), rotated.getZ());
        
        // 更新存档点
        plugin.setPlayerCheckpoint(p.getUniqueId(), cpLoc);
    }
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        String worldName = plugin.getPlayerDungeonWorld(p.getUniqueId());
        if (worldName == null) return;
        // 无需特殊操作，但可在死亡时保存存档点（默认已存）
    }
    
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        String worldName = plugin.getPlayerDungeonWorld(p.getUniqueId());
        if (worldName == null) return;

        // 优先存档点（若世界一致）
        Location cp = plugin.getPlayerCheckpoint(p.getUniqueId());
        if (cp != null && cp.getWorld() != null && worldName.equals(cp.getWorld().getName())) {
            e.setRespawnLocation(cp);
            return;
        }

        // 其次地牢出生点
        DungeonInstance instance = plugin.getDungeonInstanceByWorld(worldName);
        if (instance != null && instance.spawnLocation != null) {
            e.setRespawnLocation(instance.spawnLocation);
        }
        // 其余情况保持原版重生位置，不覆盖
    }
}