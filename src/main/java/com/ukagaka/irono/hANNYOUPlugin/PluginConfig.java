package com.ukagaka.irono.hANNYOUPlugin;

import com.ukagaka.irono.hANNYOUPlugin.udemae.UdemaeSettings;
import org.bukkit.configuration.file.FileConfiguration;

public record PluginConfig(
        boolean logRoleChanges,
        int maxRoles,
        boolean reapplyItemsOnJoin,
        UdemaeSettings udemae
) {
    public static PluginConfig from(FileConfiguration config) {
        return new PluginConfig(
                config.getBoolean("log-role-changes", true),
                config.getInt("settings.max-roles", -1),
                config.getBoolean("settings.reapply-items-on-join", true),
                UdemaeSettings.from(config.getConfigurationSection("udemae"))
        );
    }
}
