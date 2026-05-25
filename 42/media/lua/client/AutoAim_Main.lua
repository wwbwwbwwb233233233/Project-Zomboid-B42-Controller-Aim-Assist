--[[
    主循环 — 监听 OnPlayerUpdate,每帧判断 mod 是否应该激活。

    激活条件(全部满足):
      1. 是本地玩家
      2. 鼠标最近没活动(否则视为键鼠玩家在用)
      3. 手柄连接且 active
      4. 主手是远程武器
      5. 右摇杆推动超过死区

    激活时:调用 player:setIsAiming(true) 强制 vanilla 进入瞄准状态,
    然后 vanilla 调 lerpAiming(mode = RANGED_PRECISE),Java patch 接管
    做软吸附。

    不激活时:setIsAiming(false),vanilla 自己处理。
]]
ControllerAutoAim = ControllerAutoAim or {}
local M = ControllerAutoAim

-- 是否本地玩家(支持 split-screen,通过 player:isLocalPlayer())
local function isLocalPlayer(player)
    if not player then return false end
    if player.isLocalPlayer then
        local ok, v = pcall(player.isLocalPlayer, player)
        if ok then return v == true end
    end
    return player:getPlayerNum() >= 0
end

-- 主手武器是否是可瞄准的远程枪械
local function isFirearm(weapon)
    if not weapon then return false end
    if not instanceof(weapon, "HandWeapon") then return false end
    if weapon.isRanged then
        local ok, v = pcall(weapon.isRanged, weapon)
        if ok and v then return true end
    end
    -- 兜底:某些版本 API 是 isAimedFirearm
    if weapon.isAimedFirearm then
        local ok, v = pcall(weapon.isAimedFirearm, weapon)
        if ok and v then return true end
    end
    return false
end

-- 强制设置 player 的瞄准 flag(vanilla 用这个决定调 lerpAiming 时的 mode)
local function setAimingFlag(player, on)
    if not player then return end
    if player.setIsAiming then
        pcall(player.setIsAiming, player, on == true)
    end
end

-- 关闭 mod 状态 + 取消瞄准 flag
local function deactivate(player)
    if M.State then M.State.isActive = false end
    setAimingFlag(player, false)
end

local function onPlayerUpdate(player)
    if not isLocalPlayer(player) then return end
    local Config = M.Config
    local State = M.State
    local Input = M.Input
    if not Config or not State or not Input then return end

    local playerNum = player:getPlayerNum()

    -- 各种"激活条件",任何一个不满足就 deactivate 然后退出。
    if Input.isMouseRecentlyActive() then
        deactivate(player)
        return
    end
    if not Input.isControllerActive(playerNum) then
        deactivate(player)
        return
    end
    local weapon = player:getPrimaryHandItem()
    if not isFirearm(weapon) then
        deactivate(player)
        return
    end
    local stickX, stickY = Input.getRightStick(playerNum)
    local magnitude = math.sqrt(stickX * stickX + stickY * stickY)
    if magnitude < Config.STICK_DEAD_ZONE then
        deactivate(player)
        return
    end

    -- 全部 gate 通过 → 强制进入瞄准状态。
    -- vanilla 会用 RANGED_PRECISE 模式调 lerpAiming,我们的 Java patch 接管。
    -- reticle 切换(CURSOR → RETICLE)由 Patch_IsPrecisionAimKeyDown 处理。
    setAimingFlag(player, true)
    State.isActive = true
end

local function onGameStart()
    -- 新存档/重载时重置 state
    if M.State then M.State.isActive = false end
end

Events.OnPlayerUpdate.Add(onPlayerUpdate)
Events.OnGameStart.Add(onGameStart)
