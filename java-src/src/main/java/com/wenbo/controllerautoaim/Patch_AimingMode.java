package com.wenbo.controllerautoaim;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.SkeletonBone;
import zombie.input.AimingMode;
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
    public static volatile float HITBOX_WIDTH_RATIO = 0.35f;
    public static volatile float HITBOX_HEIGHT_RATIO = 0.40f;
    public static volatile float HITBOX_DOWN_RATIO = 0.15f;

    // 头部框尺寸(spriteH 的比例,半宽 + 半高)。围绕 Bip01_Head 骨骼屏幕投影。
    public static volatile float HEAD_HALF_W = 0.05f;
    public static volatile float HEAD_HALF_H = 0.04f;

    // 摇杆响应曲线指数。被 Patch_AimAxisX/Y 应用:out = sign(in) * |in|^exp。
    // 1.0 = 线性(不变换),1.5 = Steam"缓和",2.0 = "宽广",0.6 = "激进"。
    public static volatile float STICK_CURVE_EXPONENT = 1.60f;

    // 身体框内向上移动的"减速抗性"。cursor 想向上拉(targetY < src.y)时,
    // SLOWDOWN_Y 的衰减效果按此比例被免除。0 = 无抗性,1 = 完全免除。
    // 默认 0.7 让玩家在身体框内向上拉到头部框时阻力大幅降低,瞄头更顺。
    public static volatile float UP_RESISTANCE = 0.70f;

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
        // 每次进入都先重置所有 per-frame 状态(因为 lerpAiming 每 tick 都被
        // 调,即使不在瞄准状态)。
        lastInZone = false;
        lastTracking = false;
        lastInHead = false;
        lastAiming = false;
        pendingApplyExitTrack = false;
        pendingTrackDeltaX = 0f;
        pendingTrackDeltaY = 0f;

        // 各种 early-return 守卫
        if (player == null) return;
        if (!AimOverrideState.applyOverride) return;
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
        // 头骨世界坐标读取的临时 buffer(每次循环 new 1 个,避免 alloc 风暴)。
        Vector3f headBuf = new Vector3f();

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

            // 读头骨世界坐标 → 投影到屏幕。失败(ragdolling 等)记 NaN。
            float headSX = Float.NaN;
            float headSY = Float.NaN;
            AnimationPlayer ap = z.getAnimationPlayer();
            if (ap != null) {
                try {
                    ap.getBoneWorldPosition(SkeletonBone.Bip01_Head, headBuf);
                    headSX = LuaManager.GlobalObject.isoToScreenX(idx, headBuf.x, headBuf.y, headBuf.z);
                    headSY = LuaManager.GlobalObject.isoToScreenY(idx, headBuf.x, headBuf.y, headBuf.z);
                } catch (Throwable t) {
                    // 留 NaN
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
            @Patch.Argument(value = 7, readOnly = false) Vector2 out_result
    ) {
        // vanilla 此时已经把最终 cursor 写好到 out_result。
        // 我们加上当前帧的跟踪位移,实现"cursor 1:1 跟随僵尸"的效果。
        if (!pendingApplyExitTrack) return;
        if (out_result == null) return;
        out_result.x += pendingTrackDeltaX * TRACKING_FACTOR;
        out_result.y += pendingTrackDeltaY * TRACKING_FACTOR;
    }

}
