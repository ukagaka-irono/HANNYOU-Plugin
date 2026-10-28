package com.ukagaka.irono.hANNYOUPlugin.command;

import com.ukagaka.irono.hANNYOUPlugin.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** コマンド共通の小さな処理 */
final class CommandSupport {
    static final String NO_PLAYERS = "§e対象のプレイヤーが見つかりません";

    private CommandSupport() {}

    static void send(CommandSender sender, String legacy) {
        sender.sendMessage(Text.of(legacy));
    }

    /** プレイヤー以外（コンソールなど）なら案内を出して null を返す */
    static Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        send(sender, "§cこのコマンドはプレイヤーだけが使えます");
        return null;
    }

    /** ヘルプの1行。クリックすると入力欄にコマンドが入る */
    static void helpLine(CommandSender sender, String command, String args, String description) {
        sender.sendMessage(Text.suggestButton(
                "§e" + command + args + " §7- " + description,
                command + (args.isEmpty() ? "" : " "),
                "§7クリックで入力欄に入れる"));
    }
}
