#!/usr/bin/env bash
# 把工程目录 sync 到 PZ B42 Workshop 标准布局,准备上传到 Steam Workshop。
#
# 用法:
#   ./sync-to-workshop.sh
#
# 做什么:
#   1. 在 ~/Zomboid/Workshop/ 下建立 ControllerAutoAimWorkshop/ 目录(纯英文路径,
#      Steam in-game uploader 需要这种布局)。
#   2. 必备的空 common/ 目录(B42 强制要求,即使空也得在)。
#   3. rsync --delete 本工程的 42/ 子目录到 Contents/mods/<MOD_ID>/42/。
#   4. 把 LICENSE / README.md / CHANGELOG.md 一起拷过去(可选,Workshop 不展示但
#      留个版本号方便 grep)。
#
# 上传步骤(此脚本不做):
#   1. 跑这个脚本。
#   2. 准备 preview.png(强制 256×256,放在 ControllerAutoAimWorkshop/ 根目录)。
#   3. 准备 42/poster.png(任意尺寸,游戏内 mod manager 大图)。
#   4. 启动 PZ → 主菜单 → Workshop → Create and Update Items。
#   5. 选 ControllerAutoAimWorkshop,填 title/description/tags。
#   6. visibility 先选 Private,稳定后改 Public。

set -e

SRC_ROOT="/Users/wenbo/Desktop/瞎搞/其他/僵尸毁灭工程mod工程/ControllerAutoAim"
# 必须跟 42/mod.info 里的 id= 字段一致 (ZB 加载时显示这个 id 的 CamelCase 拆词版)
MOD_ID="B42ControllerAimAssist"
WS_NAME="B42ControllerAimAssistWorkshop"
WS_ROOT="$HOME/Zomboid/Workshop/$WS_NAME"
MOD_DST="$WS_ROOT/Contents/mods/$MOD_ID"

echo "==> Workshop 目录:$WS_ROOT"

# 强制 common/ 存在(B42 mod 必备)
mkdir -p "$MOD_DST/common"
mkdir -p "$MOD_DST/42"

# Sync 实际 mod 内容
echo "==> rsync 42/ → $MOD_DST/42/"
rsync -av --delete \
    "$SRC_ROOT/42/" \
    "$MOD_DST/42/"

# 拷可选文档(Workshop 不展示,但留个版本号方便日后 grep)
for f in LICENSE README.md CHANGELOG.md; do
    if [ -f "$SRC_ROOT/$f" ]; then
        cp -v "$SRC_ROOT/$f" "$MOD_DST/"
    fi
done

echo
echo "==> 清理 macOS 垃圾(.DS_Store + 可清理的 xattr)"
find "$WS_ROOT" -name ".DS_Store" -delete 2>/dev/null
xattr -cr "$WS_ROOT" 2>/dev/null
# 注意:com.apple.provenance (macOS 14+ Sequoia) 是系统级 xattr,普通用户无法删,留着无害

echo
echo "==> Sync 完成。"
echo
echo "下一步(手动):"
echo "  1. 放一张 256x256 PNG 到 $WS_ROOT/preview.png"
echo "  2. 放一张 poster 到 $MOD_DST/42/poster.png"
echo "  3. 在 42/mod.info 加 'poster=poster.png' 行(如果还没加)"
echo "  4. 启动 PZ → Workshop → Create and Update Items → 选 $WS_NAME"
echo
ls -la "$WS_ROOT"
