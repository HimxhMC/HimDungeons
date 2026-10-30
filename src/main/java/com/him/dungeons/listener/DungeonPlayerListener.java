package com.him.dungeons.listener;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.HimDungeons.DungeonInstance;
import com.him.dungeons.generator.ConfigApplier;
import com.him.dungeons.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;

public class DungeonPlayerListener implements Listener {

    private final HimDungeons plugin;
    private final Map<UUID, Integer> deathCooldownTask = new HashMap<>();
    private BukkitTask bossBoundaryTask;
    private BukkitTask actionBarTask;
    private BukkitTask pickupAndGlowTask;

    public DungeonPlayerListener(HimDungeons plugin) {
        this.plugin = plugin;
        startBossBoundaryTask();
        startActionBarTask();
        startPickupAndGlowTask();
    }

    // ==================== 定时任务 ====================

    private void startBossBoundaryTask() {
        bossBoundaryTask = new BukkitRunnable() {
            @Override public void run() {
                for (String worldName : plugin.dungeonBosses.keySet()) {
                    HimDungeons.BossData data = plugin.getBossData(worldName);
                    if (data == null || data.bossRoom == null) continue;
                    LivingEntity boss = data.entity;
                    if (boss == null || !boss.isValid() || boss.isDead()) continue;
                    checkBossBoundary(boss, data.bossRoom);
                }
            }
        }.runTaskTimer(plugin, 20L, 2L);
    }

    private void startActionBarTask() {
        actionBarTask = new BukkitRunnable() {
            @Override public void run() {
                for (DungeonInstance inst : plugin.dungeonInstances.values()) {
                    FileConfiguration cfg = ConfigUtil.getDungeonConfig(inst.dungeonName);
                    boolean showProgress = ConfigUtil.isInstanceRoomProgress(cfg);
                    boolean showBossDir = ConfigUtil.isInstanceBossDirection(cfg);
                    if (!showProgress && !showBossDir) continue;
                    if (inst.placedRooms == null || inst.placedRooms.isEmpty()) continue;

                    for (UUID uuid : inst.players) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p == null || !p.isOnline()) continue;
                        String msg = null;
                        if (showBossDir) {
                            Location bossLoc = findBossLocation(inst);
                            if (bossLoc != null) {
                                String dir = getDirection(p.getLocation(), bossLoc);
                                int dist = (int) p.getLocation().distance(bossLoc);
                                msg = LanguageManager.get(p, "boss.direction",
                                        "direction", dir, "distance", dist);
                            }
                        }
                        if (msg == null && showProgress) {
                            int inRoom = -1;
                            for (int i = 0; i < inst.placedRooms.size(); i++) {
                                RoomInstance r = inst.placedRooms.get(i);
                                if (AABBUtil.contains(r.aabb,
                                        p.getLocation().getBlockX(),
                                        p.getLocation().getBlockY(),
                                        p.getLocation().getBlockZ())) {
                                    inRoom = i; break;
                                }
                            }
                            int total = inst.placedRooms.size();
                            msg = "§7[" + (inRoom >= 0 ? inRoom + 1 : "?") + "/" + total + "]";
                        }
                        if (msg != null) {
                            p.spigot().sendMessage(
                                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(msg));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startPickupAndGlowTask() {
        pickupAndGlowTask = new BukkitRunnable() {
            @Override public void run() {
                for (DungeonInstance inst : plugin.dungeonInstances.values()) {
                    FileConfiguration cfg = ConfigUtil.getDungeonConfig(inst.dungeonName);
                    boolean doPickup = ConfigUtil.isInstanceAutoPickup(cfg);
                    boolean doGlow = ConfigUtil.isInstancePartyGlow(cfg);
                    if (!doPickup && !doGlow) continue;

                    for (UUID uuid : inst.players) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p == null || !p.isOnline()) continue;

                        if (doPickup) {
                            for (Entity near : p.getNearbyEntities(2.5, 2.5, 2.5)) {
                                if (near instanceof Item item && item.getTicksLived() > 5) {
                                    p.getInventory().addItem(item.getItemStack());
                                    item.remove();
                                }
                            }
                        }
                        if (doGlow) {
                            for (UUID mate : inst.players) {
                                if (mate.equals(uuid)) continue;
                                Player m = Bukkit.getPlayer(mate);
                                if (m != null && m.isOnline()
                                        && !m.hasPotionEffect(PotionEffectType.GLOWING)) {
                                    m.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                                            Integer.MAX_VALUE, 0, false, false, false));
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    private Location findBossLocation(DungeonInstance inst) {
        HimDungeons.BossData data = plugin.getBossData(inst.worldName);
        if (data == null || data.entity == null || !data.entity.isValid()) return null;
        return data.entity.getLocation();
    }

    private String getDirection(Location from, Location to) {
        Vector d = to.toVector().subtract(from.toVector()).normalize();
        double angle = Math.atan2(-d.getX(), d.getZ());
        double degrees = Math.toDegrees(angle);
        if (degrees < 0) degrees += 360;
        String[] dirs = {"N", "NW", "W", "SW", "S", "SE", "E", "NE"};
        return dirs[(int) Math.round(degrees / 45.0) % 8];
    }

    private void checkBossBoundary(LivingEntity entity, RoomInstance bossRoom) {
        AABBUtil.AABB aabb = bossRoom.aabb;
        Location loc = entity.getLocation();
        double margin = 0.5;
        if (loc.getX() < aabb.minX + margin || loc.getX() > aabb.maxX - margin ||
                loc.getY() < aabb.minY || loc.getY() > aabb.maxY ||
                loc.getZ() < aabb.minZ + margin || loc.getZ() > aabb.maxZ - margin) {
            entity.teleport(new Location(entity.getWorld(),
                    (aabb.minX + aabb.maxX) / 2,
                    (aabb.minY + aabb.maxY) / 2,
                    (aabb.minZ + aabb.maxZ) / 2));
        }
    }

    // ==================== 命令 / 方块 / 交互 ====================

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String worldName = plugin.getPlayerDungeonWorld(p.getUniqueId());
        if (worldName == null) return;
        ConfigApplier a = plugin.getConfigApplier(worldName);
        if (a == null) return;
        if (!a.isCommandAllowed(e.getMessage())) {
            e.setCancelled(true);
            MessageUtil.sendError(p, "listener.command-denied");
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        String worldName = plugin.getPlayerDungeonWorld(p.getUniqueId());
        if (worldName == null) return;
        ConfigApplier a = plugin.getConfigApplier(worldName);
        if (a == null) return;
        if (!a.isBuildAllowed(e.getBlockPlaced().getType())) {
            e.setCancelled(true);
            MessageUtil.sendError(p, "listener.place-denied");
            return;
        }
        if (!a.isSlimefunAllowed() && SoftDependencyUtil.isSlimefunItem(p.getInventory().getItemInMainHand())) {
            e.setCancelled(true);
            MessageUtil.sendError(p, "listener.slimefun-blocked");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        String worldName = plugin.getPlayerDungeonWorld(p.getUniqueId());
        if (worldName == null) return;
        ConfigApplier a = plugin.getConfigApplier(worldName);
        if (a == null) return;
        if (!a.isBreakAllowed(e.getBlock().getType())) {
            e.setCancelled(true);
            MessageUtil.sendError(p, "listener.break-denied");
            return;
        }
        if (!a.isSlimefunAllowed() && SoftDependencyUtil.isSlimefunItem(new ItemStack(e.getBlock().getType()))) {
            e.setCancelled(true);
            MessageUtil.sendError(p, "listener.slimefun-break-blocked");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        String worldName = plugin.getPlayerDungeonWorld(p.getUniqueId());
        if (worldName == null) return;
        ConfigApplier a = plugin.getConfigApplier(worldName);
        if (a == null) return;

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            Material m = e.getClickedBlock().getType();
            if (isInteractive(m) && !a.isInteractionAllowed(m)) {
                e.setCancelled(true);
                MessageUtil.sendError(p, "listener.interact-denied");
                return;
            }
        }
        ItemStack item = e.getItem();
        if (item == null) return;
        if (!a.isSlimefunAllowed() && SoftDependencyUtil.isSlimefunItem(item)) {
            e.setCancelled(true);
            MessageUtil.sendError(p, "listener.slimefun-blocked");
            return;
        }
        if (!a.isMythicMobsAllowed() && SoftDependencyUtil.isMythicMobsItem(item)) {
            e.setCancelled(true);
            MessageUtil.sendError(p, "listener.mythicmobs-blocked");
        }
    }

    private boolean isInteractive(Material mat) {
        if (mat == null) return false;
        String n = mat.name();
        return n.contains("CHEST") || n.contains("BARREL") || n.contains("SHULKER_BOX")
                || n.contains("FURNACE") || n.contains("SMOKER") || n.contains("BLAST_FURNACE")
                || n.contains("HOPPER") || n.contains("DROPPER") || n.contains("DISPENSER")
                || n.contains("CRAFTING_TABLE") || n.contains("ANVIL") || n.contains("GRINDSTONE")
                || n.contains("ENCHANTING_TABLE") || n.contains("BREWING_STAND") || n.contains("CAULDRON")
                || n.contains("BEACON") || n.contains("LECTERN") || n.contains("CARTOGRAPHY_TABLE")
                || n.contains("SMITHING_TABLE") || n.contains("STONECUTTER") || n.contains("LOOM")
                || n.contains("BUTTON") || n.contains("LEVER") || n.contains("PRESSURE_PLATE")
                || n.contains("DOOR") || n.contains("TRAPDOOR") || n.contains("FENCE_GATE")
                || n.endsWith("_BED") || n.contains("JUKEBOX") || n.contains("NOTE_BLOCK")
                || n.contains("REPEATER") || n.contains("COMPARATOR") || n.contains("DAYLIGHT_DETECTOR")
                || n.contains("RESPAWN_ANCHOR") || n.contains("CONDUIT") || n.contains("BELL")
                || n.contains("CAMPFIRE") || n.contains("FLOWER_POT") || n.endsWith("_CAKE")
                || n.contains("COMMAND_BLOCK");
    }

    // ==================== 实体生成 / 伤害 ====================

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent e) {
        Entity entity = e.getEntity();
        String worldName = entity.getWorld().getName();
        if (!plugin.isDungeonWorld(worldName)) return;
        ConfigApplier a = plugin.getConfigApplier(worldName);
        if (a == null) return;

        // MythicMobs 拦截
        if (!a.isMythicMobsAllowed() && SoftDependencyUtil.isMythicMobsEntity(entity)) {
            e.setCancelled(true);
            return;
        }

        // 怪物生成率 / 单房间上限
        if (!(entity instanceof Monster) && !(entity instanceof Slime)) return;
        DungeonInstance inst = plugin.getDungeonInstanceByWorld(worldName);
        if (inst == null) return;
        FileConfiguration cfg = ConfigUtil.getDungeonConfig(inst.dungeonName);
        double rate = ConfigUtil.getInstanceMobSpawnRate(cfg);
        if (rate < 1.0 && Math.random() > rate) {
            e.setCancelled(true);
            return;
        }
        int max = ConfigUtil.getInstanceMaxMobsPerRoom(cfg);
        Location loc = entity.getLocation();
        int count = 0;
        for (Entity other : entity.getWorld().getNearbyEntities(loc, 15, 10, 15)) {
            if (other instanceof Monster || other instanceof Slime) count++;
        }
        if (count > max) e.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker == null || attacker.equals(victim)) return;

        String worldName = plugin.getPlayerDungeonWorld(victim.getUniqueId());
        if (worldName == null || !worldName.equals(plugin.getPlayerDungeonWorld(attacker.getUniqueId()))) return;
        DungeonInstance inst = plugin.getDungeonInstanceByWorld(worldName);
        if (inst == null) return;
        FileConfiguration cfg = ConfigUtil.getDungeonConfig(inst.dungeonName);
        if (!ConfigUtil.isInstanceFriendlyFire(cfg)) {
            e.setCancelled(true);
        }
    }

    // ==================== 移动（存档点更新） ====================

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        String worldName = plugin.getPlayerDungeonWorld(p.getUniqueId());
        if (worldName == null) return;
        Location to = e.getTo(), from = e.getFrom();
        if (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ()) return;

        DungeonInstance inst = plugin.getDungeonInstanceByWorld(worldName);
        if (inst == null || inst.placedRooms == null) return;

        RoomInstance current = null;
        for (RoomInstance r : inst.placedRooms) {
            if (AABBUtil.contains(r.aabb, to.getBlockX(), to.getBlockY(), to.getBlockZ())) { current = r; break; }
        }
        if (current == null) return;
        File cp = ResourceUtil.getRoomCheckpointYaml(inst.dungeonName, current.baseName);
        if (cp == null || !cp.exists()) return;
        Vector v = YamlDoorUtil.parseSpawnPoint(cp);
        if (v == null) return;
        Pos3 rel = Pos3.at(v.getX(), v.getY(), v.getZ());
        Pos3 rot = TransformUtil.rotatePos(rel, current.rotationTimes);
        plugin.setPlayerCheckpoint(p.getUniqueId(), current.anchor.clone()
                .add(rot.getX(), rot.getY(), rot.getZ()));
    }

    // ==================== 死亡 / 重生 ====================

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        String worldName = plugin.getPlayerDungeonWorld(p.getUniqueId());
        if (worldName == null) return;
        DungeonInstance inst = plugin.getDungeonInstanceByWorld(worldName);
        if (inst == null) return;
        FileConfiguration cfg = ConfigUtil.getDungeonConfig(inst.dungeonName);
        if (cfg == null) return;

        String mode = ConfigUtil.getInstanceRespawnMode(cfg);
        int cooldown = ConfigUtil.getInstanceDeathCooldown(cfg);

        if ("spectator".equals(mode)) {
            if (ConfigUtil.isInstanceAllowSpectatorAfterDeath(cfg)) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline() && p.isDead()) p.spigot().respawn();
                    if (p.isOnline()) p.setGameMode(GameMode.SPECTATOR);
                    MessageUtil.sendMessage(p, "session.respawn-spectator");
                }, cooldown * 20L);
            } else {
                // 等同 lobby
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline() && p.isDead()) p.spigot().respawn();
                }, cooldown * 20L);
            }
            return;
        }

        // checkpoint / lobby：延迟自动复活（PlayerRespawnEvent 决定位置）
        if (cooldown > 0) {
            MessageUtil.sendMessage(p, "session.death-cooldown", "seconds", cooldown);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && p.isDead()) p.spigot().respawn();
        }, cooldown * 20L);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        String worldName = plugin.getPlayerDungeonWorld(p.getUniqueId());
        if (worldName == null) return;
        DungeonInstance inst = plugin.getDungeonInstanceByWorld(worldName);
        if (inst == null) return;
        FileConfiguration cfg = ConfigUtil.getDungeonConfig(inst.dungeonName);
        if (cfg == null) return;
        String mode = ConfigUtil.getInstanceRespawnMode(cfg);

        if ("checkpoint".equals(mode)) {
            Location cp = plugin.getPlayerCheckpoint(p.getUniqueId());
            if (cp != null && cp.getWorld() != null && worldName.equals(cp.getWorld().getName())) {
                e.setRespawnLocation(cp);
                return;
            }
            if (inst.spawnLocation != null) e.setRespawnLocation(inst.spawnLocation);
            return;
        }

        if ("lobby".equals(mode)) {
            e.setRespawnLocation(Bukkit.getWorlds().get(0).getSpawnLocation());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && p.getWorld().getName().equals(worldName)) {
                    plugin.getSessionService().leave(p);
                }
            });
            return;
        }

        // spectator：保留在本地牢世界
        if ("spectator".equals(mode) && ConfigUtil.isInstanceAllowSpectatorAfterDeath(cfg)) {
            e.setRespawnLocation(p.getLocation());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) p.setGameMode(GameMode.SPECTATOR);
            });
        }
    }

    // ==================== 爆炸 ====================

    @EventHandler
    public void onExplosionPrime(ExplosionPrimeEvent e) {
        String worldName = e.getEntity().getWorld().getName();
        if (!plugin.isDungeonWorld(worldName)) return;
        ConfigApplier a = plugin.getConfigApplier(worldName);
        if (a == null) return;
        if (modeOf(a, e.getEntity().getType()) == ConfigUtil.ExplosionMode.DENY) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        String worldName = e.getEntity().getWorld().getName();
        if (!plugin.isDungeonWorld(worldName)) return;
        ConfigApplier a = plugin.getConfigApplier(worldName);
        if (a == null) return;
        switch (modeOf(a, e.getEntity().getType())) {
            case ALLOW -> { }
            case DAMAGE_ONLY -> e.blockList().clear();
            case DENY -> e.setCancelled(true);
        }
    }

    private ConfigUtil.ExplosionMode modeOf(ConfigApplier a, EntityType type) {
        boolean isTnt = type == EntityType.TNT || type == EntityType.TNT_MINECART;
        return isTnt ? a.getTntExplosionMode() : a.getEntityExplosionMode();
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        String worldName = e.getBlock().getWorld().getName();
        if (!plugin.isDungeonWorld(worldName)) return;
        ConfigApplier a = plugin.getConfigApplier(worldName);
        if (a == null) return;
        switch (a.getBlockExplosionMode()) {
            case ALLOW -> { }
            case DAMAGE_ONLY -> e.blockList().clear();
            case DENY -> e.setCancelled(true);
        }
    }
}