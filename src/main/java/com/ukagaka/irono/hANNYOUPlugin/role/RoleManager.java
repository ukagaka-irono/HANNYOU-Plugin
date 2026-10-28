package com.ukagaka.irono.hANNYOUPlugin.role;

import com.ukagaka.irono.hANNYOUPlugin.storage.ItemCodec;
import com.ukagaka.irono.hANNYOUPlugin.storage.RoleStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * 役職のメモリキャッシュ。起動時に全件読み込むので、参照時にDBへ問い合わせることはない。
 * タブ補完は非同期スレッドから呼ばれることがあるため ConcurrentHashMap を使う。
 */
public final class RoleManager {
    public static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private final Map<String, Role> roles = new ConcurrentHashMap<>();
    /** 削除済み役職ID → その役職の効果（オフラインだった保持者の後始末用） */
    private final Map<String, Map<PotionEffectType, Integer>> deleted = new ConcurrentHashMap<>();
    private final RoleStorage storage;
    private final IntSupplier maxRoles;

    public RoleManager(RoleStorage storage, IntSupplier maxRoles) {
        this.storage = storage;
        this.maxRoles = maxRoles;
    }

    public void load(RoleStorage.LoadResult result, Logger logger) {
        roles.clear();
        for (RoleStorage.StoredRole row : result.roles()) {
            try {
                roles.put(row.id(), row.toRole());
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "役職の読み込みに失敗しました: " + row.id(), e);
            }
        }
        deleted.clear();
        result.deletedRoles().forEach((id, effects) -> deleted.put(id, ItemCodec.decodeEffects(effects)));
        logger.info(roles.size() + "件の役職を読み込みました");
    }

    public Role get(String id) {
        return id == null ? null : roles.get(id);
    }

    public boolean exists(String id) {
        return roles.containsKey(id);
    }

    public int size() {
        return roles.size();
    }

    public Collection<Role> all() {
        return Collections.unmodifiableCollection(roles.values());
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(roles.keySet());
    }

    public List<Role> sorted() {
        return roles.values().stream().sorted(Comparator.comparing(Role::getId)).toList();
    }

    /** 新しい役職を追加できるか（max-roles の上限チェック） */
    public boolean hasCapacity() {
        int max = maxRoles.getAsInt();
        return max < 0 || roles.size() < max;
    }

    public int maxRoles() {
        return maxRoles.getAsInt();
    }

    public void save(Role role) {
        roles.put(role.getId(), role);
        storage.save(RoleStorage.StoredRole.of(role));
        if (deleted.remove(role.getId()) != null) storage.unmarkDeleted(role.getId());
    }

    /** 役職を削除し、削除済みとして記録する（プレイヤー側の後始末は RoleApplier#deleteRole） */
    public Role delete(String id) {
        Role removed = roles.remove(id);
        if (removed != null) {
            storage.delete(id);
            deleted.put(id, Map.copyOf(removed.getEffects()));
            storage.markDeleted(id, ItemCodec.encodeEffects(removed.getEffects()));
        }
        return removed;
    }

    /** IDを変更して保存する。オンラインプレイヤーの役職タグも付け替える（旧IDは削除済み扱いにしない） */
    public void rename(String oldId, Role role) {
        if (roles.remove(oldId) != null) storage.delete(oldId);
        save(role);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.removeScoreboardTag(oldId)) {
                online.addScoreboardTag(role.getId());
            }
        }
    }

    /** 削除済み役職の効果（削除済みでなければnull） */
    public Map<PotionEffectType, Integer> deletedEffects(String id) {
        return deleted.get(id);
    }

    /** 000001 形式で未使用の最小番号IDを作る */
    public String nextAutoId() {
        for (int n = 1; ; n++) {
            String candidate = String.format("%06d", n);
            if (!roles.containsKey(candidate)) return candidate;
        }
    }

    /** プレイヤーに付与されている役職（スコアボードタグ）を探す */
    public Role findRole(Player player) {
        for (String tag : player.getScoreboardTags()) {
            Role role = roles.get(tag);
            if (role != null) return role;
        }
        return null;
    }

    /** その役職を持っているオンラインプレイヤー */
    public List<Player> onlineHolders(String id) {
        List<Player> result = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getScoreboardTags().contains(id)) result.add(online);
        }
        return result;
    }
}
