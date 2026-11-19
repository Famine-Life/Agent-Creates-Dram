
@echo off
chcp 65001 >nul
setlocal DisableDelayedExpansion

title AIGC CLI 远程登录

echo.
echo ========================================
echo       AIGC 视频平台 CLI 登录
echo ========================================
echo.

:: 切换到当前 BAT 所在目录
cd /d "%~dp0"

:: 检查 Node.js
where node >nul 2>nul
if errorlevel 1 (
    echo [错误] 未安装 Node.js
    pause
    exit /b 1
)

:: 检查 CLI 文件
if not exist "%~dp0aigc.mjs" (
    echo [错误] 找不到 aigc.mjs
    pause
    exit /b 1
)

:: 输入账号密码
set /p "USERNAME=请输入账号："
set /p "PASSWORD=请输入密码（输入时可见）："

echo.
echo 正在登录远程服务器...
echo.

:: 直接执行 Node.js CLI
node "%~dp0aigc.mjs" login --username "%USERNAME%" --password "%PASSWORD%" --url "http://1.15.243.21:9901/"

set "RESULT=%ERRORLEVEL%"
set "PASSWORD="

echo.
if "%RESULT%"=="0" (
    echo [成功] 登录命令执行成功！
) else (
    echo [失败] 登录失败，错误代码：%RESULT%
)

echo.
pause
endlocal
