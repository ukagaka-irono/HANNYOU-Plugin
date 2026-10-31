package com.ukagaka.irono.hANNYOUPlugin.udemae;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * config.yml の udemae の設定。
 * matchHolder が空なら、プレイヤーごとのスコアで「その人が試合中か」を見る。
 * 空でなければ、その持ち主（例: #game）のスコアで「今が試合中か」を見る。
 * 占領中かは、プレイヤーごとのスコア captureObjective が captureMin 以上かで見る（占領はデータパックが管理する）。
 */
public record UdemaeSettings(
        List<String> ranks,
        int pointsPerRank,
        String exportObjective,
        String matchObjective,
        String matchHolder,
        int matchValue,
        String winObjective,
        String loseObjective,
        int assistSeconds,
        String captureObjective,
        int captureMin,
        int captureStaySeconds,
        Map<UdemaeAction, Integer> points
) {
    private static final List<String> DEFAULT_RANKS =
            List.of("C-", "C", "C+", "B-", "B", "B+", "A-", "A", "A+", "S", "S+");

    public static UdemaeSettings from(ConfigurationSection section) {
        if (section == null) section = new MemoryConfiguration();
        List<String> ranks = section.getStringList("ranks").stream()
                .filter(name -> !name.isBlank()).toList();
        Map<UdemaeAction, Integer> points = new EnumMap<>(UdemaeAction.class);
        for (UdemaeAction action : UdemaeAction.values()) {
            points.put(action, section.getInt("points." + action.key(), action.defaultPoints()));
        }
        return new UdemaeSettings(
                ranks.isEmpty() ? DEFAULT_RANKS : ranks,
                Math.max(1, section.getInt("points-per-rank", 100)),
                section.getString("export-objective", "udemae"),
                section.getString("match.objective", "hannyou_match"),
                section.getString("match.holder", ""),
                section.getInt("match.value", 1),
                section.getString("win-objective", "_game_win"),
                section.getString("lose-objective", "_game_lose"),
                Math.max(1, section.getInt("assist-seconds", 10)),
                section.getString("capture.objective", "_game_capture"),
                section.getInt("capture.min", 1),
                Math.max(1, section.getInt("capture.stay-seconds", 5)),
                points);
    }

    public int points(UdemaeAction action) {
        return points.getOrDefault(action, 0);
    }
}
