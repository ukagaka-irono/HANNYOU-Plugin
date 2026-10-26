package com.ukagaka.irono.hANNYOUPlugin;

import org.bukkit.permissions.Permissible;

public final class Permissions {
    public static final String KNIGHTS_ADMIN = "hannyou.knights.admin";
    public static final String KNIGHTS_MENU = "hannyou.knights.menu";

    private Permissions() {}

    public static boolean canUseKnights(Permissible target) {
        return target.hasPermission(KNIGHTS_ADMIN) || target.hasPermission(KNIGHTS_MENU);
    }
}
