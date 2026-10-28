package com.ukagaka.irono.hANNYOUPlugin.storage;

import com.ukagaka.irono.hANNYOUPlugin.role.Role;
import com.ukagaka.irono.hANNYOUPlugin.role.RoleManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * knights-role.yml への永続化。
 * 内容はメインスレッドで文字列にまとめ、ファイルへの書き込みは専用スレッド1本で行うため、TPSを下げない。
 * 書き込み待ちの間に変更が重なった場合は、最新の内容だけを書き込む。
 * 一時ファイルに書いてから置き換えるので、書き込み中に落ちてもファイルが壊れない。
 */
public final class RoleStorage {
    public static final String FILE_NAME = "knights-role.yml";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final List<String> HEADER = List.of(
            "HANNYOU Plugin - Knights Editor の役職データ",
            "手で編集するときは、サーバーを止めてから編集してください");

    private final Logger logger;
    private final Path file;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("HANNYOU-Storage").daemon().factory());
    /** 書き込み待ちの内容（なければnull） */
    private final AtomicReference<String> pending = new AtomicReference<>();

    /** 起動時に読み込むデータ。deletedRoles は削除済み役職ID → その役職の効果（オフライン保持者の後始末用） */
    public record LoadResult(Map<String, Role> roles, Map<String, Map<PotionEffectType, Integer>> deletedRoles) {}

    public RoleStorage(File dataFolder, Logger logger) {
        this.logger = logger;
        this.file = dataFolder.toPath().resolve(FILE_NAME);
    }

    /** 保存済みの全役職を読み込む（起動時に一度だけ同期実行） */
    public LoadResult load() {
        if (!Files.isRegularFile(file)) return new LoadResult(Map.of(), Map.of());

        YamlConfiguration yml = new YamlConfiguration();
        try {
            yml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException e) {
            Path backup = file.resolveSibling(FILE_NAME + ".broken-" + TIMESTAMP.format(LocalDateTime.now()));
            try {
                Files.copy(file, backup);
                logger.log(Level.SEVERE, FILE_NAME + " を読み込めませんでした。" + backup.getFileName()
                        + " に退避し、役職なしで起動します", e);
            } catch (IOException copyError) {
                logger.log(Level.SEVERE, FILE_NAME + " を読み込めず、退避もできませんでした", copyError);
            }
            return new LoadResult(Map.of(), Map.of());
        }

        int format = yml.getInt("format", RoleYaml.FORMAT);
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
                try {
                    roles.put(id, RoleYaml.read(section, id, format, logger));
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "役職の読み込みに失敗しました: " + id, e);
                }
            }
        }

        Map<String, Map<PotionEffectType, Integer>> deleted = new HashMap<>();
        ConfigurationSection deletedSection = yml.getConfigurationSection("deleted");
        if (deletedSection != null) {
            for (String id : deletedSection.getKeys(false)) {
                deleted.put(id, RoleYaml.readEffects(deletedSection.getConfigurationSection(id)));
            }
        }
        return new LoadResult(roles, deleted);
    }

    /** 全役職と削除済み役職を書き出す（メインスレッドから呼ぶ） */
    public void save(Collection<Role> roles, Map<String, Map<PotionEffectType, Integer>> deleted) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.options().setHeader(HEADER);
        yml.set("format", RoleYaml.FORMAT);
        yml.createSection("roles");
        roles.stream().sorted(Comparator.comparing(Role::getId))
                .forEach(role -> RoleYaml.write(yml.createSection("roles." + role.getId()), role));
        yml.createSection("deleted");
        deleted.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> yml.set("deleted." + entry.getKey(), RoleYaml.writeEffects(entry.getValue())));

        if (pending.getAndSet(yml.saveToString()) == null && !executor.isShutdown()) {
            executor.execute(this::flush);
        }
    }

    private void flush() {
        String content = pending.getAndSet(null);
        if (content == null) return;
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, FILE_NAME + " への保存に失敗しました", e);
        }
    }

    /** 未処理の書き込みをすべて終える */
    public void close() {
        executor.shutdown();
        try {
            if (executor.awaitTermination(10, TimeUnit.SECONDS)) {
                flush(); // 停止の直前に積まれた分
            } else {
                logger.warning(FILE_NAME + " への書き込みが時間内に終わりませんでした");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
