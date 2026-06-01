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

-- UI 是否正在占用手柄输入(D-pad 轮盘菜单 / 地图 / 库存等)。
-- 这种时候右摇杆是给 UI 导航用的,不能被我们当成瞄准动作截获,
-- 否则会打断玩家在轮盘里选项目(vanilla 自己会 setJoypadIgnoreAimUntilCentered,
-- 但我们用 setIsAiming 强制瞄准绕过了它,所以要自己挡)。
function Input.isUICapturingJoypad(playerNum)
    -- 1. 手柄焦点被某个 UI 元素持有(通用:轮盘、库存、聚焦的小地图等都 setJoypadFocus)。
    --    这是 vanilla 自己判断 UI 占用用的同一个字段。
    if getJoypadData then
        local jd = getJoypadData(playerNum)
        if jd and jd.focus ~= nil then return true end
    end
    -- 2. D-pad 轮盘菜单可见(兜底:以防 focus 设置有一帧时序差)。
    -- 必须用 isReallyVisible(检查是否真在 UIManager 渲染树)!不能用 isVisible ——
    -- getPlayerRadialMenu 返回的是持久共享对象,visible flag 默认/隐藏后都是 true
    -- (undisplay 只 removeFromUIManager,不 setVisible(false)),用 isVisible 会永远
    -- 返回 true → suppressed 永远 true → 整个 mod 所有功能被锁死。血泪教训。
    if getPlayerRadialMenu then
        local ok, radial = pcall(getPlayerRadialMenu, playerNum)
        if ok and radial and radial.isReallyVisible then
            local ok2, vis = pcall(radial.isReallyVisible, radial)
            if ok2 and vis == true then return true end
        end
    end
    -- 3. 全屏世界地图打开(兜底:World Map 是异步打开的,刚打开几帧 focus 可能还没设)。
    --    ISWorldMap.instance 在地图打开时非 nil,关闭时置 nil。
    if ISWorldMap and ISWorldMap.instance ~= nil then return true end
    return false
end
