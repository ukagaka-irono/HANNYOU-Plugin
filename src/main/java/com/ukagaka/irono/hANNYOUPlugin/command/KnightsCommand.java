package com.ukagaka.irono.hANNYOUPlugin.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.audit.AuditLogger;
import com.ukagaka.irono.hANNYOUPlugin.gui.MainMenuGUI;
import com.ukagaka.irono.hANNYOUPlugin.role.Role;
import com.ukagaka.irono.hANNYOUPlugin.transfer.RoleTransfer;
import com.ukagaka.irono.hANNYOUPlugin.util.Text;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /knights コマンド（Brigadier）。
 * 引数の型・補完・セレクター解決はサーバー側で処理されるため、手書きのパースや補完処理が不要。
 */
public final class KnightsCommand {
    private static final String ADMIN = "knights.admin";
    private static final String MENU = "knights.menu";
    private static final UUID CONSOLE = new UUID(0, 0);

    private record PendingDeletion(String roleId, BukkitTask timeout) {}

    private final HANNYOUPlugin plugin;
    private final Map<UUID, PendingDeletion> pendingDeletions = new HashMap<>();

    public KnightsCommand(HANNYOUPlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        SuggestionProvider<CommandSourceStack> roleIds = (ctx, builder) -> {
            String input = builder.getRemainingLowerCase();
            plugin.getRoleManager().ids().stream()
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(input))
                    .sorted()
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
        SuggestionProvider<CommandSourceStack> exportTargets = (ctx, builder) -> {
            if ("all".startsWith(builder.getRemainingLowerCase())) builder.suggest("all");
            return roleIds.getSuggestions(ctx, builder);
        };
        SuggestionProvider<CommandSourceStack> importFiles = (ctx, builder) -> {
            String input = builder.getRemainingLowerCase();
            for (File file : plugin.getRoleTransfer().listImportableFiles()) {
                if (file.getName().toLowerCase(Locale.ROOT).startsWith(input)) {
                    builder.suggest(StringArgumentType.escapeIfRequired(file.getName()));
                }
            }
            return builder.buildFuture();
        };

        return Commands.literal("knights")
                .requires(src -> src.getSender().hasPermission(ADMIN) || src.getSender().hasPermission(MENU))
                .executes(ctx -> help(ctx.getSource().getSender()))
                .then(Commands.literal("menu")
                        .executes(ctx -> openMenu(ctx.getSource().getSender())))
                .then(Commands.literal("role")
                        .requires(src -> src.getSender().hasPermission(ADMIN))
                        .then(Commands.argument("targets", ArgumentTypes.players())
                                .then(Commands.argument("role_id", StringArgumentType.word())
                                        .suggests(roleIds)
                                        .executes(this::applyRole))))
                .then(Commands.literal("roleremove")
                        .requires(src -> src.getSender().hasPermission(ADMIN))
                        .then(Commands.argument("role_id", StringArgumentType.word())
                                .suggests(roleIds)
                                .executes(ctx -> scheduleRemoval(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "role_id")))))
                .then(Commands.literal("roleaccept")
                        .requires(src -> src.getSender().hasPermission(ADMIN))
                        .executes(ctx -> confirmRemoval(ctx.getSource().getSender())))
                .then(Commands.literal("export")
                        .requires(src -> src.getSender().hasPermission(ADMIN))
                        .executes(ctx -> export(ctx.getSource().getSender(), "all"))
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests(exportTargets)
                                .executes(ctx -> export(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "target")))))
                .then(Commands.literal("import")
                        .requires(src -> src.getSender().hasPermission(ADMIN))
                        .executes(ctx -> listImportable(ctx.getSource().getSender()))
                        .then(Commands.argument("file", StringArgumentType.string())
                                .suggests(importFiles)
                                .executes(ctx -> importFile(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "file"), false))
                                .then(Commands.literal("overwrite")
                                        .executes(ctx -> importFile(ctx.getSource().getSender(),
                                                StringArgumentType.getString(ctx, "file"), true)))))
                .build();
    }

    private static void send(CommandSender sender, String legacy) {
        sender.sendMessage(Text.of(legacy));
    }

    // ─────────────────────────────────────────────
    // /knights menu
    // ─────────────────────────────────────────────

    private int openMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "This command can only be used by players!");
            return 0;
        }
        new MainMenuGUI(plugin, player).open();
        return Command.SINGLE_SUCCESS;
    }

    // ─────────────────────────────────────────────
    // /knights role <player|@selector> <role_id>
    // ─────────────────────────────────────────────

    private int applyRole(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        String roleId = StringArgumentType.getString(ctx, "role_id");
        Role role = plugin.getRoleManager().get(roleId);
        if (role == null) {
            send(sender, "§cRole not found: " + roleId);
            return 0;
        }

        List<Player> targets = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class)
                .resolve(ctx.getSource());
        if (targets.isEmpty()) {
            send(sender, "§eNo matching players found.");
            return 0;
        }

        for (Player target : targets) {
            plugin.getRoleApplier().assign(target, role);
        }

        plugin.getAuditLogger().log(sender, AuditLogger.Action.ROLE_APPLY, roleId,
                "対象: " + targets.size() + "人 / ターゲット指定: " + rawArgument(ctx, "targets"));
        send(sender, "§aApplied role '" + role.getDisplayName() + "§a' to " + targets.size() + " player(s).");
        return targets.size();
    }

    private static String rawArgument(CommandContext<CommandSourceStack> ctx, String name) {
        for (ParsedCommandNode<CommandSourceStack> node : ctx.getNodes()) {
            if (node.getNode().getName().equals(name)) return node.getRange().get(ctx.getInput());
        }
        return "?";
    }

    // ─────────────────────────────────────────────
    // /knights export [role_id|all]
    // ─────────────────────────────────────────────

    private int export(CommandSender sender, String target) {
        if (target.equalsIgnoreCase("all")) {
            plugin.getRoleTransfer().exportAll(List.copyOf(plugin.getRoleManager().all())).thenAccept(fileName -> {
                if (fileName != null) {
                    send(sender, "§a全役職をエクスポートしました: exports/" + fileName);
                    plugin.getAuditLogger().log(sender, AuditLogger.Action.ROLE_EXPORT, "ALL", "file: " + fileName);
                } else {
                    send(sender, "§cエクスポートに失敗しました。コンソールを確認してください。");
                }
            });
            return Command.SINGLE_SUCCESS;
        }

        Role role = plugin.getRoleManager().get(target);
        if (role == null) {
            send(sender, "§cRole not found: " + target);
            return 0;
        }
        plugin.getRoleTransfer().exportRole(role).thenAccept(fileName -> {
            if (fileName != null) {
                send(sender, "§a役職をエクスポートしました: exports/" + fileName);
                plugin.getAuditLogger().log(sender, AuditLogger.Action.ROLE_EXPORT, target, "file: " + fileName);
            } else {
                send(sender, "§cエクスポートに失敗しました。");
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    // ─────────────────────────────────────────────
    // /knights import [filename] [overwrite]
    // ─────────────────────────────────────────────

    private int listImportable(CommandSender sender) {
        List<File> files = plugin.getRoleTransfer().listImportableFiles();
        if (files.isEmpty()) {
            send(sender, "§eインポート可能なファイルがありません。");
            send(sender, "§7exports/ フォルダにYMLファイルを配置してください。");
            return 0;
        }
        send(sender, "§6=== インポート可能なファイル ===");
        for (File file : files) send(sender, "§e  " + file.getName());
        send(sender, "§7使用: /knights import <filename> [overwrite]");
        return Command.SINGLE_SUCCESS;
    }

    private int importFile(CommandSender sender, String fileName, boolean overwrite) {
        RoleTransfer transfer = plugin.getRoleTransfer();
        File file = transfer.resolve(fileName);
        if (file == null) {
            send(sender, "§cファイルが見つかりません: " + fileName);
            return 0;
        }

        transfer.load(file).thenAccept(imported -> {
            if (imported.isEmpty()) {
                send(sender, "§cインポートに失敗しました。ファイルを確認してください。");
                return;
            }
            RoleTransfer.ImportResult result = transfer.register(imported, overwrite, sender, fileName);
            send(sender, "§aインポート完了: " + result.added() + "件追加, " + result.skipped() + "件スキップ");
            if (result.limited() > 0) {
                send(sender, "§c役職数の上限(" + plugin.getRoleManager().maxRoles() + ")に達したため "
                        + result.limited() + "件を追加できませんでした");
            }
            if (result.skipped() > 0) {
                send(sender, "§7※スキップされた役職を上書きするには overwrite オプションを使用してください");
                send(sender, "§7例: /knights import " + fileName + " overwrite");
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    // ─────────────────────────────────────────────
    // roleremove / roleaccept
    // ─────────────────────────────────────────────

    private static UUID key(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : CONSOLE;
    }

    private int scheduleRemoval(CommandSender sender, String roleId) {
        Role role = plugin.getRoleManager().get(roleId);
        if (role == null) {
            send(sender, "§cRole not found: " + roleId);
            return 0;
        }

        UUID key = key(sender);
        PendingDeletion previous = pendingDeletions.remove(key);
        if (previous != null) previous.timeout().cancel();

        int timeoutSeconds = plugin.getPluginConfig().deletionTimeoutSeconds();
        BukkitTask timeout = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (pendingDeletions.remove(key) != null) send(sender, "§eRole deletion cancelled (timeout)");
        }, timeoutSeconds * 20L);
        pendingDeletions.put(key, new PendingDeletion(roleId, timeout));

        send(sender, "§e=== Role Deletion Scheduled ===");
        send(sender, "§6Role ID: §f" + roleId);
        send(sender, "§6Display Name: §f" + role.getDisplayName());
        send(sender, "§cWarning: This cannot be undone!");
        send(sender, "§aTo confirm: §f/knights roleaccept");
        return Command.SINGLE_SUCCESS;
    }

    private int confirmRemoval(CommandSender sender) {
        PendingDeletion pending = pendingDeletions.remove(key(sender));
        if (pending == null) {
            send(sender, "§cNo pending role deletion");
            return 0;
        }
        pending.timeout().cancel();

        Role role = plugin.getRoleManager().get(pending.roleId());
        if (role == null) {
            send(sender, "§cRole not found");
            return 0;
        }

        plugin.getRoleManager().delete(pending.roleId());
        plugin.getAuditLogger().log(sender, AuditLogger.Action.ROLE_DELETE, pending.roleId(),
                "表示名: " + role.getDisplayName());

        send(sender, "§a=== Role Deleted ===");
        send(sender, "§aRole ID: §f" + pending.roleId());
        send(sender, "§aDisplay Name: §f" + role.getDisplayName());
        return Command.SINGLE_SUCCESS;
    }

    // ─────────────────────────────────────────────
    // help
    // ─────────────────────────────────────────────

    private int help(CommandSender sender) {
        send(sender, "§6=== Knights Editor Commands ===");
        send(sender, "§e/knights menu §7- Open main menu");
        send(sender, "§e/knights role <player|@selector> <role_id> §7- Apply role (@a, @s, @p, @r 対応)");
        send(sender, "§e/knights roleremove <role_id> §7- Schedule deletion");
        send(sender, "§e/knights roleaccept §7- Confirm deletion");
        send(sender, "§e/knights export [role_id|all] §7- Export role(s) to YAML");
        send(sender, "§e/knights import [filename] [overwrite] §7- Import role(s) from YAML");
        return Command.SINGLE_SUCCESS;
    }
}
