package com.ukagaka.irono.hANNYOUPlugin.role;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin;
import com.ukagaka.irono.hANNYOUPlugin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 役職の試着。試着前の持ち物と効果をプレイヤーデータ(PDC)に退避するので、
 * ログアウトやサーバー停止をまたいでも次の参加時に元へ戻せる。役職タグは付けない。
 */
public final class TrialManager implements Listener {
    public static final String UNTRY_COMMAND = "/hannyou knights untry";

    private final HANNYOUPlugin plugin;
    private final NamespacedKey itemsKey;
    private final NamespacedKey effectsKey;
    /** 試着終了時に開き直すGUIなど（メモリ上のみ。再起動後は何も開かない） */
    private final Map<UUID, Runnable> returnActions = new HashMap<>();

    public TrialManager(HANNYOUPlugin plugin) {
        this.plugin = plugin;
        this.itemsKey = new NamespacedKey(plugin, "trial_items");
        this.effectsKey = new NamespacedKey(plugin, "trial_effects");
    }

    public boolean isTrying(Player player) {
        return player.getPersistentDataContainer().has(itemsKey, PersistentDataType.BYTE_ARRAY);
    }

    /**
     * 役職を試着する。試着中に別の役職を試着した場合は、退避は最初の持ち物のまま中身だけ入れ替える。
     * @param onEnd 試着終了時に実行する処理（null可）
     */
    public void start(Player player, Role role, Runnable onEnd) {
        if (!isTrying(player)) backup(player);

        PlayerInventory inv = player.getInventory();
        inv.clear();
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        plugin.getRoleApplier().applyItems(player, role);
        plugin.getRoleApplier().applyEffects(player, role);

        if (onEnd != null) returnActions.put(player.getUniqueId(), onEnd);
        else returnActions.remove(player.getUniqueId());

        player.sendMessage(Text.of("§a役職「" + role.getDisplayName() + "§a」を試着中です ")
                .append(Text.runButton("§c§l【試着を終了】", UNTRY_COMMAND, "§7持ち物と効果を試着前に戻します")));
    }

    /**
     * 試着を終えて持ち物と効果を元に戻す。
     * @param runReturn 試着開始時に登録した処理（エディタを開き直す等）を実行するか
     * @return 試着中だったか
     */
    public boolean end(Player player, boolean runReturn) {
        if (!isTrying(player)) return false;
        restore(player);
        Runnable onEnd = returnActions.remove(player.getUniqueId());
        if (player.isOnline()) {
            player.sendMessage(Text.of("§a試着を終了し、持ち物を元に戻しました"));
            if (runReturn && onEnd != null) onEnd.run();
        }
        return true;
    }

    /** プラグイン停止時、試着中の全員を元に戻す */
    public void endAll() {
        for (Player online : Bukkit.getOnlinePlayers()) end(online, false);
    }

    // ─────────────────────────────────────────────
    // 退避と復元
    // ─────────────────────────────────────────────

    private void backup(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(itemsKey, PersistentDataType.BYTE_ARRAY,
                ItemStack.serializeItemsAsBytes(Arrays.asList(player.getInventory().getContents())));
        pdc.set(effectsKey, PersistentDataType.STRING, encodeEffects(player));
        player.saveData(); // サーバーが落ちても退避分が残るよう即保存
    }

    private void restore(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        byte[] items = pdc.get(itemsKey, PersistentDataType.BYTE_ARRAY);
        String effects = pdc.get(effectsKey, PersistentDataType.STRING);

        PlayerInventory inv = player.getInventory();
        ItemStack[] saved = items == null ? new ItemStack[0] : ItemStack.deserializeItemsFromBytes(items);
        ItemStack[] contents = new ItemStack[inv.getSize()];
        for (int i = 0; i < Math.min(saved.length, contents.length); i++) {
            contents[i] = saved[i] == null || saved[i].isEmpty() ? null : saved[i];
        }
        inv.setContents(contents);

        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        if (effects != null) decodeEffects(effects).forEach(player::addPotionEffect);

        pdc.remove(itemsKey);
        pdc.remove(effectsKey);
    }

    /** 効果を「キー,レベル,残り時間,アンビエント,パーティクル,アイコン;」の形で保存する */
    private static String encodeEffects(Player player) {
        StringBuilder sb = new StringBuilder();
        for (PotionEffect e : player.getActivePotionEffects()) {
            sb.append(e.getType().getKey().asString()).append(',')
                    .append(e.getAmplifier()).append(',')
                    .append(e.getDuration()).append(',')
                    .append(e.isAmbient()).append(',')
                    .append(e.hasParticles()).append(',')
                    .append(e.hasIcon()).append(';');
        }
        return sb.toString();
    }

    private static List<PotionEffect> decodeEffects(String data) {
        List<PotionEffect> result = new ArrayList<>();
        for (String part : data.split(";")) {
            String[] f = part.split(",");
            if (f.length != 6) continue;
            NamespacedKey key = NamespacedKey.fromString(f[0]);
            PotionEffectType type = key == null ? null : Registry.MOB_EFFECT.get(key);
            if (type == null) continue;
            try {
                result.add(new PotionEffect(type, Integer.parseInt(f[2]), Integer.parseInt(f[1]),
                        Boolean.parseBoolean(f[3]), Boolean.parseBoolean(f[4]), Boolean.parseBoolean(f[5])));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    // ─────────────────────────────────────────────
    // イベント
    // ─────────────────────────────────────────────

    /** サーバー停止などで戻せなかった試着を、参加時に戻す（役職の再配布より先に行う） */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        end(event.getPlayer(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        end(event.getPlayer(), false);
    }

    /** 試着アイテムが地面に落ちて増えないよう、ドロップを消す（持ち物はリスポーン時に戻す） */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (!isTrying(event.getPlayer())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPostRespawn(PlayerPostRespawnEvent event) {
        end(event.getPlayer(), false);
    }
}
