--[[
    手柄/鼠标输入相关的封装 — 都是对 vanilla API 的薄包装,加上 nil-safe pcall。
]]
ControllerAutoAim = ControllerAutoAim or {}
local M = ControllerAutoAim
M.Input = {}
local Input = M.Input

-- 拿当前玩家对应的手柄 ID(< 0 视为没连接)
local function joypadId(playerNum)
    if not getJoypadData then return nil end
    local data = getJoypadData(playerNum)
    if not data or not data.id or data.id < 0 then return nil end
    return data.id
end

-- 手柄是否连接 + active
function Input.isControllerActive(playerNum)
    return joypadId(playerNum) ~= nil
end

-- 当前右摇杆的 (x, y),没手柄返回 (0, 0)。
-- 注意:vanilla 的 getJoypadAimingAxisX/Y 已经被我们 Patch_AimAxisX/Y patch 过,
-- 所以这里拿到的是"曲线变换后"的值。
function Input.getRightStick(playerNum)
    local id = joypadId(playerNum)
    if not id then return 0, 0 end
    local x = (getJoypadAimingAxisX and getJoypadAimingAxisX(id)) or 0
    local y = (getJoypadAimingAxisY and getJoypadAimingAxisY(id)) or 0
    return x, y
end

-- 鼠标最近是否比手柄更活跃。vanilla 用这个判断"键鼠玩家是不是在用鼠标",
-- 是的话我们就让出控制权(不强制瞄准 flag)。
function Input.isMouseRecentlyActive()
    if wasMouseActiveMoreRecentlyThanJoypad then
        local ok, v = pcall(wasMouseActiveMoreRecentlyThanJoypad)
        if ok then return v == true end
    end
    return false
end
