package com.ukagaka.irono.hANNYOUPlugin.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.Permissions;
import com.ukagaka.irono.hANNYOUPlugin.audit.AuditLogger;
import com.ukagaka.irono.hANNYOUPlugin.gui.ConfirmDeleteGUI;
import com.ukagaka.irono.hANNYOUPlugin.gui.RoleEditorGUI;
import com.ukagaka.irono.hANNYOUPlugin.gui.RoleListGUI;
import com.ukagaka.irono.hANNYOUPlugin.role.Role;
import com.ukagaka.irono.hANNYOUPlugin.util.Text;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.ukagaka.irono.hANNYOUPlugin.command.CommandSupport.NO_PLAYERS;
import static com.ukagaka.irono.hANNYOUPlugin.command.CommandSupport.helpLine;
import static com.ukagaka.irono.hANNYOUPlugin.command.CommandSupport.requirePlayer;
import static com.ukagaka.irono.hANNYOUPlugin.command.CommandSupport.send;

/**
 * /hannyou knights サブコマンド（Brigadier）。役職の一覧・作成・編集・付与・削除。
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

        return Commands.literal("knights")
                .executes(ctx -> help(ctx.getSource().getSender()))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource().getSender()))
                        .then(roleArg(roleIds, this::details)))
                .then(Commands.literal("editor")
                        .executes(ctx -> createRole(ctx.getSource().getSender()))
                        .then(roleArg(roleIds, this::editRole)))
                .then(Commands.literal("give")
                        .then(Commands.argument("targets", ArgumentTypes.players())
                                .then(Commands.argument("role_id", StringArgumentType.word())
                                        .suggests(roleIds)
                                        .executes(this::give))))
                .then(Commands.literal("remove")
                        .then(roleArg(roleIds, this::openConfirmDelete)));
    }

    @FunctionalInterface
    private interface RoleAction {
        int run(CommandSender sender, Role role);
    }

    /** role id を1つ受け取る引数。存在しない role id ならエラーを返す */
    private RequiredArgumentBuilder<CommandSourceStack, String> roleArg(
            SuggestionProvider<CommandSourceStack> suggestions, RoleAction action) {
        return Commands.argument("role_id", StringArgumentType.word())
                .suggests(suggestions)
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    Role role = findRole(sender, StringArgumentType.getString(ctx, "role_id"));
                    return role == null ? 0 : action.run(sender, role);
                });
    }

    private Role findRole(CommandSender sender, String roleId) {
        Role role = plugin.getRoleManager().get(roleId);
        if (role == null) send(sender, "§c役職が見つかりません: " + roleId);
        return role;
    }

    // ─────────────────────────────────────────────
    // list
    // ─────────────────────────────────────────────

    /** プレイヤーには役職一覧のGUIを開き、コンソールにはチャットで一覧を出す */
    private int list(CommandSender sender) {
        if (sender instanceof Player player) {
            new RoleListGUI(plugin, player).open();
            return Command.SINGLE_SUCCESS;
        }
        List<Role> roles = plugin.getRoleManager().sorted();
        if (roles.isEmpty()) {
            send(sender, "§e登録されている役職はありません");
            return 0;
        }
        send(sender, "§6=== 役職一覧 (" + roles.size() + "件) ===");
        for (Role role : roles) send(sender, "§a" + role.getDisplayName() + " §7(" + role.getId() + ")");
        return roles.size();
    }

    /** 役職の中身と、編集・試着・削除のボタン */
    private int details(CommandSender sender, Role role) {
        send(sender, "§6=== 役職情報: " + role.getDisplayName() + "§6 ===");
        send(sender, "§7role id: §f" + role.getId());

        Component equipment = Text.of("§7装備: ");
        String[] labels = {"頭", "胴", "脚", "足", "オフハンド"};
        Role.EquipmentSlot[] slots = Role.EquipmentSlot.values();
        for (int i = 0; i < slots.length; i++) {
            ItemStack item = role.getEquipment(slots[i]);
            equipment = equipment.append(Text.of((i == 0 ? "" : " §8/ ") + "§7" + labels[i] + " "))
                    .append(item == null ? Text.of("§8なし") : item.displayName());
        }
        sender.sendMessage(equipment);

        long inv = Arrays.stream(role.getInventory()).filter(Objects::nonNull).count();
        long bar = Arrays.stream(role.getHotbar()).filter(Objects::nonNull).count();
        send(sender, "§7インベントリ: §f" + inv + "枠 §7/ ホットバー: §f" + bar + "枠");

        List<String> effects = new ArrayList<>();
        role.getEffects().forEach((type, level) ->
                effects.add(RoleEditorGUI.effectName(type) + " " + "I".repeat(level)));
        send(sender, "§7効果: §f" + (effects.isEmpty() ? "なし" : String.join(", ", effects)));

        String id = role.getId();
        sender.sendMessage(Text.runButton("§e【編集】", BASE + " editor " + id, "§7エディタを開く")
                .append(Text.of(" "))
                .append(Text.callbackButton("§d【試着】", "§7自分に一時的に適用する",
                        ClickCallback.UNLIMITED_USES, player -> tryOn(player, id)))
                .append(Text.of(" "))
                .append(Text.runButton("§c【削除】", BASE + " remove " + id, "§7削除の確認画面を開く")));
        return Command.SINGLE_SUCCESS;
    }

    /** 詳細の【試着】ボタン。押した時点の役職を試着する（OP でなくなっていたら何もしない） */
    private void tryOn(Player player, String roleId) {
        if (!Permissions.isOperator(player)) return;
        Role role = findRole(player, roleId);
        if (role != null) plugin.getTrialManager().start(player, role, null);
    }

    // ─────────────────────────────────────────────
    // editor / remove
    // ─────────────────────────────────────────────

    /** role id を省略すると新規作成、指定するとその役職を編集する */
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
    // give
    // ─────────────────────────────────────────────

    private int give(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        String roleId = StringArgumentType.getString(ctx, "role_id");
        Role role = findRole(sender, roleId);
        if (role == null) return 0;

        List<Player> targets = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class)
                .resolve(ctx.getSource());
        if (targets.isEmpty()) {
            send(sender, NO_PLAYERS);
            return 0;
        }

        for (Player target : targets) {
            plugin.getRoleApplier().assign(target, role);
        }

        plugin.getAuditLogger().log(sender, AuditLogger.Action.ROLE_APPLY, roleId,
                "対象: " + targets.size() + "人 / ターゲット指定: " + rawArgument(ctx, "targets"));
        send(sender, "§a役職「" + role.getDisplayName() + "§a」を " + targets.size() + "人に付与しました");
        return targets.size();
    }

    private static String rawArgument(CommandContext<CommandSourceStack> ctx, String name) {
        for (ParsedCommandNode<CommandSourceStack> node : ctx.getNodes()) {
            if (node.getNode().getName().equals(name)) return node.getRange().get(ctx.getInput());
        }
        return "?";
    }

    // ─────────────────────────────────────────────
    // help
    // ─────────────────────────────────────────────

    private static final String[][] HELP = {
            {"list", " [role id]", "役職一覧の GUI を開く（role id を指定するとチャットに詳細）"},
            {"editor", " [role id]", "役職を新規作成する（role id を指定すると編集）"},
            {"give", " <対象> <role id>", "役職を付与する（@a などのセレクターも使える）"},
            {"remove", " <role id>", "役職を削除する（確認画面が開く）"},
    };

    private int help(CommandSender sender) {
        send(sender, "§6=== 役職（/hannyou knights） ===");
        for (String[] line : HELP) helpLine(sender, BASE + " " + line[0], line[1], line[2]);
        return Command.SINGLE_SUCCESS;
    }
}
