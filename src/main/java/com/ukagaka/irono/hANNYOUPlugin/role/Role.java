package com.ukagaka.irono.hANNYOUPlugin.role;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Role {
    public static final int INVENTORY_SIZE = 27;
    public static final int HOTBAR_SIZE = 9;

    public enum EquipmentSlot {
        HELMET, CHESTPLATE, LEGGINGS, BOOTS, OFFHAND
    }

    private String id;
    private String displayName;
    private ItemStack icon;
    private final EnumMap<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
    private final ItemStack[] inventory = new ItemStack[INVENTORY_SIZE];
    private final ItemStack[] hotbar = new ItemStack[HOTBAR_SIZE];
    private final Map<PotionEffectType, Integer> effects = new LinkedHashMap<>();

    public Role(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
        this.icon = new ItemStack(Material.STONE);
    }

    /** 全アイテムを複製したディープコピーを作る（編集用の作業コピーや役職の複製に使う） */
    public Role copy(String newId, String newDisplayName) {
        Role copy = new Role(newId, newDisplayName);
        copy.icon = icon.clone();
        equipment.forEach((slot, item) -> copy.equipment.put(slot, item.clone()));
        for (int i = 0; i < INVENTORY_SIZE; i++) copy.inventory[i] = cloneOrNull(inventory[i]);
        for (int i = 0; i < HOTBAR_SIZE; i++) copy.hotbar[i] = cloneOrNull(hotbar[i]);
        copy.effects.putAll(effects);
        return copy;
    }

    public Role copy() {
        return copy(id, displayName);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public ItemStack getIcon() { return icon; }
    public void setIcon(ItemStack icon) {
        this.icon = isEmpty(icon) ? new ItemStack(Material.STONE) : icon;
    }

    public ItemStack getEquipment(EquipmentSlot slot) { return equipment.get(slot); }
    public Map<EquipmentSlot, ItemStack> getEquipment() { return equipment; }
    public void setEquipment(EquipmentSlot slot, ItemStack item) {
        if (isEmpty(item)) equipment.remove(slot);
        else equipment.put(slot, item);
    }

    public ItemStack[] getInventory() { return inventory; }
    public void setInventoryItem(int index, ItemStack item) { inventory[index] = isEmpty(item) ? null : item; }

    public ItemStack[] getHotbar() { return hotbar; }
    public void setHotbarItem(int index, ItemStack item) { hotbar[index] = isEmpty(item) ? null : item; }

    public Map<PotionEffectType, Integer> getEffects() { return effects; }
    public int getEffectLevel(PotionEffectType type) { return effects.getOrDefault(type, 0); }
    public void setEffectLevel(PotionEffectType type, int level) {
        if (level <= 0) effects.remove(type);
        else effects.put(type, level);
    }

    /** 内容がすべて同じか（未保存の変更があるかの判定に使う） */
    public boolean sameAs(Role other) {
        return other != null
                && id.equals(other.id)
                && displayName.equals(other.displayName)
                && icon.equals(other.icon)
                && equipment.equals(other.equipment)
                && Arrays.equals(inventory, other.inventory)
                && Arrays.equals(hotbar, other.hotbar)
                && effects.equals(other.effects);
    }

    public static boolean isEmpty(ItemStack item) {
        return item == null || item.isEmpty();
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }
}
