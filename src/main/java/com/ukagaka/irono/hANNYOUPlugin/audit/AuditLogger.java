package com.ukagaka.irono.hANNYOUPlugin.audit;

import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.Permissions;
import com.ukagaka.irono.hANNYOUPlugin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 役職操作の監査ログ。ファイル書き込みは専用スレッドで行う */
public final class AuditLogger {

    public enum Action {
        ROLE_CREATE("作成"),
        ROLE_EDIT("編集"),
        ROLE_DELETE("削除"),
        ROLE_COPY("コピー"),
        ROLE_APPLY("付与"),
        ROLE_EXPORT("エクスポート"),
        ROLE_IMPORT("インポート");

        private final String displayName;
        Action(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LOG_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HANNYOUPlugin plugin;
    private final Path logFolder;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("HANNYOU-AuditLog").daemon().factory());

    public AuditLogger(HANNYOUPlugin plugin) {
        this.plugin = plugin;
        this.logFolder = plugin.getDataFolder().toPath().resolve("logs");
    }

    /**
     * @param executor 操作者（プレイヤー以外はSYSTEM扱い）
     * @param detail   追加情報（null可）
     */
    public void log(CommandSender executor, Action action, String roleId, String detail) {
        if (!plugin.getPluginConfig().logRoleChanges()) return;

        String executorName = executor instanceof Player player ? player.getName() : "SYSTEM";
        boolean hasDetail = detail != null && !detail.isEmpty();
        LocalDateTime now = LocalDateTime.now();
        // 改行で偽の行を書き込まれないよう、制御文字は空白にする
        String message = String.format("[%s] [%s] %s -> RoleID: %s%s",
                LOG_DATE.format(now), action.getDisplayName(), executorName, roleId,
                hasDetail ? " | " + detail : "").replaceAll("\\p{Cntrl}", " ");

        Path file = logFolder.resolve("audit-" + FILE_DATE.format(now) + ".log");
        if (!writer.isShutdown()) {
            writer.execute(() -> {
                try {
                    Files.createDirectories(logFolder);
                    Files.writeString(file, message + System.lineSeparator(), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                    plugin.getLogger().warning("監査ログの書き込みに失敗しました: " + e.getMessage());
                }
            });
        }

        plugin.getLogger().info("[AuditLog] " + message);

        var chat = Text.of("§7[Log] §e" + action.getDisplayName()
                + "§7 | §f" + executorName
                + "§7 -> §b" + roleId
                + (hasDetail ? "§7 | §f" + detail : ""));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(Permissions.KNIGHTS_ADMIN)) online.sendMessage(chat);
        }
    }

    public void close() {
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
