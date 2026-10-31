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

/**
 * /hannyou ルートコマンド。各機能はサブコマンドとしてぶら下げる。
 * /hannyou udemae（自分のウデマエを見る）以外は OP だけ。OP でない人の補完には udemae だけが出る。
 */
public final class HannyouCommand {
    private final HANNYOUPlugin plugin;
    private final KnightsCommand knights;
    private final SpecCommand spec;
    private final UdemaeCommand udemae;

    public HannyouCommand(HANNYOUPlugin plugin) {
        this.plugin = plugin;
        this.knights = new KnightsCommand(plugin);
        this.spec = new SpecCommand(plugin);
        this.udemae = new UdemaeCommand(plugin);
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("hannyou")
                .executes(ctx -> help(ctx.getSource().getSender()))
                .then(udemae.node())
                .then(knights.node().requires(HannyouCommand::isOperator))
                .then(spec.specNode().requires(HannyouCommand::isOperator))
                .then(spec.unspecNode().requires(HannyouCommand::isOperator))
                .then(Commands.literal("reload").requires(HannyouCommand::isOperator)
                        .executes(ctx -> reload(ctx.getSource().getSender())))
                .build();
    }

    private static boolean isOperator(CommandSourceStack source) {
        return Permissions.isOperator(source.getSender());
    }

    private int help(CommandSender sender) {
        send(sender, "§6=== 汎用プラグイン（HANNYOU Plugin） ===");
        helpLine(sender, "/hannyou udemae", "", "自分のウデマエを見る");
        if (!Permissions.isOperator(sender)) return Command.SINGLE_SUCCESS;
        helpLine(sender, "/hannyou udemae", " <プレイヤー>", "ほかの人のウデマエを見る（set・add・reset で書き換え）");
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
