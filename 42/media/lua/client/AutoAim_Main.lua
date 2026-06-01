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

-- 把 gate 的最终结论推给 Java。aimGranted=true 时 Patch_IsPrecisionAimKeyDown 才
-- 强制精瞄。Java 不再自己读摇杆判断,完全听这个 —— 否则会绕过手柄绑定 + UI 判断。
local function setAimGranted(on)
    if caa_setAimGranted then pcall(caa_setAimGranted, on == true) end
end

-- 关闭 mod 状态 + 取消瞄准 flag。任何非激活路径都经过这里,所以在这里统一
-- 收回 aimGranted,保证"只要不是完整 gate 通过,Java 就不强制瞄准"。
local function deactivate(player)
    if M.State then M.State.isActive = false end
    setAimingFlag(player, false)
    setAimGranted(false)
end

-- 把"UI 占用手柄"状态推给 Java。suppressed=true 时所有 Java patch 完全不介入
-- (光靠 Lua 不 setIsAiming 不够 —— AimAxisX/Y 会独立改摇杆曲线干扰 UI 导航)。
local function setSuppressed(on)
    if caa_setSuppressed then pcall(caa_setSuppressed, on == true) end
end

local function onPlayerUpdate(player)
    if not isLocalPlayer(player) then return end
    local Config = M.Config
    local State = M.State
    local Input = M.Input
    if not Config or not State or not Input then return end

    local playerNum = player:getPlayerNum()

    -- 最优先:UI(轮盘菜单/地图/库存等)占用手柄时,右摇杆要完全留给 UI 导航。
    -- 这个判断独立于其它 gate,每帧第一时间推给 Java,让所有 Java patch 一起退出。
    -- 放最前是为了避免状态残留(其它 deactivate 路径可能提前 return,suppressed 来不及更新)。
    local uiCapturing = Input.isControllerActive(playerNum) and Input.isUICapturingJoypad(playerNum)
    setSuppressed(uiCapturing)
    if uiCapturing then
        deactivate(player)
        return
    end

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
    -- 武器未就绪(切换/收起/拔出动画进行中)→ 不瞄准。
    if player.isWeaponReady then
        local ok, ready = pcall(player.isWeaponReady, player)
        if ok and ready == false then
            deactivate(player)
            return
        end
    end
    local stickX, stickY = Input.getRightStick(playerNum)
    local magnitude = math.sqrt(stickX * stickX + stickY * stickY)
    if magnitude < Config.STICK_DEAD_ZONE then
        deactivate(player)
        return
    end

    -- 全部 gate 通过 → 强制进入瞄准状态。
    -- vanilla 会用 RANGED_PRECISE 模式调 lerpAiming,我们的 Java patch 接管。
    -- reticle 切换(CURSOR → RETICLE)由 Patch_IsPrecisionAimKeyDown 处理(它听 aimGranted)。
    setAimingFlag(player, true)
    setAimGranted(true)
    State.isActive = true
end

local function onGameStart()
    -- 新存档/重载时重置 state
    if M.State then M.State.isActive = false end
end

Events.OnPlayerUpdate.Add(onPlayerUpdate)
Events.OnGameStart.Add(onGameStart)
