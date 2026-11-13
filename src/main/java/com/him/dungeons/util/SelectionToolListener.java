package com.him.dungeons.util;

import com.him.dungeons.HimDungeons;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class SelectionToolListener implements Listener {

    private final HimDungeons plugin;

    public SelectionToolListener(HimDungeons plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.CREATIVE) return;
        if (!p.isOp()) return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.WOODEN_PICKAXE) return;
        if (e.getClickedBlock() == null) return;

        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            SelectionManager.setPos1(p.getUniqueId(), e.getClickedBlock().getLocation());
            org.bukkit.Location loc = e.getClickedBlock().getLocation();
            MessageUtil.sendMessage(p, "selection.pos1",
                    "x", loc.getBlockX(), "y", loc.getBlockY(), "z", loc.getBlockZ());

            SelectionManager.Selection sel = SelectionManager.getSelection(p.getUniqueId());
            if (sel != null) plugin.getDebugVisualizer().showSelection(p, sel);

            e.setCancelled(true);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            SelectionManager.setPos2(p.getUniqueId(), e.getClickedBlock().getLocation());
            org.bukkit.Location loc = e.getClickedBlock().getLocation();
            MessageUtil.sendMessage(p, "selection.pos2",
                    "x", loc.getBlockX(), "y", loc.getBlockY(), "z", loc.getBlockZ());

            SelectionManager.Selection sel = SelectionManager.getSelection(p.getUniqueId());
            if (sel != null) {
                MessageUtil.sendMessage(p, "selection.region",
                        "w", sel.width, "h", sel.height, "l", sel.length);
                plugin.getDebugVisualizer().showSelection(p, sel);
            }
            e.setCancelled(true);
        }
    }
}