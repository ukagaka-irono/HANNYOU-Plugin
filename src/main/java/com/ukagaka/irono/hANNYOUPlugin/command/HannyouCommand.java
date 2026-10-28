package com.ukagaka.irono.hANNYOUPlugin.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.Permissions;
import com.ukagaka.irono.hANNYOUPlugin.util.Text;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;

/** /hannyou ルートコマンド。各機能はサブコマンドとしてぶら下げる */
public final class HannyouCommand {
    private final HANNYOUPlugin plugin;
    private final KnightsCommand knights;

    public HannyouCommand(HANNYOUPlugin plugin) {
        this.plugin = plugin;
        this.knights = new KnightsCommand(plugin);
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("hannyou")
                .requires(src -> Permissions.isOperator(src.getSender()))
                .executes(ctx -> help(ctx.getSource().getSender()))
                .then(knights.node())
                .then(Commands.literal("reload")
                        .executes(ctx -> reload(ctx.getSource().getSender())))
                .build();
    }

    private int help(CommandSender sender) {
        sender.sendMessage(Text.of("§6=== HANNYOU Plugin ==="));
        sender.sendMessage(Text.suggestButton("§e/hannyou knights §7- 役職管理 (Knights Editor)",
                "/hannyou knights ", "§7クリックで入力欄に入れる"));
        sender.sendMessage(Text.suggestButton("§e/hannyou reload §7- config.yml を再読み込み",
                "/hannyou reload", "§7クリックで入力欄に入れる"));
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandSender sender) {
        plugin.reloadPluginConfig();
        sender.sendMessage(Text.of("§aconfig.yml を再読み込みしました"));
        return Command.SINGLE_SUCCESS;
    }
}
