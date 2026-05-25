--[[
    Debug Overlay — 在屏幕上画出辅助瞄准的"判定区"可视化。

    用途:
      - 玩家调参时直观看到身体/头部区的大小
      - 录制 mod 介绍 GIF / 截图(展示"软吸附"机制的存在)
      - 评论区有人问"这 mod 到底做了啥",开 overlay 截一张图回答

    默认 OFF。在 Options → MODS → 【B42】手柄辅瞄 → "Show snap zones" 打开。

    绘制内容:
      - 每只可见僵尸:绿色矩形 = 身体辅助区,青色矩形 = 头部辅助区
      - 当前被吸附(sticky)那只:绿/青颜色更亮 + 加粗边框

    只在 mod 处于"激活瞄准"状态时绘制(caa_isAiming() = true)。
    停止瞄准 → overlay 立即消失,不会"冻结"在屏幕上。
]]

if not ISUIElement then return end  -- 防御:某些极简启动环境没加载 UI 框架

local Overlay = ISUIElement:derive("CAA_DebugOverlay")

-- 颜色常量(R, G, B, A 都是 0-1 浮点)
local BODY_RGB         = { r = 0.30, g = 0.85, b = 0.30 }  -- 浅绿
local BODY_RGB_STICKY  = { r = 0.55, g = 1.00, b = 0.55 }  -- 亮绿(被吸附那只)
local HEAD_RGB         = { r = 0.30, g = 0.85, b = 0.95 }  -- 浅青
local HEAD_RGB_STICKY  = { r = 0.55, g = 1.00, b = 1.00 }  -- 亮青(被吸附那只)
local FILL_ALPHA       = 0.15  -- 半透明填充
local BORDER_ALPHA     = 0.85  -- 边框不透明度

function Overlay:new()
    local core = getCore()
    -- 注意:getScreenWidth/Height 不接受参数(PZ 自己的代码也是无参调用)。
    local sw = core:getScreenWidth()
    local sh = core:getScreenHeight()
    local o = ISUIElement.new(self, 0, 0, sw, sh)
    -- ISUIElement 默认不画背景,不需要 noBackground()(那是 ISPanel 的方法)
    o:setWantKeyEvents(false)
    return o
end

-- 把矩形画成 "1px 边框 × 4 条边 + 半透明填充"。
-- 用 ISUIElement 自带的 drawRect / drawRectBorder,坐标都是屏幕像素。
local function drawZone(self, x, y, w, h, rgb, sticky)
    if w <= 0 or h <= 0 then return end
    -- 半透明填充
    self:drawRect(x, y, w, h, FILL_ALPHA, rgb.r, rgb.g, rgb.b)
    -- 1px 边框
    self:drawRectBorder(x, y, w, h, BORDER_ALPHA, rgb.r, rgb.g, rgb.b)
    -- 被吸附那只:再描一圈外框增强对比
    if sticky then
        self:drawRectBorder(x - 1, y - 1, w + 2, h + 2, BORDER_ALPHA, rgb.r, rgb.g, rgb.b)
    end
end

function Overlay:render()
    -- 关闭情形快速 return,避免每帧无谓工作
    if not caa_isAiming or not caa_isAiming() then return end
    if not caa_getShowOverlay or not caa_getShowOverlay() then return end

    -- 第一玩家(split-screen 不支持,跟主 patch 一致)
    local player = getSpecificPlayer(0)
    if not player then return end
    local cell = player:getCell()
    if not cell then return end
    local zlist = cell:getZombieList()
    if not zlist then return end

    -- 当前帧的几何参数(从 ModOptions 推到 Java,通过 getter 拿)
    local hbW    = caa_getHitboxWidth   and caa_getHitboxWidth()   or 0.35
    local hbH    = caa_getHitboxHeight  and caa_getHitboxHeight()  or 0.40
    local hbDown = caa_getHitboxDown    and caa_getHitboxDown()    or 0.15
    local headHW = caa_getHeadHalfW     and caa_getHeadHalfW()     or 0.05
    local headHH = caa_getHeadHalfH     and caa_getHeadHalfH()     or 0.04

    local stickyId = caa_getLastHitZombieId and caa_getLastHitZombieId() or -1

    local n = zlist:size()
    for i = 0, n - 1 do
        local z = zlist:get(i)
        if z and not z:isDead() and z:isOnScreen() and player:CanSee(z) then
            local zid = z:getID()
            local isSticky = (zid == stickyId)

            -- 身体区:从 Java 缓存读 anchor + spriteH
            local anchorX  = caa_getBodyAnchorX  and caa_getBodyAnchorX(zid)
            local anchorY  = caa_getBodyAnchorY  and caa_getBodyAnchorY(zid)
            local spriteH  = caa_getBodySpriteH  and caa_getBodySpriteH(zid)

            if anchorX and anchorX == anchorX and spriteH and spriteH > 0 then
                local bodyW    = spriteH * hbW
                local bodyH    = spriteH * hbH
                local downExt  = spriteH * hbDown
                local bx = anchorX - bodyW * 0.5
                local by = anchorY - bodyH
                local bw = bodyW
                local bh = bodyH + downExt
                drawZone(self, bx, by, bw, bh,
                    isSticky and BODY_RGB_STICKY or BODY_RGB, isSticky)
            end

            -- 头部区:从 Java 缓存读头骨投影
            local headX = caa_getHeadScreenX and caa_getHeadScreenX(zid)
            local headY = caa_getHeadScreenY and caa_getHeadScreenY(zid)
            if headX and headX == headX and spriteH and spriteH > 0 then
                -- HEAD_HALF_W/H 是"半"宽/高(代码里乘 2 处理)
                local headW = spriteH * headHW * 2.0
                local headH = spriteH * headHH * 2.0
                local hx = headX - headW * 0.5
                local hy = headY - headH * 0.5
                drawZone(self, hx, hy, headW, headH,
                    isSticky and HEAD_RGB_STICKY or HEAD_RGB, isSticky)
            end
        end
    end
end

-- 单例 + 挂到 UIManager。OnGameStart 触发一次就够(主菜单不需要)。
local g_instance = nil

local function ensureOverlay()
    if g_instance then return end
    g_instance = Overlay:new()
    g_instance:initialise()
    g_instance:instantiate()
    g_instance:setAlwaysOnTop(false)  -- 让其它 HUD 元素覆盖在上面
    g_instance:addToUIManager()
end

Events.OnGameStart.Add(ensureOverlay)
