package com.him.dungeons.gui;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.storage.*;
import com.him.dungeons.util.LanguageManager;
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
 * 排行榜 GUI（多维度）。
 *
 * <p>维度：{@link LeaderboardMode} × {@link TeamSizeBucket} × {@link Period}。
 * Top 10 占 slot 9-18；Mode 按钮 slot 0-2；TeamSize 按钮 slot 3-7；
 * Period 按钮 slot 45-47；"我的排名" slot 49；关闭 slot 53。</p>
 */
public class LeaderboardGui implements Listener {

    public static class Holder implements InventoryHolder {
        public final String dungeon;
        public LeaderboardMode mode;
        public TeamSizeBucket bucket;
        public Period period;

        public Holder(String dungeon, LeaderboardMode mode,
                      TeamSizeBucket bucket, Period period) {
            this.dungeon = dungeon;
            this.mode = mode;
            this.bucket = bucket;
            this.period = period;
        }
        @Override public Inventory getInventory() { return null; }
    }

    private final HimDungeons plugin;

    public LeaderboardGui(HimDungeons plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p, String dungeon, LeaderboardMode mode) {
        open(p, dungeon, mode, TeamSizeBucket.ALL, Period.ALL);
    }

    public void open(Player p, String dungeon, LeaderboardMode mode,
                     TeamSizeBucket bucket, Period period) {
        Holder holder = new Holder(dungeon, mode, bucket, period);
        String title = LanguageManager.get(p, "leaderboard.title",
                "dungeon", dungeon, "mode", tr(p, mode));
        Inventory inv = Bukkit.createInventory(holder, 54, title);

        // Mode 按钮 slot 0-2
        LeaderboardMode[] modes = LeaderboardMode.values();
        for (int i = 0; i < modes.length; i++) {
            inv.setItem(i, modeBtn(p, modes[i], modes[i] == mode));
        }

        // TeamSize 按钮 slot 3-7
        TeamSizeBucket[] buckets = TeamSizeBucket.values();
        for (int i = 0; i < buckets.length; i++) {
            inv.setItem(3 + i, bucketBtn(p, buckets[i], buckets[i] == bucket));
        }

        // Top 10
        List<RankEntry> top = plugin.getRunRecordService().cache()
                .top(dungeon, mode, bucket, period, 10);
        for (int i = 0; i < top.size() && i < 10; i++) {
            inv.setItem(9 + i, head(p, top.get(i), mode));
        }
        if (top.isEmpty()) {
            inv.setItem(13, item(Material.GRAY_DYE,
                    LanguageManager.get(p, "leaderboard.lore-empty"), null));
        }

        // Period 按钮 slot 45-47
        Period[] periods = Period.values();
        for (int i = 0; i < periods.length; i++) {
            inv.setItem(45 + i, periodBtn(p, periods[i], periods[i] == period));
        }

        // 我的排名 slot 49
        UUID uuid = p.getUniqueId();
        int rank = plugin.getRunRecordService().storage()
                .playerRank(uuid, dungeon, mode, bucket, period);
        inv.setItem(49, item(Material.PLAYER_HEAD,
                LanguageManager.get(p, "leaderboard.my-rank"),
                List.of(LanguageManager.get(p, rank > 0
                        ? "leaderboard.my-rank-value"
                        : "leaderboard.my-rank-none", "rank", rank))));

        inv.setItem(53, item(Material.BARRIER,
                LanguageManager.get(p, "leaderboard.close"), null));

        p.openInventory(inv);
    }

    private String tr(Player p, LeaderboardMode mode) {
        return LanguageManager.get(p, "leaderboard.mode-" + mode.id);
    }

    private ItemStack modeBtn(Player p, LeaderboardMode m, boolean current) {
        return item(current ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                LanguageManager.get(p, current
                        ? "leaderboard.mode-current"
                        : "leaderboard.mode-select",
                        "mode", tr(p, m)), null);
    }

    private ItemStack bucketBtn(Player p, TeamSizeBucket b, boolean current) {
        return item(current ? Material.LIME_DYE : Material.GRAY_DYE,
                LanguageManager.get(p, current
                        ? "leaderboard.bucket-current"
                        : "leaderboard.bucket-select",
                        "bucket", LanguageManager.get(p, "leaderboard.bucket-" + b.name().toLowerCase())),
                null);
    }

    private ItemStack periodBtn(Player p, Period period, boolean current) {
        return item(current ? Material.CLOCK : Material.COMPASS,
                LanguageManager.get(p, current
                        ? "leaderboard.period-current"
                        : "leaderboard.period-select",
                        "period", LanguageManager.get(p, "leaderboard.period-" + period.name().toLowerCase())),
                null);
    }

    private ItemStack head(Player p, RankEntry e, LeaderboardMode mode) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta sm) {
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(e.uuid));
            sm.setDisplayName(LanguageManager.get(p, "leaderboard.entry-name",
                    "rank", e.rank, "player", e.name));
            String val = switch (mode) {
                case FASTEST -> LanguageManager.get(p, "leaderboard.value-fastest",
                        "time", formatTime(p, e.value));
                case WINS    -> LanguageManager.get(p, "leaderboard.value-wins",
                        "wins", e.value);
                case SCORE   -> LanguageManager.get(p, "leaderboard.value-score",
                        "score", e.value);
            };
            sm.setLore(Collections.singletonList(val));
            item.setItemMeta(sm);
        }
        return item;
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private static String formatTime(Player p, long ms) {
        long sec = Math.max(0, ms) / 1000;
        long m = sec / 60;
        long s = sec % 60;
        return m > 0
                ? LanguageManager.get(p, "leaderboard.time.minutes-seconds", "m", m, "s", s)
                : LanguageManager.get(p, "leaderboard.time.seconds", "s", s);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();

        if (slot >= 0 && slot <= 2) {
            LeaderboardMode[] modes = LeaderboardMode.values();
            if (slot < modes.length) open(p, h.dungeon, modes[slot], h.bucket, h.period);
            return;
        }
        if (slot >= 3 && slot <= 7) {
            TeamSizeBucket[] buckets = TeamSizeBucket.values();
            int idx = slot - 3;
            if (idx < buckets.length) open(p, h.dungeon, h.mode, buckets[idx], h.period);
            return;
        }
        if (slot >= 45 && slot <= 47) {
            Period[] periods = Period.values();
            int idx = slot - 45;
            if (idx < periods.length) open(p, h.dungeon, h.mode, h.bucket, periods[idx]);
            return;
        }
        if (slot == 53) p.closeInventory();
    }
}