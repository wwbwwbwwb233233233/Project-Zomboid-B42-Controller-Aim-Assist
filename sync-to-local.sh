#!/usr/bin/env bash
# sync-to-local.sh — 同步项目 42/ 到本地 dev mod 目录(~/Zomboid/mods/)
#
# 用途:
#   每次改完 Lua 或 gradle jar 之后,把改动推到 PZ 运行时读取的目录,然后重启 PZ 测试。
#
# 关键设计:
#   - 本地 dev mod 永远叫 B42ControllerAimAssistDev,跟 Workshop 版 B42ControllerAimAssist 区分,
#     避免订阅 Workshop 后两个同 ID mod 在 PZ 里冲突。
#   - --exclude=mod.info 关键:本地 mod.info 的 id 是 Dev 版,别让源码的 mod.info 覆盖。
#     如果你改了源码 mod.info 的其他字段(modversion, description, etc.)且想同步到 dev,
#     手动改 ~/Zomboid/mods/B42ControllerAimAssistDev/42/mod.info 对应字段就好。
#
# 用法:
#   ./sync-to-local.sh

set -e

SRC="/Users/wenbo/Desktop/瞎搞/其他/僵尸毁灭工程mod工程/ControllerAutoAim/42/"
DST="$HOME/Zomboid/mods/B42ControllerAimAssistDev/42/"

echo "==> rsync $SRC → $DST (--exclude=mod.info)"
rsync -av --delete --exclude=mod.info "$SRC" "$DST"

echo
echo "==> 完成。重启 PZ 生效。"
echo "    Mod manager 里启用: 【DEV】手柄辅瞄 / [DEV] Controller Aim Assist"
