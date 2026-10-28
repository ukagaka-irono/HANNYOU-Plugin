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
 * 役職とYAMLの相互変換。knights-role.yml とエクスポートファイルで同じ形式を使う。
 * アイテムは Paper のバイナリ形式を Base64 にした文字列で持つ。
 */
public final class RoleYaml {
    /** 1 = KnightsEditor形式（Java直列化）、2 = Paperバイナリ形式 */
    public static final int FORMAT = 2;

    private RoleYaml() {}

    public static void write(ConfigurationSection section, Role role) {
        section.set("display_name", role.getDisplayName());
        section.set("icon", encode(role.getIcon()));
        Map<String, String> equipment = new LinkedHashMap<>();
        role.getEquipment().forEach((slot, item) -> equipment.put(slot.name(), encode(item)));
        section.set("equipment", equipment);
        writeItems(section, "inventory", role.getInventory());
        writeItems(section, "hotbar", role.getHotbar());
        section.set("effects", writeEffects(role.getEffects()));
    }

    public static Map<String, Integer> writeEffects(Map<PotionEffectType, Integer> effects) {
        Map<String, Integer> result = new LinkedHashMap<>();
        effects.forEach((type, level) -> result.put(ItemCodec.effectKey(type), level));
        return result;
    }

    private static void writeItems(ConfigurationSection section, String path, ItemStack[] items) {
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) section.set(path + "." + i, encode(items[i]));
        }
    }

    private static String encode(ItemStack item) {
        return ItemCodec.toBase64(ItemCodec.encode(item));
    }

    /** 読み込めないアイテムは警告を出して空にする */
    public static Role read(ConfigurationSection section, String id, int format, Logger logger) {
        Role role = new Role(id, section.getString("display_name", "インポートされた役職"));
        role.setIcon(decode(section.getString("icon"), format, id, logger));

        ConfigurationSection equipment = section.getConfigurationSection("equipment");
        if (equipment != null) {
            for (String slotName : equipment.getKeys(false)) {
                try {
                    role.setEquipment(Role.EquipmentSlot.valueOf(slotName),
                            decode(equipment.getString(slotName), format, id, logger));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        readItems(section.getConfigurationSection("inventory"), Role.INVENTORY_SIZE, format, id, logger,
                role::setInventoryItem);
        readItems(section.getConfigurationSection("hotbar"), Role.HOTBAR_SIZE, format, id, logger,
                role::setHotbarItem);
        role.getEffects().putAll(readEffects(section.getConfigurationSection("effects")));
        return role;
    }

    public static Map<PotionEffectType, Integer> readEffects(ConfigurationSection section) {
        Map<PotionEffectType, Integer> effects = new LinkedHashMap<>();
        if (section == null) return effects;
        for (String key : section.getKeys(false)) {
            PotionEffectType type = ItemCodec.effectType(key);
            int level = section.getInt(key);
            if (type != null && level > 0) effects.put(type, level);
        }
        return effects;
    }

    private interface IndexedSetter {
        void set(int index, ItemStack item);
    }

    private static void readItems(ConfigurationSection section, int size, int format, String id, Logger logger,
                                  IndexedSetter setter) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                int index = Integer.parseInt(key);
                if (index >= 0 && index < size) setter.set(index, decode(section.getString(key), format, id, logger));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static ItemStack decode(String data, int format, String roleId, Logger logger) {
        try {
            return format >= 2 ? ItemCodec.decode(ItemCodec.fromBase64(data)) : ItemCodec.decodeLegacyBase64(data);
        } catch (Exception e) {
            logger.log(Level.WARNING, "アイテムを読み込めませんでした (役職: " + roleId + ")", e);
            return null;
        }
    }
}
