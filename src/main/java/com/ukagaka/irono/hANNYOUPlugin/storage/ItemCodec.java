package com.ukagaka.irono.hANNYOUPlugin.storage;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * アイテム・ポーション効果のシリアライズ。
 * Paperのバイナリ形式 (serializeAsBytes) を使うため、旧Java直列化より高速・小サイズで、
 * Minecraftのバージョンアップ時もDataFixerで自動変換される。
 */
public final class ItemCodec {
    private ItemCodec() {}

    public static byte[] encode(ItemStack item) {
        return item == null || item.isEmpty() ? null : item.serializeAsBytes();
    }

    public static ItemStack decode(byte[] data) {
        if (data == null || data.length == 0) return null;
        ItemStack item = ItemStack.deserializeBytes(data);
        return item.isEmpty() ? null : item;
    }

    /** null・空気を含む配列をそのまま1つのバイト列にまとめる */
    public static byte[] encodeArray(ItemStack[] items) {
        return ItemStack.serializeItemsAsBytes(Arrays.asList(items));
    }

    public static ItemStack[] decodeArray(byte[] data, int size) {
        ItemStack[] result = new ItemStack[size];
        if (data == null || data.length == 0) return result;
        ItemStack[] decoded = ItemStack.deserializeItemsFromBytes(data);
        for (int i = 0; i < Math.min(size, decoded.length); i++) {
            result[i] = decoded[i] == null || decoded[i].isEmpty() ? null : decoded[i];
        }
        return result;
    }

    public static String toBase64(byte[] data) {
        return data == null ? null : Base64.getEncoder().encodeToString(data);
    }

    public static byte[] fromBase64(String data) {
        return data == null || data.isEmpty() ? null : Base64.getMimeDecoder().decode(data);
    }

    /** KnightsEditor (旧形式: BukkitObjectOutputStream + Base64) のアイテムを読み込む */
    @SuppressWarnings("deprecation")
    public static ItemStack decodeLegacyBase64(String data) throws Exception {
        if (data == null || data.isEmpty()) return null;
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(
                new ByteArrayInputStream(Base64.getMimeDecoder().decode(data)))) {
            return (ItemStack) in.readObject();
        }
    }

    /** minecraft名前空間はキーのみ（例: speed）、それ以外は namespace:key で表す */
    public static String effectKey(PotionEffectType type) {
        NamespacedKey key = type.getKey();
        return NamespacedKey.MINECRAFT.equals(key.getNamespace()) ? key.getKey() : key.asString();
    }

    public static PotionEffectType effectType(String key) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(key.toLowerCase());
        return namespacedKey == null ? null : Registry.MOB_EFFECT.get(namespacedKey);
    }

    public static String encodeEffects(Map<PotionEffectType, Integer> effects) {
        StringBuilder sb = new StringBuilder();
        effects.forEach((type, level) -> sb.append(effectKey(type)).append('=').append(level).append(';'));
        return sb.toString();
    }

    public static Map<PotionEffectType, Integer> decodeEffects(String data) {
        Map<PotionEffectType, Integer> effects = new LinkedHashMap<>();
        if (data == null || data.isEmpty()) return effects;
        for (String part : data.split(";")) {
            int sep = part.lastIndexOf('=');
            if (sep <= 0) continue;
            PotionEffectType type = effectType(part.substring(0, sep));
            if (type == null) continue;
            try {
                effects.put(type, Integer.parseInt(part.substring(sep + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return effects;
    }
}
