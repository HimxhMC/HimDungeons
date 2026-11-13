package com.him.dungeons.gui.editor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * 用于标识"这是编辑器 GUI"。
 */
public final class EditorHolder implements InventoryHolder {

    public final String pageId;
    public final String dungeonName;
    public final String contextKey;
    public final int pageIndex;
    /** 用于列表/键值编辑器的返回定位：来源分类 section 名（root/gen/world/player/boss/economy/soft） */
    public final String returnSection;

    public EditorHolder(String pageId, String dungeonName, String contextKey, int pageIndex) {
        this(pageId, dungeonName, contextKey, pageIndex, null);
    }

    public EditorHolder(String pageId, String dungeonName, String contextKey,
                        int pageIndex, String returnSection) {
        this.pageId = pageId;
        this.dungeonName = dungeonName;
        this.contextKey = contextKey;
        this.pageIndex = pageIndex;
        this.returnSection = returnSection;
    }

    @Override
    public Inventory getInventory() { return null; }
}