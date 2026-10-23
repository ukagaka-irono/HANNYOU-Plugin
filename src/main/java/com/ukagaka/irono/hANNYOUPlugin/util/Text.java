package com.ukagaka.irono.hANNYOUPlugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

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

    /** &カラーコードを§カラーコードに変換する */
    public static String translateAmpersand(String input) {
        return SECTION.serialize(AMPERSAND.deserialize(input));
    }

    /** カラーコードを除いた文字列 */
    public static String strip(String legacy) {
        return PlainTextComponentSerializer.plainText().serialize(SECTION.deserialize(legacy));
    }
}
