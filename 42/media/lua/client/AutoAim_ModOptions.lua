--[[
    设置页面 — 使用 B42 原生 PZAPI.ModOptions API。
    玩家在 主菜单 → Options → MODS → "Controller Aim Assist" 调整。
    自动持久化到 <cache>/Lua/modOptions.ini,无需手动存档。

    所有 UI 文字按当前游戏语言显示中文/英文。

    重要:option IDs(如 "HITBOX_WIDTH"、"SLOW_X")不能改 — 改了会让玩家
    保存的旧值丢失。需要重命名的只是 UI 显示文字。
]]

if not PZAPI or not PZAPI.ModOptions then
    -- B41 / 旧版没有这个 API,直接退出(mod 仍按 Java 默认值运行)
    return
end

-- 双语 helper:根据游戏语言返回中文或英文。PZ 中文代码 CH/CN(繁/简)。
local function tr(en, cn)
    local lang = "EN"
    if getCore then
        local ok, l = pcall(getCore().getOptionLanguage, getCore())
        if ok and l then lang = tostring(l) end
    end
    if lang == "CH" or lang == "CN" then return cn end
    return en
end

local opts = PZAPI.ModOptions:create("ControllerAutoAim", tr("[B42] Controller Aim Assist", "【B42】手柄辅瞄"))
opts:addDescription(tr(
    "Tune the controller aim-assist behavior. Changes apply after you click Apply.",
    "调整手柄辅助瞄准。修改后点 Apply 才会生效。"
))
opts:addSeparator()

-- 总开关(第一行)
local tEnable = opts:addTickBox("ENABLED",
    tr("Enable aim-assist", "启用辅助瞄准"),
    true,
    tr(
        "Master toggle. When off, the mod is completely silent and the other settings have no effect.",
        "总开关。关闭时 mod 完全不介入,下方其他设置无效。"
    ))

-- Debug overlay 开关 — 显示判定区可视化(用于截图 / 调参)
local tShowOverlay = opts:addTickBox("SHOW_OVERLAY",
    tr("Show snap zones (for screenshots / tuning)",
       "显示辅助区可视化(用于截图 / 调参)"),
    false,
    tr(
        "Draws colored rectangles around each visible zombie showing the body and head snap zones. NOTE: may affect game balance — recommended for tuning / recording only.",
        "在每只可见僵尸身上画出身体吸附区(绿色矩形)和头部吸附区(青色矩形)。注意:可能影响游戏平衡性,仅建议在调参 / 录制时启用。"
    ))

opts:addSeparator()

-- 身体吸附区(矩形)
local sHBW = opts:addSlider("HITBOX_WIDTH",
    tr("Body snap zone width",  "身体吸附区 宽度"),          0.10, 2.00, 0.05, 0.35,
    tr("Width of the body snap zone (fraction of zombie sprite height).",
       "身体吸附区的宽度(僵尸 sprite 高度的比例)。"))
local sHBH = opts:addSlider("HITBOX_HEIGHT",
    tr("Body snap zone height", "身体吸附区 高度"),          0.10, 2.00, 0.05, 0.40,
    tr("Height of the body snap zone, measured upward from the foot.",
       "身体吸附区从脚下往上的高度。"))
local sHBD = opts:addSlider("HITBOX_DOWN",
    tr("Body snap zone bottom extension", "身体吸附区 下延"), 0.00, 1.00, 0.05, 0.15,
    tr("Extra downward extension below the foot (covers crawling / prone zombies).",
       "脚下往下额外延伸(覆盖爬行/趴下的僵尸)。"))

-- 头部吸附区(围绕头骨屏幕投影的小矩形)
local sHW = opts:addSlider("HEAD_W",
    tr("Head snap zone width",  "头部吸附区 宽度"),  0.00, 0.50, 0.01, 0.05,
    tr("Width of the head snap zone (centered on the head bone projection).",
       "头部吸附区宽度(围绕头骨屏幕投影居中)。"))
local sHH = opts:addSlider("HEAD_H",
    tr("Head snap zone height", "头部吸附区 高度"),  0.00, 0.50, 0.01, 0.04,
    tr("Height of the head snap zone.",
       "头部吸附区高度。"))

opts:addSeparator()

-- 吸附强度(0 = 完全不吸,1 = 完全锁死)
-- 注意:slider 显示的语义是"强度",apply() 时翻转为内部的"slow 系数"再推给 Java。
-- option ID 用 ASSIST_* 而不是旧的 SLOW_*,避免旧版玩家保存的值被反向解读。
local sSX = opts:addSlider("ASSIST_X",
    tr("Horizontal snap strength", "水平吸附强度"),    0.00, 1.00, 0.05, 0.65,
    tr("How strongly the cursor is pulled toward the zombie horizontally. Higher = stickier. 0.0 = no pull, 1.0 = full lock.",
       "准星水平方向被吸向僵尸的强度。值越大越粘。0.0 = 不吸,1.0 = 完全锁死。"))
local sSY = opts:addSlider("ASSIST_Y",
    tr("Vertical snap strength", "垂直吸附强度"),    0.00, 1.00, 0.05, 0.75,
    tr("How strongly the cursor is pulled toward the zombie vertically. Higher = stickier.",
       "准星垂直方向被吸向僵尸的强度。值越大越粘。"))
local sSH = opts:addSlider("ASSIST_HEAD",
    tr("Head snap strength",   "头部吸附强度"),      0.00, 1.00, 0.05, 0.90,
    tr("Pull strength inside the head snap zone. Higher = stickier. Usually set stronger than body so it's easier to land headshots.",
       "头部吸附区的吸附强度。值越大越粘。通常比身体更强,鼓励爆头。"))
local sUR = opts:addSlider("UP_RESIST",
    tr("Pull-up easing (body to head)", "上拉助力(身体到头部)"),    0.00, 1.00, 0.05, 0.70,
    tr("How much the body snap weakens when you push the stick upward (makes it easier to drag the cursor from the body up to the head). 0 = no easing, 1 = full easing.",
       "在身体吸附区内向上推杆时,吸附效果减弱多少(方便把准星从身体拉到头部)。0 = 不减弱,1 = 完全减弱。"))

opts:addSeparator()

-- 跟随 + 摇杆曲线
local sTrk = opts:addSlider("TRACK",
    tr("Cursor follow strength", "准星跟随强度"),  0.00, 1.00, 0.05, 1.00,
    tr("How strongly the cursor follows a moving zombie that is being snapped onto. 0 = no follow, 1 = 1:1 follow.",
       "被吸附的僵尸移动时,准星跟随它的强度。0 = 不跟,1 = 完全跟随。"))
local sCurve = opts:addSlider("CURVE",
    tr("Stick sensitivity curve", "摇杆灵敏度曲线"), 0.50, 3.00, 0.05, 1.60,
    tr("Per-axis power curve: small stick inputs become slower (finer control), large inputs faster. 1.0 = linear, 1.5 = Steam Relaxed, 2.0 = Wide. If you already use Steam Input curves, keep this at 1.0.",
       "Per-axis 幂曲线:小推杆变慢(精细操作),大推杆变快。1.0 = 线性,1.5 = Steam 缓和,2.0 = 宽广。已在 Steam Input 里设过曲线就保持 1.0。"))

-- 受总开关控制(grey out)的所有选项(包括 overlay tickbox)
local conditionalOpts = { tShowOverlay, sHBW, sHBH, sHBD, sHW, sHH, sSX, sSY, sSH, sUR, sTrk, sCurve }

local function applyEnabledState(enabled)
    for _, opt in ipairs(conditionalOpts) do
        if opt and opt.setEnabled then pcall(opt.setEnabled, opt, enabled == true) end
    end
end

-- 实时 grey out:玩家点 tickbox 时立即生效,无需等 Apply。
tEnable.onChange = function(self, value)
    applyEnabledState(value == true)
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
    -- slider 值是"吸附强度"(0=无吸,1=完全锁),Java 内部期望的是 slow 系数
    -- (target = src + (target-src) × slow_coef,slow=0 完全锁,slow=1 完全不吸)。
    -- 所以推 Java 时取 1 - slider 翻回去。
    pushFloat("caa_setSlowdownX",      1.0 - sSX:getValue())
    pushFloat("caa_setSlowdownY",      1.0 - sSY:getValue())
    pushFloat("caa_setSlowdownHead",   1.0 - sSH:getValue())
    pushFloat("caa_setUpResist",       sUR:getValue())
    pushFloat("caa_setTrackingFactor", sTrk:getValue())
    pushFloat("caa_setStickCurve",     sCurve:getValue())
    -- 同步 grey out 视觉(用户每次开 menu 看到一致状态)
    applyEnabledState(tEnable:getValue() == true)
end

-- 重要:PZAPI.ModOptions 在初始加载时 NOT 触发 apply。
-- 必须显式在主菜单进入 + 游戏开始时各调一次,确保 Java 字段同步玩家保存的值。
Events.OnMainMenuEnter.Add(function() opts:apply() end)
Events.OnGameStart.Add(function() opts:apply() end)
