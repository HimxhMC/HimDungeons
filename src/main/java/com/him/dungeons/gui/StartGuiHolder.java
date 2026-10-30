package com.him.dungeons.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * 启动 GUI 的 Holder，用于识别 InventoryClickEvent 属于本 GUI。
 *
 * <p>i18n 后标题会随语言变化，不能再靠 {@code title.equals(...)} 分发事件，
 * 因此引入本 Holder。</p>
 */
public final class StartGuiHolder implements InventoryHolder {

    public static final StartGuiHolder INSTANCE = new StartGuiHolder();

    private StartGuiHolder() {}

    @Override
    public Inventory getInventory() {
        return null;
    }
}