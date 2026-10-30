package com.him.dungeons.service;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.generator.ConfigApplier;
import com.him.dungeons.storage.BackupStorage;
import com.him.dungeons.util.ConfigUtil;
import com.him.dungeons.util.LanguageManager;
import com.him.dungeons.util.MessageUtil;
import com.him.dungeons.util.PlayerCooldownManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DungeonSessionService {

    private static final long OFFLINE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final HimDungeons plugin;
    private final BackupStorage backupStorage;
    private final Map<UUID, OfflineRecord> offlineRecords = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> cleanupTasks = new ConcurrentHashMap<>();

    public static class OfflineRecord {
        public final String worldName;
        public final String dungeonName;
        public final long offlineAt;
        public final Location lastLocation;
        public OfflineRecord(String w, String d, long t, Location l) {
            this.worldName = w; this.dungeonName = d; this.offlineAt = t;
            this.lastLocation = l == null ? null : l.clone();
        }
    }

    public DungeonSessionService(HimDungeons plugin, BackupStorage backupStorage) {
        this.plugin = plugin;
        this.backupStorage = backupStorage;
    }

    public BackupStorage getBackupStorage() { return backupStorage; }

    public boolean enter(Player player, HimDungeons.DungeonInstance instance) {
        if (player == null || instance == null) return false;
        UUID uuid = player.getUniqueId();
        String worldName = instance.worldName;

        com.him.dungeons.api.hook.DungeonContext ctx = instance.hookContext;
        if (ctx != null) {
            com.him.dungeons.api.hook.PlayerHookContext phc =
                    new com.him.dungeons.api.hook.PlayerHookContext(ctx, player);
            plugin.getHookRegistry().fireBeforeEnter(phc);
            if (phc.cancelled) return false;
        }

        cancelCleanup(worldName);
        offlineRecords.remove(uuid);

        if (instance.deepCloneActive) {
            backupStorage.save(uuid, instance.dungeonName, worldName, player);
            clearInventory(player);
        }

        plugin.setPlayerDungeonWorld(uuid, worldName);
        plugin.addPlayerToDungeon(worldName, uuid);
        instance.players.add(uuid);

        Location spawn = plugin.getPlayerCheckpoint(uuid);
        if (spawn == null || spawn.getWorld() == null || !worldName.equals(spawn.getWorld().getName())) {
            spawn = instance.spawnLocation;
        }
        if (spawn != null) player.teleport(spawn);

        // 传送无敌
        FileConfiguration cfg = ConfigUtil.getDungeonConfig(instance.dungeonName);
        int invuln = ConfigUtil.getInstanceTeleportInvulnerable(cfg);
        if (invuln > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_DAMAGE,
                    invuln * 20, 5, false, false, false));
            MessageUtil.sendMessage(player, "session.invulnerable", "seconds", invuln);
        }

        // enterTitle
        String enterTitle = ConfigUtil.getInstanceEnterTitle(cfg);
        if (enterTitle != null && !enterTitle.isEmpty()) {
            String text = ChatColor.translateAlternateColorCodes('&',
                    enterTitle.replace("%dungeon%", instance.dungeonName));
            player.sendTitle(text, "", 10, 40, 10);
        }

        // 记录开始时间（首次进入）
        if (instance.startTime == 0) instance.startTime = System.currentTimeMillis();
        if (plugin.getRoomFogManager() != null) {
            plugin.getRoomFogManager().applyFor(player, instance);
        }
        if (ctx != null) {
            plugin.getHookRegistry().fireAfterEnter(
                    new com.him.dungeons.api.hook.PlayerHookContext(ctx, player));
        }
        return true;
    }

    public void leave(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        String worldName = plugin.getPlayerDungeonWorld(uuid);
        if (worldName == null) { MessageUtil.sendError(player, "session.not-in-dungeon"); return; }
        HimDungeons.DungeonInstance instance = plugin.getDungeonInstanceByWorld(worldName);
        if (instance == null) { plugin.setPlayerDungeonWorld(uuid, null); return; }

        com.him.dungeons.api.hook.DungeonContext ctx = instance.hookContext;
        if (ctx != null) {
            com.him.dungeons.api.hook.PlayerHookContext phc =
                    new com.him.dungeons.api.hook.PlayerHookContext(ctx, player);
            plugin.getHookRegistry().fireBeforeLeave(phc);
            if (phc.cancelled) { MessageUtil.sendMessage(player, "session.leave-denied"); return; }
        }

        restoreInventory(player, instance);
        teleportLobby(player, instance);
        cleanupMarks(uuid, worldName, instance);
        if (plugin.getRoomFogManager() != null) {
            plugin.getRoomFogManager().restore(player);
        }
        FileConfiguration cfg = ConfigUtil.getDungeonConfig(instance.dungeonName);
        int cooldown = ConfigUtil.getInstancePlayerCooldown(cfg);
        PlayerCooldownManager.set(uuid, instance.dungeonName, cooldown);

        if (instance.players.isEmpty() && !instance.isFinished) startCleanup(worldName);
        MessageUtil.sendMessage(player, "session.left");

        if (ctx != null) {
            plugin.getHookRegistry().fireAfterLeave(
                    new com.him.dungeons.api.hook.PlayerHookContext(ctx, player));
        }
    }

    public void finish(Player player, HimDungeons.DungeonInstance instance, ConfigApplier applier) {
        if (player == null || instance == null) return;
        UUID uuid = player.getUniqueId();
        restoreInventory(player, instance);
        applier.grantRewards(player, instance);
        teleportLobby(player, instance);
        cleanupMarks(uuid, instance.worldName, instance);
        if (plugin.getRoomFogManager() != null) {
            plugin.getRoomFogManager().restore(player);
        }
        FileConfiguration cfg = ConfigUtil.getDungeonConfig(instance.dungeonName);
        int cooldown = ConfigUtil.getInstancePlayerCooldown(cfg);
        PlayerCooldownManager.set(uuid, instance.dungeonName, cooldown);

        com.him.dungeons.api.hook.DungeonContext ctx = instance.hookContext;
        if (ctx != null) {
            plugin.getHookRegistry().fireFinish(
                    new com.him.dungeons.api.hook.PlayerHookContext(ctx, player));
        }
    }

    public void onPlayerQuit(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        String worldName = plugin.getPlayerDungeonWorld(uuid);
        if (worldName == null) return;
        HimDungeons.DungeonInstance instance = plugin.getDungeonInstanceByWorld(worldName);
        if (instance == null) return;

        restoreInventory(player, instance);
        offlineRecords.put(uuid, new OfflineRecord(worldName, instance.dungeonName,
                System.currentTimeMillis(), player.getLocation()));
        if (!instance.isFinished && !hasAnyOnline(instance)) startCleanup(worldName);

        com.him.dungeons.api.hook.DungeonContext ctx = instance.hookContext;
        if (ctx != null) {
            plugin.getHookRegistry().fireOffline(
                    new com.him.dungeons.api.hook.PlayerHookContext(ctx, player));
        }
    }

    public void onPlayerJoin(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        OfflineRecord record = offlineRecords.remove(uuid);
        if (record != null) {
            long elapsed = System.currentTimeMillis() - record.offlineAt;
            if (elapsed < OFFLINE_TIMEOUT_MS) {
                HimDungeons.DungeonInstance inst = plugin.getDungeonInstanceByWorld(record.worldName);
                World world = Bukkit.getWorld(record.worldName);
                if (inst != null && world != null) {
                    cancelCleanup(record.worldName);
                    if (enter(player, inst)) {
                        com.him.dungeons.api.hook.DungeonContext ctx = inst.hookContext;
                        if (ctx != null) {
                            plugin.getHookRegistry().fireOnline(
                                    new com.him.dungeons.api.hook.PlayerHookContext(ctx, player));
                        }
                        return;
                    }
                }
            }
            plugin.setPlayerDungeonWorld(uuid, null);
            plugin.clearPlayerCheckpoint(uuid);
        }

        BackupStorage.BackupRecord pending = backupStorage.findForPlayer(uuid);
        if (pending != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (backupStorage.restore(uuid, pending.dungeonName, player)) {
                    player.updateInventory();
                    plugin.getLogger().info(LanguageManager.get("log.session.backup-restored",
                            "player", player.getName(), "dungeon", pending.dungeonName));
                }
            }, 1L);
        }
    }

    public void restorePendingOnEnable() {
        int marked = 0;
        for (BackupStorage.BackupRecord rec : backupStorage.findAll()) {
            if (rec.status == BackupStorage.Status.ACTIVE) {
                backupStorage.updateStatus(rec.uuid, rec.dungeonName, BackupStorage.Status.PENDING_RESTORE);
                marked++;
            }
        }
        if (marked > 0) {
            plugin.getLogger().info(LanguageManager.get("log.session.scan-marked", "count", marked));
        }
        for (BackupStorage.BackupRecord rec : backupStorage.findAll()) {
            Player p = Bukkit.getPlayer(rec.uuid);
            if (p != null && p.isOnline()) {
                if (backupStorage.restore(rec.uuid, rec.dungeonName, p)) p.updateInventory();
            }
        }
    }

    public void clearOfflineRecordsForWorld(String w) {
        offlineRecords.entrySet().removeIf(e -> e.getValue().worldName.equals(w));
    }
    public boolean isWaitingForReconnect(UUID u) { return offlineRecords.containsKey(u); }

    private boolean hasAnyOnline(HimDungeons.DungeonInstance inst) {
        for (UUID u : inst.players) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) return true;
        }
        return false;
    }

    private void restoreInventory(Player p, HimDungeons.DungeonInstance inst) {
        if (!inst.deepCloneActive) return;
        backupStorage.restore(p.getUniqueId(), inst.dungeonName, p);
        p.updateInventory();
    }

    private void clearInventory(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        p.getInventory().setExtraContents(new ItemStack[0]);
        p.updateInventory();
    }

    private void teleportLobby(Player p, HimDungeons.DungeonInstance inst) {
        ConfigApplier a = plugin.getConfigApplier(inst.worldName);
        Location lobby = a == null ? null : a.getLobbyLocation(Bukkit.getWorlds().get(0));
        if (lobby == null) lobby = Bukkit.getWorlds().get(0).getSpawnLocation();
        p.teleport(lobby);
    }

    private void cleanupMarks(UUID u, String w, HimDungeons.DungeonInstance inst) {
        plugin.setPlayerDungeonWorld(u, null);
        plugin.clearPlayerCheckpoint(u);
        inst.players.remove(u);
        Set<UUID> dp = plugin.getDungeonPlayers(w);
        if (dp != null) dp.remove(u);
    }

    public void startCleanup(String worldName) {
        cancelCleanup(worldName);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            cleanupTasks.remove(worldName);
            HimDungeons.DungeonInstance inst = plugin.getDungeonInstanceByWorld(worldName);
            if (inst == null) return;
            if (hasAnyOnline(inst)) return;
            plugin.forceFinishDungeon(inst);
        }, 20L * 60 * 5);
        cleanupTasks.put(worldName, task);
    }

    public void cancelCleanup(String worldName) {
        BukkitTask t = cleanupTasks.remove(worldName);
        if (t != null) t.cancel();
    }
}