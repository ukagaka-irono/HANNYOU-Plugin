package com.ukagaka.irono.hANNYOUPlugin.role;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.audit.AuditLogger;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.Map;

/**
 * 役職の装備・インベントリ・永続効果をプレイヤーに適用する。
 * 役職はスコアボードタグ（role id）で判定するが、付与した役職の識別番号と role id をプレイヤーデータにも記録しておき、
 * オフライン中に役職の名前が変わった・削除された・同じ role id で作り直された場合を参加時に見分ける。
 */
public final class RoleApplier implements Listener {
    private final HANNYOUPlugin plugin;
    /** 付与した役職の識別番号 */
    private final NamespacedKey roleUidKey;
    /** 付与したときの role id（付いているはずのタグ） */
    private final NamespacedKey roleIdKey;

    public RoleApplier(HANNYOUPlugin plugin) {
        this.plugin = plugin;
        this.roleUidKey = new NamespacedKey(plugin, "role_uid");
        this.roleIdKey = new NamespacedKey(plugin, "role_id");
    }

    /** 既存の役職タグを外して新しい役職を付与し、装備・インベントリ・効果を配布する */
    public void assign(Player player, Role role) {
        plugin.getTrialManager().end(player, false);
        Role previous = plugin.getRoleManager().findRole(player);
        removeRoleTags(player);
        player.addScoreboardTag(role.getId());
        remember(player, role);
        if (previous != null) clearEffects(player, previous.getEffects().keySet());
        plugin.getSpecManager().changeInventory(player, () -> applyItems(player, role));
        applyEffects(player, role);
    }

    private void removeRoleTags(Player player) {
        RoleManager roles = plugin.getRoleManager();
        for (String tag : player.getScoreboardTags().toArray(String[]::new)) {
            if (roles.exists(tag)) player.removeScoreboardTag(tag);
        }
    }

    private void remember(Player player, Role role) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(roleUidKey, PersistentDataType.STRING, role.getUid());
        pdc.set(roleIdKey, PersistentDataType.STRING, role.getId());
    }

    private void forget(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.remove(roleUidKey);
        pdc.remove(roleIdKey);
    }

    /** 役職を削除し、オンラインの保持者からタグと効果を外す（オフラインの保持者は参加時に外す） */
    public void deleteRole(CommandSender executor, Role role) {
        plugin.getRoleManager().delete(role.getId());
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.removeScoreboardTag(role.getId())) clearEffects(online, role.getEffects().keySet());
            if (role.getUid().equals(online.getPersistentDataContainer().get(roleUidKey, PersistentDataType.STRING))) {
                forget(online);
            }
        }
        plugin.getAuditLogger().log(executor, AuditLogger.Action.ROLE_DELETE, role.getId(),
                "表示名: " + role.getDisplayName());
    }

    /** role id を変更して保存し、オンラインの保持者のタグも付け替える（オフラインの保持者は参加時に付け替える） */
    public void renameRole(String oldId, Role role) {
        plugin.getRoleManager().rename(oldId, role);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.removeScoreboardTag(oldId)) {
                online.addScoreboardTag(role.getId());
                remember(online, role);
            }
        }
    }

    /** 役職の保存後、オンラインの保持者の効果を新しい内容に更新する（アイテムは配り直さない） */
    public void syncEffects(Role before, Role after) {
        for (Player holder : plugin.getRoleManager().onlineHolders(after.getId())) {
            if (plugin.getTrialManager().isTrying(holder)) continue;
            if (before != null) clearEffects(holder, before.getEffects().keySet());
            applyEffects(holder, after);
        }
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
    private static void clearEffects(Player player, Collection<PotionEffectType> types) {
        for (PotionEffectType type : types) {
            PotionEffect active = player.getPotionEffect(type);
            if (active != null && active.isInfinite()) player.removePotionEffect(type);
        }
    }

    // ─────────────────────────────────────────────
    // 参加時
    // ─────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        reconcile(player);

        Role role = plugin.getRoleManager().findRole(player);
        if (role == null) return;
        if (plugin.getPluginConfig().reapplyItemsOnJoin()) {
            plugin.getSpecManager().changeInventory(player, () -> applyItems(player, role));
        }
        applyEffects(player, role);
    }

    /** オフライン中に起きた役職の名前変更・削除・作り直しに合わせて、タグと効果を直す */
    private void reconcile(Player player) {
        RoleManager roles = plugin.getRoleManager();
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String uid = pdc.get(roleUidKey, PersistentDataType.STRING);
        String recordedId = pdc.get(roleIdKey, PersistentDataType.STRING);

        if (uid == null) {
            // 識別番号を記録する前に付与された人は、従来どおりタグで判断する
            for (String tag : player.getScoreboardTags().toArray(String[]::new)) {
                if (roles.exists(tag)) continue;
                DeletedRole deleted = roles.deletedRoleById(tag);
                if (deleted != null && player.removeScoreboardTag(tag)) clearEffects(player, deleted.effects().keySet());
            }
            Role legacy = roles.findRole(player);
            if (legacy != null) remember(player, legacy);
            return;
        }

        Role role = roles.findByUid(uid);
        if (role == null) {
            // 削除された（同じ role id で作り直されていても、別の役職なので付けない）
            DeletedRole deleted = roles.deletedRole(uid);
            if (recordedId != null && player.removeScoreboardTag(recordedId) && deleted != null) {
                clearEffects(player, deleted.effects().keySet());
            }
            forget(player);
        } else if (!role.getId().equals(recordedId)) {
            // 名前が変わった。タグが外されていなければ新しい名前に付け替える
            if (recordedId != null && player.removeScoreboardTag(recordedId)) {
                player.addScoreboardTag(role.getId());
                remember(player, role);
            } else {
                forget(player);
            }
        } else if (!player.getScoreboardTags().contains(role.getId())) {
            forget(player); // /tag などで外された
        }
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
