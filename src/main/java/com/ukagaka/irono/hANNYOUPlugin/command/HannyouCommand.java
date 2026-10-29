package com.ukagaka.irono.hANNYOUPlugin.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.Permissions;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;

import static com.ukagaka.irono.hANNYOUPlugin.command.CommandSupport.helpLine;
import static com.ukagaka.irono.hANNYOUPlugin.command.CommandSupport.send;

/** /hannyou ルートコマンド。各機能はサブコマンドとしてぶら下げる */
public final class HannyouCommand {
    private final HANNYOUPlugin plugin;
    private final KnightsCommand knights;
    private final SpecCommand spec;

    public HannyouCommand(HANNYOUPlugin plugin) {
        this.plugin = plugin;
        this.knights = new KnightsCommand(plugin);
        this.spec = new SpecCommand(plugin);
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("hannyou")
                .requires(src -> Permissions.isOperator(src.getSender()))
                .executes(ctx -> help(ctx.getSource().getSender()))
                .then(knights.node())
                .then(spec.specNode())
                .then(spec.unspecNode())
                .then(Commands.literal("reload")
                        .executes(ctx -> reload(ctx.getSource().getSender())))
                .build();
    }

    private int help(CommandSender sender) {
        send(sender, "§6=== 汎用プラグイン（HANNYOU Plugin） ===");
        helpLine(sender, "/hannyou knights", "", "役職の一覧・作成・付与・削除");
        helpLine(sender, "/hannyou spec", " [対象]", "見えない・ぶつからない・操作できないクリエイティブにする（省略時は自分）");
        helpLine(sender, "/hannyou unspec", " [対象]", "スペクモードを解除する（省略時は自分）");
        helpLine(sender, "/hannyou reload", "", "config.yml を再読み込みする");
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandSender sender) {
        plugin.reloadPluginConfig();
        send(sender, "§aconfig.yml を再読み込みしました");
        return Command.SINGLE_SUCCESS;
    }
}
