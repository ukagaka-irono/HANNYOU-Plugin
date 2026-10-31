package com.ukagaka.irono.hANNYOUPlugin.storage;

import com.ukagaka.irono.hANNYOUPlugin.role.DeletedRole;
import com.ukagaka.irono.hANNYOUPlugin.role.Role;
import com.ukagaka.irono.hANNYOUPlugin.role.RoleManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * knights-role.yml への永続化。
 * 内容はメインスレッドで文字列にまとめ、ファイルへの書き込みは {@link AsyncFileWriter} に任せる。
 */
public final class RoleStorage {
    public static final String FILE_NAME = "knights-role.yml";
    private static final List<String> HEADER = List.of(
            "汎用プラグイン（HANNYOU Plugin） - Knights Editor の役職データ",
            "手で編集するときは、サーバーを止めてから編集してください");

    private final Logger logger;
    private final Path file;
    private final AsyncFileWriter writer;
    /**
     * 役職ごとの変換済みの内容。保存のたびに全役職のアイテムを変換し直さず、変わった役職だけを変換する。
     * 役職を変更するときは必ず新しい Role（作業コピー）で保存し直すので、同じ Role なら内容も同じ。
     * 削除された役職はキーが参照されなくなり、自動で消える。メインスレッドからのみ使う。
     */
    private final Map<Role, Map<String, Object>> serialized = new WeakHashMap<>();

    /**
     * 起動時に読み込むデータ。deletedRoles は削除済み役職の識別番号 → 記録（オフライン保持者の後始末用）。
     * needsSave は、古い形式のデータに識別番号を割り振ったので書き出し直す必要があるか。
     */
    public record LoadResult(Map<String, Role> roles, Map<String, DeletedRole> deletedRoles, boolean needsSave) {}

    public RoleStorage(File dataFolder, Logger logger) {
        this.logger = logger;
        this.file = dataFolder.toPath().resolve(FILE_NAME);
        this.writer = new AsyncFileWriter(file, "HANNYOU-Storage", logger);
    }

    /** 保存済みの全役職を読み込む（起動時に一度だけ同期実行） */
    public LoadResult load() {
        if (!Files.isRegularFile(file)) return new LoadResult(Map.of(), Map.of(), false);

        YamlConfiguration yml = new YamlConfiguration();
        try {
            yml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException e) {
            writer.backupBroken(e, "役職なしで起動します");
            return new LoadResult(Map.of(), Map.of(), false);
        }

        boolean needsSave = false;

        Map<String, Role> roles = new LinkedHashMap<>();
        ConfigurationSection rolesSection = yml.getConfigurationSection("roles");
        if (rolesSection != null) {
            for (String id : rolesSection.getKeys(false)) {
                ConfigurationSection section = rolesSection.getConfigurationSection(id);
                if (section == null) continue;
                if (!RoleManager.ID_PATTERN.matcher(id).matches()) {
                    logger.warning("IDが不正な役職を読み飛ばしました（半角英数字とアンダースコアのみ）: " + id);
                    continue;
                }
                if (!section.isString("uid")) needsSave = true;
                try {
                    roles.put(id, RoleYaml.read(section, id, logger));
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "役職の読み込みに失敗しました: " + id, e);
                }
            }
        }

        Map<String, DeletedRole> deleted = new HashMap<>();
        ConfigurationSection deletedSection = yml.getConfigurationSection("deleted");
        if (deletedSection != null) {
            for (String key : deletedSection.getKeys(false)) {
                ConfigurationSection entry = deletedSection.getConfigurationSection(key);
                if (entry == null) continue;
                if (entry.isString("id")) {
                    deleted.put(key, new DeletedRole(entry.getString("id"),
                            RoleYaml.readEffects(entry.getConfigurationSection("effects"))));
                } else {
                    // 古い形式（キーが role id、中身が効果）。識別番号を割り振る
                    deleted.put(UUID.randomUUID().toString(), new DeletedRole(key, RoleYaml.readEffects(entry)));
                    needsSave = true;
                }
            }
        }
        return new LoadResult(roles, deleted, needsSave);
    }

    /** 全役職と削除済み役職を書き出す（メインスレッドから呼ぶ） */
    public void save(Collection<Role> roles, Map<String, DeletedRole> deleted) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.options().setHeader(HEADER);
        yml.set("format", RoleYaml.FORMAT);
        yml.createSection("roles");
        roles.stream().sorted(Comparator.comparing(Role::getId))
                .forEach(role -> yml.createSection("roles." + role.getId(),
                        serialized.computeIfAbsent(role, RoleYaml::toMap)));
        yml.createSection("deleted");
        deleted.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ConfigurationSection section = yml.createSection("deleted." + entry.getKey());
            section.set("id", entry.getValue().id());
            section.set("effects", RoleYaml.writeEffects(entry.getValue().effects()));
        });

        writer.write(yml.saveToString());
    }

    /** 未処理の書き込みをすべて終える */
    public void close() {
        writer.close();
    }
}
