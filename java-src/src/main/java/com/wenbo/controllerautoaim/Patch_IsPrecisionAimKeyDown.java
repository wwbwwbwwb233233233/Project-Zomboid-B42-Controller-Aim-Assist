package com.wenbo.controllerautoaim;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoPlayer;
import zombie.characters.component.CharacterInputComponent;
import zombie.characters.ecs.ECSEntity;
import zombie.input.JoypadManager;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;

/*
 * 让"举枪精瞄"状态在右摇杆推动时自动启用。
 *
 * 背景:vanilla 用 isPrecisionAimKeyDown() 来决定 cursor 用 CURSOR 还是
 * RETICLE 精确准星 sprite,以及是否进入 RANGED_PRECISE 模式。键鼠玩家按
 * RMB 触发,但手柄默认没有对应键。
 *
 * 处理方式:patch 这个方法的 OnExit,如果发现是本地玩家 + 手持远程武器
 * + 右摇杆有推动(超过死区平方),把返回值改成 true,vanilla 后续就当作
 * 玩家按住了"精瞄键",reticle 切换成红色锁定圈,我们的 Patch_AimingMode
 * 也能跟着进入 RANGED 系列模式。
 */
@Patch(className = "zombie.characters.component.CharacterInputComponent", methodName = "isPrecisionAimKeyDown", warmUp = true)
public class Patch_IsPrecisionAimKeyDown {

    // 右摇杆触发精瞄的死区(平方,省一个 sqrt)。0.18 跟 Lua 端 STICK_DEAD_ZONE 对齐。
    public static volatile float STICK_DEADZONE_SQ = 0.18f * 0.18f;

    @Patch.OnExit
    public static void onExit(
            @Patch.This(readOnly = true) CharacterInputComponent component,
            @Patch.Return(readOnly = false) boolean current
    ) {
        if (!AimOverrideState.applyOverride) return;
        if (component == null) return;

        // 拿 character (ECS 入口) 并要求是本地 IsoPlayer
        ECSEntity entity = component.getECSOwnerEntity();
        if (!(entity instanceof IsoPlayer)) return;
        IsoPlayer player = (IsoPlayer) entity;
        if (!player.isLocal()) return;

        // 必须持有远程武器(否则没有"精瞄"的意义)
        InventoryItem item = player.getPrimaryHandItem();
        if (!(item instanceof HandWeapon)) return;
        HandWeapon weapon = (HandWeapon) item;
        if (!weapon.isRanged()) return;

        // 读当前手柄右摇杆,判断推动是否超过死区
        JoypadManager jm = JoypadManager.instance;
        if (jm == null) return;
        int idx = player.getPlayerNum();
        float sx = jm.getAimingAxisX(idx);
        float sy = jm.getAimingAxisY(idx);
        boolean stickPushed = (sx * sx + sy * sy) >= STICK_DEADZONE_SQ;

        // 改写返回值 — vanilla 后续把这个当作"玩家按住了精瞄键"
        current = stickPushed;
    }
}
