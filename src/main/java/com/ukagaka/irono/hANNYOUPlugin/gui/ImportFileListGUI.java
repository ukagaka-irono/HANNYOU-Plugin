package com.ukagaka.irono.hANNYOUPlugin.gui;

import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.transfer.RoleTransfer;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;

public final class ImportFileListGUI extends BaseGUI {
    private static final int ITEMS_PER_PAGE = 45;

    private final List<File> files;

    public ImportFileListGUI(HANNYOUPlugin plugin, Player player) {
        super(plugin, player);
        this.files = plugin.getRoleTransfer().listImportableFiles();
        this.totalPages = pageCount(files.size(), ITEMS_PER_PAGE);
    }

    @Override
    protected String title() {
        return "インポート - ファイル選択 " + currentPage + "/" + totalPages;
    }

    @Override
    protected void render() {
        if (files.isEmpty()) {
            set(22, Items.item(Material.BARRIER, "§cインポート可能なファイルがありません",
                    "§7exports/ フォルダにYMLファイルを置いてください",
                    "§7/knights export で出力できます"));
        } else {
            int start = (currentPage - 1) * ITEMS_PER_PAGE;
            int end = Math.min(start + ITEMS_PER_PAGE, files.size());
            for (int i = start; i < end; i++) {
                File file = files.get(i);
                boolean isAll = file.getName().startsWith("roles_all_");
                button(i - start, Items.item(isAll ? Material.CHEST : Material.PAPER, "§e" + file.getName(),
                                "§7" + (isAll ? "全件エクスポートファイル" : "単一役職ファイル"),
                                "§7サイズ: " + String.format("%.1f KB", file.length() / 1024.0),
                                "§a左クリック: インポート（重複スキップ）",
                                "§b右クリック: インポート（重複上書き）"),
                        e -> { playClickSound(); doImport(file.getName(), e.isRightClick()); });
            }
        }

        fillBackground();
        addNavigationButtons();
        button(49, Items.item(Material.ARROW, "§bメインメニューに戻る"),
                e -> { playClickSound(); switchTo(new MainMenuGUI(plugin, player)); });
    }

    private void doImport(String fileName, boolean overwrite) {
        RoleTransfer transfer = plugin.getRoleTransfer();
        File file = transfer.resolve(fileName);
        if (file == null) {
            sendMessage("§cファイルが見つかりません: " + fileName);
            playErrorSound();
            return;
        }

        transfer.load(file).thenAccept(imported -> {
            if (!player.isOnline()) return;
            if (imported.isEmpty()) {
                sendMessage("§cインポートに失敗しました。ファイルを確認してください。");
                playErrorSound();
                return;
            }

            RoleTransfer.ImportResult result = transfer.register(imported, overwrite, player, fileName);
            sendMessage("§aインポート完了: §f" + result.added() + "件追加"
                    + (result.skipped() > 0 ? "§e, " + result.skipped() + "件スキップ" : ""));
            if (result.skipped() > 0) sendMessage("§7右クリックで上書きインポートできます");
            if (result.limited() > 0) {
                sendMessage("§c役職数の上限(" + plugin.getRoleManager().maxRoles() + ")に達したため "
                        + result.limited() + "件を追加できませんでした");
            }
            playSuccessSound();
            new MainMenuGUI(plugin, player).open();
        });
    }
}
