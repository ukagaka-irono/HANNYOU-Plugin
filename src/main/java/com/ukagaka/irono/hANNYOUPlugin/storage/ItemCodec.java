package com.ukagaka.irono.hANNYOUPlugin.storage;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputFilter;
import java.io.Serializable;
import java.util.Base64;
import java.util.Set;

/**
 * アイテム・ポーション効果のシリアライズ。
 * Paperのバイナリ形式 (serializeAsBytes) を使うため、旧Java直列化より高速・小サイズで、
 * Minecraftのバージョンアップ時もDataFixerで自動変換される。
 */
public final class ItemCodec {
    /**
     * 旧形式の読み込みで復元してよいクラスのパッケージ。Java直列化は任意のクラスを復元できてしまうため、
     * アイテムの復元に必要な値・コレクションと Bukkit のラッパー以外は拒否する（共有されたファイル対策）。
     */
    private static final Set<String> LEGACY_PACKAGES = Set.of("java.lang", "java.util", "com.google.common.collect");
    private static final String LEGACY_WRAPPER = "org.bukkit.util.io.Wrapper";
    private static final int LEGACY_MAX_DEPTH = 16;
    private static final int LEGACY_MAX_REFS = 10_000;
    private static final int LEGACY_MAX_ARRAY = 10_000;

    private static final ObjectInputFilter LEGACY_FILTER = info -> {
        if (info.depth() > LEGACY_MAX_DEPTH || info.references() > LEGACY_MAX_REFS
                || info.arrayLength() > LEGACY_MAX_ARRAY) {
            return ObjectInputFilter.Status.REJECTED;
        }
        Class<?> type = info.serialClass();
        if (type == null) return ObjectInputFilter.Status.ALLOWED;
        while (type.isArray()) type = type.getComponentType();
        if (type.isPrimitive() || LEGACY_PACKAGES.contains(type.getPackageName())
                || type.getName().equals(LEGACY_WRAPPER)) {
            return ObjectInputFilter.Status.ALLOWED;
        }
        // ラッパーから Bukkit の仕組みで作り直されたアイテム等。Serializable でない型はJava直列化では作れないので安全
        if (ConfigurationSerializable.class.isAssignableFrom(type) && !Serializable.class.isAssignableFrom(type)) {
            return ObjectInputFilter.Status.ALLOWED;
        }
        return ObjectInputFilter.Status.REJECTED;
    };

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

    /** KnightsEditor (旧形式: BukkitObjectOutputStream + Base64) のアイテムを読み込む */
    @SuppressWarnings("deprecation")
    public static ItemStack decodeLegacyBase64(String data) throws Exception {
        if (data == null || data.isEmpty()) return null;
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(
                new ByteArrayInputStream(Base64.getMimeDecoder().decode(data)))) {
            in.setObjectInputFilter(LEGACY_FILTER);
            return in.readObject() instanceof ItemStack item ? item : null;
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
}
