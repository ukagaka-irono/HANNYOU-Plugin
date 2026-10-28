package com.ukagaka.irono.hANNYOUPlugin;

import org.bukkit.permissions.ServerOperator;

/** このプラグインのコマンドと GUI は OP だけが使える */
public final class Permissions {
    private Permissions() {}

    public static boolean isOperator(ServerOperator target) {
        return target.isOp();
    }
}
