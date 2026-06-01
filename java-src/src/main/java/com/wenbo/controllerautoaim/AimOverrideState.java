package com.wenbo.controllerautoaim;

import se.krka.kahlua.integration.annotations.LuaMethod;

/*
 * Lua 桥 — 把 Java 端的字段暴露成 Lua 全局函数。
 *
 * 命名约定:所有 Lua 全局函数以 `caa_` 前缀(controller auto-aim 缩写)。
 *   - `caa_set*` 用于 PZAPI.ModOptions 写参数
 *   - `caa_get*` 用于 Lua HUD/调试查询
 *   - `caa_is*`  布尔运行时状态(供 HUD 显示)
 *
 * `applyOverride` 字段:全局开关,设 false 时 mod 完全不介入瞄准
 *(留给未来加 hotkey toggle 用,目前没有 Lua setter 暴露)。
 *
 * `debugLog` 字段:dev-only println 开关,release 保持 false。
 */
public class AimOverrideState {

    public static volatile boolean debugLog = false;
    public static volatile boolean applyOverride = true;

    // UI 占用手柄时由 Lua 每帧推送。true = 所有 patch 完全不介入(让位给轮盘/地图/库存
    // 等 UI 的右摇杆导航)。跟 applyOverride 区别:applyOverride 是玩家总开关(持久),
    // suppressed 是运行时临时状态(每帧刷新)。
    public static volatile boolean suppressed = false;

    // Lua gate 的总结论:true = 本地玩家 + 手柄已绑定 + 无 UI 占用 + 持枪 + 推杆超死区,
    // 全部满足。由 Lua 每帧推送。Patch_IsPrecisionAimKeyDown 完全听它,不再自己读摇杆
    // (自己读摇杆会绕过手柄绑定 + UI 判断 → 手柄没配对就触发、轮盘里选不了项目)。
    public static volatile boolean aimGranted = false;

    // Debug overlay 开关 — Lua 端 ISUIElement render() 时查询。默认 OFF。
    public static volatile boolean showOverlay = false;

    // ============ 总开关 ============

    @LuaMethod(global = true)
    public static void caa_setApplyOverride(boolean enabled) { applyOverride = enabled; }
    @LuaMethod(global = true)
    public static boolean caa_getApplyOverride() { return applyOverride; }

    @LuaMethod(global = true)
    public static void caa_setSuppressed(boolean v) { suppressed = v; }
    @LuaMethod(global = true)
    public static boolean caa_getSuppressed() { return suppressed; }

    @LuaMethod(global = true)
    public static void caa_setAimGranted(boolean v) { aimGranted = v; }
    @LuaMethod(global = true)
    public static boolean caa_getAimGranted() { return aimGranted; }

    @LuaMethod(global = true)
    public static void caa_setShowOverlay(boolean enabled) { showOverlay = enabled; }
    @LuaMethod(global = true)
    public static boolean caa_getShowOverlay() { return showOverlay; }

    // 上一帧 sticky 命中的僵尸 ID(-1 = 当前没命中)。debug overlay 用来高亮选中那只。
    @LuaMethod(global = true)
    public static int caa_getLastHitZombieId() { return Patch_AimingMode.lastHitZombieId; }

    // 身体锚点 + sprite 高度(Lua debug overlay 画身体框用)
    @LuaMethod(global = true)
    public static double caa_getBodyAnchorX(int zombieId) {
        float[] s = Patch_AimingMode.bodyAnchorCache.get(zombieId);
        return (s == null) ? Double.NaN : (double) s[0];
    }
    @LuaMethod(global = true)
    public static double caa_getBodyAnchorY(int zombieId) {
        float[] s = Patch_AimingMode.bodyAnchorCache.get(zombieId);
        return (s == null) ? Double.NaN : (double) s[1];
    }
    @LuaMethod(global = true)
    public static double caa_getBodySpriteH(int zombieId) {
        float[] s = Patch_AimingMode.bodyAnchorCache.get(zombieId);
        return (s == null) ? Double.NaN : (double) s[2];
    }

    // ============ 运行时状态查询(供 HUD / 玩家反馈用) ============

    @LuaMethod(global = true)
    public static boolean caa_isSnapping() {
        return Patch_AimingMode.lastInZone;
    }

    @LuaMethod(global = true)
    public static boolean caa_isTracking() {
        return Patch_AimingMode.lastTracking;
    }

    @LuaMethod(global = true)
    public static boolean caa_isInHead() {
        return Patch_AimingMode.lastInHead;
    }

    @LuaMethod(global = true)
    public static boolean caa_isAiming() {
        return Patch_AimingMode.lastAiming;
    }

    // 头骨当前帧屏幕坐标(Lua HUD 想画头部小圆时查)。NaN 表示拿不到。
    @LuaMethod(global = true)
    public static double caa_getHeadScreenX(int zombieId) {
        float[] s = Patch_AimingMode.headScreenCache.get(zombieId);
        return (s == null) ? Double.NaN : (double) s[0];
    }

    @LuaMethod(global = true)
    public static double caa_getHeadScreenY(int zombieId) {
        float[] s = Patch_AimingMode.headScreenCache.get(zombieId);
        return (s == null) ? Double.NaN : (double) s[1];
    }

    // ============ 参数 setter / getter(被 PZAPI.ModOptions 调) ============

    // 身体减速区尺寸(spriteH 比例)
    @LuaMethod(global = true)
    public static void caa_setHitboxWidth(double v) { Patch_AimingMode.HITBOX_WIDTH_RATIO = (float) v; }
    @LuaMethod(global = true)
    public static void caa_setHitboxHeight(double v) { Patch_AimingMode.HITBOX_HEIGHT_RATIO = (float) v; }
    @LuaMethod(global = true)
    public static void caa_setHitboxDown(double v) { Patch_AimingMode.HITBOX_DOWN_RATIO = (float) v; }
    @LuaMethod(global = true)
    public static double caa_getHitboxWidth() { return Patch_AimingMode.HITBOX_WIDTH_RATIO; }
    @LuaMethod(global = true)
    public static double caa_getHitboxHeight() { return Patch_AimingMode.HITBOX_HEIGHT_RATIO; }
    @LuaMethod(global = true)
    public static double caa_getHitboxDown() { return Patch_AimingMode.HITBOX_DOWN_RATIO; }

    // 头部框尺寸
    @LuaMethod(global = true)
    public static void caa_setHeadHalfW(double v) { Patch_AimingMode.HEAD_HALF_W = (float) v; }
    @LuaMethod(global = true)
    public static void caa_setHeadHalfH(double v) { Patch_AimingMode.HEAD_HALF_H = (float) v; }
    @LuaMethod(global = true)
    public static double caa_getHeadHalfW() { return Patch_AimingMode.HEAD_HALF_W; }
    @LuaMethod(global = true)
    public static double caa_getHeadHalfH() { return Patch_AimingMode.HEAD_HALF_H; }
    @LuaMethod(global = true)
    public static void caa_setHeadOffset(double v) { Patch_AimingMode.HEAD_OFFSET_RATIO = (float) v; }
    @LuaMethod(global = true)
    public static double caa_getHeadOffset() { return Patch_AimingMode.HEAD_OFFSET_RATIO; }

    // 减速强度
    @LuaMethod(global = true)
    public static void caa_setSlowdownX(double v) { Patch_AimingMode.SLOWDOWN_X = (float) v; }
    @LuaMethod(global = true)
    public static void caa_setSlowdownY(double v) { Patch_AimingMode.SLOWDOWN_Y = (float) v; }
    @LuaMethod(global = true)
    public static void caa_setSlowdownHead(double v) { Patch_AimingMode.SLOWDOWN_HEAD = (float) v; }
    @LuaMethod(global = true)
    public static double caa_getSlowdownX() { return Patch_AimingMode.SLOWDOWN_X; }
    @LuaMethod(global = true)
    public static double caa_getSlowdownY() { return Patch_AimingMode.SLOWDOWN_Y; }
    @LuaMethod(global = true)
    public static double caa_getSlowdownHead() { return Patch_AimingMode.SLOWDOWN_HEAD; }

    // 跟踪 + 摇杆曲线 + 向上抗性
    @LuaMethod(global = true)
    public static void caa_setTrackingFactor(double v) { Patch_AimingMode.TRACKING_FACTOR = (float) v; }
    @LuaMethod(global = true)
    public static void caa_setStickCurve(double v) { Patch_AimingMode.STICK_CURVE_EXPONENT = (float) v; }
    @LuaMethod(global = true)
    public static void caa_setUpResist(double v) { Patch_AimingMode.UP_RESISTANCE = (float) v; }
    @LuaMethod(global = true)
    public static double caa_getTrackingFactor() { return Patch_AimingMode.TRACKING_FACTOR; }
    @LuaMethod(global = true)
    public static double caa_getStickCurve() { return Patch_AimingMode.STICK_CURVE_EXPONENT; }
    @LuaMethod(global = true)
    public static double caa_getUpResist() { return Patch_AimingMode.UP_RESISTANCE; }

    // 回中抗性 + 速度注入(瞄准手感)
    @LuaMethod(global = true)
    public static void caa_setRecenterResist(double v) { Patch_AimingMode.RECENTER_RESISTANCE = (float) v; }
    @LuaMethod(global = true)
    public static double caa_getRecenterResist() { return Patch_AimingMode.RECENTER_RESISTANCE; }
    @LuaMethod(global = true)
    public static void caa_setVelocityInject(double v) { Patch_AimingMode.VELOCITY_INJECT = (float) v; }
    @LuaMethod(global = true)
    public static double caa_getVelocityInject() { return Patch_AimingMode.VELOCITY_INJECT; }
    @LuaMethod(global = true)
    public static void caa_setDriftCurve(double v) { Patch_AimingMode.DRIFT_CURVE_EXPONENT = (float) v; }
    @LuaMethod(global = true)
    public static double caa_getDriftCurve() { return Patch_AimingMode.DRIFT_CURVE_EXPONENT; }
}
