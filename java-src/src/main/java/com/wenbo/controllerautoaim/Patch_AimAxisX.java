package com.wenbo.controllerautoaim;

import me.zed_0xff.zombie_buddy.Patch;

/*
 * 给右摇杆 X 轴加响应曲线。Patch_AimAxisY 是它的孪生兄弟。
 *
 * 公式:out = sign(in) × |in|^exp
 *   exp = 1.0  线性,等于不处理(默认开启时为 1.6)
 *   exp = 1.5  Steam Input "Relaxed":小推杆精细、大推杆迅速
 *   exp = 2.0  "Wide":更平滑,微操优势
 *   exp = 0.6  "Aggressive":小推杆也快,适合快速大幅瞄准
 *
 * Per-axis 应用(X、Y 各自独立)— 跟 Steam Input 实际行为一致。
 * 死区:不在这里处理,由 JoypadManager 内部或 PZ vanilla 处理。
 *
 * 注意:如果玩家在 Steam Input 设置里已经选了一个曲线(例如 Relaxed),
 * 这里的曲线会叠加,可能过度"凹"。建议玩家二选一:要么 Steam 设线性 mod 这里
 * 设 1.5,要么 Steam 设 Relaxed 这里设 1.0。
 */
@Patch(className = "zombie.input.JoypadManager", methodName = "getAimingAxisX", warmUp = true)
public class Patch_AimAxisX {

    @Patch.OnExit
    public static void onExit(@Patch.Return(readOnly = false) float ret) {
        if (!AimOverrideState.applyOverride) return;
        if (AimOverrideState.suppressed) return;  // UI 占用 → 不改摇杆,留给 UI 导航
        // 用 morph 后的生效指数(polar↔drift 平滑过渡,见 Patch_AimingMode.currentCurveExp)。
        float e = Patch_AimingMode.currentCurveExp;
        // 接近 1.0 视为线性 = 不处理。避免 pow(x, 1.000001) 这种无意义计算。
        if (e > 0.999f && e < 1.001f) return;
        float a = Math.abs(ret);
        // 极小值直接返回(死区附近,curve 算出来也基本是 0)
        if (a < 0.0001f) return;
        float sign = (ret >= 0f) ? 1f : -1f;
        ret = sign * (float) Math.pow(a, e);
    }
}
