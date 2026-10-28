package com.ukagaka.irono.hANNYOUPlugin;

import org.bukkit.configuration.file.FileConfiguration;

public record PluginConfig(
        boolean logRoleChanges,
        int maxRoles,
        boolean reapplyItemsOnJoin
) {
    public static PluginConfig from(FileConfiguration config) {
        return new PluginConfig(
                config.getBoolean("log-role-changes", true),
                config.getInt("settings.max-roles", -1),
                config.getBoolean("settings.reapply-items-on-join", true)
        );
    }
}
