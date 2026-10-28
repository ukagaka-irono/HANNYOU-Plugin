package com.ukagaka.irono.hANNYOUPlugin.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.spec.SpecManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

import static com.ukagaka.irono.hANNYOUPlugin.command.CommandSupport.NO_PLAYERS;
import static com.ukagaka.irono.hANNYOUPlugin.command.CommandSupport.requirePlayer;
import static com.ukagaka.irono.hANNYOUPlugin.command.CommandSupport.send;

/** /hannyou spec と /hannyou unspec。対象を省略すると自分自身 */
public final class SpecCommand {
    private final HANNYOUPlugin plugin;

    public SpecCommand(HANNYOUPlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> specNode() {
        return node("spec", true);
    }

    public LiteralArgumentBuilder<CommandSourceStack> unspecNode() {
        return node("unspec", false);
    }

    private LiteralArgumentBuilder<CommandSourceStack> node(String name, boolean enable) {
        return Commands.literal(name)
                .executes(ctx -> self(ctx.getSource().getSender(), enable))
                .then(Commands.argument("targets", ArgumentTypes.players())
                        .executes(ctx -> targets(ctx, enable)));
    }

    private int targets(CommandContext<CommandSourceStack> ctx, boolean enable) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> targets = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class)
                .resolve(ctx.getSource());
        if (targets.isEmpty()) {
            send(sender, NO_PLAYERS);
            return 0;
        }
        return apply(sender, targets, enable);
    }

    private int self(CommandSender sender, boolean enable) {
        Player player = requirePlayer(sender);
        if (player == null) return 0;
        if (enable == plugin.getSpecManager().isSpec(player)) {
            send(sender, enable ? "§eすでにスペク中です" : "§eスペク中ではありません");
            return 0;
        }
        return apply(sender, List.of(player), enable);
    }

    private int apply(CommandSender sender, List<Player> targets, boolean enable) {
        SpecManager spec = plugin.getSpecManager();
        int changed = 0;
        for (Player target : targets) {
            if (enable ? spec.enable(target) : spec.disable(target)) changed++;
        }
        int unchanged = targets.size() - changed;
        send(sender, (enable ? "§a" + changed + "人をスペクモードにしました" : "§a" + changed + "人のスペクモードを解除しました")
                + (unchanged > 0 ? "§7（" + unchanged + "人は" + (enable ? "すでにスペク中" : "スペク中ではありません") + "）" : ""));
        return changed;
    }
}
