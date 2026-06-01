# Changelog

## 0.4.0 — 2026-06-01

### Added
- **Recenter resistance** — the cursor no longer springs straight back to screen center when you release the stick; higher values let it hold position so you stop "fighting" the spring. Default 0.9 (on) — the core feel upgrade of this release. New option, so existing players get it automatically.
- **Relative drift (experimental)** — optional "hold the stick and the cursor keeps sliding" feel (like RDR2 / twin-stick shooters), with its own independent drift sensitivity curve. Off by default; the two drift sliders stay greyed out until enabled.
- **Full in-game localization** — settings UI translated into 8 languages (English, Simplified / Traditional Chinese, German, Brazilian Portuguese, Russian, Polish, Japanese) via PZ's native translation system.

### Changed
- **Head snap now targets the real head center** — the head zone is extrapolated upward from the neck bone (the game's "head" bone actually sits at the neck), so it snaps where the head visually is and follows it as the zombie moves.
- **Default tuning pass** — recenter 0.9, body zone 0.40 × 0.45, head zone 0.03 × 0.03, head offset 1.0, stick curve 1.6, head snap strength 0.95. Affects new installs; existing players keep their saved values.
- Smooth curve morph when entering / leaving drift mode (no abrupt sensitivity jump).

### Fixed
- **Radial menu / map no longer interrupted by the aim action** when pushing the right stick to navigate while holding a firearm.
- **Aim no longer triggers before the controller is bound to a character.**
- **Residual reticle after switching from firearm to melee.**
- **Experimental drift sliders couldn't be dragged** — a settings-menu timing bug where toggling the experimental switch failed to un-grey the sliders below it.

### Compatibility
- **Verified compatible with B42.19** (the controller-input / button-remapping overhaul). All four ZombieBuddy patches still attach cleanly — zero Java changes needed.

### Note for existing players
- Your saved settings are preserved on update — nothing is overwritten. New options (Recenter resistance, etc.) apply at their new defaults automatically. To try the freshly-tuned body / head zone defaults, reset them in the settings or nudge them to the recommended values.

### Requires
- Project Zomboid Build 42.18+ (verified on 42.19)
- ZombieBuddy 2.0.0+

---

## 0.3.2 — 2026-05-27

### Fixed
- **Native UI elements (clock, minimap, etc.) no longer clickable
  while this mod was enabled.** Real root cause of the previously
  reported touchscreen / RMB / UI-click issues: the debug overlay
  added in 0.3.0 was a full-screen `ISUIElement` permanently sitting
  on top of the UI stack. Even when not visibly drawing anything, it
  still claimed mouse-over for the entire screen and swallowed any
  click underneath. Two defenses now: (1) `Overlay:isMouseOver()`
  always returns false so events fall through to UI elements below;
  (2) the overlay is only added to `UIManager` when both "Show snap
  zones" is on AND the player is actively aiming, and removed
  otherwise — most of the time it isn't in the UI stack at all.

### Added
- **ZombieBuddy mod signing (Ed25519 / ZBS).** The jar is now signed
  with `io.github.zed-0xff.zb-gradle-plugin`, producing a
  `ControllerAutoAim.jar.zbs` sidecar that ZombieBuddy verifies on
  load. ZB's mod approval dialog now shows the author identity
  ("Here to Suffer not for Fun") instead of an unsigned-mod warning.

---

## 0.3.1 — 2026-05-26

### Fixed
- **Touchscreen / RMB input swallowed while holding a firearm.** The
  `isPrecisionAimKeyDown` override was unconditionally setting the
  return value to match our stick state — when the stick wasn't pushed
  this meant *forcing* vanilla to false, eating any input that vanilla's
  own logic would have flagged as precision-aim (mouse RMB, Steam Deck
  touchscreen, etc.). Fix: only override to true when our stick is
  pushed, never override to false.

### Changed
- `mod.info` `authors` field updated to "Here to Suffer not for Fun"
  so the in-game mod manager matches the Steam profile name. No
  functional change.

---

## 0.3.0 — 2026-05-26

### Added
- **Visual debug overlay** — new "Show snap zones" toggle in settings draws colored
  rectangles around each visible zombie showing the body (green) and head (cyan) assist
  zones. Useful for tuning, recording demos, or understanding the mechanic. Off by
  default. Note: may affect game balance — intended for tuning / recording only.

### Changed
- **Clearer setting names** — all options now use self-explanatory player-facing
  language (e.g. "Body snap zone width" instead of "Body hitbox width", "Horizontal
  snap strength" instead of "Body slowdown X").
- **Snap strength semantic flipped** — body / vertical / head strength sliders now
  read as "higher = stickier" (0 = no pull, 1 = full lock). Internal math unchanged.
  Existing saved values for these three sliders will reset to new defaults because
  their option IDs changed (`SLOW_*` -> `ASSIST_*`).

### Internals
- Java: new `bodyAnchorCache` per-frame map exposed to Lua for overlay drawing.
- Lua: new `AutoAim_DebugOverlay.lua` (ISUIElement subclass, screen-space render).

---

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
