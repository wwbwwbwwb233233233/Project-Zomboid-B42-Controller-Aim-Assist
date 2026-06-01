#!/usr/bin/env bash
# set-descriptions.sh — 把 descriptions/<lang>.txt 里的标题 + 正文推到 Workshop。
#
# 用法:
#   ./set-descriptions.sh                  # 推所有语言
#   ./set-descriptions.sh english          # 只推某一种语言
#   ./set-descriptions.sh english schinese # 推指定几种语言
#
# 文件格式(每个 descriptions/<lang>.txt):
#   第 1 行: TITLE: <本地化标题>
#   第 2 行: (空行)
#   第 3 行起: BBCode 正文
#
# 支持的语言名(必须跟 Steam API language code 完全一致):
#   english schinese tchinese russian brazilian german polish japanese
#   french spanish italian koreana 等等(只挑你 descriptions/ 里实际有的)
#
# 前提:
#   - Steam 客户端在后台运行
#   - SteamUploader 编译好放在 ~/bin/steamuploader/
#   - descriptions/<lang>.txt 文件存在

set -e

WORKSHOP_ID="3731886676"
APP_ID="108600"
DESC_DIR="/Users/wenbo/Desktop/瞎搞/其他/僵尸毁灭工程mod工程/ControllerAutoAim/descriptions"
UPLOADER="$HOME/bin/steamuploader/SteamUploader"

if [ ! -x "$UPLOADER" ]; then
    echo "ERROR: SteamUploader 不在 $UPLOADER"
    exit 1
fi

# 哪些语言?用户指定的,或全部
if [ "$#" -gt 0 ]; then
    LANGS=("$@")
else
    LANGS=()
    for f in "$DESC_DIR"/*.txt; do
        [ -f "$f" ] || continue
        base="$(basename "$f" .txt)"
        LANGS+=("$base")
    done
fi

for lang in "${LANGS[@]}"; do
    f="$DESC_DIR/$lang.txt"
    if [ ! -f "$f" ]; then
        echo "==> 跳过 $lang: 文件 $f 不存在"
        continue
    fi

    # 第 1 行提取标题(去掉前缀 "TITLE: ")
    title="$(head -1 "$f" | sed 's/^TITLE: //')"
    if [ -z "$title" ]; then
        echo "==> 跳过 $lang: 第一行没找到 TITLE:"
        continue
    fi

    # 第 3 行起是 BBCode 正文,写到临时文件
    body_tmp="/tmp/desc_${lang}_$$.bbcode"
    sed -n '3,$p' "$f" > "$body_tmp"

    echo "==> 推 $lang"
    echo "    title: $title"
    echo "    body : $body_tmp ($(wc -c < "$body_tmp") 字节)"

    "$UPLOADER" \
        --appID "$APP_ID" \
        --workshopID "$WORKSHOP_ID" \
        --language "$lang" \
        --title "$title" \
        --description "$body_tmp"

    rm -f "$body_tmp"
    echo
done

echo "==> 完成。"
echo "Workshop: https://steamcommunity.com/sharedfiles/filedetails/?id=$WORKSHOP_ID"
