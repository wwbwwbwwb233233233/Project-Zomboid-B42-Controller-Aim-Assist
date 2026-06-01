--[[
    设置页面 — 使用 B42 原生 PZAPI.ModOptions API。
    玩家在 主菜单 → Options → MODS → "Controller Aim Assist" 调整。
    自动持久化到 <cache>/Lua/modOptions.ini,无需手动存档。

    所有 UI 文字走 PZ 翻译系统(getText + media/lua/shared/Translate/<LANG>/),
    按游戏语言自动切换中/英。**绝不在本文件硬编码中文** —— 硬编码的中文字面量在
    某些环境(如装了篡改字体的第三方汉化 mod)下会渲染成乱码;getText 走翻译文件
    则正常显示。翻译 key 全部以 UI_CAA_ 前缀,定义在 Translate/EN/ 与 Translate/CN/
    (各一对 UI_<LANG>.txt 传统格式 + UI.json B42 新格式,兼容新旧翻译系统)。

    重要:option IDs(如 "HITBOX_WIDTH"、"ASSIST_X")不能改 — 改了会让玩家保存
    的旧值丢失。翻译 key 与 option ID 是两回事,可独立命名。
]]

if not PZAPI or not PZAPI.ModOptions then
    -- B41 / 旧版没有这个 API,直接退出(mod 仍按 Java 默认值运行)
    return
end

local opts = PZAPI.ModOptions:create("ControllerAutoAim", getText("UI_CAA_name"))
opts:addDescription(getText("UI_CAA_desc"))
opts:addSeparator()

-- 总开关(第一行)
local tEnable = opts:addTickBox("ENABLED",
    getText("UI_CAA_enable"), true, getText("UI_CAA_enable_tip"))

-- Debug overlay 开关 — 显示判定区可视化(用于截图 / 调参)
local tShowOverlay = opts:addTickBox("SHOW_OVERLAY",
    getText("UI_CAA_overlay"), false, getText("UI_CAA_overlay_tip"))

opts:addSeparator()

-- 身体吸附区(矩形)
local sHBW = opts:addSlider("HITBOX_WIDTH",
    getText("UI_CAA_body_w"),    0.10, 2.00, 0.05, 0.40, getText("UI_CAA_body_w_tip"))
local sHBH = opts:addSlider("HITBOX_HEIGHT",
    getText("UI_CAA_body_h"),    0.10, 2.00, 0.05, 0.45, getText("UI_CAA_body_h_tip"))
local sHBD = opts:addSlider("HITBOX_DOWN",
    getText("UI_CAA_body_down"), 0.00, 1.00, 0.05, 0.15, getText("UI_CAA_body_down_tip"))

-- 头部吸附区(围绕头骨屏幕投影的小矩形)
local sHW = opts:addSlider("HEAD_W",
    getText("UI_CAA_head_w"),      0.00, 0.50, 0.01, 0.03, getText("UI_CAA_head_w_tip"))
local sHH = opts:addSlider("HEAD_H",
    getText("UI_CAA_head_h"),      0.00, 0.50, 0.01, 0.03, getText("UI_CAA_head_h_tip"))
local sHOff = opts:addSlider("HEAD_OFFSET",
    getText("UI_CAA_head_offset"), 0.00, 2.50, 0.05, 1.00, getText("UI_CAA_head_offset_tip"))

opts:addSeparator()

-- 吸附强度(0 = 完全不吸,1 = 完全锁死)
-- 注意:slider 显示的语义是"强度",apply() 时翻转为内部的"slow 系数"再推给 Java。
local sSX = opts:addSlider("ASSIST_X",
    getText("UI_CAA_assist_x"),    0.00, 1.00, 0.05, 0.65, getText("UI_CAA_assist_x_tip"))
local sSY = opts:addSlider("ASSIST_Y",
    getText("UI_CAA_assist_y"),    0.00, 1.00, 0.05, 0.75, getText("UI_CAA_assist_y_tip"))
local sSH = opts:addSlider("ASSIST_HEAD",
    getText("UI_CAA_assist_head"), 0.00, 1.00, 0.05, 0.95, getText("UI_CAA_assist_head_tip"))
local sUR = opts:addSlider("UP_RESIST",
    getText("UI_CAA_up_resist"),   0.00, 1.00, 0.05, 0.70, getText("UI_CAA_up_resist_tip"))

opts:addSeparator()

-- 跟随 + 摇杆曲线
local sTrk = opts:addSlider("TRACK",
    getText("UI_CAA_track"), 0.00, 1.00, 0.05, 1.00, getText("UI_CAA_track_tip"))
local sCurve = opts:addSlider("CURVE",
    getText("UI_CAA_curve"), 0.50, 3.00, 0.05, 1.60, getText("UI_CAA_curve_tip"))

opts:addSeparator()

-- 回中抗性(默认开的核心手感增强)
local sRecenter = opts:addSlider("RECENTER_RESIST",
    getText("UI_CAA_recenter"), 0.00, 0.90, 0.05, 0.90, getText("UI_CAA_recenter_tip"))

opts:addSeparator()

-- 实验功能:相对漂移(默认关 + 灰掉;开启后下方两项启用,用调好的默认值)
local tExperimentalDrift = opts:addTickBox("EXPERIMENTAL_DRIFT",
    getText("UI_CAA_exp_drift"), false, getText("UI_CAA_exp_drift_tip"))
local sDrift = opts:addSlider("VELOCITY_INJECT",
    getText("UI_CAA_drift_amount"), 0.00, 1.00, 0.05, 0.55, getText("UI_CAA_drift_amount_tip"))
local sDriftCurve = opts:addSlider("DRIFT_CURVE",
    getText("UI_CAA_drift_curve"), 0.50, 3.00, 0.05, 1.35, getText("UI_CAA_drift_curve_tip"))

-- 受总开关控制(grey out)的选项(含实验 toggle 本身,但不含实验漂移组)
local conditionalOpts = { tShowOverlay, sHBW, sHBH, sHBD, sHW, sHH, sHOff, sSX, sSY, sSH, sUR, sTrk, sCurve, sRecenter, tExperimentalDrift }
-- 实验漂移组:额外受 EXPERIMENTAL_DRIFT toggle 控制(总开关 AND 实验开关 都开才可调)
local experimentalOpts = { sDrift, sDriftCurve }

-- 关键时序坑(血泪):PZAPI 的 onChange 在玩家点开关"那一刻"触发,但此时
-- option.value 还没更新 —— vanilla MainOptions 只在点 "Apply" 按钮时才把 UI
-- 状态写回 option.value(见 gameOption.apply)。所以这里绝不能用 getValue()
-- (= 读 option.value),否则读到旧值,导致"开了实验开关、推子却还按‘关’来
-- grey out、根本拖不动"。必须用 onChange 传入的新值 value。
-- 无参调用(opts.apply 末尾)则回退到 getValue() —— 那时机 value 已同步,正确。
local function applyEnabledState(masterOverride, driftOverride)
    local masterOn
    if masterOverride ~= nil then masterOn = (masterOverride == true)
    else masterOn = (tEnable:getValue() == true) end
    local driftOn
    if driftOverride ~= nil then driftOn = (driftOverride == true)
    else driftOn = (tExperimentalDrift:getValue() == true) end
    for _, opt in ipairs(conditionalOpts) do
        if opt and opt.setEnabled then pcall(opt.setEnabled, opt, masterOn) end
    end
    for _, opt in ipairs(experimentalOpts) do
        if opt and opt.setEnabled then pcall(opt.setEnabled, opt, masterOn and driftOn) end
    end
end

-- 实时 grey out:玩家点任一开关时立即生效,无需等 Apply。
-- 传入 onChange 的新值,绕开"option.value 尚未更新"的时序坑(见上)。
tEnable.onChange = function(self, value)
    applyEnabledState(value, nil)
end
tExperimentalDrift.onChange = function(self, value)
    applyEnabledState(nil, value)
end

-- 把 slider 值推送到 Java 端
local function pushFloat(fnName, v)
    local fn = _G[fnName]
    if type(fn) == "function" then pcall(fn, v) end
end
local function pushBool(fnName, v)
    local fn = _G[fnName]
    if type(fn) == "function" then pcall(fn, v == true) end
end

opts.apply = function(self)
    -- 总开关先 push,Java 字段 applyOverride 决定所有 patch 是否生效
    pushBool("caa_setApplyOverride", tEnable:getValue())
    -- Debug overlay 开关
    pushBool("caa_setShowOverlay",   tShowOverlay:getValue())
    -- 11 个数值参数
    pushFloat("caa_setHitboxWidth",    sHBW:getValue())
    pushFloat("caa_setHitboxHeight",   sHBH:getValue())
    pushFloat("caa_setHitboxDown",     sHBD:getValue())
    pushFloat("caa_setHeadHalfW",      sHW:getValue())
    pushFloat("caa_setHeadHalfH",      sHH:getValue())
    pushFloat("caa_setHeadOffset",     sHOff:getValue())
    -- slider 值是"吸附强度"(0=无吸,1=完全锁),Java 内部期望的是 slow 系数
    -- (target = src + (target-src) × slow_coef,slow=0 完全锁,slow=1 完全不吸)。
    -- 所以推 Java 时取 1 - slider 翻回去。
    pushFloat("caa_setSlowdownX",      1.0 - sSX:getValue())
    pushFloat("caa_setSlowdownY",      1.0 - sSY:getValue())
    pushFloat("caa_setSlowdownHead",   1.0 - sSH:getValue())
    pushFloat("caa_setUpResist",       sUR:getValue())
    pushFloat("caa_setTrackingFactor", sTrk:getValue())
    pushFloat("caa_setStickCurve",     sCurve:getValue())
    pushFloat("caa_setRecenterResist", sRecenter:getValue())
    -- 相对漂移:仅实验开关开启时生效;关闭时推 0(功能完全不介入,即使 slider 有值)。
    local driftOn = tExperimentalDrift:getValue() == true
    pushFloat("caa_setVelocityInject", driftOn and sDrift:getValue() or 0)
    pushFloat("caa_setDriftCurve",     sDriftCurve:getValue())
    -- 同步 grey out 视觉(用户每次开 menu 看到一致状态)
    applyEnabledState()
end

-- 重要:PZAPI.ModOptions 在初始加载时 NOT 触发 apply。
-- 必须显式在主菜单进入 + 游戏开始时各调一次,确保 Java 字段同步玩家保存的值。
Events.OnMainMenuEnter.Add(function() opts:apply() end)
Events.OnGameStart.Add(function() opts:apply() end)
