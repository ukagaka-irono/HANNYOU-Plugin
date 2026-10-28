package com.ukagaka.irono.hANNYOUPlugin.audit;

import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.Permissions;
import com.ukagaka.irono.hANNYOUPlugin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** 役職操作の監査ログ。サーバーのログと OP のチャットに出す */
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

    private final HANNYOUPlugin plugin;

    public AuditLogger(HANNYOUPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @param executor 操作者（プレイヤー以外はSYSTEM扱い）
     * @param detail   追加情報（null可）
     */
    public void log(CommandSender executor, Action action, String roleId, String detail) {
        if (!plugin.getPluginConfig().logRoleChanges()) return;

        String executorName = executor instanceof Player player ? player.getName() : "SYSTEM";
        boolean hasDetail = detail != null && !detail.isEmpty();
        // 改行で偽の行を書き込まれないよう、制御文字は空白にする
        String message = String.format("[%s] %s -> RoleID: %s%s",
                action.getDisplayName(), executorName, roleId,
                hasDetail ? " | " + detail : "").replaceAll("\\p{Cntrl}", " ");

        plugin.getLogger().info("[AuditLog] " + message);

        var chat = Text.of("§7[Log] §e" + action.getDisplayName()
                + "§7 | §f" + executorName
                + "§7 -> §b" + roleId
                + (hasDetail ? "§7 | §f" + detail : ""));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (Permissions.isOperator(online)) online.sendMessage(chat);
        }
    }
}
