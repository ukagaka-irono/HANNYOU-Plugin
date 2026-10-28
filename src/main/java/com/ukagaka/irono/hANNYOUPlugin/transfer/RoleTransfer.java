package com.ukagaka.irono.hANNYOUPlugin.transfer;

import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.audit.AuditLogger;
import com.ukagaka.irono.hANNYOUPlugin.role.Role;
import com.ukagaka.irono.hANNYOUPlugin.role.RoleManager;
import com.ukagaka.irono.hANNYOUPlugin.storage.RoleYaml;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * 役職のYAMLエクスポート／インポート。
 * ファイルの読み書きは非同期で行い、アイテムの変換と結果の通知はメインスレッドで行う。
 * KnightsEditor が出力した旧形式のファイルも読み込める。
 */
public final class RoleTransfer {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /** invalid = IDが不正（半角英数字とアンダースコア以外を含む）で読み込まなかった件数 */
    public record ImportResult(int added, int skipped, int limited, int invalid) {}

    private final HANNYOUPlugin plugin;
    private final Path exportFolder;
    private final Executor mainThread;

    public RoleTransfer(HANNYOUPlugin plugin) {
        this.plugin = plugin;
        this.exportFolder = plugin.getDataFolder().toPath().resolve("exports");
        this.mainThread = task -> plugin.getServer().getScheduler().runTask(plugin, task);
        try {
            Files.createDirectories(exportFolder);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "exports フォルダを作成できませんでした", e);
        }
    }

    // ─────────────────────────────────────────────
    // エクスポート
    // ─────────────────────────────────────────────

    /** @return 出力ファイル名（失敗時null）をメインスレッドで返す */
    public CompletableFuture<String> exportRole(Role role) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("format", RoleYaml.FORMAT);
        yml.set("id", role.getId());
        RoleYaml.write(yml, role);
        return write("role_" + role.getId() + "_" + TIMESTAMP.format(LocalDateTime.now()) + ".yml", yml);
    }

    public CompletableFuture<String> exportAll(Collection<Role> roles) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("format", RoleYaml.FORMAT);
        for (Role role : roles) {
            RoleYaml.write(yml.createSection("roles." + role.getId()), role);
        }
        return write("roles_all_" + TIMESTAMP.format(LocalDateTime.now()) + ".yml", yml);
    }

    private CompletableFuture<String> write(String fileName, YamlConfiguration yml) {
        String content = yml.saveToString(); // 役職の状態はメインスレッドで確定させる
        Path file = exportFolder.resolve(fileName);
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(exportFolder);
                Files.writeString(file, content, StandardCharsets.UTF_8);
                plugin.getLogger().info("役職をエクスポートしました: " + fileName);
                return fileName;
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "エクスポートに失敗しました: " + fileName, e);
                return null;
            }
        }).thenApplyAsync(name -> name, mainThread);
    }

    // ─────────────────────────────────────────────
    // インポート
    // ─────────────────────────────────────────────

    /** exports/ 内のYMLファイル（新しい順） */
    public List<File> listImportableFiles() {
        File[] files = exportFolder.toFile().listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return List.of();
        return Arrays.stream(files)
                .sorted(Comparator.comparingLong(File::lastModified).reversed())
                .toList();
    }

    /** ファイル名を exports/ 内のファイルに解決する（フォルダ外を指す名前は拒否） */
    public File resolve(String fileName) {
        Path file = exportFolder.resolve(fileName).normalize();
        if (!file.startsWith(exportFolder.normalize()) || !Files.isRegularFile(file)) return null;
        return file.toFile();
    }

    /** ファイルを読み込み、含まれる役職をメインスレッドで返す（失敗時は空） */
    public CompletableFuture<Map<String, Role>> load(File file) {
        return CompletableFuture.supplyAsync(() -> {
            YamlConfiguration yml = new YamlConfiguration();
            try {
                yml.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            } catch (IOException | InvalidConfigurationException e) {
                plugin.getLogger().log(Level.WARNING, "インポートファイルを読み込めませんでした: " + file.getName(), e);
            }
            return yml;
        }).thenApplyAsync(this::parse, mainThread);
    }

    private Map<String, Role> parse(YamlConfiguration yml) {
        int format = yml.getInt("format", 1);
        Map<String, Role> result = new LinkedHashMap<>();
        ConfigurationSection rolesSection = yml.getConfigurationSection("roles");
        if (rolesSection != null) {
            for (String id : rolesSection.getKeys(false)) {
                ConfigurationSection section = rolesSection.getConfigurationSection(id);
                if (section != null) result.put(id, parseRole(section, id, format));
            }
        } else if (yml.contains("id")) {
            String id = yml.getString("id", "unknown");
            result.put(id, parseRole(yml, id, format));
        }
        return result;
    }

    private Role parseRole(ConfigurationSection section, String id, int format) {
        return RoleYaml.read(section, id, format, plugin.getLogger());
    }

    /** 読み込んだ役職を登録する（overwrite=false の場合は既存IDをスキップ） */
    public ImportResult register(Map<String, Role> imported, boolean overwrite, CommandSender executor, String fileName) {
        RoleManager roles = plugin.getRoleManager();
        int added = 0, skipped = 0, limited = 0, invalid = 0;
        for (Role role : imported.values()) {
            // ファイル名・タグ・クリックできるコマンドに使うので、エディタと同じ規則のIDだけ受け付ける
            if (!RoleManager.ID_PATTERN.matcher(role.getId()).matches()) {
                plugin.getLogger().warning("IDが不正な役職を読み込みませんでした: " + role.getId());
                invalid++;
                continue;
            }
            boolean exists = roles.exists(role.getId());
            if (exists && !overwrite) {
                skipped++;
                continue;
            }
            if (!exists && !roles.hasCapacity()) {
                limited++;
                continue;
            }
            Role before = roles.get(role.getId());
            roles.save(role);
            if (before != null) plugin.getRoleApplier().syncEffects(before, role);
            plugin.getAuditLogger().log(executor, AuditLogger.Action.ROLE_IMPORT, role.getId(),
                    "file: " + fileName + (overwrite ? " [上書き]" : ""));
            added++;
        }
        return new ImportResult(added, skipped, limited, invalid);
    }
}
