package com.him.dungeons.gui;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.storage.DungeonSummary;
import com.him.dungeons.storage.Period;
import com.him.dungeons.util.InstanceSnapshot;
import com.him.dungeons.util.LanguageManager;
import com.him.dungeons.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * 运行实例管理 GUI（3 层）。
 *
 * <p>L1 总览 / L2 详情 / L3 踢人。只读 {@code InstanceSnapshotService} 的快照，
 * 不直接遍历 {@code dungeonInstances}——防止每次渲染做 45 次 getPlayer。</p>
 *
 * <p>forceEnd / 踢出全部都需要 {@code Shift + 左键} 二次确认。</p>
 */
public class InstanceManagerGui implements Listener {

    private static final String P_OVERVIEW = "imOverview";
    private static final String P_DETAIL   = "imDetail";
    private static final String P_KICK     = "imKick";

    private static final int PER_PAGE = 45;

    public static class Holder implements InventoryHolder {
        public final String pageId;
        public final String worldName;
        public final int pageIndex;

        public Holder(String pageId, String worldName, int pageIndex) {
            this.pageId = pageId;
            this.worldName = worldName;
            this.pageIndex = pageIndex;
        }

        @Override public Inventory getInventory() { return null; }
    }

    private final HimDungeons plugin;

    public InstanceManagerGui(HimDungeons plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ==================== L1 总览 ====================

    public void openOverview(Player p, int page) {
        List<InstanceSnapshot> list = sortedSnapshots();

        int maxPage = Math.max(0, (list.size() - 1) / PER_PAGE);
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;

        String title = LanguageManager.get(p, "instances.title", "count", list.size());
        Inventory inv = Bukkit.createInventory(
                new Holder(P_OVERVIEW, null, page), 54, title);

        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < list.size(); i++) {
            inv.setItem(i, buildEntry(p, list.get(start + i)));
        }
        if (list.isEmpty()) {
            inv.setItem(22, item(Material.GRAY_DYE,
                    LanguageManager.get(p, "instances.empty"), null));
        }

        inv.setItem(45, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.page-prev"), null));
        inv.setItem(46, item(Material.PAPER,
                LanguageManager.get(p, "editor.common.page-info", "page", page + 1), null));
        inv.setItem(47, item(Material.ARROW,
                LanguageManager.get(p, "editor.common.page-next"), null));
        inv.setItem(48, item(Material.LIME_DYE,
                LanguageManager.get(p, "instances.btn-refresh"), null));
        inv.setItem(49, item(Material.BARRIER,
                LanguageManager.get(p, "instances.btn-close"), null));

        p.openInventory(inv);
    }

    private List<InstanceSnapshot> sortedSnapshots() {
        List<InstanceSnapshot> list = new ArrayList<>(
                plugin.getInstanceSnapshotService().snapshots().values());
        list.sort(Comparator.comparingLong((InstanceSnapshot s) -> s.startedAt).reversed());
        return list;
    }

    private ItemStack buildEntry(Player p, InstanceSnapshot s) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (!(head.getItemMeta() instanceof SkullMeta sm)) return head;

        if (!s.playerUuids.isEmpty()) {
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(s.playerUuids.get(0)));
        }
        sm.setDisplayName(LanguageManager.get(p, "instances.entry-name",
                "dungeon", s.dungeonName));

        List<String> lore = new ArrayList<>();
        lore.add(LanguageManager.get(p, "instances.entry-world",
                "world", s.worldName));
        lore.add(LanguageManager.get(p, "instances.entry-team",
                "size", s.teamSize()));

        StringBuilder names = new StringBuilder();
        int shown = 0;
        for (UUID uuid : s.playerUuids) {
            if (shown > 0) names.append(", ");
            if (shown >= 4) { names.append("..."); break; }
            names.append(s.playerNames.getOrDefault(uuid, "?"));
            shown++;
        }
        lore.add(LanguageManager.get(p, "instances.entry-players",
                "list", names.length() == 0 ? "-" : names.toString()));

        lore.add(LanguageManager.get(p, "instances.entry-rooms",
                "done", s.roomsDone, "total", s.roomsTotal));

        lore.add(s.remainingSec >= 0
                ? LanguageManager.get(p, "instances.entry-time",
                        "time", formatDuration(s.remainingSec))
                : LanguageManager.get(p, "instances.entry-time-unlimited"));

        lore.add(s.bossHpPercent >= 0
                ? LanguageManager.get(p, "instances.entry-boss",
                        "percent", String.format("%.0f", s.bossHpPercent))
                : LanguageManager.get(p, "instances.entry-boss-none"));

        lore.add(LanguageManager.get(p, "instances.entry-uptime",
                "time", formatDuration((System.currentTimeMillis() - s.startedAt) / 1000L)));
        lore.add("");
        lore.add(LanguageManager.get(p, "instances.entry-click"));

        sm.setLore(lore);
        head.setItemMeta(sm);
        return head;
    }

    // ==================== L2 详情 ====================

    public void openDetail(Player p, String worldName) {
        InstanceSnapshot s = plugin.getInstanceSnapshotService().get(worldName);
        if (s == null) {
            MessageUtil.sendError(p, "instances.msg.instance-gone");
            openOverview(p, 0);
            return;
        }

        String title = LanguageManager.get(p, "instances.detail.title",
                "dungeon", s.dungeonName);
        Inventory inv = Bukkit.createInventory(
                new Holder(P_DETAIL, worldName, 0), 27, title);

        // Boss HP 进度条（10 格玻璃）
        int filled = s.bossHpPercent >= 0
                ? (int) Math.round(s.bossHpPercent / 10.0) : 0;
        for (int i = 0; i < 10; i++) {
            Material mat = i < filled
                    ? Material.RED_STAINED_GLASS_PANE
                    : Material.GRAY_STAINED_GLASS_PANE;
            inv.setItem(i, item(mat, " ", null));
        }

        inv.setItem(10, item(Material.PLAYER_HEAD,
                LanguageManager.get(p, "instances.detail.btn-kick"),
                List.of(LanguageManager.get(p, "instances.detail.btn-kick-lore"))));
        inv.setItem(11, item(Material.BOOK,
                LanguageManager.get(p, "instances.detail.btn-stats"),
                List.of(LanguageManager.get(p, "instances.detail.btn-stats-lore"))));
        inv.setItem(12, item(Material.COMPASS,
                LanguageManager.get(p, "instances.detail.btn-copy-world"),
                List.of(LanguageManager.get(p, "instances.detail.btn-copy-world-lore"))));
        inv.setItem(13, item(Material.MAP,
                LanguageManager.get(p, "instances.detail.world-info"),
                List.of(
                        LanguageManager.get(p, "instances.entry-world", "world", s.worldName),
                        LanguageManager.get(p, "instances.entry-rooms",
                                "done", s.roomsDone, "total", s.roomsTotal),
                        s.remainingSec >= 0
                                ? LanguageManager.get(p, "instances.entry-time",
                                        "time", formatDuration(s.remainingSec))
                                : LanguageManager.get(p, "instances.entry-time-unlimited"))));
        inv.setItem(14, item(Material.TNT,
                LanguageManager.get(p, "instances.detail.btn-forceend"),
                List.of(LanguageManager.get(p, "instances.detail.btn-forceend-lore"))));
        inv.setItem(16, item(Material.ARROW,
                LanguageManager.get(p, "instances.detail.btn-back"), null));

        p.openInventory(inv);
    }

    // ==================== L3 踢人 ====================

    public void openKick(Player p, String worldName) {
        InstanceSnapshot s = plugin.getInstanceSnapshotService().get(worldName);
        if (s == null) {
            MessageUtil.sendError(p, "instances.msg.instance-gone");
            openOverview(p, 0);
            return;
        }

        String title = LanguageManager.get(p, "instances.kick.title",
                "dungeon", s.dungeonName);
        Inventory inv = Bukkit.createInventory(
                new Holder(P_KICK, worldName, 0), 27, title);

        for (int i = 0; i < s.playerUuids.size() && i < 18; i++) {
            UUID uuid = s.playerUuids.get(i);
            String name = s.playerNames.getOrDefault(uuid, "?");
            Player online = Bukkit.getPlayer(uuid);
            boolean isOnline = online != null && online.isOnline();

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
                sm.setDisplayName("§e" + name + (isOnline ? "" : " §7(离线)"));
                sm.setLore(List.of(LanguageManager.get(p,
                        isOnline ? "instances.kick.click-kick"
                                 : "instances.kick.click-kick-offline")));
                head.setItemMeta(sm);
            }
            inv.setItem(i, head);
        }

        inv.setItem(22, item(Material.TNT,
                LanguageManager.get(p, "instances.kick.btn-kick-all"),
                List.of(LanguageManager.get(p, "instances.kick.btn-kick-all-lore"))));
        inv.setItem(26, item(Material.ARROW,
                LanguageManager.get(p, "instances.kick.btn-back"), null));

        p.openInventory(inv);
    }

    // ==================== 事件分发 ====================

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.hasPermission("himdungeons.admin")) return;

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getInventory().getSize()) return;

        switch (h.pageId) {
            case P_OVERVIEW -> clickOverview(p, h, slot);
            case P_DETAIL   -> clickDetail(p, h, slot, e);
            case P_KICK     -> clickKick(p, h, slot, e);
        }
    }

    private void clickOverview(Player p, Holder h, int slot) {
        if (slot == 45) { openOverview(p, Math.max(0, h.pageIndex - 1)); return; }
        if (slot == 47) { openOverview(p, h.pageIndex + 1); return; }
        if (slot == 48) { openOverview(p, h.pageIndex); return; }
        if (slot == 49) { p.closeInventory(); return; }

        if (slot >= 0 && slot < PER_PAGE) {
            List<InstanceSnapshot> list = sortedSnapshots();
            int idx = h.pageIndex * PER_PAGE + slot;
            if (idx < list.size()) openDetail(p, list.get(idx).worldName);
        }
    }

    private void clickDetail(Player p, Holder h, int slot, InventoryClickEvent e) {
        String worldName = h.worldName;
        InstanceSnapshot s = plugin.getInstanceSnapshotService().get(worldName);
        if (s == null) {
            MessageUtil.sendError(p, "instances.msg.instance-gone");
            openOverview(p, 0);
            return;
        }

        switch (slot) {
            case 10 -> openKick(p, worldName);

            case 11 -> {
                p.closeInventory();
                // 复用命令统计页
                p.performCommand("himdungeons stats admin " + s.dungeonName);
            }

            case 12 -> {
                p.closeInventory();
                p.sendMessage("§e" + worldName);
                MessageUtil.sendMessage(p, "instances.msg.copied-world");
            }

            case 14 -> { // 强制结束
                if (!e.isShiftClick()) {
                    MessageUtil.sendError(p, "instances.msg.confirm-forceend");
                    return;
                }
                HimDungeons.DungeonInstance inst =
                        plugin.getDungeonInstanceByWorld(worldName);
                if (inst != null) {
                    plugin.forceFinishDungeon(inst);
                    MessageUtil.sendMessage(p, "instances.msg.forced-end",
                            "world", worldName);
                }
                
                // ★ 修复：如果是结束自己，这里强制关一下，并延迟重开总览
                p.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline()) openOverview(p, 0);
                }, 2L);
            }

            case 16 -> openOverview(p, 0);
        }
    }

    private void clickKick(Player p, Holder h, int slot, InventoryClickEvent e) {
        String worldName = h.worldName;
        InstanceSnapshot s = plugin.getInstanceSnapshotService().get(worldName);
        if (s == null) {
            MessageUtil.sendError(p, "instances.msg.instance-gone");
            openOverview(p, 0);
            return;
        }

        if (slot == 26) { openDetail(p, worldName); return; }

        if (slot == 22) {
            if (!e.isShiftClick()) {
                MessageUtil.sendError(p, "instances.msg.confirm-kick-all");
                return;
            }
            int n = 0;
            for (UUID uuid : new ArrayList<>(s.playerUuids)) {
                Player target = Bukkit.getPlayer(uuid);
                if (target != null && target.isOnline()) {
                    plugin.getSessionService().leave(target);
                    n++;
                }
            }
            MessageUtil.sendMessage(p, "instances.msg.kicked-all", "count", n);
            openOverview(p, 0);
            return;
        }

        if (slot >= 0 && slot < 18 && slot < s.playerUuids.size()) {
            UUID uuid = s.playerUuids.get(slot);
            Player target = Bukkit.getPlayer(uuid);
            if (target == null || !target.isOnline()) {
                MessageUtil.sendError(p, "instances.kick.player-offline",
                        "player", s.playerNames.getOrDefault(uuid, "?"));
                return;
            }
            plugin.getSessionService().leave(target);
            MessageUtil.sendMessage(p, "instances.msg.kicked",
                    "player", target.getName());
            openKick(p, worldName);
        }
    }

    // ==================== 工具 ====================

    private static ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private static String formatDuration(long sec) {
        long m = sec / 60;
        long s = sec % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }
}