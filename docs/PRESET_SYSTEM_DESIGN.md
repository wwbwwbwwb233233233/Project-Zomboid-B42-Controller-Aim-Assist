# Preset System Design (Mode A: built-in presets + migration)

目标:把一堆裸滑条收敛成几个一键预设;新玩家默认"推荐",老玩家手感零打扰。

## ModOptions 机制(已读源码确认)

`PZAPI/ModOptions.lua` + UI 在 `OptionScreens/MainOptions.lua`,combobox UI 是 `ISComboBox`。

- `addComboBox(id, name, tooltip)` → option;`:addItem(text, isSelected)` 加项;`:getValue()` 返回选中索引(1-based);`:setValue(index)` 设索引。
- `addButton(id, name, tooltip, onclickfunc, target, arg1..4)`。
- `addSlider(...)`:`:getValue()` / `:setValue(v)`。
- **关键:`setValue` 不触发 onChange。** slider 的 setValue 调 `element:setCurrentValue(value, true)`,而 `ISSliderPanel:setCurrentValue(_v, _ignoreOnChange)` 第二参数 = ignore。combobox setValue 直接设 `.selected`,也不触发。
- **onChange 只在玩家 UI 交互时触发**(MainOptions.lua 在控件回调里调 `option:onChange(...)`)。combobox 的 onChange 签名见 `ISComboBox.lua:253`:`onChange(target, combobox, ...)` —— 实测确认参数。
- **load 直接赋 `.value`/`.selected`,连 setValue 都不经过,更不触发 onChange。**
- **save 写所有 option**(不只改过的)到 `ModOptions.ini`,格式 `type|modID|optionID|value`。

### 由此得出的两条保证(无回调循环)
1. 套用预设 = 对所有 slider `setValue` → 不触发它们的 onChange → 不会反手把 combobox 切回"自定义"。
2. load 不触发 onChange → 重进游戏不会乱套用预设。

## 预设系统设计

### combobox "预设方案"
```
[ 自定义 | 推荐 | 经典(旧版手感) | (精准爆头) | (快速横扫) ]
默认选中 = 自定义(安全,不自动覆盖任何人)
```

### 预设表(硬编码,全套手感参数)
```lua
local PRESETS = {
  -- key 对应 combobox item 索引(自定义=1,无预设)
  [2] = { -- 推荐(新玩家默认)
    HITBOX_WIDTH=?, HITBOX_HEIGHT=?, HITBOX_DOWN=?,
    HEAD_W=?, HEAD_H=?, HEAD_OFFSET=?,
    ASSIST_X=?, ASSIST_Y=?, ASSIST_HEAD=?, UP_RESIST=?,
    TRACK=?, CURVE=?, RECENTER=?, VELOCITY_INJECT=?, DRIFT_CURVE=?,
  },
  [3] = { -- 经典 = 旧版行为(下面已填全,无需用户提供)
    HITBOX_WIDTH=0.35, HITBOX_HEIGHT=0.40, HITBOX_DOWN=0.15,
    HEAD_W=0.05, HEAD_H=0.04, HEAD_OFFSET=0.0,   -- 0 = 旧的脖子点判定
    ASSIST_X=0.65, ASSIST_Y=0.75, ASSIST_HEAD=0.90, -- = 旧 SLOWDOWN 0.35/0.25/0.10 翻转
    UP_RESIST=0.70, TRACK=1.00, CURVE=1.60,
    RECENTER=0.0, VELOCITY_INJECT=0.0, DRIFT_CURVE=1.0, -- 新参数全关 = 旧行为
  },
}
```
注意 ASSIST_* 是"吸附强度"(UI 翻转),Java 端 SLOWDOWN = 1 − ASSIST。经典用 0.65/0.75/0.90 ↔ 旧 SLOWDOWN 0.35/0.25/0.10。

预设**不含** ENABLED(总开关)和 SHOW_OVERLAY(debug)—— 选预设只改手感,不动这两个。

### 套用 + 切回自定义
```lua
local applying = false  -- 保险:套用期间不响应任何 onChange

local function applyPreset(idx)
    local p = PRESETS[idx]; if not p then return end
    applying = true
    sHBW:setValue(p.HITBOX_WIDTH); sHBH:setValue(p.HITBOX_HEIGHT); ... 全部 setValue ...
    applying = false
end

cPreset.onChange = function(target, combo)         -- 玩家选预设
    local idx = cPreset:getValue()
    if idx ~= CUSTOM_IDX then applyPreset(idx) end
end

-- 每个手感 slider:玩家手动拖 → 切回"自定义"
local function markCustom()
    if applying then return end                    -- 套用预设触发的不算手动
    cPreset:setValue(CUSTOM_IDX)
end
sHBW.onChange = markCustom; sHBH.onChange = markCustom; ... 全部 ...
```
(setValue 不触发 onChange,所以 `applying` flag 理论上多余,但留作保险。)

## 迁移逻辑(新玩家默认推荐,老玩家零打扰)

```lua
local function iniHasOurMod()
    local f = getFileReader("ModOptions.ini", false)  -- false=不创建;确认签名
    if not f then return false end
    local found = false
    while true do
        local line = f:readLine(); if not line then break end
        if line:find("|ControllerAutoAim|", 1, true) then found = true; break end
    end
    f:close(); return found
end

-- OnGameStart 跑一次(此时 PZAPI 已 load ini → option.value 已是 ini 值或默认)
local function migrate()
    if iniHasOurMod() then return end       -- 老玩家(有行)→ 完全不动
    -- 新玩家:套用"推荐"预设 + 选中它 + 存盘
    cPreset:setValue(RECOMMENDED_IDX)
    applyPreset(RECOMMENDED_IDX)
    PZAPI.ModOptions:save()                  -- 写 ini,从此有行,迁移幂等
end
```

- 幂等:新玩家迁移后 ini 有行,下次 `iniHasOurMod()` 返回 true → 不再迁移。
- 老玩家:ini 一直有行 → 永不迁移 → combobox 停在"自定义"(他们存的)+ 参数 = 他们的 ini 值。
- 迁移时设置页未打开(element=nil),setValue 只设 `.value`,save 写盘 → 完全静默。

## 待实现时验证的点(用临时 log)
1. **combobox onChange 的确切签名/参数**(`(target, combo)` 还是别的)—— log 一下 onChange 收到什么。
2. **OnGameStart 时 PZAPI 是否已 load 了 ini**(option.value 已是 ini 值)—— 若没有,迁移时机改到更晚的事件。
3. **`getFileReader("ModOptions.ini", false)` 第二参数**(false 是否=不创建)—— 确认不会误建空文件。
4. **setValue 确实不触发 onChange** —— 套用预设时 log 看 slider.onChange 有没有被调(预期没有)。

## 待用户提供
- **推荐预设全套值**(15 个手感参数)= 新玩家默认开成什么。
- 要不要 **精准爆头 / 快速横扫** 等额外预设?要的话给各自全套值。
