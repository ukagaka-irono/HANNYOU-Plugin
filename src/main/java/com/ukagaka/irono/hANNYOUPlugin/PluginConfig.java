package com.ukagaka.irono.hANNYOUPlugin;

import org.bukkit.configuration.file.FileConfiguration;

public record PluginConfig(
        boolean logRoleChanges,
        int deletionTimeoutSeconds,
        int maxRoles,
        boolean reapplyItemsOnJoin
) {
    public static PluginConfig from(FileConfiguration config) {
        return new PluginConfig(
                config.getBoolean("log-role-changes", true),
                Math.max(1, config.getInt("settings.deletion-timeout", 30)),
                config.getInt("settings.max-roles", -1),
                config.getBoolean("settings.reapply-items-on-join", true)
        );
    }
}
