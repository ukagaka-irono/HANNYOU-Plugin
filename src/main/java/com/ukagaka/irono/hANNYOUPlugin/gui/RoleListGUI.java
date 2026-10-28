package com.ukagaka.irono.hANNYOUPlugin.gui;

import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.audit.AuditLogger;
import com.ukagaka.irono.hANNYOUPlugin.role.Role;
import com.ukagaka.irono.hANNYOUPlugin.role.RoleManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class RoleListGUI extends BaseGUI {
    private static final int ITEMS_PER_PAGE = 44; // スロット44は検索ボタン
    private static final int SEARCH_SLOT = 44;

    private List<Role> filtered = List.of();
    private String searchQuery = "";

    public RoleListGUI(HANNYOUPlugin plugin, Player player) {
        super(plugin, player);
        reload();
    }

    private void reload() {
        List<Role> all = plugin.getRoleManager().sorted();
        if (searchQuery.isEmpty()) {
            filtered = all;
        } else {
            String q = searchQuery.toLowerCase(Locale.ROOT);
            filtered = all.stream()
                    .filter(r -> r.getId().toLowerCase(Locale.ROOT).contains(q)
                            || r.getPlainName().toLowerCase(Locale.ROOT).contains(q))
                    .toList();
        }
        totalPages = pageCount(filtered.size(), ITEMS_PER_PAGE);
        currentPage = Math.min(currentPage, totalPages);
    }

    @Override
    protected String title() {
        return searchQuery.isEmpty()
                ? "役職一覧 - ページ " + currentPage + "/" + totalPages
                : "役職一覧 [検索: " + searchQuery + "] " + currentPage + "/" + totalPages;
    }

    @Override
    protected void render() {
        int start = (currentPage - 1) * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, filtered.size());
        for (int i = start; i < end; i++) {
            Role role = filtered.get(i);
            button(i - start, Items.named(role.getIcon(), "§a" + role.getDisplayName(), List.of(
                            "§7ID: " + role.getId(),
                            "",
                            "§e左クリック: 編集",
                            "§b右クリック: コピー",
                            "§dShift+左クリック: 試着")),
                    e -> {
                        playClickSound();
                        Role current = plugin.getRoleManager().get(role.getId());
                        if (current == null) return;
                        switch (e.getClick()) {
                            case SHIFT_LEFT -> transition(() -> {
                                player.closeInventory();
                                plugin.getTrialManager().start(player, current, () -> new RoleListGUI(plugin, player).open());
                            });
                            case RIGHT -> promptCopy(current);
                            case LEFT -> switchTo(RoleEditorGUI.edit(plugin, player, current));
                            default -> {}
                        }
                    });
        }

        fillBackground();
        addNavigationButtons();
        addBottomBar();
    }

    private void addBottomBar() {
        if (searchQuery.isEmpty()) {
            button(SEARCH_SLOT, Items.item(Material.COMPASS, "§b検索",
                            "§7IDまたは表示名で絞り込みます",
                            "§eクリックで検索ワード入力"),
                    e -> { playClickSound(); openSearch(); });
        } else {
            button(SEARCH_SLOT, Items.item(Material.CLOCK, "§b検索中: §f" + searchQuery,
                            "§7絞り込み中: " + filtered.size() + "件",
                            "§c右クリックで検索クリア",
                            "§e左クリックで再検索"),
                    e -> {
                        playClickSound();
                        if (e.isRightClick()) applySearch("");
                        else openSearch();
                    });
        }

        button(49, Items.item(Material.ARROW, "§bメインメニューに戻る"),
                e -> { playClickSound(); switchTo(new MainMenuGUI(plugin, player)); });
    }

    /** コピー先の role id を入力してもらう */
    private void promptCopy(Role original) {
        RoleManager roles = plugin.getRoleManager();
        if (!roles.hasCapacity()) {
            sendMessage("§c役職数の上限(" + roles.maxRoles() + ")に達しています");
            playErrorSound();
            return;
        }
        transition(() -> TextPrompt.open(plugin, player, "§bコピー先の role id を入力",
                Items.item(Material.PAPER, "§f新しい role id を入力",
                        "§7コピー元: " + original.getId(),
                        "§7半角英数字とアンダースコアのみ使用可能"),
                input -> copyRole(original, input),
                this::open));
    }

    private void copyRole(Role original, String newId) {
        // 入力している間に OP を外された場合に備えて、確定時にも確かめる
        if (!requireOperator()) return;
        RoleManager roles = plugin.getRoleManager();
        if (newId.isEmpty()) {
            // 何も入力しなければコピーしない
        } else if (!RoleManager.ID_PATTERN.matcher(newId).matches()) {
            sendMessage("§crole id は半角英数字とアンダースコアのみ使用できます");
            playErrorSound();
        } else if (roles.exists(newId)) {
            sendMessage("§crole id「" + newId + "」は既に存在します");
            playErrorSound();
        } else if (!roles.hasCapacity()) {
            sendMessage("§c役職数の上限(" + roles.maxRoles() + ")に達しています");
            playErrorSound();
        } else {
            roles.save(original.copy(newId, original.getDisplayName() + " (コピー)"));
            plugin.getAuditLogger().log(player, AuditLogger.Action.ROLE_COPY, newId, "コピー元: " + original.getId());
            sendMessage("§a役職「" + original.getDisplayName() + "§a」を role id「" + newId + "」にコピーしました");
            playSuccessSound();
        }
        reload();
        open();
    }

    private void openSearch() {
        transition(() -> TextPrompt.open(plugin, player, "§b役職を検索",
                Items.item(Material.COMPASS, "§f検索ワードを入力",
                        "§7IDまたは表示名で絞り込みます",
                        "§7部分一致で検索できます"),
                this::applySearch,
                this::open));
    }

    private void applySearch(String query) {
        searchQuery = query == null ? "" : query.trim();
        currentPage = 1;
        reload();
        if (player.getOpenInventory().getTopInventory() == getInventory()) {
            refresh();
        } else {
            open();
        }
    }
}
