package com.ukagaka.irono.hANNYOUPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 全GUI・全文字入力プロンプトのイベントを1つのリスナーで振り分ける */
public final class GuiListener implements Listener {
    private final Map<UUID, TextPrompt> prompts = new HashMap<>();

    void track(Player player, TextPrompt prompt) {
        prompts.put(player.getUniqueId(), prompt);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder(false) instanceof BaseGUI gui) {
            gui.dispatchClick(event);
            return;
        }
        TextPrompt prompt = prompts.get(event.getWhoClicked().getUniqueId());
        if (prompt != null && prompt.isView(event.getView())) prompt.handleClick(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof BaseGUI gui) {
            gui.dispatchDrag(event);
            return;
        }
        TextPrompt prompt = prompts.get(event.getWhoClicked().getUniqueId());
        if (prompt != null && prompt.isView(event.getView())) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof BaseGUI gui) {
            gui.handleClose(event);
            return;
        }
        UUID id = event.getPlayer().getUniqueId();
        TextPrompt prompt = prompts.get(id);
        if (prompt != null && prompt.isView(event.getView())) {
            prompts.remove(id);
            prompt.handleClose();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
    }

    /** プラグイン停止時、開いているGUIをすべて閉じる */
    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            var top = player.getOpenInventory().getTopInventory();
            if (top.getHolder(false) instanceof BaseGUI || prompts.containsKey(player.getUniqueId())) {
                player.closeInventory();
            }
        }
        prompts.clear();
    }
}
