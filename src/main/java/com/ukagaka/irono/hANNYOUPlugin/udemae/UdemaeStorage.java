package com.ukagaka.irono.hANNYOUPlugin.udemae;

import com.ukagaka.irono.hANNYOUPlugin.storage.AsyncFileWriter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * udemae.yml への永続化。プレイヤーの UUID ごとに、最後に見た名前と合計ポイントを持つ。
 * ランクは合計ポイントから計算するので、config.yml でランクの段階を変えても保存データは壊れない。
 */
public final class UdemaeStorage {
    public static final String FILE_NAME = "udemae.yml";
    private static final List<String> HEADER = List.of(
            "汎用プラグイン（HANNYOU Plugin） - ウデマエのデータ",
            "total は合計ポイント（ランクの番号 × 1ランクのポイント + ランクの中のポイント）",
            "手で編集するときは、サーバーを止めてから編集してください");

    /** 保存する1人分。name は表示とコマンドでの検索用 */
    public record Entry(String name, int total) {}

    private final Logger logger;
    private final Path file;
    private final AsyncFileWriter writer;

    public UdemaeStorage(File dataFolder, Logger logger) {
        this.logger = logger;
        this.file = dataFolder.toPath().resolve(FILE_NAME);
        this.writer = new AsyncFileWriter(file, "HANNYOU-Udemae", logger);
    }

    /** 起動時に一度だけ同期実行する */
    public Map<UUID, Entry> load() {
        Map<UUID, Entry> result = new HashMap<>();
        if (!Files.isRegularFile(file)) return result;

        YamlConfiguration yml = new YamlConfiguration();
        try {
            yml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException e) {
            writer.backupBroken(e, "ウデマエなしで起動します");
            return result;
        }

        ConfigurationSection players = yml.getConfigurationSection("players");
        if (players == null) return result;
        for (String key : players.getKeys(false)) {
            ConfigurationSection entry = players.getConfigurationSection(key);
            if (entry == null) continue;
            try {
                result.put(UUID.fromString(key),
                        new Entry(entry.getString("name", ""), Math.max(0, entry.getInt("total"))));
            } catch (IllegalArgumentException e) {
                logger.warning(FILE_NAME + " の UUID が不正なので読み飛ばしました: " + key);
            }
        }
        return result;
    }

    /** 全員分を書き出す（メインスレッドから呼ぶ） */
    public void save(Map<UUID, Entry> entries) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.options().setHeader(HEADER);
        ConfigurationSection players = yml.createSection("players");
        entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    ConfigurationSection section = players.createSection(e.getKey().toString());
                    section.set("name", e.getValue().name());
                    section.set("total", e.getValue().total());
                });
        writer.write(yml.saveToString());
    }

    public void close() {
        writer.close();
    }
}
