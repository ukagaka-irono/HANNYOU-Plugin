package com.ukagaka.irono.hANNYOUPlugin.storage;

import com.ukagaka.irono.hANNYOUPlugin.role.Role;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 役職と knights-role.yml の相互変換。
 * アイテムは Paper のバイナリ形式を Base64 にした文字列で持つ。
 */
public final class RoleYaml {
    /** knights-role.yml の形式のバージョン（2 = Paperバイナリ形式） */
    public static final int FORMAT = 2;

    private RoleYaml() {}

    /** 役職を YAML に書く内容に変換する（アイテムの変換が重いので、結果は RoleStorage で使い回す） */
    public static Map<String, Object> toMap(Role role) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("uid", role.getUid());
        map.put("display_name", role.getDisplayName());
        map.put("icon", encode(role.getIcon()));
        Map<String, String> equipment = new LinkedHashMap<>();
        role.getEquipment().forEach((slot, item) -> equipment.put(slot.name(), encode(item)));
        map.put("equipment", equipment);
        map.put("inventory", encodeItems(role.getInventory()));
        map.put("hotbar", encodeItems(role.getHotbar()));
        map.put("effects", writeEffects(role.getEffects()));
        return map;
    }

    public static Map<String, Integer> writeEffects(Map<PotionEffectType, Integer> effects) {
        Map<String, Integer> result = new LinkedHashMap<>();
        effects.forEach((type, level) -> result.put(ItemCodec.effectKey(type), level));
        return result;
    }

    private static Map<String, String> encodeItems(ItemStack[] items) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) result.put(String.valueOf(i), encode(items[i]));
        }
        return result;
    }

    private static String encode(ItemStack item) {
        return ItemCodec.toBase64(ItemCodec.encode(item));
    }

    /** 読み込めないアイテムは警告を出して空にする */
    public static Role read(ConfigurationSection section, String id, Logger logger) {
        // 改行などの制御文字はログやチャットの表示を崩すので取り除く
        String displayName = section.getString("display_name", "").replaceAll("\\p{Cntrl}", "");
        Role role = new Role(id, displayName.isBlank() ? "インポートされた役職" : displayName);
        String uid = section.getString("uid");
        if (uid != null && !uid.isBlank()) role.setUid(uid);
        role.setIcon(decode(section.getString("icon"), id, logger));

        ConfigurationSection equipment = section.getConfigurationSection("equipment");
        if (equipment != null) {
            for (String slotName : equipment.getKeys(false)) {
                try {
                    role.setEquipment(Role.EquipmentSlot.valueOf(slotName),
                            decode(equipment.getString(slotName), id, logger));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        readItems(section.getConfigurationSection("inventory"), Role.INVENTORY_SIZE, id, logger,
                role::setInventoryItem);
        readItems(section.getConfigurationSection("hotbar"), Role.HOTBAR_SIZE, id, logger,
                role::setHotbarItem);
        role.getEffects().putAll(readEffects(section.getConfigurationSection("effects")));
        return role;
    }

    public static Map<PotionEffectType, Integer> readEffects(ConfigurationSection section) {
        Map<PotionEffectType, Integer> effects = new LinkedHashMap<>();
        if (section == null) return effects;
        for (String key : section.getKeys(false)) {
            PotionEffectType type = ItemCodec.effectType(key);
            int level = Math.min(section.getInt(key), Role.MAX_EFFECT_LEVEL);
            if (type != null && level > 0) effects.put(type, level);
        }
        return effects;
    }

    private interface IndexedSetter {
        void set(int index, ItemStack item);
    }

    private static void readItems(ConfigurationSection section, int size, String id, Logger logger,
                                  IndexedSetter setter) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                int index = Integer.parseInt(key);
                if (index >= 0 && index < size) setter.set(index, decode(section.getString(key), id, logger));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static ItemStack decode(String data, String roleId, Logger logger) {
        try {
            return ItemCodec.decode(ItemCodec.fromBase64(data));
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "アイテムを読み込めませんでした (役職: " + roleId + ")", e);
            return null;
        }
    }
}
