package com.ukagaka.irono.hANNYOUPlugin.storage;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.Base64;

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

    public static String toBase64(byte[] data) {
        return data == null ? null : Base64.getEncoder().encodeToString(data);
    }

    public static byte[] fromBase64(String data) {
        return data == null || data.isEmpty() ? null : Base64.getMimeDecoder().decode(data);
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
}
