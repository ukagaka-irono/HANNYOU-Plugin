package com.ukagaka.irono.hANNYOUPlugin.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.audit.AuditLogger;
import com.ukagaka.irono.hANNYOUPlugin.gui.ConfirmDeleteGUI;
import com.ukagaka.irono.hANNYOUPlugin.gui.RoleEditorGUI;
import com.ukagaka.irono.hANNYOUPlugin.gui.RoleListGUI;
import com.ukagaka.irono.hANNYOUPlugin.role.Role;
import com.ukagaka.irono.hANNYOUPlugin.transfer.RoleTransfer;
import com.ukagaka.irono.hANNYOUPlugin.util.Text;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * /hannyou knights サブコマンド（Brigadier）。
 * 引数の型・補完・セレクター解決はサーバー側で処理されるため、手書きのパースや補完処理が不要。
 */
public final class KnightsCommand {
    public static final String BASE = "/hannyou knights";

    private final HANNYOUPlugin plugin;

    public KnightsCommand(HANNYOUPlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> node() {
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
                .executes(ctx -> help(ctx.getSource().getSender()))
                .then(Commands.literal("itiran")
                        .executes(ctx -> openRoleList(ctx.getSource().getSender())))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource().getSender())))
                .then(Commands.literal("editor")
                        .executes(ctx -> createRole(ctx.getSource().getSender()))
                        .then(roleArg(roleIds, this::editRole)))
                .then(Commands.literal("try")
                        .then(roleArg(roleIds, this::tryOn)))
                .then(Commands.literal("untry")
                        .executes(ctx -> untry(ctx.getSource().getSender())))
                .then(Commands.literal("role")
                        .then(Commands.argument("targets", ArgumentTypes.players())
                                .then(Commands.argument("role_id", StringArgumentType.word())
                                        .suggests(roleIds)
                                        .executes(this::applyRole))))
                .then(Commands.literal("spec")
                        .executes(ctx -> specSelf(ctx.getSource().getSender(), true))
                        .then(Commands.argument("targets", ArgumentTypes.players())
                                .executes(ctx -> spec(ctx, true))))
                .then(Commands.literal("unspec")
                        .executes(ctx -> specSelf(ctx.getSource().getSender(), false))
                        .then(Commands.argument("targets", ArgumentTypes.players())
                                .executes(ctx -> spec(ctx, false))))
                .then(Commands.literal("roleremove")
                        .then(roleArg(roleIds, this::openConfirmDelete)))
                .then(Commands.literal("export")
                        .executes(ctx -> export(ctx.getSource().getSender(), "all"))
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests(exportTargets)
                                .executes(ctx -> export(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "target")))))
                .then(Commands.literal("import")
                        .executes(ctx -> listImportable(ctx.getSource().getSender()))
                        .then(Commands.argument("file", StringArgumentType.string())
                                .suggests(importFiles)
                                .executes(ctx -> importFile(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "file"), false))
                                .then(Commands.literal("overwrite")
                                        .executes(ctx -> importFile(ctx.getSource().getSender(),
                                                StringArgumentType.getString(ctx, "file"), true)))))
                .then(Commands.literal("reload")
                        .executes(ctx -> reload(ctx.getSource().getSender())));
    }

    @FunctionalInterface
    private interface RoleAction {
        int run(CommandSender sender, Role role);
    }

    /** 役職IDを1つ受け取る引数。存在しないIDならエラーを返す */
    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> roleArg(
            SuggestionProvider<CommandSourceStack> suggestions, RoleAction action) {
        return Commands.argument("role_id", StringArgumentType.word())
                .suggests(suggestions)
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    String roleId = StringArgumentType.getString(ctx, "role_id");
                    Role role = plugin.getRoleManager().get(roleId);
                    if (role == null) {
                        send(sender, "§cRole not found: " + roleId);
                        return 0;
                    }
                    return action.run(sender, role);
                });
    }

    private static void send(CommandSender sender, String legacy) {
        sender.sendMessage(Text.of(legacy));
    }

    private static Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        send(sender, "This command can only be used by players!");
        return null;
    }

    private static String quote(String argument) {
        return StringArgumentType.escapeIfRequired(argument);
    }

    // ─────────────────────────────────────────────
    // GUIを開くコマンド
    // ─────────────────────────────────────────────

    private int openRoleList(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return 0;
        new RoleListGUI(plugin, player).open();
        return Command.SINGLE_SUCCESS;
    }

    /** 役職IDを省略すると新規作成、指定するとその役職を編集する */
    private int createRole(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return 0;
        RoleEditorGUI.create(plugin, player).open();
        return Command.SINGLE_SUCCESS;
    }

    private int editRole(CommandSender sender, Role role) {
        Player player = requirePlayer(sender);
        if (player == null) return 0;
        RoleEditorGUI.edit(plugin, player, role).open();
        return Command.SINGLE_SUCCESS;
    }

    private int openConfirmDelete(CommandSender sender, Role role) {
        Player player = requirePlayer(sender);
        if (player == null) return 0;
        new ConfirmDeleteGUI(plugin, player, role, null).open();
        return Command.SINGLE_SUCCESS;
    }

    // ─────────────────────────────────────────────
    // 試着
    // ─────────────────────────────────────────────

    private int tryOn(CommandSender sender, Role role) {
        Player player = requirePlayer(sender);
        if (player == null) return 0;
        plugin.getTrialManager().start(player, role, null);
        return Command.SINGLE_SUCCESS;
    }

    private int untry(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return 0;
        if (!plugin.getTrialManager().end(player, true)) {
            send(sender, "§e試着中ではありません");
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    // ─────────────────────────────────────────────
    // list
    // ─────────────────────────────────────────────

    private int list(CommandSender sender) {
        List<Role> roles = plugin.getRoleManager().sorted();
        if (roles.isEmpty()) {
            send(sender, "§e登録されている役職はありません");
            return 0;
        }
        send(sender, "§6=== 役職一覧 (" + roles.size() + "件) ===");
        for (Role role : roles) {
            sender.sendMessage(Text.runButton(
                    "§a" + role.getDisplayName() + " §7(" + role.getId() + ")",
                    BASE + " editor " + role.getId(), "§7クリックでエディタを開く"));
        }
        return roles.size();
    }

    // ─────────────────────────────────────────────
    // role
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

    // ─────────────────────────────────────────────
    // spec / unspec
    // ─────────────────────────────────────────────

    private int spec(CommandContext<CommandSourceStack> ctx, boolean enable) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> targets = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class)
                .resolve(ctx.getSource());
        if (targets.isEmpty()) {
            send(sender, "§eNo matching players found.");
            return 0;
        }
        return spec(sender, targets, enable);
    }

    /** 対象を省略したときは自分自身 */
    private int specSelf(CommandSender sender, boolean enable) {
        Player player = requirePlayer(sender);
        if (player == null) return 0;
        if (enable ? plugin.getSpecManager().isSpec(player) : !plugin.getSpecManager().isSpec(player)) {
            send(sender, enable ? "§eすでにスペク中です" : "§eスペク中ではありません");
            return 0;
        }
        return spec(sender, List.of(player), enable);
    }

    private int spec(CommandSender sender, List<Player> targets, boolean enable) {
        int changed = 0;
        for (Player target : targets) {
            boolean done = enable
                    ? plugin.getSpecManager().enable(target)
                    : plugin.getSpecManager().disable(target);
            if (done) changed++;
        }
        int unchanged = targets.size() - changed;
        send(sender, (enable ? "§a" + changed + "人をスペクモードにしました" : "§a" + changed + "人のスペクモードを解除しました")
                + (unchanged > 0 ? "§7（" + unchanged + "人は" + (enable ? "すでにスペク中" : "スペク中ではありません") + "）" : ""));
        return changed;
    }

    private static String rawArgument(CommandContext<CommandSourceStack> ctx, String name) {
        for (ParsedCommandNode<CommandSourceStack> node : ctx.getNodes()) {
            if (node.getNode().getName().equals(name)) return node.getRange().get(ctx.getInput());
        }
        return "?";
    }

    // ─────────────────────────────────────────────
    // export / import / reload
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

    private int listImportable(CommandSender sender) {
        List<File> files = plugin.getRoleTransfer().listImportableFiles();
        if (files.isEmpty()) {
            send(sender, "§eインポート可能なファイルがありません。");
            send(sender, "§7exports/ フォルダにYMLファイルを配置してください。");
            return 0;
        }
        send(sender, "§6=== インポート可能なファイル ===");
        for (File file : files) {
            sender.sendMessage(Text.suggestButton("§e  " + file.getName(),
                    BASE + " import " + quote(file.getName()), "§7クリックで入力欄に入れる"));
        }
        send(sender, "§7使用: " + BASE + " import <filename> [overwrite]");
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
            if (result.invalid() > 0) send(sender, "§cIDが不正なため " + result.invalid() + "件を読み込みませんでした（半角英数字とアンダースコアのみ使えます）");
            if (result.limited() > 0) {
                send(sender, "§c役職数の上限(" + plugin.getRoleManager().maxRoles() + ")に達したため "
                        + result.limited() + "件を追加できませんでした");
            }
            if (result.skipped() > 0) {
                send(sender, "§7※スキップされた役職を上書きするには overwrite オプションを使用してください");
                sender.sendMessage(Text.runButton("§b§l【上書きでインポート】",
                        BASE + " import " + quote(fileName) + " overwrite", "§7既存の役職を上書きして読み込み直す"));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandSender sender) {
        plugin.reloadPluginConfig();
        send(sender, "§aconfig.yml を再読み込みしました");
        return Command.SINGLE_SUCCESS;
    }

    // ─────────────────────────────────────────────
    // help
    // ─────────────────────────────────────────────

    private static final String[][] HELP = {
            {"itiran", "", "役職一覧をGUIで開く"},
            {"list", "", "役職の一覧"},
            {"role", " <player|@selector> <role_id>", "Apply role (@a, @s, @p, @r 対応)"},
            {"try", " <role_id>", "役職を試着"},
            {"untry", "", "試着を終了"},
            {"editor", " [role_id]", "役職を新規作成（IDを指定すると編集）"},
            {"spec", " [player|@selector]", "見えない・ぶつからない・操作できないクリエイティブにする"},
            {"unspec", " [player|@selector]", "スペクモードを解除"},
            {"roleremove", " <role_id>", "削除の確認画面を開く"},
            {"export", " [role_id|all]", "Export role(s) to YAML"},
            {"import", " [filename] [overwrite]", "Import role(s) from YAML"},
            {"reload", "", "config.yml を再読み込み"},
    };

    private int help(CommandSender sender) {
        send(sender, "§6=== Knights Editor Commands ===");
        for (String[] line : HELP) {
            sender.sendMessage(Text.suggestButton(
                    "§e" + BASE + " " + line[0] + line[1] + " §7- " + line[2],
                    BASE + " " + line[0] + (line[1].isEmpty() ? "" : " "),
                    "§7クリックで入力欄に入れる"));
        }
        return Command.SINGLE_SUCCESS;
    }
}
