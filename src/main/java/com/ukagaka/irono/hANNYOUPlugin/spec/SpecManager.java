package com.ukagaka.irono.hANNYOUPlugin.spec;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.util.Text;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * スペクモード: クリエイティブのまま、スペク中の人同士以外からは見えず、
 * 他の物体とぶつからず、ゲームに影響する操作ができない状態。
 *
 * プレイヤーを非表示にするAPI(hidePlayer)はタブリストからも消してしまうため使わず、
 * 体は透明化の効果、装備と手持ちは見る側にだけ空気を送ることで隠し、名前はチーム設定で隠す。
 * スペク中の人同士はチームの「味方の透明を見る」設定で半透明に見える。
 * 状態はプレイヤーデータ(PDC)に保存するので、ログアウトや再起動をまたいで続く。
 */
public final class SpecManager implements Listener {
    private static final String TEAM_NAME = "hannyou_spec";
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final Map<EquipmentSlot, ItemStack> EMPTY_EQUIPMENT = new EnumMap<>(EquipmentSlot.class);

    static {
        for (EquipmentSlot slot : SLOTS) EMPTY_EQUIPMENT.put(slot, ItemStack.empty());
    }

    private final HANNYOUPlugin plugin;
    /** スペク開始前のゲームモード（このキーがあればスペク中） */
    private final NamespacedKey gameModeKey;
    /** スペク開始前に入っていたチーム（なければ空文字） */
    private final NamespacedKey teamKey;
    private final Set<UUID> pendingMask = new HashSet<>();
    private BukkitTask task;

    public SpecManager(HANNYOUPlugin plugin) {
        this.plugin = plugin;
        this.gameModeKey = new NamespacedKey(plugin, "spec_gamemode");
        this.teamKey = new NamespacedKey(plugin, "spec_team");
    }

    /** 毎秒、装備の隠し直し（取りこぼし対策）とアクションバー表示を行う。オンラインのスペク中の人に状態を再適用する */
    public void start() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (isSpec(online)) applyState(online);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!isSpec(online)) continue;
                hideEquipment(online);
                online.sendActionBar(Text.of("§7[スペク中] ほかのプレイヤーからは見えません"));
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public boolean isSpec(Player player) {
        return player.getPersistentDataContainer().has(gameModeKey, PersistentDataType.STRING);
    }

    /** @return スペクモードにしたら true（すでにスペク中なら false） */
    public boolean enable(Player player) {
        if (isSpec(player)) return false;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(gameModeKey, PersistentDataType.STRING, player.getGameMode().name());
        Team current = scoreboard().getEntryTeam(player.getName());
        pdc.set(teamKey, PersistentDataType.STRING,
                current == null || current.getName().equals(TEAM_NAME) ? "" : current.getName());

        applyState(player);
        player.sendMessage(Text.of("§7スペクモードになりました（ほかのプレイヤーからは見えません）"));
        return true;
    }

    /** @return スペクモードを解除したら true（スペク中でなければ false） */
    public boolean disable(Player player) {
        if (!isSpec(player)) return false;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String gameMode = pdc.get(gameModeKey, PersistentDataType.STRING);
        String previousTeam = pdc.get(teamKey, PersistentDataType.STRING);
        pdc.remove(gameModeKey); // 先に外して、ゲームモード変更の禁止に引っかからないようにする
        pdc.remove(teamKey);

        player.setGameMode(parseGameMode(gameMode));
        player.setCollidable(true);
        PotionEffect invisibility = player.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (invisibility != null && invisibility.isInfinite()) player.removePotionEffect(PotionEffectType.INVISIBILITY);

        Scoreboard scoreboard = scoreboard();
        Team specTeam = scoreboard.getTeam(TEAM_NAME);
        if (specTeam != null) specTeam.removeEntry(player.getName());
        Team restore = previousTeam == null || previousTeam.isEmpty() ? null : scoreboard.getTeam(previousTeam);
        if (restore != null) restore.addEntry(player.getName());

        // 自分の装備を見えるように戻し、自分からはほかのスペク中の人の装備が見えないようにする
        for (Player viewer : player.getTrackedBy()) viewer.sendEquipmentChange(player, realEquipment(player));
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (isSpec(other) && other.getTrackedBy().contains(player)) {
                player.sendEquipmentChange(other, EMPTY_EQUIPMENT);
            }
        }

        player.sendActionBar(Text.of(""));
        player.sendMessage(Text.of("§aスペクモードを解除しました"));
        return true;
    }

    private void applyState(Player player) {
        if (player.getGameMode() != GameMode.CREATIVE) player.setGameMode(GameMode.CREATIVE);
        player.setCollidable(false);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                PotionEffect.INFINITE_DURATION, 0, true, false, false));
        specTeam().addEntry(player.getName());

        hideEquipment(player);
        // ほかのスペク中の人の装備は、自分には見えるようにする
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != player && isSpec(other) && other.getTrackedBy().contains(player)) {
                player.sendEquipmentChange(other, realEquipment(other));
            }
        }
    }

    // ─────────────────────────────────────────────
    // チーム
    // ─────────────────────────────────────────────

    private static Scoreboard scoreboard() {
        return Bukkit.getScoreboardManager().getMainScoreboard();
    }

    private static Team specTeam() {
        Scoreboard scoreboard = scoreboard();
        Team team = scoreboard.getTeam(TEAM_NAME);
        if (team == null) team = scoreboard.registerNewTeam(TEAM_NAME);
        team.setCanSeeFriendlyInvisibles(true);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        return team;
    }

    private static GameMode parseGameMode(String name) {
        try {
            return name == null ? GameMode.SURVIVAL : GameMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return GameMode.SURVIVAL;
        }
    }

    // ─────────────────────────────────────────────
    // 装備を隠す
    // ─────────────────────────────────────────────

    /** スペク中でない、このプレイヤーを見ている全員に「何も装備していない」と送る */
    private void hideEquipment(Player spec) {
        for (Player viewer : spec.getTrackedBy()) {
            if (!isSpec(viewer)) viewer.sendEquipmentChange(spec, EMPTY_EQUIPMENT);
        }
    }

    private static Map<EquipmentSlot, ItemStack> realEquipment(Player player) {
        Map<EquipmentSlot, ItemStack> items = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : SLOTS) items.put(slot, player.getEquipment().getItem(slot));
        return items;
    }

    /** 装備の変更はイベントの後にサーバーから送られるので、1tick後に隠し直す（同じtick内の重複はまとめる） */
    private void hideEquipmentLater(Player spec) {
        if (!pendingMask.add(spec.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingMask.remove(spec.getUniqueId());
            if (spec.isOnline() && isSpec(spec)) hideEquipment(spec);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTrack(PlayerTrackEntityEvent event) {
        if (!(event.getEntity() instanceof Player spec) || !isSpec(spec) || isSpec(event.getPlayer())) return;
        Player viewer = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (spec.isOnline() && viewer.isOnline() && isSpec(spec) && !isSpec(viewer)) {
                viewer.sendEquipmentChange(spec, EMPTY_EQUIPMENT);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        if (isSpec(event.getPlayer())) hideEquipmentLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeldItem(PlayerItemHeldEvent event) {
        if (isSpec(event.getPlayer())) hideEquipmentLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isSpec(event.getPlayer())) hideEquipmentLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isSpec(player)) hideEquipmentLater(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        if (isSpec(event.getPlayer())) hideEquipmentLater(event.getPlayer());
    }

    // ─────────────────────────────────────────────
    // 状態の維持
    // ─────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isSpec(event.getPlayer())) applyState(event.getPlayer());
    }

    /** スペク中はクリエイティブ以外にできないようにする（解除は unspec で） */
    @EventHandler(ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (isSpec(event.getPlayer()) && event.getNewGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Text.of("§cスペク中はゲームモードを変更できません（/hannyou knights unspec で解除）"));
        }
    }
}
