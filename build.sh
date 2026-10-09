#!/bin/bash
# 构建 debug APK
#
# 用法（在装有 JDK 17 + Android SDK 的 Linux 终端里）：
#     bash build.sh
#
# 产物（gradle 原始路径，固定不变）：
#     app/build/outputs/apk/debug/app-debug.apk
#
# 安装（Android 侧执行，需要一个 shell 通道）：
#     cp app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/d.apk
#     pm install -r /data/local/tmp/d.apk

set -u

# 项目根目录 = 脚本所在目录（不写死任何本机路径）
WS="$(cd "$(dirname "$0")" && pwd)"

# gradle 可执行文件：优先 PATH 里的，其次常见的本地安装位置
GRADLE_BIN="$(command -v gradle || true)"
if [ -z "$GRADLE_BIN" ]; then
    GRADLE_BIN=/root/gradle/gradle-9.1.0/bin/gradle
fi

cd "$WS" || exit 1

# 这些环境变量若外部已提供则不覆盖（路径均为通用位置，不含任何个人/设备信息）
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-arm64}"
export ANDROID_HOME="${ANDROID_HOME:-/root/Android}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/root/.gradle}"

echo "==> gradle assembleDebug"
"$GRADLE_BIN" assembleDebug --no-daemon || exit 1

APK="$WS/app/build/outputs/apk/debug/app-debug.apk"
echo "==> 产物：$APK"
ls -l "$APK"