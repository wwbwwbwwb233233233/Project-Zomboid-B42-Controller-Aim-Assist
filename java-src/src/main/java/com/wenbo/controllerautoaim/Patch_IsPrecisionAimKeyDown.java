package com.wenbo.controllerautoaim;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoPlayer;
import zombie.characters.component.CharacterInputComponent;
import zombie.characters.ecs.ECSEntity;

/*
 * 让"举枪精瞄"状态在 Lua gate 批准瞄准时自动启用。
 *
 * 背景:vanilla 用 isPrecisionAimKeyDown() 决定 cursor 用 CURSOR 还是 RETICLE
 * 精确准星 sprite,以及是否进入 RANGED_PRECISE 模式。键鼠玩家按 RMB 触发,手柄
 * 默认没有对应键,所以我们在玩家"想用手柄瞄准"时强制它返回 true。
 *
 * 关键:这个 patch 不再自己读摇杆 / 自己判断。它完全听 Lua 推来的 aimGranted。
 * 为什么:之前自己读 JoypadManager.getAimingAxisX(idx) 会绕过两件事 ——
 *   1. 手柄是否真的绑定到这个玩家(getAimingAxisX 读物理摇杆,手柄没配对也返回值
 *      → 手柄还没绑定到角色时推右摇杆就触发瞄准)
 *   2. UI 是否占用手柄(轮盘/地图打开时推右摇杆是给 UI 导航的,不该触发瞄准)
 * Lua 的 gate 已经综合判断了 本地玩家 + 手柄绑定 + UI 占用 + 持枪 + 推杆超死区,
 * 结论推成 aimGranted。Java 只执行,不重复(且独立地)判断。
 *
 * 只单向补充 true(aimGranted 时),绝不强行 false 覆盖 vanilla —— 否则会吞掉
 * 键鼠/触摸玩家的精瞄输入(见历史 bug)。
 */
@Patch(className = "zombie.characters.component.CharacterInputComponent", methodName = "isPrecisionAimKeyDown", warmUp = true)
public class Patch_IsPrecisionAimKeyDown {

    @Patch.OnExit
    public static void onExit(
            @Patch.This(readOnly = true) CharacterInputComponent component,
            @Patch.Return(readOnly = false) boolean current
    ) {
        if (!AimOverrideState.applyOverride) return;
        // 完全听 Lua gate 的结论。没批准 → 不动 vanilla 原值。
        if (!AimOverrideState.aimGranted) return;
        if (component == null) return;

        // 确认 reticle 只强制给本地玩家(aimGranted 是全局单 flag,这里再确认一次归属)。
        ECSEntity entity = component.getECSOwnerEntity();
        if (!(entity instanceof IsoPlayer)) return;
        if (!((IsoPlayer) entity).isLocal()) return;

        current = true;
    }
}
