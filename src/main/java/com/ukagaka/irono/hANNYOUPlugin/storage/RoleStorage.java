package com.ukagaka.irono.hANNYOUPlugin.storage;

import com.ukagaka.irono.hANNYOUPlugin.role.Role;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLiteへの永続化。
 * 書き込みは専用スレッド1本で順番に処理するため、メインスレッド（TPS）をブロックしない。
 * 接続もそのスレッドだけが使うのでロック不要。
 */
public final class RoleStorage {
    private static final String UPSERT = """
            INSERT INTO roles (id, display_name, icon, equipment, inventory, hotbar, effects)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                display_name = excluded.display_name,
                icon = excluded.icon,
                equipment = excluded.equipment,
                inventory = excluded.inventory,
                hotbar = excluded.hotbar,
                effects = excluded.effects,
                updated_at = CURRENT_TIMESTAMP""";

    private final Logger logger;
    private final File dbFile;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("HANNYOU-Storage").daemon().factory());
    private Connection connection;

    /** DBとの受け渡し用スナップショット。アイテムはメインスレッドでバイト列化しておく */
    public record StoredRole(String id, String displayName, byte[] icon, byte[] equipment,
                             byte[] inventory, byte[] hotbar, String effects) {

        public static StoredRole of(Role role) {
            ItemStack[] equipment = new ItemStack[Role.EquipmentSlot.values().length];
            role.getEquipment().forEach((slot, item) -> equipment[slot.ordinal()] = item);
            return new StoredRole(
                    role.getId(),
                    role.getDisplayName(),
                    ItemCodec.encode(role.getIcon()),
                    ItemCodec.encodeArray(equipment),
                    ItemCodec.encodeArray(role.getInventory()),
                    ItemCodec.encodeArray(role.getHotbar()),
                    ItemCodec.encodeEffects(role.getEffects()));
        }

        public Role toRole() {
            Role role = new Role(id, displayName);
            role.setIcon(ItemCodec.decode(icon));
            Role.EquipmentSlot[] slots = Role.EquipmentSlot.values();
            var equip = ItemCodec.decodeArray(equipment, slots.length);
            for (Role.EquipmentSlot slot : slots) role.setEquipment(slot, equip[slot.ordinal()]);
            var inv = ItemCodec.decodeArray(inventory, Role.INVENTORY_SIZE);
            for (int i = 0; i < inv.length; i++) role.setInventoryItem(i, inv[i]);
            var bar = ItemCodec.decodeArray(hotbar, Role.HOTBAR_SIZE);
            for (int i = 0; i < bar.length; i++) role.setHotbarItem(i, bar[i]);
            role.getEffects().putAll(ItemCodec.decodeEffects(effects));
            return role;
        }
    }

    public RoleStorage(File dataFolder, Logger logger) {
        this.logger = logger;
        this.dbFile = new File(dataFolder, "roles.db");
    }

    /** 接続とテーブル作成を行い、保存済みの全役職を読み込む（起動時に一度だけ同期実行） */
    public List<StoredRole> open() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                dbFile.getParentFile().mkdirs();
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA journal_mode = WAL");
                    stmt.execute("PRAGMA synchronous = NORMAL");
                    stmt.execute("""
                            CREATE TABLE IF NOT EXISTS roles (
                                id TEXT PRIMARY KEY,
                                display_name TEXT NOT NULL,
                                icon BLOB,
                                equipment BLOB,
                                inventory BLOB,
                                hotbar BLOB,
                                effects TEXT,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                            )""");
                }
                return loadAll();
            } catch (ClassNotFoundException | SQLException e) {
                logger.log(Level.SEVERE, "SQLiteデータベースに接続できませんでした。メモリのみで動作します。", e);
                connection = null;
                return List.<StoredRole>of();
            }
        }, executor).join();
    }

    public boolean isConnected() {
        return connection != null;
    }

    private List<StoredRole> loadAll() throws SQLException {
        List<StoredRole> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT id, display_name, icon, equipment, inventory, hotbar, effects FROM roles")) {
            while (rs.next()) {
                result.add(new StoredRole(rs.getString(1), rs.getString(2), rs.getBytes(3),
                        rs.getBytes(4), rs.getBytes(5), rs.getBytes(6), rs.getString(7)));
            }
        }
        return result;
    }

    public void save(StoredRole role) {
        submit("save " + role.id(), () -> {
            try (PreparedStatement stmt = connection.prepareStatement(UPSERT)) {
                stmt.setString(1, role.id());
                stmt.setString(2, role.displayName());
                stmt.setBytes(3, role.icon());
                stmt.setBytes(4, role.equipment());
                stmt.setBytes(5, role.inventory());
                stmt.setBytes(6, role.hotbar());
                stmt.setString(7, role.effects());
                stmt.executeUpdate();
            }
        });
    }

    public void delete(String id) {
        submit("delete " + id, () -> {
            try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM roles WHERE id = ?")) {
                stmt.setString(1, id);
                stmt.executeUpdate();
            }
        });
    }

    private interface SqlTask {
        void run() throws SQLException;
    }

    private void submit(String description, SqlTask task) {
        if (!isConnected() || executor.isShutdown()) return;
        executor.execute(() -> {
            try {
                task.run();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "データベース処理に失敗しました: " + description, e);
            }
        });
    }

    /** 未処理の書き込みをすべて終えてから接続を閉じる */
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("データベースへの書き込みが時間内に終わりませんでした");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "データベース接続のクローズに失敗しました", e);
            }
        }
    }
}
