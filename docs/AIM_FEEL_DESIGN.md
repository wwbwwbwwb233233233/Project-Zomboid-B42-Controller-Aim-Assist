# Aim Feel Design — Recenter Resistance + Velocity Injection

设计目标:消除 PZ B42 手柄 polar 瞄准的"回中力对抗感"(玩家想保持准星位置必须一直
保持摇杆,松手就被拽回中心),可选地逼近 relative(直角坐标系)手感。

## 核心比喻

准星 = 桌面小球,默认用橡皮筋拴在屏幕中心(= vanilla polar)。

- **回中抗性 (RECENTER_RESISTANCE)** = 把橡皮筋调粘,松手后球不太往回走
- **速度注入 (VELOCITY_INJECT)** = 给球装小马达,推着摇杆球持续往外滑(relative 的精髓)

两个参数张成一条连续谱:

```
纯 polar ──── 粘 polar ──── 混合 ──── 接近 relative
resist=0     resist 高      resist 高    resist 高
inject=0     inject=0       inject 中    inject 大
```

## 为什么需要两个参数(减法 vs 加法鸿沟)

回中抗性是**减法**(削弱 vanilla 已有的朝中心位移)。它解决"松手被拽回",但**无法**让
"保持推杆时准星持续移动" —— 因为准星到达 polar 目标后位移≈0,减法乘 0 还是 0。

要补上"保持推杆持续移动",必须**加法**(主动注入向外速度)。那个注入项就是 relative 的
速度模型。所以:回中抗性 ←→ 全 relative 是同一公式两端,VELOCITY_INJECT 是连接旋钮。

## 公式(全在 lerpAiming OnExit 实现)

vanilla 已算好 `out_result = src + d_polar`,其中 `d_polar = out_result − src` 是这帧
polar 位移。我们在 OnExit 改写:

```
d = out_result − src                        // vanilla 这帧位移

// 1. 回中抗性:只削弱朝 center 的径向分量
toCenter = normalize(center − src)
radial   = dot(d, toCenter)                 // >0 = 这帧在朝中心收
if radial > 0:
    d −= toCenter × radial × RECENTER_RESISTANCE

// 2. 速度注入:沿 vanilla 的"向外"方向(target−center)加位移
outward = normalize(target − center)
d += outward × stickMagnitude × VELOCITY_INJECT_SCALE × dt

out_result = src + d                         // 写回

// 3. tracking(已有):跟随僵尸屏幕位移,加在最后
out_result += trackDelta × TRACKING_FACTOR
```

关键性质:
- 只削**径向**回中分量,切向移动(绕僵尸微调)不受影响
- 注入项 `× stickMagnitude` → 松手(mag=0)注入自动归零
- 因此**退出逻辑自动保住**:松手 → 注入消失 → 残余回中力(resist<1)让准星缓慢飘回
  中心 → 自然退出。退出节奏 = 玩家松手时长,纯操作驱动,无客观条件。

## 与减速区(snap)的交互 —— 重要

管线顺序:OnEnter 做 snap 减速(改 target)→ vanilla lerp → OnExit 做回中抗性/注入/tracking。

- **回中抗性** = 削弱移动(朝 center),与减速区**同向**(都让 cursor 更稳),不冲突。
- **速度注入** = 增加移动(向外),与减速区**反向**。如果注入无视减速区,会在僵尸身上把
  减速好不容易压下的移动加回来,**抵消"粘"感**。

修正(必须):OnEnter 命中减速区时记下减速系数 `lastInjectScale = (slowX+slowY)/2`
(没命中 = 1.0),OnExit 的注入乘上它。于是:
- 空地(无减速)→ 注入全效,移动过程顺滑
- 减速区内 → 注入同等被压,cursor 仍"粘"住精调

设计含义:relative drift 的价值在"把准星移到目标的过程",瞄上了就该粘住精调,
不需要持续滑动。减速区压制注入正好实现这个意图。

## 边界 / corner cases

1. **RECENTER_RESISTANCE 上限 0.9,不到 1.0** —— 留 ~10% 回中力保证准星最终能飘回退出。
2. **dt 必须 clamp** —— 用 System.currentTimeMillis() 差值,clamp [0.005s, 0.05s],
   防首帧/卡顿时巨大 dt 让准星瞬移。
3. **径向分解必须做** —— 只削回中分量,否则正常瞄准移动被整体拖慢。
4. **inject 越大越失去 polar 直观定位** —— polar 是"推到哪准星到哪半径",inject 大了准星
   会越过 stick 对应半径继续漂,需要预判。所以 inject 是"调味"不是"主料",默认小。
5. **默认两个都 0** —— = vanilla 行为不变,opt-in。不破坏现有用户手感。

## 参数 / 滑条

| ID | 显示名 | 范围 | 默认 | 说明 |
|---|---|---|---|---|
| RECENTER_RESIST | Recenter resistance / 回中抗性 | 0 ~ 0.9 | 0 | 松手后准星多抵抗回中 |
| VELOCITY_INJECT | Relative drift / 相对漂移 | 0 ~ 1 | 0 | 推杆时额外向外滑动(relative 味) |

内部 VELOCITY_INJECT_SCALE ≈ 600 px/s(滑条 1.0 = 600 像素/秒)。

## 待游戏内验证

- lerpAiming 的 `src` 语义(我们的公式只用 vanilla 自己的 src→out_result 位移,
  不依赖 src 是否=上一帧,所以**回中抗性不受影响**;但若将来要纯 relative 累加,需确认)
- dt 估算够不够稳(currentTimeMillis 差值 vs 真实帧间隔)
