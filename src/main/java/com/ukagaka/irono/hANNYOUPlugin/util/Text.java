package com.ukagaka.irono.hANNYOUPlugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * §カラーコード文字列 ⇔ Adventure Component の変換。
 * 役職の表示名は既存データとの互換のため §コード付き文字列で保持している。
 */
public final class Text {
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    private Text() {}

    /** §コード付き文字列をComponentに変換する（アイテム名と同じく斜体なし） */
    public static Component of(String legacy) {
        return SECTION.deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    /** クリックでコマンドを実行するチャットボタン */
    public static Component runButton(String legacy, String command, String hoverLegacy) {
        return of(legacy)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(of(hoverLegacy)));
    }

    /**
     * クリックで処理を実行するチャットボタン（コマンドを登録せずに済む。クリックできるのは12時間まで）。
     * @param uses クリックできる回数（ClickCallback.UNLIMITED_USES で無制限）
     */
    public static Component callbackButton(String legacy, String hoverLegacy, int uses, Consumer<Player> action) {
        ClickCallback.Options options = ClickCallback.Options.builder().uses(uses).build();
        return of(legacy)
                .clickEvent(ClickEvent.callback(audience -> {
                    if (audience instanceof Player player) action.accept(player);
                }, options))
                .hoverEvent(HoverEvent.showText(of(hoverLegacy)));
    }

    /** クリックでチャット入力欄にコマンドを入れるボタン */
    public static Component suggestButton(String legacy, String command, String hoverLegacy) {
        return of(legacy)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(of(hoverLegacy)));
    }

    /** &カラーコードを§カラーコードに変換する */
    public static String translateAmpersand(String input) {
        return SECTION.serialize(AMPERSAND.deserialize(input));
    }

    /** カラーコードを除いた文字列 */
    public static String strip(String legacy) {
        return PlainTextComponentSerializer.plainText().serialize(SECTION.deserialize(legacy));
    }
}
