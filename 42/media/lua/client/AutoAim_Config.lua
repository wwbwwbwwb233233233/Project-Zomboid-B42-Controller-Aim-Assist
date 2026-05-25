-- Lua 端配置 + 运行时状态。其余可调参数都在 PZAPI.ModOptions 里(玩家通过设置页面调)。
ControllerAutoAim = ControllerAutoAim or {}
local M = ControllerAutoAim

M.Config = {
    -- 摇杆死区:右摇杆推动 magnitude 小于此值时认为玩家没在瞄准,mod 不激活。
    STICK_DEAD_ZONE = 0.18,
}

M.State = {
    -- 当前帧 mod 是否处于"激活"状态(手柄推杆+举枪等全部条件通过)。供其它模块查询。
    isActive = false,
}
