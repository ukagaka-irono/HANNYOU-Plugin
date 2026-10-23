package com.ukagaka.irono.hANNYOUPlugin;

import com.ukagaka.irono.hANNYOUPlugin.audit.AuditLogger;
import com.ukagaka.irono.hANNYOUPlugin.command.KnightsCommand;
import com.ukagaka.irono.hANNYOUPlugin.gui.GuiListener;
import com.ukagaka.irono.hANNYOUPlugin.role.RoleApplier;
import com.ukagaka.irono.hANNYOUPlugin.role.RoleManager;
import com.ukagaka.irono.hANNYOUPlugin.storage.RoleStorage;
import com.ukagaka.irono.hANNYOUPlugin.transfer.RoleTransfer;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class HANNYOUPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private RoleStorage storage;
    private RoleManager roleManager;
    private RoleApplier roleApplier;
    private RoleTransfer roleTransfer;
    private AuditLogger auditLogger;
    private GuiListener guiListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = PluginConfig.from(getConfig());

        storage = new RoleStorage(getDataFolder(), getLogger());
        roleManager = new RoleManager(storage, () -> pluginConfig.maxRoles());
        roleManager.load(storage.open(), getLogger());
        if (!storage.isConnected()) {
            getLogger().warning("データベースに接続できないため、役職は再起動で失われます");
        }

        auditLogger = new AuditLogger(this);
        roleTransfer = new RoleTransfer(this);
        roleApplier = new RoleApplier(this);
        guiListener = new GuiListener();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(roleApplier, this);
        pm.registerEvents(guiListener, this);

        KnightsCommand command = new KnightsCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.build(), "Knights Editor - 役職管理"));
    }

    @Override
    public void onDisable() {
        if (guiListener != null) guiListener.closeAll();
        if (storage != null) storage.close();
        if (auditLogger != null) auditLogger.close();
    }

    public PluginConfig getPluginConfig() { return pluginConfig; }
    public RoleManager getRoleManager() { return roleManager; }
    public RoleApplier getRoleApplier() { return roleApplier; }
    public RoleTransfer getRoleTransfer() { return roleTransfer; }
    public AuditLogger getAuditLogger() { return auditLogger; }
    public GuiListener getGuiListener() { return guiListener; }
}
