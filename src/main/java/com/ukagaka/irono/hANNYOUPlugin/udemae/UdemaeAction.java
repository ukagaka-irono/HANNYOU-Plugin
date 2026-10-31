package com.ukagaka.irono.hANNYOUPlugin.udemae;

/** ウデマエのポイントが増減する行動。ポイントは config.yml の udemae.points.<キー> で決める（0 なら変わらない） */
public enum UdemaeAction {
    MELEE_KILL("melee-kill", 10),
    MELEE_ASSIST("melee-assist", 5),
    RANGED_KILL("ranged-kill", 12),
    RANGED_ASSIST("ranged-assist", 6),
    SOLDIER_MELEE_KILL("soldier-melee-kill", 6),
    SOLDIER_MELEE_ASSIST("soldier-melee-assist", 3),
    SOLDIER_RANGED_KILL("soldier-ranged-kill", 6),
    SOLDIER_RANGED_ASSIST("soldier-ranged-assist", 3),
    MOB_SOLDIER_KILL("mob-soldier-kill", 3),
    MOB_SOLDIER_ASSIST("mob-soldier-assist", 1),
    // 占領（データパックのスコアで判定する）
    CAPTURE_STAY("capture-stay", 2),
    CAPTURE_KILL("capture-kill", 15),
    CAPTURE_ASSIST("capture-assist", 7),
    WIN("win", 30),
    // 減る行動（マイナスの値を入れると減る）
    DEATH("death", 0),
    LOSE("lose", 0);

    private final String key;
    private final int defaultPoints;

    UdemaeAction(String key, int defaultPoints) {
        this.key = key;
        this.defaultPoints = defaultPoints;
    }

    public String key() { return key; }
    public int defaultPoints() { return defaultPoints; }
}
