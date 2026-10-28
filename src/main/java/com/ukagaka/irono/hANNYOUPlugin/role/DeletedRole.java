package com.ukagaka.irono.hANNYOUPlugin.role;

import org.bukkit.potion.PotionEffectType;

import java.util.Map;

/** 削除済みの役職の記録。オフラインだった保持者が参加したときに、タグと効果を外すのに使う */
public record DeletedRole(String id, Map<PotionEffectType, Integer> effects) {}
