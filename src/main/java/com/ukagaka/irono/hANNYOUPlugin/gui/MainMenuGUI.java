package com.ukagaka.irono.hANNYOUPlugin.gui;

import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class MainMenuGUI extends BaseGUI {

    public MainMenuGUI(HANNYOUPlugin plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String title() {
        return "Knights Editor - Main Menu";
    }

    @Override
    protected void render() {
        button(10, Items.item(Material.IRON_SWORD, "§a兵士の作成"),
                e -> { playClickSound(); switchTo(RoleEditorGUI.create(plugin, player)); });

        button(12, Items.item(Material.BOOK, "§e兵士一覧",
                        "§7登録済みの役職を表示します",
                        "§aクリックで開く"),
                e -> { playClickSound(); switchTo(new RoleListGUI(plugin, player)); });

        button(49, Items.item(Material.BARRIER, "§cメニューを閉じる"),
                e -> { playClickSound(); closeLater(); });

        fillBackground();
        addNavigationButtons();
    }
}
