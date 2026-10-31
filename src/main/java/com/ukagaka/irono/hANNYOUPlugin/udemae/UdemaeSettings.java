package com.ukagaka.irono.hANNYOUPlugin.udemae;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * config.yml の udemae の設定。
 * matchHolder が空なら、プレイヤーごとのスコアで「その人が試合中か」を見る。
 * 空でなければ、その持ち主（例: #game）のスコアで「今が試合中か」を見る。
 * 占領の判定は {@link Capture}。
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
        Capture capture,
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
                Capture.from(section),
                points);
    }

    public int points(UdemaeAction action) {
        return points.getOrDefault(action, 0);
    }

    /**
     * 占領の判定。占領はデータパック（cok_area）が管理していて、プレイヤーごとの「占領中」のスコアは無いので、
     * データパックと同じ条件をこちらでも見る: phaseObjective の phaseHolder の値が今の拠点の番号（0 = 1つ目）で、
     * team のプレイヤーがその拠点の radius ブロック以内にいれば占領中。
     * 拠点の座標はデータパックの main.mcfunction と同じものを config.yml に書く。
     * コマンドの {@code execute positioned x y z} は、整数の x と z をブロックの中心（+0.5）として扱うので、ここでもそうする。
     *
     * @param world 拠点のあるワールドの名前（空ならメインのワールド。データパックの tick 関数はメインのワールドで動く）
     */
    public record Capture(
            String phaseObjective,
            String phaseHolder,
            String team,
            String world,
            double radius,
            List<Vector> points,
            int staySeconds
    ) {
        private static final List<Vector> DEFAULT_POINTS = List.of(
                new Vector(-118, 69, -154), new Vector(-64, 74, -266),
                new Vector(-48, 75, -132), new Vector(17, 82, -147));

        static Capture from(ConfigurationSection udemae) {
            ConfigurationSection section = udemae.getConfigurationSection("capture");
            if (section == null) section = new MemoryConfiguration();
            List<Vector> points = new ArrayList<>();
            for (Object entry : section.getList("points", List.of())) {
                if (entry instanceof List<?> xyz && xyz.size() == 3
                        && xyz.stream().allMatch(Number.class::isInstance)) {
                    points.add(new Vector(((Number) xyz.get(0)).doubleValue(),
                            ((Number) xyz.get(1)).doubleValue(), ((Number) xyz.get(2)).doubleValue()));
                }
            }
            if (!section.contains("points")) points.addAll(DEFAULT_POINTS);
            return new Capture(
                    section.getString("phase-objective", "area_manage"),
                    section.getString("phase-holder", "phase"),
                    section.getString("team", "atk"),
                    section.getString("world", ""),
                    Math.max(0, section.getDouble("radius", 2)),
                    points.stream().map(p -> new Vector(p.getX() + 0.5, p.getY(), p.getZ() + 0.5)).toList(),
                    Math.max(1, section.getInt("stay-seconds", 5)));
        }
    }
}
