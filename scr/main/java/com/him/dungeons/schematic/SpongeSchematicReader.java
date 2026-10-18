package com.him.dungeons.schematic;

import net.querz.nbt.io.NBTInputStream;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.*;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Sponge 原理图读取器，支持 v2 和 v3 格式。
 * 完全替代 WorldEdit 的 ClipboardFormat 读取。
 */
public class SpongeSchematicReader {

    /**
     * 读取原理图文件。
     *
     * @param file    原理图文件（.schem）
     * @param version 期望的格式版本（2 或 3），传 0 表示自动检测
     * @return NbtSchematic 数据模型，失败返回 null
     */
    public static NbtSchematic read(File file, int version) {
        if (file == null || !file.exists()) return null;

        try (FileInputStream fis = new FileInputStream(file);
             GZIPInputStream gzis = new GZIPInputStream(fis);
             NBTInputStream nbtIn = new NBTInputStream(gzis)) {

            NamedTag namedTag = nbtIn.readTag(512);
            Tag<?> rootTag = namedTag.getTag();
            if (!(rootTag instanceof CompoundTag)) return null;

            CompoundTag root = (CompoundTag) rootTag;

            // 自动检测版本
            int detectedVersion = version;
            if (detectedVersion == 0) {
                detectedVersion = root.getInt("Version");
                if (detectedVersion == 0) detectedVersion = 2; // 兜底
            }

            return detectedVersion >= 3 ? parseV3(root) : parseV2(root);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== V2 解析 ====================

    private static NbtSchematic parseV2(CompoundTag root) {
        NbtSchematic schem = new NbtSchematic();
        schem.version = 2;
        schem.width  = root.getShort("Width") & 0xFFFF;
        schem.height = root.getShort("Height") & 0xFFFF;
        schem.length = root.getShort("Length") & 0xFFFF;

        // 偏移量
        if (root.containsKey("Offset")) {
            IntArrayTag offset = root.getIntArrayTag("Offset");
            if (offset != null && offset.getValue().length >= 3) {
                schem.offsetX = offset.getValue()[0];
                schem.offsetY = offset.getValue()[1];
                schem.offsetZ = offset.getValue()[2];
            }
        }

        // 调色板
        CompoundTag paletteTag = root.getCompoundTag("Palette");
        if (paletteTag == null) return null;
        schem.palette = parsePalette(paletteTag);

        // 方块数据（varint 解码）
        ByteArrayTag dataTag = root.getByteArrayTag("BlockData");
        if (dataTag == null) return null;
        int volume = schem.width * schem.height * schem.length;
        schem.blockData = decodeVarInts(dataTag.getValue(), volume);

Tag<?> beTag = root.get("BlockEntities");
if (beTag instanceof ListTag<?> beList) {
    schem.blockEntities = parseBlockEntities(beList);
}

        return schem;
    }

    // ==================== V3 解析 ====================

    private static NbtSchematic parseV3(CompoundTag root) {
        NbtSchematic schem = new NbtSchematic();
        schem.version = 3;

        CompoundTag blocks = root.getCompoundTag("Blocks");
        if (blocks == null) return null;

        schem.width  = blocks.getShort("Width") & 0xFFFF;
        schem.height = blocks.getShort("Height") & 0xFFFF;
        schem.length = blocks.getShort("Length") & 0xFFFF;

        // 调色板
        CompoundTag paletteTag = blocks.getCompoundTag("Palette");
        if (paletteTag == null) return null;
        schem.palette = parsePalette(paletteTag);

        // 方块数据
        ByteArrayTag dataTag = blocks.getByteArrayTag("Data");
        if (dataTag == null) return null;
        int volume = schem.width * schem.height * schem.length;
        schem.blockData = decodeVarInts(dataTag.getValue(), volume);

        // 方块实体
Tag<?> beTag = blocks.get("BlockEntities");
if (beTag instanceof ListTag<?> beList) {
    schem.blockEntities = parseBlockEntities(beList);
}

        return schem;
    }

    // ==================== 工具方法 ====================

    /** 解析调色板 */
    private static BlockData[] parsePalette(CompoundTag paletteTag) {
        // 确定最大索引
        int maxIndex = 0;
        for (String key : paletteTag.keySet()) {
            int idx = paletteTag.getInt(key);
            if (idx > maxIndex) maxIndex = idx;
        }
        BlockData[] palette = new BlockData[maxIndex + 1];
        for (String key : paletteTag.keySet()) {
            int idx = paletteTag.getInt(key);
            try {
                palette[idx] = Bukkit.createBlockData(key);
            } catch (Exception e) {
                // 未知方块降级为空气
                palette[idx] = Bukkit.createBlockData("minecraft:air");
            }
        }
        return palette;
    }

    /** 解析方块实体列表 */
private static List<NbtSchematic.BlockEntityData> parseBlockEntities(ListTag<?> list) {
    List<NbtSchematic.BlockEntityData> result = new ArrayList<>();
    for (Tag<?> tag : list) {
        if (!(tag instanceof CompoundTag be)) continue;

        String id = be.getString("Id");
        IntArrayTag pos = be.getIntArrayTag("Pos");
        if (id == null || pos == null || pos.getValue().length < 3) continue;

        int[] p = pos.getValue();
        Map<String, Object> data = new HashMap<>();
        for (String key : be.keySet()) {
            if (key.equals("Id") || key.equals("Pos")) continue;
            data.put(key, be.get(key));
        }
        result.add(new NbtSchematic.BlockEntityData(p[0], p[1], p[2], id, data));
    }
    return result;
}

    /**
     * 解码 varint 字节数组为 int 数组。
     * Sponge 格式使用 varint 编码调色板索引。
     */
    public static int[] decodeVarInts(byte[] data, int expectedCount) {
        int[] result = new int[expectedCount];
        int pos = 0, index = 0;
        while (pos < data.length && index < expectedCount) {
            int value = 0, shift = 0;
            while (true) {
                if (pos >= data.length) break;
                byte b = data[pos++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
                if (shift > 35) break; // 防溢出
            }
            result[index++] = value;
        }
        return result;
    }

    /**
     * 将 int 数组编码为 varint 字节数组（用于写入）。
     */
    public static byte[] encodeVarInts(int[] values) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int value : values) {
            int v = value;
            while ((v & ~0x7F) != 0) {
                baos.write((v & 0x7F) | 0x80);
                v >>>= 7;
            }
            baos.write(v);
        }
        return baos.toByteArray();
    }
}