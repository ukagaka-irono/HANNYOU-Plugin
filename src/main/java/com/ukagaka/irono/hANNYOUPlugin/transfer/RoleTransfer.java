package com.ukagaka.irono.hANNYOUPlugin.transfer;

import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.audit.AuditLogger;
import com.ukagaka.irono.hANNYOUPlugin.role.Role;
import com.ukagaka.irono.hANNYOUPlugin.role.RoleManager;
import com.ukagaka.irono.hANNYOUPlugin.storage.ItemCodec;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

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
    /** 1 = KnightsEditor形式（Java直列化）、2 = Paperバイナリ形式 */
    private static final int FORMAT = 2;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public record ImportResult(int added, int skipped, int limited) {}

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
        yml.set("format", FORMAT);
        yml.set("id", role.getId());
        writeRole(yml, role);
        return write("role_" + role.getId() + "_" + TIMESTAMP.format(LocalDateTime.now()) + ".yml", yml);
    }

    public CompletableFuture<String> exportAll(Collection<Role> roles) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("format", FORMAT);
        for (Role role : roles) {
            writeRole(yml.createSection("roles." + role.getId()), role);
        }
        return write("roles_all_" + TIMESTAMP.format(LocalDateTime.now()) + ".yml", yml);
    }

    private void writeRole(ConfigurationSection section, Role role) {
        section.set("display_name", role.getDisplayName());
        section.set("icon", encode(role.getIcon()));
        Map<String, String> equipment = new LinkedHashMap<>();
        role.getEquipment().forEach((slot, item) -> equipment.put(slot.name(), encode(item)));
        section.set("equipment", equipment);
        writeItems(section, "inventory", role.getInventory());
        writeItems(section, "hotbar", role.getHotbar());
        Map<String, Integer> effects = new LinkedHashMap<>();
        role.getEffects().forEach((type, level) -> effects.put(ItemCodec.effectKey(type), level));
        section.set("effects", effects);
    }

    private void writeItems(ConfigurationSection section, String path, ItemStack[] items) {
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) section.set(path + "." + i, encode(items[i]));
        }
    }

    private static String encode(ItemStack item) {
        return ItemCodec.toBase64(ItemCodec.encode(item));
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
        Role role = new Role(id, section.getString("display_name", "インポートされた役職"));
        role.setIcon(decode(section.getString("icon"), format, id));

        ConfigurationSection equipment = section.getConfigurationSection("equipment");
        if (equipment != null) {
            for (String slotName : equipment.getKeys(false)) {
                try {
                    role.setEquipment(Role.EquipmentSlot.valueOf(slotName),
                            decode(equipment.getString(slotName), format, id));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        readItems(section.getConfigurationSection("inventory"), Role.INVENTORY_SIZE, format, id, role::setInventoryItem);
        readItems(section.getConfigurationSection("hotbar"), Role.HOTBAR_SIZE, format, id, role::setHotbarItem);

        ConfigurationSection effects = section.getConfigurationSection("effects");
        if (effects != null) {
            for (String key : effects.getKeys(false)) {
                PotionEffectType type = ItemCodec.effectType(key);
                if (type != null) role.setEffectLevel(type, effects.getInt(key));
            }
        }
        return role;
    }

    private interface IndexedSetter {
        void set(int index, ItemStack item);
    }

    private void readItems(ConfigurationSection section, int size, int format, String id, IndexedSetter setter) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                int index = Integer.parseInt(key);
                if (index >= 0 && index < size) setter.set(index, decode(section.getString(key), format, id));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private ItemStack decode(String data, int format, String roleId) {
        try {
            return format >= 2 ? ItemCodec.decode(ItemCodec.fromBase64(data)) : ItemCodec.decodeLegacyBase64(data);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "アイテムを読み込めませんでした (役職: " + roleId + ")", e);
            return null;
        }
    }

    /** 読み込んだ役職を登録する（overwrite=false の場合は既存IDをスキップ） */
    public ImportResult register(Map<String, Role> imported, boolean overwrite, CommandSender executor, String fileName) {
        RoleManager roles = plugin.getRoleManager();
        int added = 0, skipped = 0, limited = 0;
        for (Role role : imported.values()) {
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
        return new ImportResult(added, skipped, limited);
    }
}
