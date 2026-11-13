package com.him.dungeons.gui;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.LanguageManager;
import com.him.dungeons.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

public class DungeonStartGUI implements Listener {

    private final HimDungeons plugin;

    public DungeonStartGUI(HimDungeons plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openStartMenu(Player player) {
        String title = LanguageManager.get(player, "startgui.title");
        Inventory inv = Bukkit.createInventory(StartGuiHolder.INSTANCE, 54, title);

        File dataFolder = plugin.getDataFolder();
        File[] dirs = dataFolder.listFiles(File::isDirectory);
        int slot = 9;
        if (dirs != null) {
            for (File dir : dirs) {
                if (new File(dir, "config.yml").exists()) {
                    ItemStack item = new ItemStack(Material.LIME_WOOL);
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(ChatColor.GREEN + dir.getName());
                        List<String> lore = new ArrayList<>();
                        lore.add(LanguageManager.get(player, "startgui.lore-click-join"));
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                    }
                    inv.setItem(slot++, item);
                    if (slot >= 45) break;
                }
            }
        }

        setItem(inv, player, 45, Material.DISPENSER,
                "startgui.item-random", "startgui.item-random-lore");
        setItem(inv, player, 46, Material.PLAYER_HEAD,
                "startgui.item-single", "startgui.item-single-lore");
        setItem(inv, player, 47, Material.PLAYER_HEAD,
                "startgui.item-party", "startgui.item-party-lore");
        setItem(inv, player, 53, Material.BARRIER,
                "startgui.item-close", null);
        player.openInventory(inv);
    }

    private void setItem(Inventory inv, Player player, int slot, Material mat,
                         String nameKey, String loreKey) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(LanguageManager.get(player, nameKey));
            if (loreKey != null) {
                meta.setLore(Collections.singletonList(LanguageManager.get(player, loreKey)));
            }
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof StartGuiHolder)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (slot == 53) {
            p.closeInventory();
            return;
        }

        if (slot >= 9 && slot < 45) {
            ItemMeta meta = clicked.getItemMeta();
            if (meta == null || meta.getDisplayName() == null) return;
            String dungeonName = ChatColor.stripColor(meta.getDisplayName());
            p.closeInventory();
            plugin.startDungeon(p, dungeonName, "single");
            return;
        }

        if (slot == 45) {
            p.closeInventory();
            // 随机选择逻辑留待后续实现
        } else if (slot == 46) {
            p.closeInventory();
            MessageUtil.sendMessage(p, "startgui.msg-pick-dungeon");
        } else if (slot == 47) {
            p.closeInventory();
            MessageUtil.sendMessage(p, "startgui.msg-detect-nearby");
        }
    }
}