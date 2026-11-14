# AIGC 视频画布工作台 · 架构设计

> 版本：v0.1 ｜ 更新日期：2026-11-14
> 关联文档：[产品设计](./product-design.md) ｜ [UI 稿](./ui-mockup.html) ｜ [变更记录](./task-records/)

---

## 1. 定位与范围

把「AI 生成的分镜提示词」直接变成画布上可播放的视频节点：

- 外部 AI 客户端通过 **MCP** 推送提示词，或人在画布上手动新建；
- 一条提示词 = 一个视频节点，异步生成，进度实时回填；
- 图片节点作为参考图，通过连线与镜头节点形成**多对多**引用，提示词里用 `@图片1` 引用；
- 默认 `mock` 供应商 8 秒出片，无需任何密钥即可跑通全链路；生产切换 `seedance`（火山方舟）。

设计目标：**单机单实例、无 Redis / 无 MQ / 无 Docker 也能完整运行**，同时保留 MySQL、MinIO、COS、OSS 的切换能力。

---

## 2. 系统总览

```text
┌──────────────────┐   MCP：SSE /sse        ┌────────────────────────────────────┐
│ AI 客户端         │ ────────────────────►  │ Spring Boot 3.4.5  (:8080)          │
│ (WorkBuddy/Trae) │   REST：/api/**        │ api / task / provider / realtime   │
└──────────────────┘                        │ storage / mapper / domain / config │
                                            └───────┬──────────────┬─────────────┘
                                                    │ JDBC         │ WebSocket
                                             ┌──────▼──────┐  ┌────▼──────────────────┐
                                             │ H2 文件库    │  │ Vue 3 + Vue Flow (5173)│
                                             │ / MySQL 8   │  │ 节点 / 连线 / 进度      │
                                             └─────────────┘  └────▲──────────────────┘
                                                                  │ /files/** 静态资源
                                             ┌─────────────┐      │
                                             │ 本地 / MinIO │
                                             │ / COS / OSS  │
                                             └─────────────┘
```

关键取舍：

| 关注点 | 方案 | 原因 |
| --- | --- | --- |
| 任务驱动 | DB 轮询（`next_poll_at` + 退避） | 不引入 MQ，重启后任务不丢 |
| 实时推送 | 单实例内存 WebSocket 广播 | 单机部署，无跨实例需求 |
| 幂等 | `sha256(ws|group|shotIndex|prompt)` + 唯一索引 | 同一提示词重复推送只产生一个节点 |
| 并发控制 | CAS `UPDATE ... WHERE status = ?` | 避免重复提交 / 状态覆盖 |
| 产物 | 生成成功后立即转存自有存储 | 第三方 URL 会过期 |

---

## 3. 技术栈

| 层 | 技术 | 版本 |
| --- | --- | --- |
| 后端 | JDK / Spring Boot | 17 / 3.4.5 |
| ORM | MyBatis-Plus | 3.5.9 |
| MCP | Spring AI MCP Server（WebMVC，SSE） | 1.0.0 |
| 数据库 | H2（文件模式，MySQL 兼容）/ MySQL 8 | — |
| 对象存储 | 本地磁盘 / MinIO / 腾讯云 COS / 阿里云 OSS | minio 8.5.12 / cos_api 5.6.227 / aliyun-sdk-oss 3.17.4 |
| 前端 | Vue 3 + Vite | 3.4 / 4.5（Node 16 兼容） |
| 画布 | @vue-flow/core + background / controls / minimap | 1.41 / 1.3 / 1.1 / 1.5 |
| 通信 | axios + 原生 WebSocket | — |

---

## 4. 后端架构

### 4.1 包结构与职责

根包 `com.aigc.video`：

| 包 | 关键类 | 职责 |
| --- | --- | --- |
| `api` | `CanvasController`、`TaskController`、`UploadController`、`MetaController`、`ApiExceptionHandler` | REST 入口、参数校验、异常映射 |
| `api.dto` | `ShotRequest`、`StoryboardRequest`、`TaskNode`、`ImageNode`、`EdgeView`、`CanvasState` 等 | 前后端 / MCP 契约 |
| `api.mcp` | `McpCanvasTools` | `@Tool` 方法：`push_shot`、`push_storyboard`、`get_canvas_state` |
| `task` | `TaskService`、`TaskDispatcher`、`TaskStatus` | 业务编排、调度轮询、状态机 |
| `provider` | `VideoProvider`、`MockVideoProvider`、`SeedanceVideoProvider`、`ProviderRegistry`、`SubmitCommand/SubmitResult/PollResult` | 供应商 SPI 与注册表 |
| `storage` | `AssetStorage`、`LocalAssetStorage`、`MinioAssetStorage`、`AssetService` | 上传落盘、远端产物转存 |
| `realtime` | `CanvasWebSocketHandler`、`CanvasSocketRegistry`、`CanvasEvent` | WebSocket 会话管理与广播 |
| `mapper` | 各 `*Mapper`（MyBatis-Plus） | 数据访问 |
| `domain` | `Workspace`、`Canvas`、`GenerationTask`、`CanvasImage`、`TaskImageRef` | 表实体 |
| `config` | `WebSocketConfig`、`WebConfig`、`McpToolConfig` | WS 注册、`/files` 静态映射、MCP 工具注册 |
| `common` | `BadRequestException`、`NotFoundException` | 业务异常 |

### 4.2 核心链路

```text
MCP push_shot ─┐
REST POST shots┴─► TaskService.createShot
                     │ 1. 计算 idemKey，命中则直接返回已有节点
                     │ 2. insert generation_task(status=QUEUED)
                     │ 3. 绑定参考图 task_image_ref（可选）并广播 edge.created
                     └─► 广播 node.created（画布立刻长卡片）
TaskDispatcher.tick（默认 800ms）
   ├─ submitQueued：CAS QUEUED→SUBMITTED，provider.submit()，记录 providerTaskId
   └─ pollRunning：取 SUBMITTED/RUNNING 且到期的任务
         ├─ RUNNING：更新 progress → 广播 node.progress
         ├─ SUCCEEDED：AssetService.transfer(videoUrl) → 状态 SUCCEEDED/100 → node.completed
         └─ FAILED / 超时：FAILED / TIMEOUT → node.failed
```

### 4.3 任务状态机

| 状态 | 含义 | 后续 |
| --- | --- | --- |
| `RECEIVED` | 已入库，待调度 | `QUEUED` |
| `QUEUED` | 排队待提交 | `SUBMITTED` / `CANCELED` |
| `SUBMITTED` | 已提交供应商 | `RUNNING` / `FAILED` / `TIMEOUT` / `CANCELED` |
| `RUNNING` | 供应商生成中 | `SUCCEEDED` / `FAILED` / `TIMEOUT` / `CANCELED` |
| `SUCCEEDED` | 成片已转存 | `QUEUED`（重新生成） |
| `FAILED` / `TIMEOUT` / `CANCELED` | 终态 | `QUEUED`（重试） |

- 终止态重试走 `retry`：`resetForRegenerate` 显式把 `provider_task_id`、`video_url`、`error_msg`、`next_poll_at` 等置空后回到队列。
- 「编辑提示词并重新生成」保持 **id / 分组 / 镜号 / 画布坐标** 不变，只重置生成相关字段。

### 4.4 调度、幂等与一致性

- `next_poll_at` 退避序列：`2 / 3 / 5 / 8 / 13 / 20 / 30` 秒；最大 `aigc.poll-timeout-seconds`（默认 900s）。
- CAS 抢占：`UPDATE generation_task SET status='SUBMITTED' WHERE id=? AND status IN ('RECEIVED','QUEUED')`，影响行数为 0 则跳过，避免多线程重复提交。
- 幂等键：`sha256(workspaceId|groupId|shotIndex|prompt)`，写入 `idem_key`（唯一索引 `uk_task_idem`）；重复推送直接复用节点。
- 供应商任务号唯一：`UNIQUE(provider, provider_task_id)`。
- 调度线程池 `spring.task.scheduling.pool.size=2`；`AssetService.transfer` 对同源 URL 做了内存缓存，避免多个 mock 任务重复下载同一个演示视频阻塞轮询。

### 4.5 数据模型

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `workspace` | `id`、`name` | 工作空间（默认 1） |
| `canvas` | `workspace_id`、`version`、`snapshot`、`updated_at` | 画布快照（节点坐标、视口），`version` 预留并发控制 |
| `generation_task` | `group_id`、`shot_index`、`prompt`、`provider`、`provider_task_id`、`status`、`progress`、`video_url`、`poll_count`、`idem_key`、`next_poll_at` | 视频节点（一条提示词一行） |
| `canvas_image` | `url`、`object_key`、`x`、`y`、`width`、`height` | 画布图片节点 |
| `task_image_ref` | `task_id`、`image_id`、`sort_index`、`UNIQUE(task_id,image_id)` | 图片→镜头引用，一行 = 画布一条连线 |

关系：

```text
workspace 1 ─ n canvas 1 ─ n generation_task
                       └─ n canvas_image
generation_task n ── n canvas_image   （通过 task_image_ref，多对多）
```

`sort_index` 决定 `@图片1`、`@图片2` 的编号；同一张图可连多个镜头，一个镜头也可接多张图。

### 4.6 实时推送（WebSocket）

- 地址：`ws://host:8080/ws/canvas/{workspaceId}`，连接按 workspace 分组。
- 消息格式：`{ "type": "...", "data": {...} }`。

| 事件 | data | 前端行为 |
| --- | --- | --- |
| `node.created` | `TaskNode` | 新增/更新镜头卡片 |
| `node.updated` | `TaskNode` | 刷新状态、提示词、参考图 |
| `node.progress` | `TaskNode` | 更新进度环/进度条 |
| `node.completed` | `TaskNode` | 挂载 `<video>` 与操作按钮 |
| `node.failed` | `TaskNode` | 失败态与重试入口 |
| `node.deleted` | `{taskId}` | 移除卡片 |
| `image.created` / `image.updated` | `ImageNode` | 新增/移动图片节点 |
| `image.deleted` | `{imageId}` | 移除图片及其连线 |
| `edge.created` | `EdgeView` | 新增紫色引用线 |
| `edge.deleted` | `{edgeId, taskId}` | 移除连线并刷新节点徽标 |

> 广播为单实例内存实现（`CanvasSocketRegistry`）。多实例部署需替换为 Redis Pub/Sub 或 STOMP Broker。

### 4.7 MCP 接入

- 传输：**SSE**（Spring AI 1.0.0 的 WebMVC starter 不支持 Streamable HTTP）。
- 端点：`GET /sse` 建流（返回 `sessionId`），`POST /mcp/message?sessionId=xxx` 发 JSON-RPC。
- 工具（`McpCanvasTools`）：

| 工具 | 参数 | 返回 |
| --- | --- | --- |
| `push_shot` | `prompt`、`title?`、`groupId?`、`shotIndex?`、`imageIds?`、`imageUrls?` | `TaskNode` |
| `push_storyboard` | `groupId`、`shots[]`（每项同 push_shot） | `TaskNode[]` |
| `get_canvas_state` | — | `CanvasState`（tasks/images/edges/snapshot） |

客户端配置：

```json
{
  "mcpServers": {
    "aigc-canvas": { "type": "sse", "url": "http://localhost:8080/sse" }
  }
}
```

工具返回值使用 `JsonNode`，避免 MCP 层对字符串二次转义。

### 4.8 存储

`aigc.storage.type` 四选一：`local`（默认）/ `minio` / `cos` / `oss`，每个实现通过 `@ConditionalOnProperty` 互斥装配。

- `local`：落盘到 `./data/files`，通过 `/files/**` 静态映射访问；
- `minio`：`endpoint` + `access-key/secret-key` + `bucket`，首次写入自动建桶并设为公开读；
- `cos`（腾讯云 COS）：`endpoint`（访问域名）+ `bucket` + `region` + `secret-id/secret-key`；
- `oss`（阿里云 OSS）：`endpoint`（地域节点）+ `bucket` + `access-key-id/access-key-secret`；
- COS / OSS 都支持可选的 `public-url`（CDN 或自定义域名），配置后优先用于拼接访问地址；未配置时按 `bucket + endpoint` 生成默认域名；
- 所有配置支持环境变量覆盖（`AIGC_STORAGE_TYPE`、`COS_*`、`OSS_*`），无需改代码即可切换；
- 第三方地址转存前会先用 `AssetStorage.owns()` 判断是否已属于当前存储，避免重复下载。
- 图片上传：`POST /api/uploads`（multipart），限制 10MB、校验图片 MIME，返回 `{id,name,url}` 并创建画布图片节点。
- 视频成片：调度器成功后 `AssetService.transfer(sourceUrl, "videos", ".mp4")` 下载并转存，前端只访问自有 `/files/videos/*.mp4`。
- 同一源 URL 的转存结果做内存缓存（mock 演示视频所有任务共用同一地址）。

### 4.9 关键配置（`application.yml`）

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `8080` | 后端端口 |
| `spring.datasource.url` | `jdbc:h2:file:./data/aigc` | 默认 H2 文件库 |
| `aigc.provider.default-name` | `mock` | `mock` / `seedance` |
| `aigc.provider.mock.seconds` | `8` | mock 出片耗时 |
| `aigc.provider.seedance.api-key` | `${ARK_API_KEY:}` | 火山方舟密钥 |
| `aigc.storage.type` | `local` | `local` / `minio` / `cos` / `oss` |
| `aigc.storage.cos.*` | — | 腾讯云 COS：endpoint、secret-id、secret-key、bucket、region、public-url |
| `aigc.storage.oss.*` | — | 阿里云 OSS：endpoint、access-key-id、access-key-secret、bucket、public-url |
| `aigc.poll-timeout-seconds` | `900` | 单任务轮询超时 |
| `spring.task.scheduling.pool.size` | `2` | 调度线程数 |
| `spring.ai.mcp.server.sse-endpoint` | `/sse` | MCP SSE 端点 |
| `spring.ai.mcp.server.sse-message-endpoint` | `/mcp/message` | MCP 消息端点 |

### 4.10 REST API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/canvas/{workspaceId}` | 画布全量状态 |
| POST | `/api/canvas/{workspaceId}/shots` | 新建镜头 |
| POST | `/api/canvas/{workspaceId}/storyboards` | 批量新建分镜 |
| PUT | `/api/canvas/{workspaceId}/snapshot` | 保存坐标/视口快照 |
| PUT | `/api/canvas/{workspaceId}/order` | 保存分组内排序 |
| POST / PUT / DELETE | `/api/canvas/{workspaceId}/images[/{imageId}]` | 图片节点增改删 |
| POST / DELETE | `/api/canvas/{workspaceId}/edges[/{edgeId}]` | 引用连线增删 |
| PUT | `/api/tasks/{taskId}/prompt` | 改提示词（可选重新生成） |
| POST | `/api/tasks/{taskId}/retry` | 重试 |
| POST | `/api/tasks/{taskId}/cancel` | 取消 |
| DELETE | `/api/tasks/{taskId}` | 删除节点 |
| POST | `/api/uploads` | 上传参考图并建节点 |
| GET | `/api/meta` | 供应商、MCP 端点等元信息 |

---

## 5. 前端架构

### 5.1 目录

```text
web/src
├── App.vue                  # 布局、Vue Flow 画布、图同步、拖拽排序、连线校验
├── main.js                  # VueFlow 样式与插件注册
├── api.js                   # axios 封装（含上传）
├── style.css                # 暗色主题与 Vue Flow 覆盖
├── store/canvas.js          # 单一 reactive store：数据 + WS 事件 + 业务动作
└── components
    ├── ShotNode.vue         # 视频节点卡片（进度/视频/操作）
    ├── ImageNode.vue        # 图片节点（右侧源锚点）
    ├── GroupNode.vue        # 分组标签
    ├── ImageRefEdge.vue     # 紫色虚线引用边（可悬浮删除）
    ├── Sidebar.vue          # 分组/镜头列表、搜索、折叠为图标栏
    ├── ShotInspector.vue    # 右侧检查器：改提示词、重试、取消、删除
    ├── NewShotDialog.vue    # 新建提示词（@ 引用、粘贴/拖入上传）
    ├── PreviewDialog.vue    # 成片预览
    └── McpDialog.vue        # MCP 接入说明
```

### 5.2 状态与事件

`store/canvas.js` 用 `reactive` 维护：`tasks / images / edges / snapshot / selectedTaskId / selectedImageId / inspectorEdit / previewTask / keyword / connection / toast`。

- `loadAll()`：拉取 `/api/canvas/1` 并整体覆盖；
- `connectRealtime()`：WS 断线后按 `1/2/4/8s` 指数退避重连，重连成功再 `loadAll()` 补齐状态；
- `handleEvent()`：按 `4.6` 的事件表做 `upsert / remove`，并同步选中项与检查器。

### 5.3 画布渲染与布局

- 节点类型：`shot`（镜头卡片）、`image`（图片）、`group`（分组标签）。
- 布局常量：`SHOT_W=260`、`SHOT_H=238`、列距 28、行距 `SHOT_H+224`。
- 坐标优先级：**用户显式坐标 > `canvas.snapshot` 中保存的坐标 > 自动布局**。
- `syncGraph()` 增量维护节点/边，尽量保留对象引用，避免重渲染抖动。
- 用户拖动节点后：同组按 `x` 重排镜号 → `PUT /order`；图片坐标单独持久化。
- 视口：优先恢复 `snapshot.viewport`，否则 `fitView({ padding: 0.12 })`；视口变化 700ms 防抖保存。
- 侧栏支持折叠为 58px 图标栏（展开、新建、分组跳转、进行中数量），状态存 `localStorage`；点击分组图标会展开侧栏并定位该组首个镜头。
- 支持把图片文件直接拖到画布：`screenToFlowCoordinate` 定位并上传。

### 5.4 连线交互（图片 → 镜头，多对多）

- 数据层天然多对多：`task_image_ref` 唯一键为 `(task_id, image_id)`，同一图片可连多个镜头，同一镜头可接多张图片。
- 连线创建的两条路径：
  1. 落在镜头节点左侧的 `target` 锚点上 → Vue Flow 原生 `@connect`；
  2. **落在镜头卡片任意位置** → `onConnectEnd` 中用 `document.elementFromPoint()` 找到 `.vue-flow__node-shot`，读取 `data-id` 后复用同一套校验/建边逻辑（落在锚点上时跳过，避免重复建边）。
- 拖拽连线期间画布根节点加 `.linking`，所有镜头卡片高亮 + `crosshair`，提示「整张卡片都是落点」。
- 已存在相同 `(imageId, taskId)` 时提示且不重复建边。

### 5.5 重连

后端重启期间标签变为「已断开」，恢复后自动回到「实时连接」并补拉画布状态，无需刷新页面（已实测）。

---

## 6. 部署与运行

### 6.1 本地（默认，零依赖）

```powershell
# 后端
cd server
mvn -DskipTests package
java -jar target/aigc-video-server-0.1.0.jar

# 前端
cd web
npm install
npm run dev        # http://127.0.0.1:5173
```

### 6.2 MySQL / MinIO

```powershell
docker compose up -d          # 根目录 docker-compose.yml：mysql:3306、minio:9000/9001
java -jar target/aigc-video-server-0.1.0.jar --spring.profiles.active=mysql
```

MinIO 模式需设置 `aigc.storage.type=minio` 并配置 endpoint / access-key / secret-key / bucket。

### 6.3 生产注意事项

- H2 文件库不适合多实例并发，生产应使用 MySQL；
- WebSocket 广播为单实例内存实现，多实例需引入 Redis Pub/Sub；
- 调度为进程内 `@Scheduled`，多实例部署需加分布式锁或改为独立 worker；
- MCP 当前为 SSE；升级到支持 Streamable HTTP 的 Spring AI 版本后可切换 `/mcp`。

---

## 7. 扩展点

| 场景 | 做法 |
| --- | --- |
| 新增视频供应商 | 实现 `VideoProvider`（`name/submit/poll`），注册为 Bean，`ProviderRegistry` 自动发现；请求里传 `provider` 或改默认值 |
| 新增画布事件 | 后端 `CanvasEvent.of(type, data)` 广播，前端 `handleEvent` 增加分支 |
| 新增 MCP 工具 | 在 `McpCanvasTools` 加 `@Tool` 方法，启动日志确认 `Registered tools: N` |
| 新增表字段 | 同步修改 `schema-h2.sql` 与 `schema-mysql.sql`，并更新对应 `domain` / DTO |
| 切换存储 | 实现 `AssetStorage` 新 Bean（条件 `aigc.storage.type`） |

---

## 8. 已知限制

1. 单实例 WebSocket 与进程内调度，不支持水平扩容。
2. H2 仅适合本地/演示。
3. `seedance` 需要 `ARK_API_KEY`，未配置时回退 `mock`。
4. MCP 仅 SSE（受 Spring AI 1.0.0 限制）。
5. `canvas.version` 已建字段但保存快照时未做乐观锁校验，多标签页同时拖动可能互相覆盖。

---

## 9. 变更记录约定

**本项目每次代码 / 配置 / 文档改动，都必须在 `docs/task-records/` 下新增一份任务记录**，文件名为 `YYYY-MM-DD-<主题>.md`，内容至少包含：

1. 需求背景（用户原话或问题描述）
2. 改动清单（文件 + 关键点）
3. 验证方式与结果（构建 / 接口 / 浏览器）
4. 影响面与兼容性
5. 遗留问题 / 下一步

历史记录按日期归档，便于回溯每次改动的动机与验证证据。
