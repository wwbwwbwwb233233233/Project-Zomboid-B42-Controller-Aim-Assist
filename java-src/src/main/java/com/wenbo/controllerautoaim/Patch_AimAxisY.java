package com.wenbo.controllerautoaim;

import me.zed_0xff.zombie_buddy.Patch;

/*
 * Y 轴的曲线孪生 — 详见 Patch_AimAxisX 的文档。
 */
@Patch(className = "zombie.input.JoypadManager", methodName = "getAimingAxisY", warmUp = true)
public class Patch_AimAxisY {

    @Patch.OnExit
    public static void onExit(@Patch.Return(readOnly = false) float ret) {
        if (!AimOverrideState.applyOverride) return;
        float e = Patch_AimingMode.STICK_CURVE_EXPONENT;
        if (e > 0.999f && e < 1.001f) return;
        float a = Math.abs(ret);
        if (a < 0.0001f) return;
        float sign = (ret >= 0f) ? 1f : -1f;
        ret = sign * (float) Math.pow(a, e);
    }
}
