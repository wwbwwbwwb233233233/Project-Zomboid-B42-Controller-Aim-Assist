#!/usr/bin/env bash
# set-tags.sh — 用 SteamUploader 设置 / 修改 Workshop 上的 tags
#
# 用法:
#   ./set-tags.sh                                # 用脚本里默认的 tags
#   ./set-tags.sh "Build 42,QoL,Interface,Other" # 自定义 tags(逗号分隔)
#   ./set-tags.sh ""                             # 清空所有 tags
#
# 为什么不用 release.sh / SteamCMD:
#   SteamCMD 的 vdf 不支持 tags 字段(历史 bug)。这个脚本调
#   SirDoggyJvla 的 SteamUploader(Steamworks SDK 包装),专门为
#   PZ 等 mod 上传场景做的,支持 tag 操作。
#
# 前提:
#   1. Steam 客户端在后台运行(SteamUploader 需要)
#   2. 已登录正确账号(就是发布这个 mod 的账号)
#   3. SteamUploader 已编译并放在 ~/bin/steamuploader/

set -e

WORKSHOP_ID="3731886676"
APP_ID="108600"
DEFAULT_TAGS="Build 42,QoL,Interface"

UPLOADER="$HOME/bin/steamuploader/SteamUploader"

if [ ! -x "$UPLOADER" ]; then
    echo "ERROR: SteamUploader 不在 $UPLOADER"
    echo "从源码构建:见 https://github.com/SirDoggyJvla/Steam-Uploader"
    exit 1
fi

if [ -n "${1+x}" ]; then
    TAGS="$1"
else
    TAGS="$DEFAULT_TAGS"
fi

echo "==> 设置 Workshop tags"
echo "    Workshop ID: $WORKSHOP_ID"
echo "    Tags:        $TAGS"
echo

"$UPLOADER" --appID "$APP_ID" --workshopID "$WORKSHOP_ID" --tags "$TAGS"

echo
echo "==> 完成。验证: https://steamcommunity.com/sharedfiles/filedetails/?id=$WORKSHOP_ID"
