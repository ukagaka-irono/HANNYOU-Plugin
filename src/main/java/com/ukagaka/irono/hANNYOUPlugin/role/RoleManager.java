package com.ukagaka.irono.hANNYOUPlugin.role;

import com.ukagaka.irono.hANNYOUPlugin.storage.RoleStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

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
    private final RoleStorage storage;
    private final IntSupplier maxRoles;

    public RoleManager(RoleStorage storage, IntSupplier maxRoles) {
        this.storage = storage;
        this.maxRoles = maxRoles;
    }

    public void load(List<RoleStorage.StoredRole> stored, Logger logger) {
        roles.clear();
        for (RoleStorage.StoredRole row : stored) {
            try {
                roles.put(row.id(), row.toRole());
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "役職の読み込みに失敗しました: " + row.id(), e);
            }
        }
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
    }

    public void delete(String id) {
        if (roles.remove(id) != null) {
            storage.delete(id);
        }
    }

    /** IDを変更して保存する。オンラインプレイヤーの役職タグも付け替える */
    public void rename(String oldId, Role role) {
        delete(oldId);
        save(role);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.removeScoreboardTag(oldId)) {
                online.addScoreboardTag(role.getId());
            }
        }
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
}
