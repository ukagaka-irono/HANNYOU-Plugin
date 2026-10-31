package com.ukagaka.irono.hANNYOUPlugin.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.Permissions;
import com.ukagaka.irono.hANNYOUPlugin.udemae.UdemaeManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.ukagaka.irono.hANNYOUPlugin.command.CommandSupport.requirePlayer;
import static com.ukagaka.irono.hANNYOUPlugin.command.CommandSupport.send;

/**
 * /hannyou udemae。自分のウデマエはだれでも見られる（このプラグインで OP でなくても使える唯一のコマンド）。
 * ほかの人のウデマエを見る・書き換えるのは OP だけ。
 */
public final class UdemaeCommand {
    private final HANNYOUPlugin plugin;

    public UdemaeCommand(HANNYOUPlugin plugin) {
        this.plugin = plugin;
    }

    private UdemaeManager udemae() {
        return plugin.getUdemaeManager();
    }

    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("udemae")
                .executes(ctx -> self(ctx.getSource().getSender()))
                .then(Commands.literal("set").requires(UdemaeCommand::isOperator)
                        .then(playerArgument()
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .suggests(this::suggestRanks)
                                        .then(Commands.argument("points", IntegerArgumentType.integer(0))
                                                .executes(this::set)))))
                .then(Commands.literal("add").requires(UdemaeCommand::isOperator)
                        .then(playerArgument()
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(this::add))))
                .then(Commands.literal("reset").requires(UdemaeCommand::isOperator)
                        .then(playerArgument().executes(this::reset)))
                .then(playerArgument().requires(UdemaeCommand::isOperator)
                        .executes(this::view));
    }

    private static boolean isOperator(CommandSourceStack source) {
        return Permissions.isOperator(source.getSender());
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> playerArgument() {
        return Commands.argument("player", StringArgumentType.word()).suggests(this::suggestPlayers);
    }

    private int self(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return 0;
        send(sender, "§6あなたのウデマエ: §e" + udemae().rank(udemae().total(player.getUniqueId())).display());
        return Command.SINGLE_SUCCESS;
    }

    private int view(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) return 0;
        int total = udemae().total(target.id());
        send(ctx.getSource().getSender(),
                "§6" + target.name() + " のウデマエ: §e" + udemae().rank(total).display() + " §7（合計 " + total + "）");
        return Command.SINGLE_SUCCESS;
    }

    private int set(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) return 0;
        String rank = ctx.getArgument("rank", String.class);
        int total = udemae().totalOf(rank, ctx.getArgument("points", Integer.class));
        if (total < 0) {
            send(ctx.getSource().getSender(), "§cランクが見つかりません: " + rank);
            return 0;
        }
        return change(ctx.getSource().getSender(), target, total);
    }

    private int add(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) return 0;
        return change(ctx.getSource().getSender(), target,
                udemae().total(target.id()) + ctx.getArgument("amount", Integer.class));
    }

    private int reset(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) return 0;
        return change(ctx.getSource().getSender(), target, 0);
    }

    private int change(CommandSender sender, Target target, int total) {
        udemae().set(target.id(), target.name(), total);
        send(sender, "§a" + target.name() + " のウデマエを §e"
                + udemae().rank(udemae().total(target.id())).display() + " §aにしました");
        return Command.SINGLE_SUCCESS;
    }

    /** コマンドで指定したプレイヤー。オンラインの人か、一度でもウデマエが記録された人 */
    private record Target(UUID id, String name) {}

    private Target target(CommandContext<CommandSourceStack> ctx) {
        String name = ctx.getArgument("player", String.class);
        UUID id = udemae().findByName(name);
        if (id == null) {
            send(ctx.getSource().getSender(), CommandSupport.NO_PLAYERS);
            return null;
        }
        Player online = Bukkit.getPlayer(id);
        return new Target(id, online != null ? online.getName() : udemae().nameOf(id));
    }

    private CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx,
                                                          SuggestionsBuilder builder) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
        names.addAll(udemae().knownNames());
        String prefix = builder.getRemainingLowerCase();
        names.stream().filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestRanks(CommandContext<CommandSourceStack> ctx,
                                                        SuggestionsBuilder builder) {
        udemae().rankNames().forEach(builder::suggest);
        return builder.buildFuture();
    }
}
