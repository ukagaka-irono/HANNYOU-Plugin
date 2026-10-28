package com.ukagaka.irono.hANNYOUPlugin.spec;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;

/**
 * スペク中のプレイヤーがゲームに影響を与えないようにする。
 * ブロック操作・アイテムの出し入れ・攻撃・乗り物などを禁止し、
 * 飛び道具や感圧板などに対しても「そこにいない」ように扱う。
 * クリエイティブでアイテムを新しく手に入れることもできないようにする（ほかのプラグインで売る・渡すなどの抜け道を防ぐ）。
 */
public final class SpecRestrictions implements Listener {
    private final SpecManager spec;

    public SpecRestrictions(SpecManager spec) {
        this.spec = spec;
    }

    private boolean isSpec(Entity entity) {
        return entity instanceof Player player && spec.isSpec(player);
    }

    /** 直接、または飛び道具を撃ったのがスペク中のプレイヤーか */
    private boolean isSpecSource(Entity entity) {
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return isSpec(shooter);
        }
        return isSpec(entity);
    }

    private void cancelIf(boolean condition, Cancellable event) {
        if (condition) event.setCancelled(true);
    }

    // ─── ブロック ───

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        cancelIf(isSpec(event.getPlayer()), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        cancelIf(isSpec(event.getPlayer()), event);
    }

    /** 右クリック・左クリック・感圧板やトリップワイヤーなどの踏みつけ、アイテムの使用をすべて止める */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        cancelIf(isSpec(event.getPlayer()), event);
    }

    /** スカルクセンサーなどに振動を伝えない */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGameEvent(BlockReceiveGameEvent event) {
        cancelIf(event.getEntity() != null && isSpec(event.getEntity()), event);
    }

    // ─── アイテム ───

    /**
     * クリエイティブインベントリでの操作（好きなアイテムを取り出せる）と、中クリックでの複製を止める。
     * InventoryCreativeEvent も InventoryClickEvent として届く。
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isSpec(event.getWhoClicked())) return;
        cancelIf(event instanceof InventoryCreativeEvent || event.getAction() == InventoryAction.CLONE_STACK, event);
    }

    /** ブロックやエンティティを中クリックしてアイテムを手に入れる操作 */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickItem(PlayerPickItemEvent event) {
        cancelIf(isSpec(event.getPlayer()), event);
    }

    /** 万一死んでも持ち物を地面に落とさない（解除時に、保存してある持ち物へ戻す） */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (!isSpec(event.getPlayer())) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        cancelIf(isSpec(event.getPlayer()), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        cancelIf(isSpec(event.getEntity()), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickupArrow(PlayerPickupArrowEvent event) {
        cancelIf(isSpec(event.getPlayer()), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickupExperience(PlayerPickupExperienceEvent event) {
        cancelIf(isSpec(event.getPlayer()), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        cancelIf(isSpec(event.getPlayer()), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        cancelIf(isSpecSource(event.getEntity()), event);
    }

    // ─── エンティティ ───

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        cancelIf(isSpec(event.getPlayer()), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        cancelIf(isSpec(event.getPlayer()), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        cancelIf(isSpec(event.getPlayer()), event);
    }

    /** スペク中の人は攻撃できず、攻撃も受けない */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        cancelIf(isSpecSource(event.getDamager()), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        cancelIf(isSpec(event.getEntity()), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        cancelIf(event.getRemover() != null && isSpecSource(event.getRemover()), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        cancelIf(event.getTarget() != null && isSpec(event.getTarget()), event);
    }

    /** 飛び道具はスペク中の人をすり抜ける */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        cancelIf(event.getHitEntity() != null && isSpec(event.getHitEntity()), event);
    }

    // ─── 乗り物 ───

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        cancelIf(isSpec(event.getEntered()), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        cancelIf(event.getAttacker() != null && isSpecSource(event.getAttacker()), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleCollision(VehicleEntityCollisionEvent event) {
        cancelIf(isSpec(event.getEntity()), event);
    }
}
