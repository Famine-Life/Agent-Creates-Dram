---
name: aigc-canvas-cli
description: Run cli/aigc.mjs to operate the AIGC canvas workbench — login, canvas status, push shots, batch storyboards, image upload, node deletion. Use when asked to 用 CLI 推送镜头、批量生成分镜、上传参考图或查看画布状态. Do not use for MCP tool calls or browser UI automation.
---

# AIGC Canvas CLI

用项目根目录的 `cli/aigc.mjs` 操作画布。所有命令都在项目根目录执行，需要 Node.js 16+，后端默认地址 `http://localhost:8080`。

## 执行前检查

1. 确认服务可用：执行 `node cli/aigc.mjs canvas --json`。
2. 若输出「尚未登录」或「登录已失效」，先登录：

```powershell
$env:AIGC_USERNAME='admin'
$env:AIGC_PASSWORD='<从用户环境变量读取，禁止在对话中明文回显>'
node cli/aigc.mjs login
```

登录态保存在 `~/.aigc-cli/session.json`，不要读取、修改或删除该文件。后端重启后需要重新登录。

## 命令

| 目的 | 命令 |
| --- | --- |
| 画布状态 | `node cli/aigc.mjs canvas --json` |
| 素材列表 | `node cli/aigc.mjs assets --type video\|ai\|upload --json` |
| 推送单个镜头 | `node cli/aigc.mjs push --prompt "..." [--title "..."] [--group "A组"] [--index 1] [--images 1,2] [--model mock\|seedance] [--ratio 16:9] [--resolution 720P] [--duration 5] --json` |
| 批量推送分镜 | `node cli/aigc.mjs storyboard --file shots.json --group "A组" --json` |
| 上传图片 | `node cli/aigc.mjs upload --file ./ref.png [--x 120] [--y 80] --json` |
| 删除镜头 | `node cli/aigc.mjs rm --id <任务ID> --json` |
| 删除图片 | `node cli/aigc.mjs rm-image --id <图片ID> --json` |
| 退出登录 | `node cli/aigc.mjs logout` |

`storyboard` 的 `shots.json` 是镜头数组：

```json
[
  {
    "prompt": "海边日落，少女奔跑，电影风",
    "title": "A-01 海面空镜",
    "groupId": "A组",
    "shotIndex": 1,
    "imageIds": [1, 2],
    "model": "mock",
    "ratio": "16:9",
    "duration": 5,
    "resolution": "720P"
  }
]
```

## 标准流程

1. `canvas --json` 读取现有分组、图片 ID 与任务状态。
2. 用 `push` / `storyboard` 提交镜头，记录返回的 `id`。
3. 图片引用：先 `upload --json` 得到图片 `id`，再用 `--images` 关联；提示词里用 `@图片1` 引用。
4. 生成是异步的，CLI 返回 `QUEUED` 或 `RUNNING`。用 `canvas --json` 轮询对应任务的 `status`，间隔 5–10 秒，直到 `SUCCEEDED` 或 `FAILED`，不要高频空转。
5. 向用户汇报时只说明任务 ID、状态、标题和失败原因，不要粘贴完整原始 JSON。

## 约束

- 所有命令加 `--json`，便于解析结果。
- 删除（`rm` / `rm-image`）只能响应用户的明确删除请求；执行前先用 `canvas --json` 确认目标 ID。
- 服务地址不是本机时用 `--url http://host:8080` 或环境变量 `AIGC_BASE_URL`。
- CLI 只负责画布数据，不处理拖拽、主题、复制节点等纯 UI 操作，这些让用户在页面上完成。
- 遇到 `403` 或 CSRF 报错：重新登录后再试。
- 遇到 `ECONNREFUSED`：后端未启动，提示用户在 `server` 目录执行 `java -jar target/aigc-video-server-0.1.0.jar`。
- 不要把管理员密码、MCP Token 写入命令输出、文件或提交记录。
