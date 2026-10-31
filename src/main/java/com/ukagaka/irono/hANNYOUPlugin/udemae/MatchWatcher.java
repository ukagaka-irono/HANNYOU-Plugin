package com.ukagaka.irono.hANNYOUPlugin.udemae;

import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 試合中かどうかと勝ち負けを、スコアボードから読む（試合の進行はデータパックなどが行う）。
 * 勝ち負けは、勝ち（または負け）のスコアが 0 から 0 以外に変わったときに一度だけ数える。
 */
public final class MatchWatcher {
    private static final int WIN = 1;
    private static final int LOSE = -1;

    private final HANNYOUPlugin plugin;
    /** 前回見たときの勝ち負け（勝ち・負けのどちらでもない人は入れない） */
    private final Map<UUID, Integer> results = new HashMap<>();
    private BukkitTask task;

    public MatchWatcher(HANNYOUPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::checkResults, 20L, 20L);
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
