package com.wenbo.controllerautoaim;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.SkeletonBone;
import zombie.input.AimingMode;
import zombie.input.JoypadManager;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoCell;
import zombie.iso.Vector2;
import zombie.Lua.LuaManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.lwjgl.util.vector.Vector3f;

/*
 * 核心补丁:patch vanilla 的 `AimingMode.lerpAiming` 方法。
 *
 *   - OnEnter:在 vanilla 计算 cursor 之前,识别 cursor 当前是否落在某只
 *     可见僵尸的"身体框"或"头部框"内。如果命中,改写 `targetX/targetY` 参数
 *     让 vanilla 后续的插值把 cursor 拉向 src(= 减速效果)。
 *
 *   - OnExit:vanilla 已经把最终 cursor 写到 `out_result` 后,我们再把"跟踪
 *     位移"加上去。放在 OnExit 是因为加在 OnEnter 的 targetX/Y 上会被
 *     vanilla 内部的 lerp(src,target,rate) 衰减(只有 rate 比例的 delta 被
 *     带到 cursor 上),放 OnExit 直接改最终 cursor 实现 1:1 跟随。
 *
 * 整套设计要点见 memory 文档,关键坑:
 *   - 所有方法 + helper 必须 public,ZB 把 advice body 注入到 vanilla 类
 *     context 里跑,vanilla 类访问不了 private/synthetic 方法。
 *   - 不能用 lambda(`removeIf(e -> ...)` 编译成 private synthetic 方法,
 *     同样的 IllegalAccessError)。用显式 Iterator。
 *   - tracking delta 用世界坐标缓存 + 当前帧投影,绝对不能存屏幕坐标
 *     (相机 offX 每 tick lerp 漂移会污染 delta)。
 */
@Patch(className = "zombie.input.AimingMode", methodName = "lerpAiming", warmUp = true)
public class Patch_AimingMode {

    // ============ 可调参数(由 PZAPI.ModOptions 通过 Lua 写入) ============

    // 身体减速强度。cursor 在 body 框内时,target 朝 src 收缩比例。
    // 越小越"吸",值为 1 完全不减速,0 完全锁死。
    public static volatile float SLOWDOWN_X = 0.35f;
    public static volatile float SLOWDOWN_Y = 0.25f;

    // 头部减速强度(命中 head 框时使用,通常更强)。
    public static volatile float SLOWDOWN_HEAD = 0.10f;

    // 跟踪强度。OnExit 把僵尸位移加到 cursor 上的比例。1.0 = 1:1 跟随。
    public static volatile float TRACKING_FACTOR = 1.0f;

    // 身体框尺寸(spriteH 的比例)。
    // WIDTH_RATIO 是全宽,HEIGHT_RATIO 是从锚点往上的高度,
    // DOWN_RATIO 是从锚点往下额外延伸(覆盖爬行/趴下姿态)。
    public static volatile float HITBOX_WIDTH_RATIO = 0.40f;
    public static volatile float HITBOX_HEIGHT_RATIO = 0.45f;
    public static volatile float HITBOX_DOWN_RATIO = 0.15f;

    // 头部框尺寸(spriteH 的比例,半宽 + 半高)。围绕真实头中心屏幕投影。
    public static volatile float HEAD_HALF_W = 0.03f;
    public static volatile float HEAD_HALF_H = 0.03f;

    // 头中心外推系数:Bip01_Head 骨骼实际在脖子顶(偏低),真实头中心在它之上。
    // 头中心 = Head + (Head − Neck) × HEAD_OFFSET_RATIO,沿颈→头方向往上外推。
    // 用 (Head−Neck) 的倍数 → 自动适应体型/姿态,且摇头晃脑时头中心实时跟随。
    // 玩家用 debug overlay 看着拖滑条调到贴头。
    public static volatile float HEAD_OFFSET_RATIO = 1.0f;

    // 摇杆响应曲线指数。被 Patch_AimAxisX/Y 应用:out = sign(in) * |in|^exp。
    // 1.0 = 线性(不变换),1.5 = Steam"缓和",2.0 = "宽广",0.6 = "激进"。
    public static volatile float STICK_CURVE_EXPONENT = 1.60f;

    // 身体框内向上移动的"减速抗性"。cursor 想向上拉(targetY < src.y)时,
    // SLOWDOWN_Y 的衰减效果按此比例被免除。0 = 无抗性,1 = 完全免除。
    // 默认 0.7 让玩家在身体框内向上拉到头部框时阻力大幅降低,瞄头更顺。
    public static volatile float UP_RESISTANCE = 0.70f;

    // ============ 回中抗性 + 速度注入(瞄准手感,见 docs/AIM_FEEL_DESIGN.md) ============

    // 回中抗性:削弱 vanilla polar 把 cursor 朝屏幕中心拉回的力。
    // 0 = vanilla(松手 cursor 立刻回中),0.9 = 几乎不回中(松手 cursor 基本停住)。
    // 只削"朝 center 的径向分量",切向移动不受影响。上限 0.9 保留少量回中力以便自然退出。
    public static volatile float RECENTER_RESISTANCE = 0.9f;

    // 速度注入:推杆时额外往"向外"方向加位移,模拟 relative(直角坐标系)的"推着持续移动"。
    // 0 = 纯 polar 定位(推到哪 cursor 到对应半径即停),>0 = 带 relative 味(推着持续滑)。
    // 乘 VELOCITY_INJECT_SCALE 转成像素/秒尺度。乘 stick magnitude → 松手自动归零。
    public static volatile float VELOCITY_INJECT = 0.0f;

    // 注入尺度:滑条 1.0 = 这么多像素/秒。
    public static final float VELOCITY_INJECT_SCALE = 600.0f;

    // 当前帧的注入衰减系数:cursor 在减速区内时 = 该区的减速系数(让注入也被减速,
    // 不抵消"粘"感);不在减速区 = 1.0(注入全效)。OnEnter 设,OnExit 读。
    public static volatile float lastInjectScale = 1.0f;

    // relative drift 模式下右摇杆用的独立曲线指数(默认 1.0 = 线性)。
    // polar 模式用 STICK_CURVE_EXPONENT(定位曲线),drift 模式摇杆是速度油门,
    // 默认线性最可控。Patch_AimAxisX/Y 根据 inRelativeDriftMode 选用哪条。
    public static volatile float DRIFT_CURVE_EXPONENT = 1.35f;

    // 当前是否处于 relative drift 模式(drift 开启 + 正在瞄准)。
    // 由 OnEnter 设置,Patch_AimAxisX/Y 读取来切换曲线。drift 关闭时永远 false,
    // 所以不开 drift 的玩家曲线行为完全不变。
    public static volatile boolean inRelativeDriftMode = false;

    // 曲线 morph:当前"生效"的曲线指数,每帧朝目标(drift 或 polar)平滑过渡,
    // 让进出 drift 时摇杆灵敏度平滑变化而非硬跳。初始 1.0(线性,恒等),
    // 几帧内 morph 到真实 polar 曲线。Patch_AimAxisX/Y 直接读这个值。
    public static volatile float currentCurveExp = 1.0f;

    // 上一帧时间戳(算 dt 用)。PZ 单线程,无需锁。
    public static volatile long lastFrameMs = 0L;

    // ============ 运行时状态(Lua HUD/调试读取) ============

    // cursor 当前是否落在某个僵尸的命中区(身体或头部)。
    public static volatile boolean lastInZone = false;

    // 当前帧是否应用了跟踪位移(僵尸在动且 prev cache 有效)。
    public static volatile boolean lastTracking = false;

    // 命中的是否是头部框(否则是身体框)。
    public static volatile boolean lastInHead = false;

    // mod 是否处于"激活"瞄准状态(RANGED 模式 + 远程武器)。
    // 是 Lua HUD 生成/销毁的唯一开关。
    public static volatile boolean lastAiming = false;

    // 上一次命中的僵尸 ID。多个僵尸堆叠时,优先继续选中这只,避免 cursor 在
    // 重叠 hitbox 间高频切换造成的视觉抖动。
    public static volatile int lastHitZombieId = -1;

    // ============ 跨 tick 缓存 ============

    // 每只可见僵尸上一帧的世界坐标 {worldX, worldY, worldZ, timestamp}。
    // 关键:存世界坐标而不是屏幕坐标 — 相机 offX 每 tick 漂移会污染屏幕 delta,
    // 用世界坐标在当前帧重新投影才能抵消相机移动。
    public static final ConcurrentHashMap<Integer, float[]> trackWorld = new ConcurrentHashMap<>();

    // 每只可见僵尸头骨的屏幕投影 {hsX, hsY, timestamp}。供 Lua 查询(若有需要)。
    public static final ConcurrentHashMap<Integer, float[]> headScreenCache = new ConcurrentHashMap<>();

    // 每只可见僵尸的身体锚点 + sprite 高度 {anchorX, anchorY, spriteHpx, timestamp}。
    // 供 Lua 端 debug overlay 画身体辅助区矩形用。
    public static final ConcurrentHashMap<Integer, float[]> bodyAnchorCache = new ConcurrentHashMap<>();

    // tracking delta 的有效期(超过则视为 stale,不算 delta)。
    public static final long ANCHOR_TTL_MS = 250L;

    // cache entry 清理阈值(超过 500ms 没更新 = 僵尸离开 cell/视野,清掉)。
    public static final long CACHE_CLEANUP_TTL_MS = 500L;

    // OnEnter → OnExit 的传递字段:OnEnter 算好跟踪 delta 后存这里,
    // OnExit 拿出来加到 vanilla 写好的 out_result 上。
    public static volatile boolean pendingApplyExitTrack = false;
    public static volatile float pendingTrackDeltaX = 0f;
    public static volatile float pendingTrackDeltaY = 0f;

    @Patch.OnEnter
    public static void onEnter(
            @Patch.This(readOnly = true) AimingMode mode,
            @Patch.Argument(value = 0, readOnly = true) IsoPlayer player,
            @Patch.Argument(value = 1, readOnly = true) Vector2 src,
            @Patch.Argument(value = 2, readOnly = false) float targetX,
            @Patch.Argument(value = 3, readOnly = false) float targetY,
            @Patch.Argument(value = 4, readOnly = true) float centerX,
            @Patch.Argument(value = 5, readOnly = true) float centerY,
            @Patch.Argument(value = 6, readOnly = true) float zoom,
            @Patch.Argument(value = 7, readOnly = true) Vector2 out_result
    ) {
        // 曲线 morph:每帧让生效曲线指数朝目标平滑过渡(用上一帧的 inRelativeDriftMode,
        // 渐变天然容忍一帧延迟)。0.15/帧 ≈ 0.3 秒过渡到位。放最开头,即使下面 early
        // return 也每帧更新,保证退出 drift 时曲线能平滑 morph 回 polar。
        float curveTarget = inRelativeDriftMode ? DRIFT_CURVE_EXPONENT : STICK_CURVE_EXPONENT;
        currentCurveExp += (curveTarget - currentCurveExp) * 0.15f;

        // 每次进入都先重置所有 per-frame 状态(因为 lerpAiming 每 tick 都被
        // 调,即使不在瞄准状态)。
        lastInZone = false;
        lastTracking = false;
        lastInHead = false;
        lastAiming = false;
        lastInjectScale = 1.0f;
        inRelativeDriftMode = false;
        pendingApplyExitTrack = false;
        pendingTrackDeltaX = 0f;
        pendingTrackDeltaY = 0f;

        // 各种 early-return 守卫
        if (player == null) return;
        if (!AimOverrideState.applyOverride) return;
        // UI 占用手柄 → 完全不介入(lastAiming 保持 false,OnExit 因此也自动跳过)。
        if (AimOverrideState.suppressed) return;
        // 只在 vanilla 进入 RANGED 系列模式时介入(RANGED_PRECISE 等)。
        if (mode == null || !mode.name().startsWith("RANGED")) return;
        if (src == null) return;

        InventoryItem item = player.getPrimaryHandItem();
        if (!(item instanceof HandWeapon)) return;
        HandWeapon weapon = (HandWeapon) item;
        if (!weapon.isRanged()) return;

        // 所有 gate 通过 = 玩家正在用远程武器瞄准。这是 Lua HUD 的开关信号。
        // 设在 cell 检查之前,即使周围没僵尸,HUD 也保持 active 状态。
        lastAiming = true;

        // drift 开启 + 瞄准中 → 进入 relative drift 模式,右摇杆切换到 drift 曲线。
        inRelativeDriftMode = (VELOCITY_INJECT > 0.001f);

        IsoCell cell = player.getCell();
        if (cell == null) return;
        ArrayList<IsoZombie> list = cell.getZombieList();
        if (list == null || list.isEmpty()) return;

        int idx = player.getPlayerNum();
        float srcX = src.x;
        float srcY = src.y;
        long nowMs = System.currentTimeMillis();
        // 时间戳压到 float 32 位(取 long 低 31 位,避免 sign bit 翻转)。
        // 用 float 而不是 long 因为缓存 entry 是 float[],节省一个数组。
        float nowF = (float) (nowMs & 0x7FFFFFFFL);
        // 头/颈骨世界坐标读取的临时 buffer(循环外 new,避免 alloc 风暴)。
        Vector3f headBuf = new Vector3f();
        Vector3f neckBuf = new Vector3f();

        // 命中候选:sticky(优先延续上帧选中的僵尸)或 first(本帧第一个命中)。
        int stickyId = lastHitZombieId;
        IsoZombie hitZombie = null;
        int hitZid = -1;
        boolean isHeadHit = false;
        float trackDeltaX = 0f;
        float trackDeltaY = 0f;
        boolean hasTrackingDelta = false;
        boolean stickyFound = false;

        int n = list.size();
        for (int i = 0; i < n; i++) {
            IsoZombie z = list.get(i);
            if (z == null || z.isDead()) continue;
            if (!z.isOnScreen()) continue;
            // 视线遮挡过滤 — 不加这条,墙后/建筑里的僵尸也会被算进命中,
            // 而且 HUD overlay 会把 hitbox 画到墙后,变成透视外挂。
            if (!player.CanSee(z)) continue;

            // 僵尸世界坐标 + 投影到屏幕的脚下锚点。
            float zwx = z.getX();
            float zwy = z.getY();
            float zwz = z.getZ();
            float h = z.getHeightAboveFloor();
            if (h < 0.1f) h = 1.85f;  // ragdoll/特殊状态兜底
            float anchorX = LuaManager.GlobalObject.isoToScreenX(idx, zwx, zwy, zwz);
            float anchorY = LuaManager.GlobalObject.isoToScreenY(idx, zwx, zwy, zwz);
            float topAnchorY = LuaManager.GlobalObject.isoToScreenY(idx, zwx, zwy, zwz + h);
            // 屏幕上 sprite 的高度(像素),用于把 ratio 参数换算成像素。
            float spriteHpx = anchorY - topAnchorY;
            if (spriteHpx < 10f) spriteHpx = 80f;

            int zid = z.getID();

            // 读颈/头骨世界坐标,沿 颈→头 方向外推到真实头中心,再投影到屏幕。
            // Bip01_Head 骨骼实际在脖子顶(偏低),直接用它判定会 snap 不到真实头部。
            // 头中心 = Head + (Head − Neck) × HEAD_OFFSET_RATIO。摇头晃脑时两骨骼都动,
            // 外推方向实时跟随,头中心始终贴在真实头部上。失败(ragdolling 等)记 NaN。
            float headSX = Float.NaN;
            float headSY = Float.NaN;
            AnimationPlayer ap = z.getAnimationPlayer();
            if (ap != null) {
                try {
                    ap.getBoneWorldPosition(SkeletonBone.Bip01_Head, headBuf);
                    ap.getBoneWorldPosition(SkeletonBone.Bip01_Neck, neckBuf);
                    // 颈→头 向量 = 头朝向轴
                    float axx = headBuf.x - neckBuf.x;
                    float axy = headBuf.y - neckBuf.y;
                    float axz = headBuf.z - neckBuf.z;
                    // 沿轴往上外推到头中心
                    float hcx = headBuf.x + axx * HEAD_OFFSET_RATIO;
                    float hcy = headBuf.y + axy * HEAD_OFFSET_RATIO;
                    float hcz = headBuf.z + axz * HEAD_OFFSET_RATIO;
                    headSX = LuaManager.GlobalObject.isoToScreenX(idx, hcx, hcy, hcz);
                    headSY = LuaManager.GlobalObject.isoToScreenY(idx, hcx, hcy, hcz);
                } catch (Throwable t) {
                    // 留 NaN(Neck 或 Head 拿不到,退化为只有身体区)
                }
            }
            // 头骨屏幕坐标存 cache(给 Lua HUD overlay 用)。
            float[] hsSlot = headScreenCache.get(zid);
            if (hsSlot == null) {
                hsSlot = new float[3];
                headScreenCache.put(zid, hsSlot);
            }
            hsSlot[0] = headSX;
            hsSlot[1] = headSY;
            hsSlot[2] = nowF;

            // 身体锚点 + spriteH 存 cache(给 Lua debug overlay 画身体框用)。
            float[] baSlot = bodyAnchorCache.get(zid);
            if (baSlot == null) {
                baSlot = new float[4];
                bodyAnchorCache.put(zid, baSlot);
            }
            baSlot[0] = anchorX;
            baSlot[1] = anchorY;
            baSlot[2] = spriteHpx;
            baSlot[3] = nowF;

            // 命中检测 — sticky 锁定后,后续僵尸不再判定。
            if (!stickyFound) {
                // 1) 头部框:矩形,围绕头骨屏幕坐标。
                boolean inHead = false;
                if (!Float.isNaN(headSX)) {
                    float halfW = spriteHpx * HEAD_HALF_W;
                    float halfH = spriteHpx * HEAD_HALF_H;
                    if (Math.abs(srcX - headSX) <= halfW && Math.abs(srcY - headSY) <= halfH) {
                        inHead = true;
                    }
                }

                // 2) 身体框:矩形,围绕脚下锚点。头部命中时跳过身体检测(头优先)。
                boolean inBody = false;
                if (!inHead) {
                    float hboxH = spriteHpx * HITBOX_HEIGHT_RATIO;
                    float hboxW = spriteHpx * HITBOX_WIDTH_RATIO;
                    float downExt = spriteHpx * HITBOX_DOWN_RATIO;
                    float halfW = hboxW * 0.5f;
                    float left = anchorX - halfW;
                    float right = anchorX + halfW;
                    float bottom = anchorY + downExt;
                    float top = anchorY - hboxH;
                    if (srcX >= left && srcX <= right && srcY <= bottom && srcY >= top) {
                        inBody = true;
                    }
                }

                if (inHead || inBody) {
                    boolean isSticky = (zid == stickyId);
                    // sticky 永远赢过 first 候选;否则按 list 顺序第一个被选中。
                    if (isSticky || hitZombie == null) {
                        hitZombie = z;
                        hitZid = zid;
                        isHeadHit = inHead;

                        // 关键时序:tracking delta 必须在这里算,因为下面会用
                        // 本帧坐标覆盖 trackWorld[zid] 的 slot。如果延后到
                        // loop 外算,prev = current = delta 0,跟踪失效。
                        hasTrackingDelta = false;
                        trackDeltaX = 0f;
                        trackDeltaY = 0f;
                        float[] prev = trackWorld.get(zid);
                        if (prev != null) {
                            float prevTs = prev[3];
                            if ((nowF - prevTs) >= 0f && (nowF - prevTs) < (float) ANCHOR_TTL_MS) {
                                // 用本帧的相机投影上一帧的世界坐标(消除相机漂移影响)。
                                float prevScreenX = LuaManager.GlobalObject.isoToScreenX(idx, prev[0], prev[1], prev[2]);
                                float prevScreenY = LuaManager.GlobalObject.isoToScreenY(idx, prev[0], prev[1], prev[2]);
                                trackDeltaX = anchorX - prevScreenX;
                                trackDeltaY = anchorY - prevScreenY;
                                hasTrackingDelta = true;
                            }
                        }

                        if (isSticky) stickyFound = true;
                    }
                }
            }

            // 写入本帧世界坐标 cache(必须在 hit detection 之后)。
            float[] slot = trackWorld.get(zid);
            if (slot == null) {
                slot = new float[4];
                trackWorld.put(zid, slot);
            }
            slot[0] = zwx;
            slot[1] = zwy;
            slot[2] = zwz;
            slot[3] = nowF;
        }

        // 清理过期 cache entry(僵尸离开 cell/视野超过 500ms 就删,避免内存累积)。
        // 注意:必须用显式 Iterator,不能用 removeIf(lambda),ZB 注入后
        // vanilla 类访问不了 synthetic 私有 lambda 方法。
        Iterator<Map.Entry<Integer, float[]>> it1 = trackWorld.entrySet().iterator();
        while (it1.hasNext()) {
            Map.Entry<Integer, float[]> e = it1.next();
            if ((nowF - e.getValue()[3]) >= (float) CACHE_CLEANUP_TTL_MS) {
                it1.remove();
            }
        }
        Iterator<Map.Entry<Integer, float[]>> it2 = headScreenCache.entrySet().iterator();
        while (it2.hasNext()) {
            Map.Entry<Integer, float[]> e = it2.next();
            if ((nowF - e.getValue()[2]) >= (float) CACHE_CLEANUP_TTL_MS) {
                it2.remove();
            }
        }
        Iterator<Map.Entry<Integer, float[]>> it3 = bodyAnchorCache.entrySet().iterator();
        while (it3.hasNext()) {
            Map.Entry<Integer, float[]> e = it3.next();
            if ((nowF - e.getValue()[3]) >= (float) CACHE_CLEANUP_TTL_MS) {
                it3.remove();
            }
        }

        if (hitZombie == null) {
            lastHitZombieId = -1;
            return;
        }

        lastInZone = true;
        lastInHead = isHeadHit;
        lastHitZombieId = hitZid;

        // 减速主公式:target' = src + (target - src) × slow。slow 越小 cursor 越被吸住。
        float slowX = isHeadHit ? SLOWDOWN_HEAD : SLOWDOWN_X;
        float slowY = isHeadHit ? SLOWDOWN_HEAD : SLOWDOWN_Y;
        // 身体区独有的"向上抗性":cursor 想往上拉(屏幕 y 减小)时,
        // 把 slowY 朝 1.0 拉(减速被衰减),便于把准心从身体往头上送。
        if (!isHeadHit && targetY < src.y) {
            slowY = 1.0f - (1.0f - slowY) * (1.0f - UP_RESISTANCE);
        }
        targetX = src.x + (targetX - src.x) * slowX;
        targetY = src.y + (targetY - src.y) * slowY;

        // 记下本帧减速系数(两轴平均),供 OnExit 让速度注入同等衰减,
        // 避免注入在减速区内把"粘"感抵消掉。
        lastInjectScale = (slowX + slowY) * 0.5f;

        // 跟踪 delta 留给 OnExit 用,直接加在 vanilla 的最终 cursor 上,
        // 绕过 vanilla 内部 lerp 的衰减。
        if (hasTrackingDelta) {
            pendingApplyExitTrack = true;
            pendingTrackDeltaX = trackDeltaX;
            pendingTrackDeltaY = trackDeltaY;
            lastTracking = true;
        }
    }

    @Patch.OnExit
    public static void onExit(
            @Patch.Argument(value = 0, readOnly = true) IsoPlayer player,
            @Patch.Argument(value = 1, readOnly = true) Vector2 src,
            @Patch.Argument(value = 2, readOnly = true) float targetX,
            @Patch.Argument(value = 3, readOnly = true) float targetY,
            @Patch.Argument(value = 4, readOnly = true) float centerX,
            @Patch.Argument(value = 5, readOnly = true) float centerY,
            @Patch.Argument(value = 7, readOnly = false) Vector2 out_result
    ) {
        // vanilla 此时已经把最终 cursor 写好到 out_result。
        if (out_result == null) return;
        if (!AimOverrideState.applyOverride) return;
        // 只在瞄准激活时介入(lastAiming 由 OnEnter 的 gate 链设置)。
        if (!lastAiming || src == null) return;

        // === 回中抗性 + 速度注入(见 docs/AIM_FEEL_DESIGN.md)===
        // 两者默认 0 = 完全不改 vanilla,跳过整段省开销。
        if (RECENTER_RESISTANCE > 0.001f || VELOCITY_INJECT > 0.001f) {
            // dt:用时间戳差值,clamp 防首帧/卡顿巨大值。
            long now = System.currentTimeMillis();
            float dt = 0.016f;
            if (lastFrameMs != 0L) {
                float raw = (now - lastFrameMs) / 1000.0f;
                dt = (raw < 0.005f) ? 0.005f : (raw > 0.05f ? 0.05f : raw);
            }
            lastFrameMs = now;

            // 这帧 vanilla 的位移
            float dx = out_result.x - src.x;
            float dy = out_result.y - src.y;

            // 1) 回中抗性:削弱朝 center 的径向分量
            if (RECENTER_RESISTANCE > 0.001f) {
                float toCx = centerX - src.x;
                float toCy = centerY - src.y;
                float toClen = (float) Math.sqrt(toCx * toCx + toCy * toCy);
                if (toClen > 0.001f) {
                    float ux = toCx / toClen;
                    float uy = toCy / toClen;
                    float radial = dx * ux + dy * uy; // >0 = 这帧在朝中心收
                    if (radial > 0f) {
                        dx -= ux * radial * RECENTER_RESISTANCE;
                        dy -= uy * radial * RECENTER_RESISTANCE;
                    }
                }
            }

            // 2) 速度注入:沿 vanilla 的"向外"方向(target - center)加位移。
            //    乘 stick magnitude → 松手(mag=0)自动归零,退出逻辑因此保住。
            if (VELOCITY_INJECT > 0.001f) {
                JoypadManager jm = JoypadManager.instance;
                if (jm != null) {
                    int idx = player.getPlayerNum();
                    float sx = jm.getAimingAxisX(idx);
                    float sy = jm.getAimingAxisY(idx);
                    float mag = (float) Math.sqrt(sx * sx + sy * sy);
                    if (mag > 0.001f) {
                        float outx = targetX - centerX;
                        float outy = targetY - centerY;
                        float outlen = (float) Math.sqrt(outx * outx + outy * outy);
                        if (outlen > 0.001f) {
                            // 乘 lastInjectScale:减速区内注入被同等压制,不抵消"粘"感。
                            float inj = mag * VELOCITY_INJECT * VELOCITY_INJECT_SCALE * dt * lastInjectScale;
                            dx += (outx / outlen) * inj;
                            dy += (outy / outlen) * inj;
                        }
                    }
                }
            }

            out_result.x = src.x + dx;
            out_result.y = src.y + dy;
        }

        // === tracking:跟随僵尸屏幕位移,加在最后(1:1 跟随)===
        if (pendingApplyExitTrack) {
            out_result.x += pendingTrackDeltaX * TRACKING_FACTOR;
            out_result.y += pendingTrackDeltaY * TRACKING_FACTOR;
        }
    }

}
