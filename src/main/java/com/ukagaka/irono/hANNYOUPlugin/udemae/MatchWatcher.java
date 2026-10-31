package com.ukagaka.irono.hANNYOUPlugin.udemae;

import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 試合中かどうかと勝ち負けを、スコアボードから読む（試合の進行はデータパックなどが行う）。
 * 勝ち負けは、勝ち（または負け）のスコアが 0 から 0 以外に変わったときに一度だけ数える。
 * 占領中かもデータパックのスコアと拠点の座標で見て、占領地に決まった秒数いるごとにポイントを入れる。
 */
public final class MatchWatcher {
    private static final int WIN = 1;
    private static final int LOSE = -1;

    private final HANNYOUPlugin plugin;
    /** 前回見たときの勝ち負け（勝ち・負けのどちらでもない人は入れない） */
    private final Map<UUID, Integer> results = new HashMap<>();
    /** 占領地に続けている秒数（占領地にいない人は入れない） */
    private final Map<UUID, Integer> captureSeconds = new HashMap<>();
    private BukkitTask task;

    public MatchWatcher(HANNYOUPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            checkResults();
            checkCapture();
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private UdemaeSettings settings() {
        return plugin.getPluginConfig().udemae();
    }

    /** ウデマエを数える対象か（試合中で、スペク中でない） */
    public boolean isCounted(Player player) {
        return !plugin.getSpecManager().isSpec(player) && isInMatch(player);
    }

    private boolean isInMatch(Player player) {
        UdemaeSettings settings = settings();
        Objective objective = objective(settings.matchObjective());
        if (objective == null) return false;
        Score score = settings.matchHolder().isEmpty()
                ? objective.getScoreFor(player)
                : objective.getScore(settings.matchHolder());
        return score.isScoreSet() && score.getScore() == settings.matchValue();
    }

    /** 占領中か（データパックと同じ条件。{@link UdemaeSettings.Capture}） */
    public boolean isCapturing(Player player) {
        UdemaeSettings.Capture capture = settings().capture();
        Objective phaseObjective = objective(capture.phaseObjective());
        if (phaseObjective == null) return false;
        Score phase = phaseObjective.getScore(capture.phaseHolder());
        if (!phase.isScoreSet() || phase.getScore() < 0 || phase.getScore() >= capture.points().size()) return false;

        if (!capture.team().isEmpty()) {
            Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(player.getName());
            if (team == null || !team.getName().equals(capture.team())) return false;
        }
        World world = capture.world().isEmpty() ? Bukkit.getWorlds().getFirst() : Bukkit.getWorld(capture.world());
        if (world == null || !player.getWorld().equals(world)) return false;
        Vector point = capture.points().get(phase.getScore());
        return player.getLocation().toVector().distanceSquared(point) <= capture.radius() * capture.radius();
    }

    /** 毎秒呼ぶ */
    private void checkCapture() {
        int staySeconds = settings().capture().staySeconds();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!isCounted(player) || !isCapturing(player)) {
                captureSeconds.remove(id);
                continue;
            }
            int seconds = captureSeconds.merge(id, 1, Integer::sum);
            if (seconds >= staySeconds) {
                captureSeconds.remove(id);
                plugin.getUdemaeManager().reward(player, UdemaeAction.CAPTURE_STAY);
            }
        }
        captureSeconds.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private void checkResults() {
        UdemaeSettings settings = settings();
        Objective win = objective(settings.winObjective());
        Objective lose = objective(settings.loseObjective());
        if (win == null && lose == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getSpecManager().isSpec(player)) continue;
            int result = isNonZero(win, player) ? WIN : isNonZero(lose, player) ? LOSE : 0;
            Integer previous = result == 0 ? results.remove(player.getUniqueId())
                    : results.put(player.getUniqueId(), result);
            if (result == 0 || Integer.valueOf(result).equals(previous)) continue;
            plugin.getUdemaeManager().reward(player, result == WIN ? UdemaeAction.WIN : UdemaeAction.LOSE);
        }
    }

    private static boolean isNonZero(Objective objective, Player player) {
        if (objective == null) return false;
        Score score = objective.getScoreFor(player);
        return score.isScoreSet() && score.getScore() != 0;
    }

    private static Objective objective(String name) {
        if (name == null || name.isBlank()) return null;
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        return scoreboard.getObjective(name);
    }
}
