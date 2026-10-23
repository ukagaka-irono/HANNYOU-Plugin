package com.ukagaka.irono.hANNYOUPlugin.gui;

import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.AnvilView;

import java.util.function.Consumer;

/**
 * 金床を使った文字入力。
 * 入力欄の文字列は AnvilView#getRenameText で直接取得するため、結果スロットの生成や
 * 経験値コストに依存しない。リスナーは GuiListener 1つで全プロンプトを処理する。
 */
public final class TextPrompt {
    private static final int RESULT_SLOT = 2;

    private final HANNYOUPlugin plugin;
    private final Player player;
    private final AnvilView view;
    private final String placeholder;
    private final Consumer<String> onSubmit;
    private final Runnable onCancel;
    private boolean submitted;

    private TextPrompt(HANNYOUPlugin plugin, Player player, AnvilView view, String placeholder,
                       Consumer<String> onSubmit, Runnable onCancel) {
        this.plugin = plugin;
        this.player = player;
        this.view = view;
        this.placeholder = placeholder;
        this.onSubmit = onSubmit;
        this.onCancel = onCancel;
    }

    /**
     * @param onSubmit 確定時に入力文字列（未入力なら空文字）を受け取る
     * @param onCancel 確定せずに閉じられた時に呼ばれる
     */
    public static void open(HANNYOUPlugin plugin, Player player, String title, ItemStack input,
                            Consumer<String> onSubmit, Runnable onCancel) {
        AnvilView view = MenuType.ANVIL.builder()
                .title(Text.of(title))
                .checkReachable(false)
                .build(player);
        view.getTopInventory().setItem(0, input);
        Component name = input.getItemMeta().customName();
        String placeholder = name == null ? "" : PlainTextComponentSerializer.plainText().serialize(name);

        plugin.getGuiListener().track(player, new TextPrompt(plugin, player, view, placeholder, onSubmit, onCancel));
        player.openInventory(view);
    }

    boolean isView(InventoryView other) {
        return view.getTopInventory().equals(other.getTopInventory());
    }

    void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (submitted || event.getRawSlot() != RESULT_SLOT) return;

        submitted = true;
        String text = view.getRenameText();
        String input = text == null || text.equals(placeholder) ? "" : text.trim();
        runLater(() -> onSubmit.accept(input));
    }

    /** 閉じる時に入力用アイテムを消す（金床の仕様でプレイヤーに返却されるのを防ぐ） */
    void handleClose() {
        view.getTopInventory().clear();
        if (!submitted) runLater(onCancel);
    }

    private void runLater(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) task.run();
        });
    }
}
