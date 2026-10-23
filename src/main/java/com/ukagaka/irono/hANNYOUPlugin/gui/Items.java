package com.ukagaka.irono.hANNYOUPlugin.gui;

import com.ukagaka.irono.hANNYOUPlugin.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

/** GUIボタン用アイテムの生成ヘルパー（名前・説明文は§カラーコードで指定） */
public final class Items {
    private static final ItemStack GLASS = item(Material.GRAY_STAINED_GLASS_PANE, " ");

    private Items() {}

    public static ItemStack item(Material material, String name, String... lore) {
        return named(new ItemStack(material), name, Arrays.asList(lore));
    }

    /** 既存アイテムの名前と説明文を書き換えたコピーを返す */
    public static ItemStack named(ItemStack base, String name, List<String> lore) {
        ItemStack item = base.clone();
        item.editMeta(meta -> {
            meta.customName(Text.of(name));
            meta.lore(lore.isEmpty() ? null : lore.stream().map(Text::of).toList());
        });
        return item;
    }

    public static ItemStack glass() {
        return GLASS.clone();
    }
}
