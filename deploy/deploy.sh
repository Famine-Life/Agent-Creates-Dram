#!/bin/bash
# ============================================================================
# deploy.sh — 一键部署 AIGC Video Canvas（CentOS 7）
#
# 执行前准备：
#   1. 本地打包后端: mvn clean package -DskipTests
#      → server/target/aigc-video-server-0.1.0.jar
#   2. 本地打包前端: cd web && npm run build
#      → web/dist/
#   3. 把 deploy/ 目录 + 上述产物 scp 到服务器同目录
#
# 用法：
#   chmod +x deploy.sh && ./deploy.sh
#   或 bash deploy.sh
#
# 可选步骤（按需注释/取消注释）：
#   STEP_JDK=1   安装 JDK 17
#   STEP_MYSQL=1 安装 MySQL 5.7 + 初始化
#   STEP_NGINX=1 安装 Nginx + 写配置
#   STEP_ART=1   部署前后端产物
#   STEP_START=1 启动服务 + 开机自启
# ============================================================================

set -e

# ---------- 可开关步骤 ----------
STEP_JDK=1
STEP_MYSQL=1
STEP_NGINX=1
STEP_ART=1
STEP_START=1

# ---------- 路径常量 ----------
APP_NAME="aigc-video-server"
APP_VER="0.1.0"
APP_JAR="${APP_NAME}-${APP_VER}.jar"
DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVER_DIR="/opt/aigc"
FRONTEND_DIR="/usr/share/nginx/html"
DB_NAME="aigc_video"

# ---------- 打印工具 ----------
info()  { echo -e "\033[32m[INFO]\033[0m  $*"; }
warn()  { echo -e "\033[33m[WARN]\033[0m  $*"; }
error() { echo -e "\033[31m[ERROR]\033[0m $*"; exit 1; }

# ============================================================================
# STEP 1 — JDK 17
# ============================================================================
if [ "$STEP_JDK" = "1" ]; then
    info "====== STEP 1/5 安装 JDK 17 ======"
    bash "$DEPLOY_DIR/install-jdk17.sh"
    info "JDK 17 安装完成 ✅"
fi

# ============================================================================
# STEP 2 — MySQL 5.7 + 建库
# ============================================================================
if [ "$STEP_MYSQL" = "1" ]; then
    info "====== STEP 2/5 安装 MySQL 5.7 ======"

    # CentOS 7 自带 MySQL 5.7（mariadb 是 fork，推荐用 Oracle 原版）
    if ! command -v mysql &>/dev/null; then
        yum install -y mariadb mariadb-server mariadb-devel
    fi

    systemctl enable --now mariadb
    sleep 2

    # 建库
    if command -v mysql &>/dev/null; then
        if ! mysql -uroot -e "USE $DB_NAME" 2>/dev/null; then
            info "创建数据库 $DB_NAME ..."
            mysql -uroot -e "CREATE DATABASE $DB_NAME DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
        fi
        info "MySQL + $DB_NAME 就绪 ✅"
    fi
fi

# ============================================================================
# STEP 3 — Nginx
# ============================================================================
if [ "$STEP_NGINX" = "1" ]; then
    info "====== STEP 3/5 安装 Nginx ======"

    if ! command -v nginx &>/dev/null; then
        # CentOS 7 EOL 后换阿里源
        yum install -y epel-release || true
        yum install -y nginx
    fi

    # 写配置
    cp -f "$DEPLOY_DIR/aigc.conf" /etc/nginx/conf.d/aigc.conf

    # 删掉默认 server（冲突）
    rm -f /etc/nginx/conf.d/default.conf

    # 语法检查
    nginx -t

    systemctl enable --now nginx
    info "Nginx + aigc.conf 部署完成 ✅"
fi

# ============================================================================
# STEP 4 — 部署前后端产物
# ============================================================================
if [ "$STEP_ART" = "1" ]; then
    info "====== STEP 4/5 部署产物 ======"

    # --- 后端 jar ---
    mkdir -p "$SERVER_DIR"
    JAR_SRC="$DEPLOY_DIR/$APP_JAR"
    if [ -f "$JAR_SRC" ]; then
        cp -f "$JAR_SRC" "$SERVER_DIR/$APP_JAR"
        info "后端 jar → $SERVER_DIR/$APP_JAR ✅"
    else
        warn "⚠️  找不到 $JAR_SRC，请先在本地 mvn package 后 scp 上传"
    fi

    # --- 前端 dist ---
    DIST_SRC="$DEPLOY_DIR/../web/dist"
    # 兼容从 deploy/ 或 项目根目录 两种位置执行
    if [ ! -d "$DIST_SRC" ]; then
        DIST_SRC="$DEPLOY_DIR/web/dist"
    fi

    if [ -d "$DIST_SRC" ]; then
        rm -rf "$FRONTEND_DIR"/*
        cp -rf "$DIST_SRC"/* "$FRONTEND_DIR/"
        info "前端 dist → $FRONTEND_DIR ✅"
    else
        warn "⚠️  找不到 web/dist，请先 npm run build 后 scp 上传"
    fi

    # --- schema ---
    SCHEMA_SRC="$DEPLOY_DIR/../server/src/main/resources/db/schema-mysql.sql"
    if [ ! -f "$SCHEMA_SRC" ]; then
        SCHEMA_SRC="$DEPLOY_DIR/schema-mysql.sql"
    fi
    if [ -f "$SCHEMA_SRC" ] && [ "$STEP_MYSQL" = "1" ]; then
        info "导入表结构 ..."
        mysql -uroot "$DB_NAME" < "$SCHEMA_SRC" 2>/dev/null || true
        info "schema-mysql.sql 已导入 ✅"
    fi

    # --- systemd 服务 ---
    cp -f "$DEPLOY_DIR/aigc.service" /etc/systemd/system/aigc.service

    # --- 日志目录 ---
    mkdir -p /var/log/aigc

    info "产物部署完成 ✅"
fi

# ============================================================================
# STEP 5 — 启动服务
# ============================================================================
if [ "$STEP_START" = "1" ]; then
    info "====== STEP 5/5 启动服务 ======"

    systemctl daemon-reload
    systemctl enable --now aigc 2>/dev/null || {
        warn "启动失败，查看日志: journalctl -u aigc -n 50"
    }

    # 等后端起来
    for i in {1..30}; do
        if curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:9900/api/meta 2>/dev/null | grep -q "200"; then
            break
        fi
        sleep 1
    done

    echo ""
    echo "============================================================"
    echo "  🎉 部署完成！"
    echo "============================================================"
    echo ""
    echo "  访问：http://$(hostname -I | awk '{print $1}')/"
    echo ""
    echo "  后端状态：  systemctl status aigc"
    echo "  后端日志：  journalctl -u aigc -f"
    echo "  重启：      systemctl restart aigc"
    echo "  Nginx 日志：tail -f /var/log/nginx/aigc.error.log"
    echo "  后端日志：  tail -f /var/log/aigc/app.log"
    echo "============================================================"
fi
