package com.him.dungeons.gui;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.LanguageManager;
import com.him.dungeons.util.WinStorage;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * 排行榜 GUI：支持"最快通关"和"最多通关"两种模式。
 */
public class LeaderboardGui implements Listener {

    public enum Mode { FASTEST, WINS }

    public static class Holder implements InventoryHolder {
        public final String dungeon;
        public final Mode mode;
        public Holder(String dungeon, Mode mode) { this.dungeon = dungeon; this.mode = mode; }
        @Override public Inventory getInventory() { return null; }
    }

    private final HimDungeons plugin;

    public LeaderboardGui(HimDungeons plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p, String dungeon, Mode mode) {
        Map<UUID, Long> fastest = WinStorage.getAllFastestTimes(dungeon);
        Map<UUID, Integer> wins = WinStorage.getAllWins(dungeon);

        List<UUID> sorted;
        if (mode == Mode.FASTEST) {
            sorted = fastest.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .sorted(Map.Entry.comparingByValue())
                    .limit(10)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        } else {
            sorted = wins.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                    .limit(10)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }

        Holder holder = new Holder(dungeon, mode);
        String title = LanguageManager.get(p, "leaderboard.title", "dungeon", dungeon);
        Inventory inv = Bukkit.createInventory(holder, 27, title);

        for (int i = 0; i < sorted.size(); i++) {
            UUID u = sorted.get(i);
            String name = WinStorage.getName(dungeon, u);
            if (name == null || name.isEmpty()) name = u.toString().substring(0, 8);

            String displayName = LanguageManager.get(p, "leaderboard.entry-name",
                    "rank", i + 1, "player", name);
            String lore;
            if (mode == Mode.FASTEST) {
                lore = LanguageManager.get(p, "leaderboard.lore-fastest",
                        "time", formatTime(fastest.get(u)));
            } else {
                lore = LanguageManager.get(p, "leaderboard.lore-wins",
                        "wins", wins.getOrDefault(u, 0));
            }

            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(Collections.singletonList(lore));
                item.setItemMeta(meta);
            }
            inv.setItem(9 + i, item);
        }

        if (sorted.isEmpty()) {
            ItemStack empty = new ItemStack(Material.GRAY_DYE);
            ItemMeta meta = empty.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(LanguageManager.get(p, "leaderboard.lore-empty"));
                empty.setItemMeta(meta);
            }
            inv.setItem(13, empty);
        }

        // 切换按钮
        inv.setItem(0, button(Material.COMPARATOR,
                LanguageManager.get(p, "leaderboard.switch-fastest"),
                mode == Mode.FASTEST ? LanguageManager.get(p, "leaderboard.mode-current",
                        "mode", LanguageManager.get(p, "leaderboard.mode-fastest")) : null));
        inv.setItem(1, button(Material.DIAMOND,
                LanguageManager.get(p, "leaderboard.switch-wins"),
                mode == Mode.WINS ? LanguageManager.get(p, "leaderboard.mode-current",
                        "mode", LanguageManager.get(p, "leaderboard.mode-wins")) : null));
        inv.setItem(26, button(Material.BARRIER, LanguageManager.get(p, "leaderboard.close"), null));

        p.openInventory(inv);
    }

    private static ItemStack button(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(Collections.singletonList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String formatTime(long ms) {
        long totalSec = Math.max(0, ms) / 1000;
        long m = totalSec / 60;
        long s = totalSec % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot == 0) open(p, holder.dungeon, Mode.FASTEST);
        else if (slot == 1) open(p, holder.dungeon, Mode.WINS);
        else if (slot == 26) p.closeInventory();
    }
}