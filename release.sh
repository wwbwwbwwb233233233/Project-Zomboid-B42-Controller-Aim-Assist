#!/usr/bin/env bash
# release.sh — 一键发版:同步到 Workshop 目录 + SteamCMD 上传
#
# 用法:
#   ./release.sh                       # 用 pz_upload.vdf 里现有的 changenote
#   ./release.sh "0.3.0 增加爆头判定"  # 先改 changenote 再上传
#
# 发版前 checklist:
#   1. 改 42/mod.info 里的 modversion=
#   2. (可选)更新 CHANGELOG.md
#   3. changenote 别带 | & " 这种字符(sed 会爆)
#
# 前提:
#   - ~/steamcmd/ 装好
#   - ~/steamcmd/pz_upload.vdf 配好
#   - SteamCMD 至少登录成功过一次(凭据缓存在 ~/Library/Application Support/Steam/)

set -e

SRC_ROOT="/Users/wenbo/Desktop/瞎搞/其他/僵尸毁灭工程mod工程/ControllerAutoAim"
STEAM_USER="ilovechina_"
VDF="$HOME/steamcmd/pz_upload.vdf"
WORKSHOP_ID="3731886676"

VERSION=$(grep '^modversion=' "$SRC_ROOT/42/mod.info" | cut -d= -f2)
echo "==> 当前 mod 版本: $VERSION"

if [ -n "$1" ]; then
    CHANGENOTE="$1"
    echo "==> 更新 vdf changenote: $CHANGENOTE"
    # macOS sed: -i '' 必须的(跟 GNU sed -i 不同)
    sed -i '' "s|\"changenote\".*|\"changenote\"       \"$CHANGENOTE\"|" "$VDF"
fi

echo
bash "$SRC_ROOT/sync-to-workshop.sh"

echo
echo "==> SteamCMD 上传"
cd "$HOME/steamcmd"
./steamcmd.sh +login "$STEAM_USER" +workshop_build_item "$VDF" +quit

echo
echo "==> 发版完成 (v$VERSION)"
echo "Workshop: https://steamcommunity.com/sharedfiles/filedetails/?id=$WORKSHOP_ID"
