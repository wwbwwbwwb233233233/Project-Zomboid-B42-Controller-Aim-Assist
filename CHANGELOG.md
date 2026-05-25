# Changelog

## 0.2.0 — 2026-05-24 (workshop-ready)

### Features
- Body slow-down zone (rectangle, three params: width / height / down extension).
- Independent head slow-down zone via Bip01_Head bone projection (rectangle, half-W / half-H).
- Tracking: cursor follows the locked zombie's screen motion 1:1 (applied in `lerpAiming` OnExit).
- Upward resistance: body slow-down is partially bypassed when cursor moves up.
- Stick response curve (per-axis, default 1.6 ≈ Steam "Relaxed").
- Sticky hit zombie — overlapping zombies no longer cause cursor jitter.
- Visibility filtering — zombies behind walls or off-screen don't get pulled.
- Aiming gate — mod fully silent when not aiming a ranged weapon.

### Settings
- All 11 knobs exposed in Main Menu → Options → MODS → "Controller Aim Assist" (B42 native `PZAPI.ModOptions`). Auto-persisted to `<cache>/Lua/modOptions.ini`.

### Internals
- Java side: 5 ZombieBuddy patches (`Patch_AimingMode`, `Patch_IsPrecisionAimKeyDown`, `Patch_AimAxisX`, `Patch_AimAxisY`) + `AimOverrideState` Lua bridge.
- Lua side: 4 modules (Main loop, Input, Config, ModOptions).
- Detailed Chinese comments throughout the source.

### Requires
- Project Zomboid Build 42.18+
- ZombieBuddy 2.0.0+

---

## 0.1.0 (internal pre-workshop)

Iterative development. Rejected approaches documented in project memory:
- `IsoObjectPicker` pixel-accurate hit test — vanilla never fills the picker in lerpAiming timing.
- `IsoGameCharacter.getHitInfoList()` lock-target API — same timing issue, list empty.
- `BallisticsController.getCachedTargetedBodyPart` — same.
