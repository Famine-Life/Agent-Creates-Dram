#!/bin/bash
# ============================================================================
# install-jdk17.sh — CentOS 7 一键安装 JDK 17 (Eclipse Temurin)
#
# 为什么选 Temurin：
#   Oracle JDK 17 官方最低支持 glibc 2.28，CentOS 7 只有 2.17
#   Temurin（原 AdoptOpenJDK）原生支持 CentOS 7，免费、热更新、国内镜像快
#
# 用法：
#   curl -fsSL https://xxx/install-jdk17.sh | bash
#   或  bash install-jdk17.sh
# ============================================================================

set -e

JDK_VERSION="17.0.12+7"
JDK_ARCHIVE="OpenJDK17U-jdk_x64_linux_hotspot_17.0.12_7.tar.gz"
JDK_DIR="jdk-17.0.12+7"
INSTALL_PATH="/usr/local/jdk17"

# ---------- 工具函数 ----------
error() { echo -e "\033[31m[ERROR]\033[0m $*" >&2; exit 1; }
info()  { echo -e "\033[32m[INFO]\033[0m $*"; }
warn()  { echo -e "\033[33m[WARN]\033[0m $*"; }

echo "============================================================"
echo "  JDK 17 Installer for CentOS 7  |  Temurin $JDK_VERSION"
echo "============================================================"

# ---------- 0. 清理旧版本 ----------
if [ -d "$INSTALL_PATH" ]; then
    info "发现已有 $INSTALL_PATH，备份到 /usr/local/jdk17.bak"
    rm -rf /usr/local/jdk17.bak 2>/dev/null || true
    mv "$INSTALL_PATH" /usr/local/jdk17.bak
fi

# ---------- 1. 下载 ----------
info "[1/5] 下载 Temurin JDK 17 ..."
cd /usr/local/src

# 多个镜像源（全部可用时按需加）
# 清华源是二进制 tarball，更稳定
MIRRORS=(
    # 1. 华为云（可能缺文件）
    "https://repo.huaweicloud.com/adoptium/temurin17-binaries/${JDK_VERSION}/${JDK_ARCHIVE}"
    # 2. 清华镜像（binary）
    "https://mirrors.tuna.tsinghua.edu.cn/github-release/adoptium/temurin17-binaries/LatestRelease/${JDK_ARCHIVE}"
    # 3. GitHub 直链（最终兜底）
    "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-${JDK_VERSION//+/%2B}/${JDK_ARCHIVE}"
)

# 下载 + 校验（CentOS 7 wget 1.14 不支持 --show-progress，静默模式）
try_download() {
    local url="$1" out="$2"
    rm -f "$out"
    # wget 优先（CentOS 7 自带），不行 curl
    if command -v wget &>/dev/null; then
        wget -q -O "$out" "$url" 2>/dev/null || true
    fi
    # wget 退出码 0 也可能存了错误页 HTML，用 gzip 校验
    if [ -s "$out" ] && gzip -t "$out" 2>/dev/null; then
        return 0   # ✅ 真是 tar.gz
    fi
    # 文件无效（太小或 HTML），删掉再用 curl 试一次
    rm -f "$out"
    if command -v curl &>/dev/null; then
        curl -fsSL -o "$out" "$url" 2>/dev/null || true
        if [ -s "$out" ] && gzip -t "$out" 2>/dev/null; then
            return 0
        fi
    fi
    rm -f "$out"
    return 1
}

DOWNLOADED=0
for url in "${MIRRORS[@]}"; do
    info "尝试: $url"
    if try_download "$url" "$JDK_ARCHIVE"; then
        DOWNLOADED=1
        break
    fi
    warn "此镜像不可用，换下一个 ..."
done

[ "$DOWNLOADED" = "1" ] || error "所有镜像均下载失败，请手动下载 $JDK_ARCHIVE 放到 /usr/local/src/ 后重跑"

info "✅ 下载完成  $(du -h "$JDK_ARCHIVE" | cut -f1)"

# ---------- 2. 解压安装 ----------
echo "[2/5] 解压到 $INSTALL_PATH ..."
tar xzf "$JDK_ARCHIVE" -C /usr/local/
mv "/usr/local/$JDK_DIR" "$INSTALL_PATH"
rm -f "$JDK_ARCHIVE"

# ---------- 3. 写入环境变量 ----------
echo "[3/5] 写入 /etc/profile.d/jdk17.sh ..."
cat > /etc/profile.d/jdk17.sh <<'EOF'
export JAVA_HOME=/usr/local/jdk17
export PATH=$JAVA_HOME/bin:$PATH
export CLASSPATH=.:$JAVA_HOME/lib/tools.jar:$JAVA_HOME/lib/dt.jar
EOF
chmod +x /etc/profile.d/jdk17.sh
source /etc/profile.d/jdk17.sh

# ---------- 4. 注册 alternatives ----------
echo "[4/5] 注册系统默认 java ..."
if command -v update-alternatives &>/dev/null; then
    update-alternatives --install /usr/bin/java java "$JAVA_HOME/bin/java" 17012
    update-alternatives --install /usr/bin/javac javac "$JAVA_HOME/bin/javac" 17012
    update-alternatives --install /usr/bin/jar jar "$JAVA_HOME/bin/jar" 17012
    update-alternatives --set java  "$JAVA_HOME/bin/java"  2>/dev/null || true
    update-alternatives --set javac "$JAVA_HOME/bin/javac" 2>/dev/null || true
    update-alternatives --set jar   "$JAVA_HOME/bin/jar"   2>/dev/null || true
else
    # 没有 alternatives 工具链的兜底方案
    ln -sf "$JAVA_HOME/bin/java"  /usr/bin/java
    ln -sf "$JAVA_HOME/bin/javac" /usr/bin/javac
    ln -sf "$JAVA_HOME/bin/jar"   /usr/bin/jar
fi

# ---------- 5. 验证 ----------
echo "[5/5] 验证安装 ..."
echo "------------------------------------------------------------"
java  -version 2>&1
javac -version 2>&1
echo "------------------------------------------------------------"

INSTALLED_VER=$(java -version 2>&1 | head -1)
echo ""
echo "✅  JDK 17 安装成功！"
echo "    安装路径 : $INSTALL_PATH"
echo "    版本     : $INSTALLED_VER"
echo ""
echo "    当前终端已生效；新开终端自动加载 /etc/profile.d/jdk17.sh"
echo "    如需立即生效可执行: source /etc/profile.d/jdk17.sh"
echo "============================================================"
