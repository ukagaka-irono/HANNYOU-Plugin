package com.ukagaka.irono.hANNYOUPlugin;

import com.ukagaka.irono.hANNYOUPlugin.audit.AuditLogger;
import com.ukagaka.irono.hANNYOUPlugin.command.HannyouCommand;
import com.ukagaka.irono.hANNYOUPlugin.gui.GuiListener;
import com.ukagaka.irono.hANNYOUPlugin.role.RoleApplier;
import com.ukagaka.irono.hANNYOUPlugin.role.RoleManager;
import com.ukagaka.irono.hANNYOUPlugin.role.TrialManager;
import com.ukagaka.irono.hANNYOUPlugin.spec.SpecManager;
import com.ukagaka.irono.hANNYOUPlugin.spec.SpecRestrictions;
import com.ukagaka.irono.hANNYOUPlugin.storage.RoleStorage;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class HANNYOUPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private RoleStorage storage;
    private RoleManager roleManager;
    private RoleApplier roleApplier;
    private TrialManager trialManager;
    private AuditLogger auditLogger;
    private GuiListener guiListener;
    private SpecManager specManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = PluginConfig.from(getConfig());

        storage = new RoleStorage(getDataFolder(), getLogger());
        roleManager = new RoleManager(storage, () -> pluginConfig.maxRoles());
        roleManager.load(storage.load(), getLogger());

        auditLogger = new AuditLogger(this);
        roleApplier = new RoleApplier(this);
        trialManager = new TrialManager(this);
        guiListener = new GuiListener();
        specManager = new SpecManager(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(trialManager, this);
        pm.registerEvents(roleApplier, this);
        pm.registerEvents(guiListener, this);
        pm.registerEvents(specManager, this);
        pm.registerEvents(new SpecRestrictions(specManager), this);
        specManager.start();

        HannyouCommand command = new HannyouCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.build(), "HANNYOU Plugin"));
    }

    @Override
    public void onDisable() {
        if (specManager != null) specManager.stop();
        if (guiListener != null) guiListener.closeAll();
        if (trialManager != null) trialManager.endAll();
        if (storage != null) storage.close();
    }

    /** config.yml を読み直す */
    public void reloadPluginConfig() {
        reloadConfig();
        pluginConfig = PluginConfig.from(getConfig());
    }

    public PluginConfig getPluginConfig() { return pluginConfig; }
    public RoleManager getRoleManager() { return roleManager; }
    public RoleApplier getRoleApplier() { return roleApplier; }
    public TrialManager getTrialManager() { return trialManager; }
    public AuditLogger getAuditLogger() { return auditLogger; }
    public GuiListener getGuiListener() { return guiListener; }
    public SpecManager getSpecManager() { return specManager; }
}
