package com.him.dungeons.util;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 木镐选区工具监听器。
 * 左键设置点1，右键设置点2。
 * 仅当玩家为创造模式且是 OP 时有效。
 */
public class SelectionToolListener implements Listener {

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
                    "x", loc.getBlockX(),
                    "y", loc.getBlockY(),
                    "z", loc.getBlockZ());
            e.setCancelled(true);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            SelectionManager.setPos2(p.getUniqueId(), e.getClickedBlock().getLocation());
            org.bukkit.Location loc = e.getClickedBlock().getLocation();
            MessageUtil.sendMessage(p, "selection.pos2",
                    "x", loc.getBlockX(),
                    "y", loc.getBlockY(),
                    "z", loc.getBlockZ());

            SelectionManager.Selection sel = SelectionManager.getSelection(p.getUniqueId());
            if (sel != null) {
                MessageUtil.sendMessage(p, "selection.region",
                        "w", sel.width,
                        "h", sel.height,
                        "l", sel.length);
            }
            e.setCancelled(true);
        }
    }
}