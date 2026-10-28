package com.ukagaka.irono.hANNYOUPlugin.role;

import com.ukagaka.irono.hANNYOUPlugin.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class Role {
    public static final int INVENTORY_SIZE = 27;
    public static final int HOTBAR_SIZE = 9;
    /** 効果レベルの上限（エディタで選べる I〜III） */
    public static final int MAX_EFFECT_LEVEL = 3;

    public enum EquipmentSlot {
        HELMET, CHESTPLATE, LEGGINGS, BOOTS, OFFHAND
    }

    /**
     * 表には出ない識別番号。role id を変えても変わらず、同じ role id で作り直すと別の番号になる。
     * プレイヤー側にも記録して、オフライン中の名前変更や削除・作り直しを見分けるのに使う。
     */
    private String uid = UUID.randomUUID().toString();
    private String id;
    private String displayName;
    /** 色を除いた表示名（検索用。表示名が変わったら null にする） */
    private String plainName;
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

    /** 全アイテムを複製した、別の役職としてのコピーを作る（識別番号は新しくなる） */
    public Role copy(String newId, String newDisplayName) {
        Role copy = new Role(newId, newDisplayName);
        copy.icon = icon.clone();
        equipment.forEach((slot, item) -> copy.equipment.put(slot, item.clone()));
        for (int i = 0; i < INVENTORY_SIZE; i++) copy.inventory[i] = cloneOrNull(inventory[i]);
        for (int i = 0; i < HOTBAR_SIZE; i++) copy.hotbar[i] = cloneOrNull(hotbar[i]);
        copy.effects.putAll(effects);
        return copy;
    }

    /** 編集用の作業コピー（同じ役職なので識別番号も同じ） */
    public Role copy() {
        Role copy = copy(id, displayName);
        copy.uid = uid;
        return copy;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.plainName = null;
    }

    /** 色を除いた表示名（検索で毎回変換しないよう覚えておく） */
    public String getPlainName() {
        if (plainName == null) plainName = Text.strip(displayName);
        return plainName;
    }

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

    public static boolean isEmpty(ItemStack item) {
        return item == null || item.isEmpty();
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }
}
