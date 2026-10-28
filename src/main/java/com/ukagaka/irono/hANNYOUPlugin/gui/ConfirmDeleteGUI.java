package com.ukagaka.irono.hANNYOUPlugin.gui;

import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.role.Role;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Supplier;

/** 役職削除の確認画面 */
public final class ConfirmDeleteGUI extends BaseGUI {
    private final Role role;
    private final Supplier<BaseGUI> back;

    /** @param back 削除後・キャンセル後に開く画面（null なら画面を閉じる） */
    public ConfirmDeleteGUI(HANNYOUPlugin plugin, Player player, Role role, Supplier<BaseGUI> back) {
        super(plugin, player);
        this.role = role;
        this.back = back;
    }

    @Override
    protected int size() {
        return 27;
    }

    @Override
    protected String title() {
        return "役職を削除 - " + role.getDisplayName();
    }

    @Override
    protected void render() {
        int holders = plugin.getRoleManager().onlineHolders(role.getId()).size();
        set(13, Items.named(role.getIcon(), "§a" + role.getDisplayName(), List.of(
                "§7ID: " + role.getId(),
                "§7オンラインの保持者: " + holders + "人")));

        button(11, Items.item(Material.LIME_CONCRETE, "§a削除する",
                        "§7この役職を削除します",
                        "§7保持者からタグと役職の効果を外します",
                        "§7（アイテムはそのまま）",
                        "§c※元に戻せません"),
                e -> delete());

        button(15, Items.item(Material.RED_CONCRETE, "§cやめる", "§7削除せずに戻ります"),
                e -> { playClickSound(); leave(); });

        fillBackground();
    }

    private void delete() {
        Role current = plugin.getRoleManager().get(role.getId());
        if (current == null) {
            sendMessage("§c役職が見つかりません: " + role.getId());
            playErrorSound();
        } else {
            plugin.getRoleApplier().deleteRole(player, current);
            sendMessage("§a役職「" + current.getDisplayName() + "§a」を削除しました");
            playSuccessSound();
        }
        leave();
    }

    private void leave() {
        if (back == null) closeLater();
        else switchTo(back.get());
    }
}
