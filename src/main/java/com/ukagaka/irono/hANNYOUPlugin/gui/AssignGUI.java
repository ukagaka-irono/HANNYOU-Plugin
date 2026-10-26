package com.ukagaka.irono.hANNYOUPlugin.gui;

import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.audit.AuditLogger;
import com.ukagaka.irono.hANNYOUPlugin.role.Role;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** オンラインプレイヤーを選んで役職を付与する画面 */
public final class AssignGUI extends BaseGUI {
    private static final int ITEMS_PER_PAGE = 45;

    private final String roleId;
    private final Supplier<BaseGUI> back;
    private final Set<UUID> selected = new HashSet<>();
    private List<Player> players = List.of();

    /** @param back 「戻る」で開く画面 */
    public AssignGUI(HANNYOUPlugin plugin, Player player, String roleId, Supplier<BaseGUI> back) {
        super(plugin, player);
        this.roleId = roleId;
        this.back = back;
    }

    private Role role() {
        return plugin.getRoleManager().get(roleId);
    }

    @Override
    protected String title() {
        Role role = role();
        return "役職を付与 - " + (role == null ? roleId : role.getDisplayName());
    }

    @Override
    protected void render() {
        players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        selected.removeIf(id -> Bukkit.getPlayer(id) == null);
        totalPages = pageCount(players.size(), ITEMS_PER_PAGE);
        currentPage = Math.min(currentPage, totalPages);

        int start = (currentPage - 1) * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, players.size());
        for (int i = start; i < end; i++) {
            Player target = players.get(i);
            int slot = i - start;
            button(slot, head(target), e -> {
                if (!selected.remove(target.getUniqueId())) selected.add(target.getUniqueId());
                playClickSound();
                set(slot, head(target));
            });
        }

        fillBackground();
        addNavigationButtons();

        button(48, Items.item(Material.EMERALD_BLOCK, "§a選択した人に付与",
                        "§7選択中のプレイヤーに役職を付与します",
                        "§7（インベントリは役職の中身に置き換わります）"),
                e -> assign(selected.stream().map(Bukkit::getPlayer).filter(p -> p != null).toList(), "GUI: 選択"));
        button(49, Items.item(Material.ARROW, "§b戻る"),
                e -> { playClickSound(); switchTo(back.get()); });
        button(50, Items.item(Material.GOLD_BLOCK, "§6全員に付与",
                        "§7オンラインの全員に役職を付与します",
                        "§7（インベントリは役職の中身に置き換わります）"),
                e -> assign(List.copyOf(Bukkit.getOnlinePlayers()), "GUI: 全員"));
    }

    private ItemStack head(Player target) {
        Role current = plugin.getRoleManager().findRole(target);
        boolean isSelected = selected.contains(target.getUniqueId());
        boolean holds = current != null && current.getId().equals(roleId);

        List<String> lore = new ArrayList<>();
        lore.add("§7現在の役職: " + (current == null ? "§8なし" : "§f" + current.getDisplayName()));
        if (holds) lore.add("§e✦ この役職を持っています");
        lore.add("");
        lore.add(isSelected ? "§a✔ 選択中 §7(クリックで解除)" : "§eクリックで選択");

        ItemStack head = Items.named(new ItemStack(Material.PLAYER_HEAD),
                (isSelected ? "§a" : "§f") + target.getName(), lore);
        head.editMeta(SkullMeta.class, meta -> {
            meta.setPlayerProfile(target.getPlayerProfile());
            meta.setEnchantmentGlintOverride(isSelected);
        });
        return head;
    }

    private void assign(List<Player> targets, String detail) {
        Role role = role();
        if (role == null) {
            sendMessage("§cRole not found: " + roleId);
            playErrorSound();
            return;
        }
        if (targets.isEmpty()) {
            sendMessage("§eプレイヤーが選択されていません");
            playErrorSound();
            return;
        }

        for (Player target : targets) plugin.getRoleApplier().assign(target, role);
        plugin.getAuditLogger().log(player, AuditLogger.Action.ROLE_APPLY, roleId,
                "対象: " + targets.size() + "人 / " + detail);
        sendMessage("§aApplied role '" + role.getDisplayName() + "§a' to " + targets.size() + " player(s).");
        playSuccessSound();
        selected.clear();
        refresh();
    }
}
