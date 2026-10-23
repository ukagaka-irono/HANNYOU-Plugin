package com.ukagaka.irono.hANNYOUPlugin.gui;

import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.audit.AuditLogger;
import com.ukagaka.irono.hANNYOUPlugin.role.Role;
import com.ukagaka.irono.hANNYOUPlugin.role.RoleManager;
import com.ukagaka.irono.hANNYOUPlugin.util.Text;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 役職の作成・編集GUI（旧 RoleCreationGUI / RoleEditGUI を統合）。
 * 編集は役職の作業コピーに対して行い、「保存」を押した時だけ反映する。
 */
public final class RoleEditorGUI extends BaseGUI {
    private enum Mode { CREATE, EDIT }

    private static final int PAGE_EQUIPMENT = 1;
    private static final int PAGE_INVENTORY = 2;
    private static final int PAGE_EFFECTS = 3;
    private static final int PAGE_SYSTEM = 4;

    private static final int SLOT_EXPORT = 48;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_SAVE = 50;
    private static final int SLOT_SYSTEM_ID = 13;
    private static final int SLOT_ICON = 22;
    private static final int SLOT_NAME = 31;
    private static final int[] BOTTOM_GLASS = {46, 47, 48, 51, 52};

    private static final Map<Role.EquipmentSlot, Integer> EQUIPMENT_SLOTS = new EnumMap<>(Map.of(
            Role.EquipmentSlot.HELMET, 11,
            Role.EquipmentSlot.CHESTPLATE, 20,
            Role.EquipmentSlot.OFFHAND, 21,
            Role.EquipmentSlot.LEGGINGS, 29,
            Role.EquipmentSlot.BOOTS, 38));
    private static final Set<Integer> EQUIPMENT_SLOT_SET = Set.copyOf(EQUIPMENT_SLOTS.values());

    private static final List<PotionEffectType> EFFECTS = List.of(
            PotionEffectType.SPEED, PotionEffectType.HASTE, PotionEffectType.STRENGTH,
            PotionEffectType.JUMP_BOOST, PotionEffectType.REGENERATION, PotionEffectType.RESISTANCE,
            PotionEffectType.FIRE_RESISTANCE, PotionEffectType.WATER_BREATHING, PotionEffectType.INVISIBILITY,
            PotionEffectType.NIGHT_VISION, PotionEffectType.HEALTH_BOOST, PotionEffectType.ABSORPTION,
            PotionEffectType.SATURATION, PotionEffectType.GLOWING, PotionEffectType.SLOW_FALLING,
            PotionEffectType.CONDUIT_POWER, PotionEffectType.DOLPHINS_GRACE, PotionEffectType.LUCK,
            PotionEffectType.HERO_OF_THE_VILLAGE, PotionEffectType.SLOWNESS, PotionEffectType.MINING_FATIGUE,
            PotionEffectType.INSTANT_DAMAGE, PotionEffectType.NAUSEA, PotionEffectType.BLINDNESS,
            PotionEffectType.HUNGER, PotionEffectType.WEAKNESS, PotionEffectType.POISON);
    private static final int[] EFFECT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23,
            28, 29, 30, 31, 32, 33, 34, 35,
            37, 38, 39, 40, 41, 42, 43};
    private static final Set<PotionEffectType> BUFFS = Set.copyOf(EFFECTS.subList(0, 19));
    private static final String[] ROMAN = {"", "I", "II", "III"};
    private static final int MAX_EFFECT_LEVEL = 3;

    /** ポーションのボタンは種類×レベルで固定なので一度だけ作る */
    private static final Map<PotionEffectType, ItemStack[]> POTION_CACHE = new HashMap<>();

    private final Mode mode;
    private final Role role;
    private final String originalId;
    private boolean autoId;
    private boolean finished;

    private RoleEditorGUI(HANNYOUPlugin plugin, Player player, Mode mode, Role role, boolean autoId) {
        super(plugin, player);
        this.mode = mode;
        this.role = role;
        this.originalId = role.getId();
        this.autoId = autoId;
        this.totalPages = 4;
    }

    public static RoleEditorGUI create(HANNYOUPlugin plugin, Player player) {
        return new RoleEditorGUI(plugin, player, Mode.CREATE,
                new Role(plugin.getRoleManager().nextAutoId(), "新規役職"), true);
    }

    public static RoleEditorGUI edit(HANNYOUPlugin plugin, Player player, Role role) {
        return new RoleEditorGUI(plugin, player, Mode.EDIT, role.copy(), false);
    }

    @Override
    protected String title() {
        return mode == Mode.CREATE
                ? "役職作成 - ページ " + currentPage + "/" + totalPages
                : "役職編集 - " + role.getDisplayName();
    }

    // ─────────────────────────────────────────────
    // 描画
    // ─────────────────────────────────────────────

    @Override
    protected void render() {
        switch (currentPage) {
            case PAGE_EQUIPMENT -> renderEquipment();
            case PAGE_INVENTORY -> renderInventory();
            case PAGE_EFFECTS -> renderEffects();
            case PAGE_SYSTEM -> renderSystem();
            default -> {}
        }
        addNavigationButtons();
        addActionButtons();
    }

    private void renderEquipment() {
        fillBackground();
        EQUIPMENT_SLOTS.forEach((equipSlot, slot) -> set(slot, role.getEquipment(equipSlot)));
    }

    private void renderInventory() {
        ItemStack[] inv = role.getInventory();
        ItemStack[] hotbar = role.getHotbar();
        for (int i = 0; i < Role.INVENTORY_SIZE; i++) set(i, inv[i]);
        for (int i = 0; i < Role.HOTBAR_SIZE; i++) set(36 + i, hotbar[i]);
        for (int i = 27; i <= 35; i++) fillGlass(i);
        fillGlass(BOTTOM_GLASS);
    }

    private void renderEffects() {
        for (int i = 0; i < EFFECT_SLOTS.length; i++) renderEffect(i);
        fillGlass(BOTTOM_GLASS);
    }

    private void renderEffect(int index) {
        PotionEffectType type = EFFECTS.get(index);
        button(EFFECT_SLOTS[index], potion(type, role.getEffectLevel(type)), e -> {
            role.setEffectLevel(type, (role.getEffectLevel(type) + 1) % (MAX_EFFECT_LEVEL + 1));
            renderEffect(index);
            playClickSound();
        });
    }

    private static ItemStack potion(PotionEffectType type, int level) {
        return POTION_CACHE.computeIfAbsent(type, t -> {
            ItemStack[] levels = new ItemStack[MAX_EFFECT_LEVEL + 1];
            for (int l = 0; l <= MAX_EFFECT_LEVEL; l++) levels[l] = buildPotion(t, l);
            return levels;
        })[level];
    }

    private static ItemStack buildPotion(PotionEffectType type, int level) {
        ItemStack potion = new ItemStack(Material.POTION);
        boolean isBuff = BUFFS.contains(type);
        String color = isBuff ? "§a" : "§c";
        String key = type.getKey().getKey();
        String name = key.substring(0, 1).toUpperCase() + key.substring(1);

        potion.editMeta(PotionMeta.class, meta -> {
            if (level > 0) {
                meta.setColor(isBuff ? Color.fromRGB(0, 255, 0) : Color.fromRGB(255, 0, 0));
                meta.addCustomEffect(new PotionEffect(type, 1, level - 1), true);
            } else {
                meta.setColor(Color.fromRGB(128, 128, 128));
            }
            meta.setEnchantmentGlintOverride(level > 0);
            meta.customName(Text.of(color + name));
            meta.lore(List.of(
                    Text.of(level == 0 ? "§7効果なし" : color + "アンプリファイア: " + ROMAN[level]),
                    Text.of("§eクリックでレベル変更")));
        });
        return potion;
    }

    private void renderSystem() {
        fillBackground();
        set(SLOT_ICON, role.getIcon());

        List<String> idLore = new ArrayList<>(List.of("§7現在: §f" + role.getId(), "", "§eクリックで変更"));
        idLore.add(mode == Mode.CREATE
                ? "§c※空欄の場合は自動でIDが割り振られます"
                : "§c※変更すると適用中のプレイヤーに影響します");
        button(SLOT_SYSTEM_ID, Items.named(new ItemStack(Material.COMMAND_BLOCK), "§6システムID設定", idLore),
                e -> { playClickSound(); promptId(); });

        if (mode == Mode.CREATE) {
            set(SLOT_NAME, Items.item(Material.BOOK, "§b役職情報",
                    "§7表示名: " + role.getDisplayName(),
                    "§7ID: " + role.getId()));
        } else {
            button(SLOT_NAME, Items.item(Material.NAME_TAG, "§6表示名設定",
                            "§7現在: §f" + role.getDisplayName(), "", "§eクリックで変更"),
                    e -> { playClickSound(); promptName(); });
        }
    }

    private void addActionButtons() {
        boolean create = mode == Mode.CREATE;
        button(SLOT_CLOSE, Items.item(Material.BARRIER, create ? "§cメニューを閉じる" : "§c保存せずに閉じる"),
                e -> { playClickSound(); finished = true; closeLater(); });

        button(SLOT_SAVE, Items.item(Material.EMERALD_BLOCK, create ? "§a役職を保存" : "§a変更を保存",
                        "§7現在の設定を保存します",
                        "§eクリックで保存"),
                e -> { playClickSound(); save(); });

        if (!create) {
            button(SLOT_EXPORT, Items.item(Material.BOOKSHELF, "§bこの役職をエクスポート",
                            "§7exports/ フォルダにYMLで出力します",
                            "§eクリックでエクスポート"),
                    e -> { playClickSound(); export(); });
        }
    }

    // ─────────────────────────────────────────────
    // クリック制御
    // ─────────────────────────────────────────────

    private boolean isEditable(int rawSlot) {
        return switch (currentPage) {
            case PAGE_EQUIPMENT -> EQUIPMENT_SLOT_SET.contains(rawSlot);
            case PAGE_INVENTORY -> rawSlot >= 0 && rawSlot < 27 || rawSlot >= 36 && rawSlot < 45;
            case PAGE_SYSTEM -> rawSlot == SLOT_ICON;
            default -> false;
        };
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        // ダブルクリックでGUI内のガラスやボタンを手元に集められないようにする
        if (finished || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        if (runButton(event)) return;

        int raw = event.getRawSlot();
        if (raw >= SIZE) {
            // 自分のインベントリ操作は自由。シフトクリックはアイテムを置けるページのみ
            if (event.isShiftClick() && currentPage != PAGE_EQUIPMENT && currentPage != PAGE_INVENTORY) {
                event.setCancelled(true);
            }
            return;
        }
        if (!isEditable(raw)) event.setCancelled(true);
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        for (int raw : event.getRawSlots()) {
            if (raw < SIZE && !isEditable(raw)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @Override
    protected void beforePageChange() {
        capture();
    }

    /** 現在のページでGUIに置かれたアイテムを作業コピーへ取り込む */
    private void capture() {
        switch (currentPage) {
            case PAGE_EQUIPMENT -> EQUIPMENT_SLOTS.forEach((equipSlot, slot) ->
                    role.setEquipment(equipSlot, cloneOf(get(slot))));
            case PAGE_INVENTORY -> {
                for (int i = 0; i < Role.INVENTORY_SIZE; i++) role.setInventoryItem(i, cloneOf(get(i)));
                for (int i = 0; i < Role.HOTBAR_SIZE; i++) role.setHotbarItem(i, cloneOf(get(36 + i)));
            }
            case PAGE_SYSTEM -> {
                ItemStack icon = get(SLOT_ICON);
                if (!Role.isEmpty(icon)) role.setIcon(icon.clone());
            }
            default -> {}
        }
    }

    private static ItemStack cloneOf(ItemStack item) {
        return Role.isEmpty(item) ? null : item.clone();
    }

    // ─────────────────────────────────────────────
    // 入力（ID・表示名）
    // ─────────────────────────────────────────────

    private void promptId() {
        capture();
        transition(() -> TextPrompt.open(plugin, player, "§6システムIDを入力",
                Items.item(Material.PAPER, "§f新しいIDを入力",
                        "§7半角英数字とアンダースコアのみ使用可能",
                        "§7例: warrior_01, mage_02"),
                this::updateId, this::open));
    }

    private void promptName() {
        capture();
        transition(() -> TextPrompt.open(plugin, player, "§6表示名を入力",
                Items.item(Material.PAPER, "§f新しい表示名を入力",
                        "§7好きな名前を入力してください",
                        "§7カラーコード: &c赤 &a緑 &9青"),
                this::updateName, this::open));
    }

    private void updateId(String input) {
        RoleManager roles = plugin.getRoleManager();
        if (input.isEmpty()) {
            if (mode == Mode.CREATE) {
                String id = roles.nextAutoId();
                role.setId(id);
                autoId = true;
                sendMessage("§aIDを自動生成しました: " + id);
                playSuccessSound();
            }
        } else if (!RoleManager.ID_PATTERN.matcher(input).matches()) {
            sendMessage("§cIDは半角英数字とアンダースコアのみ使用できます");
            playErrorSound();
        } else if (roles.exists(input) && !(mode == Mode.EDIT && input.equals(originalId))) {
            sendMessage("§cID「" + input + "」は既に存在します");
            playErrorSound();
        } else {
            role.setId(input);
            autoId = false;
            sendMessage("§aシステムIDを「" + input + "」に更新しました");
            playSuccessSound();
        }
        open();
    }

    private void updateName(String input) {
        if (!input.isEmpty()) {
            String formatted = Text.translateAmpersand(input);
            if (Text.strip(formatted).isBlank()) {
                sendMessage("§c表示名を空にはできません");
                playErrorSound();
            } else {
                role.setDisplayName(formatted);
                sendMessage("§a表示名を「" + formatted + "」に更新しました");
                playSuccessSound();
            }
        }
        open();
    }

    // ─────────────────────────────────────────────
    // 保存・エクスポート
    // ─────────────────────────────────────────────

    private void save() {
        capture();
        RoleManager roles = plugin.getRoleManager();

        if (mode == Mode.CREATE) {
            if (!roles.hasCapacity()) {
                sendMessage("§c役職数の上限(" + roles.maxRoles() + ")に達しています");
                playErrorSound();
                return;
            }
            if (roles.exists(role.getId())) {
                // 編集中に他の管理者が同じIDで作成した場合
                if (!autoId) {
                    sendMessage("§cID「" + role.getId() + "」は既に存在します");
                    playErrorSound();
                    return;
                }
                role.setId(roles.nextAutoId());
                sendMessage("§eIDを自動生成しました: " + role.getId());
            }
            roles.save(role);
            plugin.getAuditLogger().log(player, AuditLogger.Action.ROLE_CREATE, role.getId(),
                    "表示名: " + role.getDisplayName());
            sendMessage("§a役職「" + role.getDisplayName() + "」を保存しました");
            sendMessage("§7ID: " + role.getId());
        } else {
            if (!role.getId().equals(originalId)) {
                if (roles.exists(role.getId())) {
                    sendMessage("§cID「" + role.getId() + "」は既に存在します");
                    playErrorSound();
                    return;
                }
                roles.rename(originalId, role);
            } else {
                roles.save(role);
            }
            plugin.getAuditLogger().log(player, AuditLogger.Action.ROLE_EDIT, role.getId(),
                    "表示名: " + role.getDisplayName());
            sendMessage("§a役職「" + role.getDisplayName() + "」の変更を保存しました");
        }

        finished = true;
        playSuccessSound();
        closeLater();
    }

    private void export() {
        capture();
        String roleId = role.getId();
        plugin.getRoleTransfer().exportRole(role).thenAccept(fileName -> {
            if (!player.isOnline()) return;
            if (fileName != null) {
                sendMessage("§aエクスポートしました: exports/" + fileName);
                plugin.getAuditLogger().log(player, AuditLogger.Action.ROLE_EXPORT, roleId, "file: " + fileName);
                playSuccessSound();
            } else {
                sendMessage("§cエクスポートに失敗しました。");
                playErrorSound();
            }
        });
    }
}
