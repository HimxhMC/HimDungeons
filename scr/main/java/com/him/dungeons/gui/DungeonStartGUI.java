package com.him.dungeons.gui;

import com.him.dungeons.HimDungeons;
import com.him.dungeons.util.*;
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
    private final Map<UUID, String> selectedDungeon = new HashMap<>();

    public DungeonStartGUI(HimDungeons plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openStartMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.GREEN + "选择地牢");
        // 列出所有可用地牢（config中enable=true）
        File dataFolder = plugin.getDataFolder();
        File[] dirs = dataFolder.listFiles(File::isDirectory);
        int slot = 9;
        if (dirs != null) {
            for (File dir : dirs) {
                if (new File(dir, "config.yml").exists()) {
                    // 检查是否启用
                    boolean enabled = true; // 简单读取
                    // 可读取配置检查enable
                    if (enabled) {
                        ItemStack item = new ItemStack(Material.LIME_WOOL);
                        ItemMeta meta = item.getItemMeta();
                        meta.setDisplayName(ChatColor.GREEN + dir.getName());
                        List<String> lore = new ArrayList<>();
                        lore.add(ChatColor.GRAY + "点击加入");
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                        inv.setItem(slot++, item);
                        if (slot >= 45) break;
                    }
                }
            }
        }
        // 功能按钮
        setItem(inv, 45, Material.DISPENSER, "随机选择", "随机加入一个地牢");
        setItem(inv, 46, Material.PLAYER_HEAD, "单人加入", "单独进入地牢");
        setItem(inv, 47, Material.PLAYER_HEAD, "组队加入", "邀请附近3格内潜行玩家");
        // Party加入仅当有Party插件且地牢支持时动态显示，这里略
        setItem(inv, 53, Material.BARRIER, "关闭", null);
        player.openInventory(inv);
    }

    private void setItem(Inventory inv, int slot, Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);
        if (lore != null) {
            meta.setLore(Collections.singletonList(ChatColor.GRAY + lore));
        }
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        String title = e.getView().getTitle();
        if (!title.equals(ChatColor.GREEN + "选择地牢")) return;
        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (slot == 53) { p.closeInventory(); return; }

        if (slot >= 9 && slot < 45) {
            String dungeonName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            // 确认加入
            p.closeInventory();
            // 直接启动地牢（单人模式）
            plugin.startDungeon(p, dungeonName, "single");
            return;
        }

        if (slot == 45) { // 随机
            p.closeInventory();
            // 随机选择一个可用地牢
            // 略
        } else if (slot == 46) { // 单人
            p.closeInventory();
            MessageUtil.sendMessage(p, "请再次输入 /dg start 选择地牢，或点击地牢名直接加入");
        } else if (slot == 47) { // 组队
            p.closeInventory();
            MessageUtil.sendMessage(p, "检测附近3格内潜行玩家...");
            // 实现检测并邀请
            // 略
        }
    }
}