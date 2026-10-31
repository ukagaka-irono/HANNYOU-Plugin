package com.ukagaka.irono.hANNYOUPlugin.udemae;

import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * プレイヤーごとのウデマエ（合計ポイント）を持ち、ランクを計算する。
 * 合計ポイントはスコアボードのスコア（既定 udemae）にも書き出し、RandomTeam などほかのプラグインやコマンドから読めるようにする。
 * 保存は変更のたびではなく、数秒ごとにまとめて行う。
 */
public final class UdemaeManager implements Listener {
    private static final long SAVE_INTERVAL_TICKS = 100L;

    /** ランクと、そのランクの中のポイント。top はいちばん上のランク（ポイントに上限が無い） */
    public record Rank(String name, int points, int pointsPerRank, boolean top) {
        public String display() {
            return top ? name + " " + points : name + " " + points + "/" + pointsPerRank;
        }
    }

    private final HANNYOUPlugin plugin;
    private final UdemaeStorage storage;
    private final Map<UUID, UdemaeStorage.Entry> entries = new HashMap<>();
    private boolean dirty;
    private BukkitTask task;

    public UdemaeManager(HANNYOUPlugin plugin, UdemaeStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void start() {
        entries.putAll(storage.load());
        // load: STARTUP なので、起動時はまだワールドもスコアボードも無い。サーバーが動き始めてから書き出す
        Bukkit.getScheduler().runTask(plugin, this::exportOnline);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::saveIfDirty, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
    }

    public void stop() {
        if (task != null) task.cancel();
        saveIfDirty();
        storage.close();
    }

    /** config.yml を読み直したとき（書き出し先のスコアの名前が変わっていることがある） */
    public void reload() {
        exportOnline();
    }

    private UdemaeSettings settings() {
        return plugin.getPluginConfig().udemae();
    }

    public int total(UUID id) {
        UdemaeStorage.Entry entry = entries.get(id);
        return entry == null ? 0 : entry.total();
    }

    public Rank rank(int total) {
        UdemaeSettings settings = settings();
        List<String> ranks = settings.ranks();
        int perRank = settings.pointsPerRank();
        int index = Math.min(total / perRank, ranks.size() - 1);
        return new Rank(ranks.get(index), total - index * perRank, perRank, index == ranks.size() - 1);
    }

    /** ランクとその中のポイントから、合計ポイントを返す（無いランクなら -1） */
    public int totalOf(String rankName, int points) {
        int index = settings().ranks().indexOf(rankName);
        return index < 0 ? -1 : index * settings().pointsPerRank() + points;
    }

    public List<String> rankNames() {
        return settings().ranks();
    }

    /** 行動に応じてポイントを増減する（設定が 0 なら何もしない） */
    public void reward(Player player, UdemaeAction action) {
        int points = settings().points(action);
        if (points == 0) return;
        set(player.getUniqueId(), player.getName(), total(player.getUniqueId()) + points);
    }

    /** 合計ポイントを書き換える（0 未満にはならない） */
    public void set(UUID id, String name, int total) {
        int clamped = Math.max(0, total);
        entries.put(id, new UdemaeStorage.Entry(name, clamped));
        dirty = true;
        export(name, clamped);
    }

    /** 名前から探す（オンラインのプレイヤーを優先し、なければ保存データから。大文字小文字は区別しない） */
    public UUID findByName(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        for (Map.Entry<UUID, UdemaeStorage.Entry> entry : entries.entrySet()) {
            if (entry.getValue().name().equalsIgnoreCase(name)) return entry.getKey();
        }
        return null;
    }

    /** 保存データにある名前（オフラインの人を含む） */
    public List<String> knownNames() {
        return entries.values().stream().map(UdemaeStorage.Entry::name).filter(n -> !n.isEmpty()).sorted().toList();
    }

    /** 保存データにある名前（なければ空文字） */
    public String nameOf(UUID id) {
        UdemaeStorage.Entry entry = entries.get(id);
        return entry == null ? "" : entry.name();
    }

    private void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        storage.save(Map.copyOf(entries));
    }

    // ─────────────────────────────────────────────
    // スコアボードへの書き出し
    // ─────────────────────────────────────────────

    private void exportOnline() {
        if (exportObjective() == null) return; // だれもいなくても、スコアだけは先に作っておく
        for (Player player : Bukkit.getOnlinePlayers()) {
            export(player.getName(), total(player.getUniqueId()));
        }
    }

    private void export(String name, int total) {
        Objective objective = exportObjective();
        if (objective != null && !name.isEmpty()) objective.getScore(name).setScore(total);
    }

    private Objective exportObjective() {
        String name = settings().exportObjective();
        if (name == null || name.isBlank()) return null;
        if (Bukkit.getScoreboardManager() == null) return null; // ワールドの読み込み前
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective objective = scoreboard.getObjective(name);
        if (objective != null) return objective;
        try {
            return scoreboard.registerNewObjective(name, Criteria.DUMMY, Component.text("ウデマエ"));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("udemae.export-objective のスコアを作れませんでした: " + name);
            return null;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UdemaeStorage.Entry entry = entries.get(player.getUniqueId());
        if (entry != null && !entry.name().equals(player.getName())) {
            // 名前が変わった
            entries.put(player.getUniqueId(), new UdemaeStorage.Entry(player.getName(), entry.total()));
            dirty = true;
        }
        export(player.getName(), total(player.getUniqueId()));
    }
}
