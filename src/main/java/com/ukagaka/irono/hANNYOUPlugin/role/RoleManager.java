package com.ukagaka.irono.hANNYOUPlugin.role;

import com.ukagaka.irono.hANNYOUPlugin.storage.RoleStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * 役職のメモリキャッシュ。起動時に knights-role.yml から全件読み込むので、参照時にファイルを読むことはない。
 * タブ補完は非同期スレッドから呼ばれることがあるため ConcurrentHashMap を使う。
 */
public final class RoleManager {
    public static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private final Map<String, Role> roles = new ConcurrentHashMap<>();
    /** 削除済み役職の識別番号 → 記録（オフラインだった保持者の後始末用） */
    private final Map<String, DeletedRole> deleted = new ConcurrentHashMap<>();
    private final RoleStorage storage;
    private final IntSupplier maxRoles;
    /** role id 順に並べた一覧（役職が変わるまで使い回す。変わったら null にする） */
    private volatile List<Role> sortedCache;

    public RoleManager(RoleStorage storage, IntSupplier maxRoles) {
        this.storage = storage;
        this.maxRoles = maxRoles;
    }

    public void load(RoleStorage.LoadResult result, Logger logger) {
        roles.clear();
        roles.putAll(result.roles());
        sortedCache = null;
        deleted.clear();
        deleted.putAll(result.deletedRoles());
        logger.info(roles.size() + "件の役職を読み込みました");
        if (result.needsSave()) persist(); // 識別番号を新しく割り振ったら、すぐ書き出して固定する
    }

    /** 現在の内容を knights-role.yml に書き出す */
    private void persist() {
        sortedCache = null;
        storage.save(roles.values(), deleted);
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
        List<Role> cached = sortedCache;
        if (cached == null) {
            cached = roles.values().stream().sorted(Comparator.comparing(Role::getId)).toList();
            sortedCache = cached;
        }
        return cached;
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
        deleted.remove(role.getUid());
        persist();
    }

    /** 役職を削除し、削除済みとして記録する（プレイヤー側の後始末は RoleApplier#deleteRole） */
    public Role delete(String id) {
        Role removed = roles.remove(id);
        if (removed != null) {
            deleted.put(removed.getUid(), new DeletedRole(id, Map.copyOf(removed.getEffects())));
            persist();
        }
        return removed;
    }

    /** role id を変更して保存する（プレイヤーのタグの付け替えは RoleApplier#renameRole） */
    public void rename(String oldId, Role role) {
        roles.remove(oldId);
        save(role);
    }

    /** 識別番号から役職を探す（名前が変わっていても見つかる） */
    public Role findByUid(String uid) {
        if (uid == null) return null;
        for (Role role : roles.values()) {
            if (role.getUid().equals(uid)) return role;
        }
        return null;
    }

    /** 削除済み役職の記録（削除済みでなければnull） */
    public DeletedRole deletedRole(String uid) {
        return uid == null ? null : deleted.get(uid);
    }

    /** 識別番号を記録する前に付与されたプレイヤー用。role id から削除済み役職の記録を探す */
    public DeletedRole deletedRoleById(String id) {
        for (DeletedRole role : deleted.values()) {
            if (role.id().equals(id)) return role;
        }
        return null;
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
