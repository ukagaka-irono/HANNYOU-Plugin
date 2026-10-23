package com.ukagaka.irono.hANNYOUPlugin.role;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;

/** 役職の装備・インベントリ・永続効果をプレイヤーに適用する */
public final class RoleApplier implements Listener {
    private final HANNYOUPlugin plugin;

    public RoleApplier(HANNYOUPlugin plugin) {
        this.plugin = plugin;
    }

    /** 既存の役職タグを外して新しい役職を付与し、装備・インベントリ・効果を配布する */
    public void assign(Player player, Role role) {
        RoleManager roles = plugin.getRoleManager();
        Role previous = roles.findRole(player);
        for (String tag : player.getScoreboardTags().toArray(String[]::new)) {
            if (roles.exists(tag)) player.removeScoreboardTag(tag);
        }
        player.addScoreboardTag(role.getId());
        if (previous != null) clearEffects(player, previous);
        applyItems(player, role);
        applyEffects(player, role);
    }

    public void applyItems(Player player, Role role) {
        PlayerInventory inv = player.getInventory();
        ItemStack helmet = role.getEquipment(Role.EquipmentSlot.HELMET);
        ItemStack chest = role.getEquipment(Role.EquipmentSlot.CHESTPLATE);
        ItemStack legs = role.getEquipment(Role.EquipmentSlot.LEGGINGS);
        ItemStack boots = role.getEquipment(Role.EquipmentSlot.BOOTS);
        ItemStack offhand = role.getEquipment(Role.EquipmentSlot.OFFHAND);
        if (helmet != null) inv.setHelmet(helmet.clone());
        if (chest != null) inv.setChestplate(chest.clone());
        if (legs != null) inv.setLeggings(legs.clone());
        if (boots != null) inv.setBoots(boots.clone());
        if (offhand != null) inv.setItemInOffHand(offhand.clone());

        // メインインベントリ36枠を一括で差し替える（1枠ずつのsetItemより送信パケットが少ない）
        ItemStack[] storage = new ItemStack[36];
        ItemStack[] hotbar = role.getHotbar();
        ItemStack[] main = role.getInventory();
        for (int i = 0; i < Role.HOTBAR_SIZE; i++) {
            if (hotbar[i] != null) storage[i] = hotbar[i].clone();
        }
        for (int i = 0; i < Role.INVENTORY_SIZE; i++) {
            if (main[i] != null) storage[i + Role.HOTBAR_SIZE] = main[i].clone();
        }
        inv.setStorageContents(storage);
    }

    /** 永続効果を無限時間で付与する（同種の効果は一度外してレベルを確実に揃える） */
    public void applyEffects(Player player, Role role) {
        for (Map.Entry<PotionEffectType, Integer> entry : role.getEffects().entrySet()) {
            player.removePotionEffect(entry.getKey());
            player.addPotionEffect(new PotionEffect(entry.getKey(), PotionEffect.INFINITE_DURATION,
                    entry.getValue() - 1, true, true));
        }
    }

    /** 役職由来（無限時間）の効果だけを外す */
    private void clearEffects(Player player, Role role) {
        for (PotionEffectType type : role.getEffects().keySet()) {
            PotionEffect active = player.getPotionEffect(type);
            if (active != null && active.isInfinite()) player.removePotionEffect(type);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Role role = plugin.getRoleManager().findRole(player);
        if (role == null) return;
        if (plugin.getPluginConfig().reapplyItemsOnJoin()) applyItems(player, role);
        applyEffects(player, role);
    }

    @EventHandler
    public void onPostRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        Role role = plugin.getRoleManager().findRole(player);
        if (role != null) applyEffects(player, role);
    }

    /** 牛乳・不死のトーテムで役職の永続効果が消えないようにする（消してから付け直すより軽く、ちらつかない） */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEffectCleared(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getAction() != EntityPotionEffectEvent.Action.CLEARED) return;
        if (event.getCause() != EntityPotionEffectEvent.Cause.MILK
                && event.getCause() != EntityPotionEffectEvent.Cause.TOTEM) return;

        PotionEffect old = event.getOldEffect();
        if (old == null || !old.isInfinite()) return;
        Role role = plugin.getRoleManager().findRole(player);
        if (role != null && role.getEffectLevel(old.getType()) == old.getAmplifier() + 1) {
            event.setCancelled(true);
        }
    }
}
