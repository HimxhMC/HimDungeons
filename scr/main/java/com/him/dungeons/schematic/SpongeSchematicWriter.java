package com.him.dungeons.schematic;

import net.querz.nbt.io.NBTOutputStream;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.*;
import org.bukkit.block.data.BlockData;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/**
 * Sponge 原理图写入器，支持 v2 和 v3 格式。
 * 完全替代 WorldEdit 的 ClipboardWriter。
 */
public class SpongeSchematicWriter {

    /**
     * 将原理图数据写入文件。
     *
     * @param file     输出文件
     * @param schem    原理图数据
     * @param version  输出格式版本（2 或 3）
     * @return 是否成功
     */
    public static boolean write(File file, NbtSchematic schem, int version) {
        if (file == null || schem == null) return false;
        try {
            // 构建调色板反向映射
            Map<String, Integer> paletteMap = new LinkedHashMap<>();
            BlockData[] palette = schem.palette;
            for (int i = 0; i < palette.length; i++) {
                if (palette[i] != null) {
                    paletteMap.put(palette[i].getAsString(), i);
                }
            }

            CompoundTag root = version >= 3 ? buildV3(schem, paletteMap) : buildV2(schem, paletteMap);

            // 写入 gzip 压缩的 NBT
            try (FileOutputStream fos = new FileOutputStream(file);
                 GZIPOutputStream gzos = new GZIPOutputStream(fos);
                 NBTOutputStream nbtOut = new NBTOutputStream(gzos)) {
                nbtOut.writeTag(new NamedTag("", root),512);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ==================== V2 构建 ====================

    private static CompoundTag buildV2(NbtSchematic schem, Map<String, Integer> paletteMap) {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2);
        root.putInt("DataVersion", getCurrentDataVersion());
        root.putShort("Width", (short) schem.width);
        root.putShort("Height", (short) schem.height);
        root.putShort("Length", (short) schem.length);
        root.putIntArray("Offset", new int[]{schem.offsetX, schem.offsetY, schem.offsetZ});

        // Palette
        CompoundTag paletteTag = new CompoundTag();
        for (Map.Entry<String, Integer> e : paletteMap.entrySet()) {
            paletteTag.putInt(e.getKey(), e.getValue());
        }
        root.put("Palette", paletteTag);
        root.putInt("PaletteMax", paletteMap.size());

        // BlockData
        byte[] varintData = SpongeSchematicReader.encodeVarInts(schem.blockData);
        root.putByteArray("BlockData", varintData);

        // BlockEntities
        if (schem.blockEntities != null && !schem.blockEntities.isEmpty()) {
            ListTag<CompoundTag> beList = buildBlockEntities(schem.blockEntities);
            root.put("BlockEntities", beList);
        }

        return root;
    }

    // ==================== V3 构建 ====================

    private static CompoundTag buildV3(NbtSchematic schem, Map<String, Integer> paletteMap) {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 3);
        root.putInt("DataVersion", getCurrentDataVersion());

        CompoundTag blocks = new CompoundTag();
        blocks.putShort("Width", (short) schem.width);
        blocks.putShort("Height", (short) schem.height);
        blocks.putShort("Length", (short) schem.length);

        // Palette
        CompoundTag paletteTag = new CompoundTag();
        for (Map.Entry<String, Integer> e : paletteMap.entrySet()) {
            paletteTag.putInt(e.getKey(), e.getValue());
        }
        blocks.put("Palette", paletteTag);

        // Data
        byte[] varintData = SpongeSchematicReader.encodeVarInts(schem.blockData);
        blocks.putByteArray("Data", varintData);

        // BlockEntities
        if (schem.blockEntities != null && !schem.blockEntities.isEmpty()) {
            blocks.put("BlockEntities", buildBlockEntities(schem.blockEntities));
        }

        root.put("Blocks", blocks);
        return root;
    }

    // ==================== 工具方法 ====================

    private static ListTag<CompoundTag> buildBlockEntities(
            List<NbtSchematic.BlockEntityData> entities) {
        ListTag<CompoundTag> list = new ListTag<>(CompoundTag.class);
        for (NbtSchematic.BlockEntityData be : entities) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", be.id);
            tag.putIntArray("Pos", new int[]{be.x, be.y, be.z});
            if (be.nbtData != null) {
                for (Map.Entry<String, Object> e : be.nbtData.entrySet()) {
                    if (e.getValue() instanceof Tag) {
                        tag.put(e.getKey(), (Tag<?>) e.getValue());
                    }
                }
            }
            list.add(tag);
        }
        return list;
    }

    /** 获取当前 Minecraft 数据版本（简化实现，按需更新） */
    private static int getCurrentDataVersion() {
        return 4189; // 1.21.3
    }
}