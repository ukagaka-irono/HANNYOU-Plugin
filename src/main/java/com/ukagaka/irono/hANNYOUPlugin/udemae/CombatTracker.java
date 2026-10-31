package com.ukagaka.irono.hANNYOUPlugin.udemae;

import com.destroystokyo.paper.MaterialTags;
import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 試合中のキルとアシストを数えて、ウデマエのポイントにする。
 * 誰の手柄かはダメージの原因（DamageSource）で決める。飛び道具なら遠距離、そうでなければ近接。
 * スポーンエッグで出したモブは「兵士」として持ち主を記録し、兵士の手柄は持ち主のものにする。
 * 同じチームどうし、スペク中の人、試合中でない人は数えない。
 */
public final class CombatTracker implements Listener {
    /** スポーンエッグを使ってからモブが出るまでの距離の上限（ブロック） */
    private static final double EGG_SPAWN_DISTANCE = 8.0;
    /** これより多くの相手の記録が溜まったら、古い記録を捨てる（倒されずに消えたモブの分） */
    private static final int PRUNE_THRESHOLD = 256;

    private enum Kind { MELEE, RANGED, SOLDIER_MELEE, SOLDIER_RANGED }

    /** 手柄の持ち主（プレイヤー）と、どうやって攻撃したか */
    private record Credit(UUID player, Kind kind) {}

    private record Hit(Kind kind, int tick) {}

    private record EggUse(int tick, Location location) {}

    private final HANNYOUPlugin plugin;
    /** モブ兵士の持ち主の UUID */
    private final NamespacedKey ownerKey;
    /** 倒される側 → 攻撃したプレイヤー → 最後の攻撃 */
    private final Map<UUID, Map<UUID, Hit>> hits = new HashMap<>();
    /** スポーンエッグを使った直後のプレイヤー */
    private final Map<UUID, EggUse> eggUses = new HashMap<>();

    public CombatTracker(HANNYOUPlugin plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "soldier_owner");
    }

    // ─────────────────────────────────────────────
    // モブ兵士の持ち主
    // ─────────────────────────────────────────────

    // スポーンエッグを使った人を、モブが出るイベント（使った人が分からない）と結びつけるため、同じ tick の近くで使った人を探す

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        rememberEggUse(event.getPlayer(), event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        rememberEggUse(event.getPlayer(), event.getPlayer().getInventory().getItem(event.getHand()));
    }

    private void rememberEggUse(Player player, ItemStack item) {
        if (item == null || !MaterialTags.SPAWN_EGGS.isTagged(item)) return;
        eggUses.put(player.getUniqueId(), new EggUse(Bukkit.getCurrentTick(), player.getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG || eggUses.isEmpty()) return;
        Location spawned = event.getLocation();
        int tick = Bukkit.getCurrentTick();
        UUID owner = null;
        double nearest = EGG_SPAWN_DISTANCE * EGG_SPAWN_DISTANCE;
        for (Map.Entry<UUID, EggUse> entry : eggUses.entrySet()) {
            EggUse use = entry.getValue();
            if (use.tick() != tick || !use.location().getWorld().equals(spawned.getWorld())) continue;
            double distance = use.location().distanceSquared(spawned);
            if (distance <= nearest) {
                nearest = distance;
                owner = entry.getKey();
            }
        }
        eggUses.values().removeIf(use -> use.tick() != tick);
        if (owner != null) {
            event.getEntity().getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
        }
    }

    private UUID ownerOf(Entity entity) {
        String owner = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (owner == null) return null;
        try {
            return UUID.fromString(owner);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────
    // 攻撃の記録
    // ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!isTarget(victim)) return;
        Credit credit = credit(event.getDamageSource());
        if (credit == null || !isEligible(credit.player(), victim)) return;

        if (hits.size() > PRUNE_THRESHOLD) prune();
        hits.computeIfAbsent(victim.getUniqueId(), id -> new HashMap<>())
                .put(credit.player(), new Hit(credit.kind(), Bukkit.getCurrentTick()));
    }

    /** ウデマエの対象になる相手（プレイヤーかモブ兵士） */
    private boolean isTarget(Entity entity) {
        return entity instanceof Player || ownerOf(entity) != null;
    }

    /** ダメージの原因から、手柄の持ち主を決める（プレイヤーかモブ兵士の持ち主。それ以外は null） */
    private Credit credit(DamageSource source) {
        Entity causing = source.getCausingEntity();
        if (causing == null) return null;
        boolean ranged = source.getDirectEntity() instanceof Projectile;
        if (causing instanceof Player player) {
            return new Credit(player.getUniqueId(), ranged ? Kind.RANGED : Kind.MELEE);
        }
        UUID owner = ownerOf(causing);
        return owner == null ? null : new Credit(owner, ranged ? Kind.SOLDIER_RANGED : Kind.SOLDIER_MELEE);
    }

    /** 手柄の持ち主がオンラインで試合中、相手が自分でも味方でもないか */
    private boolean isEligible(UUID creditId, Entity victim) {
        Player attacker = Bukkit.getPlayer(creditId);
        if (attacker == null || !plugin.getMatchWatcher().isCounted(attacker)) return false;
        if (victim instanceof Player player) {
            if (player.getUniqueId().equals(creditId) || !plugin.getMatchWatcher().isCounted(player)) return false;
            return !isAlly(attacker, player.getName());
        }
        UUID owner = ownerOf(victim);
        if (owner == null || owner.equals(creditId)) return false;
        String ownerName = Bukkit.getOfflinePlayer(owner).getName();
        return ownerName == null || !isAlly(attacker, ownerName);
    }

    private static boolean isAlly(Player attacker, String otherName) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getEntryTeam(attacker.getName());
        return team != null && team.equals(scoreboard.getEntryTeam(otherName));
    }

    private void prune() {
        int oldest = Bukkit.getCurrentTick() - assistTicks();
        hits.values().forEach(map -> map.values().removeIf(hit -> hit.tick() < oldest));
        hits.values().removeIf(Map::isEmpty);
    }

    private int assistTicks() {
        return plugin.getPluginConfig().udemae().assistSeconds() * 20;
    }

    // ─────────────────────────────────────────────
    // 倒したとき
    // ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        if (plugin.getMatchWatcher().isCounted(victim)) {
            plugin.getUdemaeManager().reward(victim, UdemaeAction.DEATH);
        }
        onKilled(victim, event.getDamageSource());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return; // onPlayerDeath で数える
        if (ownerOf(event.getEntity()) == null) return;
        onKilled(event.getEntity(), event.getDamageSource());
    }

    private void onKilled(Entity victim, DamageSource source) {
        Map<UUID, Hit> victimHits = hits.remove(victim.getUniqueId());
        if (victimHits == null) victimHits = Map.of();
        int oldest = Bukkit.getCurrentTick() - assistTicks();
        boolean soldier = !(victim instanceof Player);

        // とどめの原因が分からない（落下や炎など）ときは、最後に攻撃した人のキルにする
        Credit killer = credit(source);
        if (killer == null) {
            killer = victimHits.entrySet().stream()
                    .filter(e -> e.getValue().tick() >= oldest)
                    .max(Map.Entry.comparingByValue((a, b) -> Integer.compare(a.tick(), b.tick())))
                    .map(e -> new Credit(e.getKey(), e.getValue().kind()))
                    .orElse(null);
        }
        if (killer != null && isEligible(killer.player(), victim)) {
            reward(killer.player(), soldier ? UdemaeAction.MOB_SOLDIER_KILL : killAction(killer.kind()));
        }

        for (Map.Entry<UUID, Hit> entry : victimHits.entrySet()) {
            UUID id = entry.getKey();
            if (killer != null && id.equals(killer.player())) continue;
            if (entry.getValue().tick() < oldest || !isEligible(id, victim)) continue;
            reward(id, soldier ? UdemaeAction.MOB_SOLDIER_ASSIST : assistAction(entry.getValue().kind()));
        }
    }

    private void reward(UUID id, UdemaeAction action) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) plugin.getUdemaeManager().reward(player, action);
    }

    private static UdemaeAction killAction(Kind kind) {
        return switch (kind) {
            case MELEE -> UdemaeAction.MELEE_KILL;
            case RANGED -> UdemaeAction.RANGED_KILL;
            case SOLDIER_MELEE -> UdemaeAction.SOLDIER_MELEE_KILL;
            case SOLDIER_RANGED -> UdemaeAction.SOLDIER_RANGED_KILL;
        };
    }

    private static UdemaeAction assistAction(Kind kind) {
        return switch (kind) {
            case MELEE -> UdemaeAction.MELEE_ASSIST;
            case RANGED -> UdemaeAction.RANGED_ASSIST;
            case SOLDIER_MELEE -> UdemaeAction.SOLDIER_MELEE_ASSIST;
            case SOLDIER_RANGED -> UdemaeAction.SOLDIER_RANGED_ASSIST;
        };
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        hits.remove(id);
        eggUses.remove(id);
    }
}
