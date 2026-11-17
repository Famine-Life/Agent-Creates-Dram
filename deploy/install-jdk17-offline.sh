#!/bin/bash
# ============================================================================
# install-jdk17-offline.sh — 离线安装 JDK 17（已手动下载好 tar.gz）
#
# 前提：压缩包已放在 SRC_DIR 下
#   /home_aiwhl/deploy/OpenJDK17U-jdk_x64_linux_hotspot_*.tar.gz
#   或项目 deploy/ 目录下（脚本会自动找）
#
# 用法：bash install-jdk17-offline.sh
# ============================================================================

set -e

# ---------- 可改 ----------
# 优先在 /home_aiwhl/deploy/ 找，没有就在当前脚本所在目录找
SRC_DIR_CANDIDATES=(
    "/home_aiwhl/deploy"
    "$(cd "$(dirname "$0")" && pwd)"
    "."
)
INSTALL_PATH="/usr/local/jdk17"

echo "============================================================"
echo "  JDK 17 Offline Installer  |  CentOS 7"
echo "============================================================"

# ---------- 1. 查找压缩包 ----------
JDK_TGZ=""
for dir in "${SRC_DIR_CANDIDATES[@]}"; do
    if [ -d "$dir" ]; then
        JDK_TGZ=$(ls "$dir"/OpenJDK17U-jdk_x64_linux_hotspot_*.tar.gz 2>/dev/null | head -1 || true)
        if [ -n "$JDK_TGZ" ]; then
            break
        fi
    fi
done

if [ -z "$JDK_TGZ" ]; then
    echo "[ERROR] 没找到 JDK tar.gz，搜过："
    for dir in "${SRC_DIR_CANDIDATES[@]}"; do echo "  $dir/"; done
    echo ""
    echo "请手动下载 Temurin JDK 17："
    echo "  文件名应为 OpenJDK17U-jdk_x64_linux_hotspot_17.0.xx_x.tar.gz"
    exit 1
fi

if ! gzip -t "$JDK_TGZ" 2>/dev/null; then
    echo "[ERROR] 文件不是有效的 tar.gz：$JDK_TGZ"
    exit 1
fi

echo "[1/4] 找到  $JDK_TGZ  ($(du -h "$JDK_TGZ" | cut -f1))"

# ---------- 2. 备份旧版 ----------
if [ -d "$INSTALL_PATH" ]; then
    echo "[2/4] 备份旧版 → /usr/local/jdk17.bak"
    rm -rf /usr/local/jdk17.bak
    mv "$INSTALL_PATH" /usr/local/jdk17.bak
else
    echo "[2/4] 无旧版，跳过备份"
fi

# ---------- 3. 解压安装 ----------
echo "[3/4] 解压到 $INSTALL_PATH ..."
tar xzf "$JDK_TGZ" -C /usr/local/

# 动态推断解压目录名（兼容不同小版本）
EXTRACTED_NAME=$(tar tzf "$JDK_TGZ" | head -1 | cut -d'/' -f1)
mv "/usr/local/$EXTRACTED_NAME" "$INSTALL_PATH"

# ---------- 4. 配置环境变量 + alternatives ----------
echo "[4/4] 配置 /etc/profile.d/jdk17.sh ..."
cat > /etc/profile.d/jdk17.sh <<'EOF'
export JAVA_HOME=/usr/local/jdk17
export PATH=$JAVA_HOME/bin:$PATH
export CLASSPATH=.:$JAVA_HOME/lib/tools.jar:$JAVA_HOME/lib/dt.jar
EOF
chmod +x /etc/profile.d/jdk17.sh
source /etc/profile.d/jdk17.sh

# 注册系统默认 java（有 alternatives 就用，没有就软链兜底）
if command -v update-alternatives &>/dev/null; then
    update-alternatives --install /usr/bin/java java  "$JAVA_HOME/bin/java"  17012 2>/dev/null || true
    update-alternatives --install /usr/bin/javac javac "$JAVA_HOME/bin/javac" 17012 2>/dev/null || true
    update-alternatives --install /usr/bin/jar   jar   "$JAVA_HOME/bin/jar"   17012 2>/dev/null || true
    update-alternatives --set java  "$JAVA_HOME/bin/java"  2>/dev/null || true
    update-alternatives --set javac "$JAVA_HOME/bin/javac" 2>/dev/null || true
    update-alternatives --set jar   "$JAVA_HOME/bin/jar"   2>/dev/null || true
fi

# 兜底软链（有些 minimal CentOS 没有 alternatives）
ln -sf "$JAVA_HOME/bin/java"  /usr/bin/java  2>/dev/null || true
ln -sf "$JAVA_HOME/bin/javac" /usr/bin/javac 2>/dev/null || true
ln -sf "$JAVA_HOME/bin/jar"   /usr/bin/jar   2>/dev/null || true

# ---------- 验证 ----------
echo ""
echo "------------------------------------------------------------"
java  -version 2>&1
javac -version 2>&1
echo "------------------------------------------------------------"
INSTALLED_VER=$(java -version 2>&1 | head -1)
echo ""
echo "✅ JDK 17 安装成功！"
echo "   安装路径 : $INSTALL_PATH"
echo "   版本     : $INSTALLED_VER"
echo ""
echo "   当前终端已生效"
echo "   新开终端自动加载 /etc/profile.d/jdk17.sh"
echo "============================================================"
