# [B42] Controller Aim Assist / 【B42】手柄辅瞄

Controller aim-assist for ranged weapons in Project Zomboid Build 42.
为僵尸毁灭工程 Build 42 手柄玩家提供的远程武器辅助瞄准。

---

## English

### What it does
While aiming with a firearm and the right stick, the cursor softly snaps to nearby zombies. The mod gives you two independent slow-down zones:

- **Body zone** (rectangle around the zombie's foot anchor) — pulls the cursor onto the zombie.
- **Head zone** (small rectangle around the head bone, follows animation) — stronger pull for headshots.

Plus quality-of-life touches:
- **Tracking**: when the locked zombie moves, the cursor follows 1:1.
- **Upward resistance**: easier to drag the cursor up from body to head inside the body zone.
- **Stick response curve**: per-axis power curve (default 1.6 ≈ Steam "Relaxed"), gives slow precise small input and fast large input.

### Requirements
- **Project Zomboid Build 42.18+**
- **[ZombieBuddy](https://steamcommunity.com/sharedfiles/filedetails/?id=3619862853)** — required Java agent framework. Subscribe to it first.

### Installation
1. Subscribe to ZombieBuddy on Steam Workshop.
2. Subscribe to this mod.
3. **macOS only**: ZombieBuddy needs manual install. First PZ launch will pop up instructions. You need to:
   - Copy `ZombieBuddy.jar` from `~/Library/Application Support/Steam/steamapps/workshop/content/108600/3619862853/mods/ZombieBuddy/libs/` to `~/Library/Application Support/Steam/steamapps/common/ProjectZomboid/Project Zomboid.app/Contents/Java/`
   - Add to Steam Launch Options: `-javaagent:ZombieBuddy.jar --`
4. First in-game prompt: ZombieBuddy will ask to approve this mod's Java patch — accept. (If no prompt: edit `~/.zombie_buddy/mod_approvals.json`, find the `ControllerAutoAim` entry, flip `"decision": false` → `true`, save, restart.)
5. Enable both mods in the in-game mod manager.

### Configuration
**Main Menu → Options → MODS → Controller Aim Assist**. All settings persist automatically.

| Setting | What it does |
|---|---|
| Enable aim-assist | Master toggle. Off = mod is silent. |
| Body hitbox width / height / down | Size of the body slow-down zone (fraction of zombie sprite height). |
| Head box half-W / half-H | Size of the head slow-down zone. |
| Body slowdown X / Y | How much the cursor is held when inside the body zone. Lower = stickier. |
| Head slowdown | Same, for the head zone. |
| Upward resistance | How much the body slowdown is bypassed when pulling cursor up. |
| Tracking strength | How much the cursor follows a moving zombie (0 = none, 1 = 1:1). |
| Stick response curve | Per-axis power curve. 1.0 = linear, 1.5 = Relaxed, 2.0 = Wide. |

### How it works (one-line summary)
Three ZombieBuddy patches hook `AimingMode.lerpAiming`, `CharacterInputComponent.isPrecisionAimKeyDown`, and `JoypadManager.getAimingAxisX/Y`. See source code in `java-src/` — every file has detailed Chinese comments.

### Known limitations
- Right stick only, no effect with mouse or melee.
- Head zone needs the zombie's `Bip01_Head` bone — ragdolling/dead zombies fall back to body zone only.
- Multiplayer: both client and server need ZombieBuddy + this mod installed.

### License
MIT — see [LICENSE](LICENSE).

---

## 中文

### 这是什么
玩 Project Zomboid Build 42 用手柄射击时,推右摇杆瞄准枪械,准心会自动"软吸附"到附近的僵尸身上。

mod 给出两个独立的减速区:

- **身体区**(脚下锚点附近的矩形)— 准心进入时被吸住,移动变慢。
- **头部区**(头骨周围的小矩形,跟动画走)— 吸附更强,鼓励爆头。

附加细节:
- **跟踪**:锁定僵尸移动时,准心跟着 1:1 平移。
- **向上抗性**:身体区内向上推杆时减速减弱,方便把准心从身体往头上拉。
- **摇杆响应曲线**:per-axis 幂曲线(默认 1.6 ≈ Steam"缓和"),小推杆精细、大推杆迅速。

### 必备
- **Project Zomboid Build 42.18+**(B42 不稳定分支)
- **[ZombieBuddy](https://steamcommunity.com/sharedfiles/filedetails/?id=3619862853)** — 必备 Java agent 框架,先订阅它。

### 安装
1. 在 Steam Workshop 订阅 ZombieBuddy。
2. 订阅本 mod。
3. **macOS 玩家**:ZB 需要手动安装,第一次启动 PZ 会弹窗提示。你需要:
   - 把 `ZombieBuddy.jar` 从 `~/Library/Application Support/Steam/steamapps/workshop/content/108600/3619862853/mods/ZombieBuddy/libs/` 复制到 `~/Library/Application Support/Steam/steamapps/common/ProjectZomboid/Project Zomboid.app/Contents/Java/`
   - 在 Steam 启动选项里加 `-javaagent:ZombieBuddy.jar --`
4. 第一次启动 ZB 会弹窗让你 approve 本 mod 的 Java patch — 同意即可。(没弹窗:手动编辑 `~/.zombie_buddy/mod_approvals.json`,找 `ControllerAutoAim` entry,把 `"decision": false` 改成 `true`,保存重启。)
5. 在游戏内 mod 管理器里启用两个 mod。

### 设置
**主菜单 → Options → MODS → Controller Aim Assist**。修改自动持久化。

| 选项 | 作用 |
|---|---|
| 启用辅助瞄准 | 总开关,关掉 mod 完全不介入。 |
| 身体框 宽度/高度/下延 | 身体减速区的尺寸(僵尸 sprite 高度的比例)。 |
| 头部框 半宽/半高 | 头部减速区的尺寸。 |
| 身体减速 X/Y | 准心在身体区时的吸附强度。值越小越"吸"。 |
| 头部减速 | 同上,头部区用。 |
| 向上抗性 | 身体区内向上推杆时,减速被多大程度抵消。 |
| 跟踪强度 | 准心跟随移动僵尸的强度(0 = 不跟,1 = 完全跟)。 |
| 摇杆响应曲线 | 摇杆 per-axis 幂曲线。1.0 = 线性,1.5 = 缓和,2.0 = 宽广。 |

### 工作原理
通过 ZombieBuddy 给三个 vanilla 私有方法注入字节码补丁:`AimingMode.lerpAiming`(改 cursor 目标)、`CharacterInputComponent.isPrecisionAimKeyDown`(强制精瞄)、`JoypadManager.getAimingAxisX/Y`(应用曲线)。完整中文注释见 `java-src/src/main/java/`。

### 已知限制
- 仅右摇杆,鼠标/近战时不介入。
- 头部区需要 `Bip01_Head` 骨骼,ragdoll/死亡僵尸退化为只有身体区。
- 多人:服务器和客户端都要装 ZombieBuddy + 本 mod。

### 协议
MIT — 见 [LICENSE](LICENSE)。
