package com.ukagaka.irono.hANNYOUPlugin.gui;

import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * 54スロットGUIの基底クラス。
 * GUI自身を InventoryHolder にすることで、タイトル文字列の比較や静的セッションMapなしに
 * クリックされたインベントリから直接GUIを特定できる。ボタンはスロット番号→処理の配列で持つ。
 */
public abstract class BaseGUI implements InventoryHolder {
    protected static final int SIZE = 54;
    protected static final int PREV_SLOT = 45;
    protected static final int NEXT_SLOT = 53;

    @FunctionalInterface
    public interface ClickAction {
        void onClick(InventoryClickEvent event);
    }

    protected final HANNYOUPlugin plugin;
    protected final Player player;
    protected int currentPage = 1;
    protected int totalPages = 1;

    private final ClickAction[] actions = new ClickAction[SIZE];
    private Inventory inventory;
    private Component renderedTitle;
    /** 画面遷移を予約中（次tickまでのクリックで古い画面の内容を読み書きしないよう全て無効にする） */
    private boolean transitioning;

    protected BaseGUI(HANNYOUPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    /** 現在の状態でのタイトル（§カラーコード） */
    protected abstract String title();

    /** インベントリの中身を描画する（描画前に中身とボタンはクリア済み） */
    protected abstract void render();

    /** スロット数（9の倍数、最大54） */
    protected int size() {
        return SIZE;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    // ─────────────────────────────────────────────
    // 表示
    // ─────────────────────────────────────────────

    public void open() {
        show();
        playClickSound();
    }

    private void show() {
        transitioning = false;
        Component title = Text.of(title());
        if (inventory == null || !title.equals(renderedTitle)) {
            inventory = Bukkit.createInventory(this, size(), title);
            renderedTitle = title;
        }
        draw();
        player.openInventory(inventory);
    }

    private void draw() {
        inventory.clear();
        Arrays.fill(actions, null);
        render();
    }

    /** 再描画する。タイトルが変わらなければ開き直さずその場で書き換える（カーソル位置もリセットされない） */
    protected void refresh() {
        boolean viewing = player.getOpenInventory().getTopInventory() == inventory;
        if (viewing && Text.of(title()).equals(renderedTitle)) {
            draw();
        } else {
            transition(this::show);
        }
    }

    /** 別のGUIへ移動する（クリックイベント中に開き直さないよう1tick後に実行） */
    protected void switchTo(BaseGUI next) {
        transition(next::open);
    }

    protected void closeLater() {
        transition(player::closeInventory);
    }

    /** この画面を離れる処理を1tick後に実行し、それまでの操作を無効にする */
    protected void transition(Runnable task) {
        transitioning = true;
        runLater(task);
    }

    protected void runLater(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) task.run();
        });
    }

    // ─────────────────────────────────────────────
    // 部品
    // ─────────────────────────────────────────────

    protected void set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    protected ItemStack get(int slot) {
        return inventory.getItem(slot);
    }

    protected void button(int slot, ItemStack item, ClickAction action) {
        inventory.setItem(slot, item);
        actions[slot] = action;
    }

    protected void fillBackground() {
        ItemStack glass = Items.glass();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack current = inventory.getItem(i);
            if (current == null || current.isEmpty()) inventory.setItem(i, glass);
        }
    }

    protected void fillGlass(int... slots) {
        ItemStack glass = Items.glass();
        for (int slot : slots) inventory.setItem(slot, glass);
    }

    protected void addNavigationButtons() {
        ItemStack arrow = Items.item(Material.SPECTRAL_ARROW, "§e≫ [" + currentPage + "/" + totalPages + "]");
        button(PREV_SLOT, arrow, e -> changePage(-1));
        button(NEXT_SLOT, arrow, e -> changePage(1));
    }

    protected void changePage(int delta) {
        int target = currentPage + delta;
        if (target < 1 || target > totalPages) {
            playErrorSound();
            return;
        }
        beforePageChange();
        currentPage = target;
        playClickSound();
        refresh();
    }

    /** ページ切り替え直前に呼ばれる（編集中の内容を取り込む等） */
    protected void beforePageChange() {}

    protected static int pageCount(int items, int perPage) {
        return Math.max(1, (items + perPage - 1) / perPage);
    }

    // ─────────────────────────────────────────────
    // イベント（GuiListenerから呼ばれる）
    // ─────────────────────────────────────────────

    final void dispatchClick(InventoryClickEvent event) {
        if (transitioning) event.setCancelled(true);
        else handleClick(event);
    }

    final void dispatchDrag(InventoryDragEvent event) {
        if (transitioning) event.setCancelled(true);
        else handleDrag(event);
    }

    /** 既定ではすべてのクリックをキャンセルし、ボタンがあれば実行する */
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        runButton(event);
    }

    protected boolean runButton(InventoryClickEvent event) {
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= inventory.getSize() || actions[raw] == null) return false;
        event.setCancelled(true);
        actions[raw].onClick(event);
        return true;
    }

    public void handleDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    public void handleClose(InventoryCloseEvent event) {}

    // ─────────────────────────────────────────────
    // メッセージ・効果音
    // ─────────────────────────────────────────────

    protected void sendMessage(String legacy) {
        player.sendMessage(Text.of(legacy));
    }

    protected void playClickSound() {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    protected void playSuccessSound() {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
    }

    protected void playErrorSound() {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
    }
}
