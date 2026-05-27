# Steam Deck Setup Guide

This mod requires **ZombieBuddy** as its Java bytecode-injection framework.
On Steam Deck (and Linux in general), ZombieBuddy can't auto-install — you
have to copy one file manually. Total time: ~3 minutes.

> The same instructions apply to other Linux setups. Steam Deck is just
> the most common case.

---

## Before you start

Subscribe to **both** items in Steam Workshop:

- [ZombieBuddy](https://steamcommunity.com/sharedfiles/filedetails/?id=3619862853)
- [B42 Controller Aim Assist](https://steamcommunity.com/sharedfiles/filedetails/?id=3731886676)

Wait for both downloads to finish in Steam.

---

## Step 1: Switch to Desktop Mode

Press the **STEAM** button → **Power** → **Switch to Desktop**.

You'll land in a Linux KDE desktop. The next steps need a file manager.

<!-- TODO: add screenshot of Power menu -->

---

## Step 2: Open Dolphin (file manager)

In the taskbar (bottom-left), click the file manager icon, or open
**Application Launcher** → search "Dolphin".

Enable hidden file display:

- Press `Ctrl+H` (the next folder we need starts with a dot)

<!-- TODO: add screenshot of Dolphin with hidden files toggled -->

---

## Step 3: Copy `ZombieBuddy.jar`

In Dolphin's address bar, press `Ctrl+L` to enable text input, then paste:

```
~/.local/share/Steam/steamapps/workshop/content/108600/3619862853/mods/ZombieBuddy/libs/
```

Hit Enter. You should see `ZombieBuddy.jar` inside.

Right-click it → **Copy**.

<!-- TODO: add screenshot of the libs folder with ZombieBuddy.jar highlighted -->

---

## Step 4: Paste into PZ's Java folder

Address bar again (`Ctrl+L`), paste:

```
~/.local/share/Steam/steamapps/common/ProjectZomboid/jre64/lib/
```

Hit Enter. Right-click empty area → **Paste**.

> **If that path doesn't exist**, try:
> `~/.local/share/Steam/steamapps/common/ProjectZomboid/projectzomboid/jre64/lib/`
> (some PZ versions add an extra `projectzomboid/` directory level).

<!-- TODO: add screenshot of the destination folder after paste -->

---

## Step 5: Add the launch option

1. Open Steam (still in Desktop Mode)
2. Right-click **Project Zomboid** → **Properties**
3. **General** tab → find the **Launch Options** text field at the bottom
4. Paste exactly:

   ```
   -javaagent:ZombieBuddy.jar --
   ```

5. Close the window (saves automatically)

> The trailing ` --` (space + two dashes) is a separator. Don't omit it,
> don't add anything after it.

<!-- TODO: add screenshot of the Launch Options field with text pasted -->

---

## Step 6: Return to Game Mode and launch PZ

Desktop → double-click **Return to Gaming Mode**.

Launch Project Zomboid normally.

On first launch with this mod enabled, ZombieBuddy will pop up an approval
dialog asking whether to allow this mod's Java patch. **Click Approve**.

<!-- TODO: add screenshot of ZB approval dialog -->

You're done. The mod is now loaded.

---

## Troubleshooting

### "No ZB approval dialog appears, but the mod doesn't work"

Edit the approval file manually:

1. Desktop Mode → Dolphin → navigate to `~/.zombie_buddy/`
2. Open `mod_approvals.json` in a text editor (Kate, KWrite, etc.)
3. Find the entry for `B42ControllerAimAssist`
4. Change `"decision": false` to `"decision": true`
5. Save → restart PZ

### "ZB error: java agent not found"

The destination path in Step 4 was wrong. PZ's Java directory location
varies slightly across PZ versions. Try the alternative path noted in
Step 4. If neither works, search:

```bash
find ~/.local/share/Steam/steamapps/common/ProjectZomboid -name "java" -type d
```

The result is the parent of `lib/` — copy `ZombieBuddy.jar` into the
nested `lib/` folder.

### "Game launches but no aim assist when I push the right stick"

Check that the master toggle is on:

Main Menu → Options → MODS → **[B42] Controller Aim Assist** → "Enable
aim-assist" tickbox at the top.

### "ZB shows the mod as unsigned" / "Author unknown"

This was fixed in v0.3.2. Make sure you're running 0.3.2 or higher — Steam
Workshop should auto-update; if not, unsubscribe and resubscribe to force
re-download.

### Still stuck?

Drop a comment on the [Workshop page](https://steamcommunity.com/sharedfiles/filedetails/?id=3731886676)
with what you tried and what you see. I read all comments and reply
within a day or two.

---

## Why is this manual on Linux but not on Windows?

ZombieBuddy is a Java agent — Java's `-javaagent:` flag has to point to a
jar that's on the JVM's library path. On Windows, ZombieBuddy's installer
patches the registry / Steam auto-config to add this. On Linux (and macOS),
there's no equivalent automatic hook, so the manual copy + launch option
edit is the only path.

The hassle is a one-time setup. Once done, every ZombieBuddy-based mod
(this one and any future ones) works without additional configuration.
