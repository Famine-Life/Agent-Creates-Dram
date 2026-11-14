# AIGC 视频画布工作台 · 架构设计

> 版本：v0.2 ｜ 更新日期：2026-11-15
> 关联文档：[产品设计](./product-design.md) ｜ [UI 稿](./ui-mockup.html) ｜ [变更记录](./task-records/)
>
> v0.2 更新：画布节点（文本 / 脚本 / 图片 / 视频）、AI 脚本 / 图片 / 提示词优化、生成参数（模型 / 比例 / 清晰度 / 时长）、Redis Pub/Sub 可选广播、画布快照乐观锁。
>
> 2026-11-14 安全更新：网页账号密码 + Session、REST/WebSocket/本地文件鉴权、MCP 独立 Bearer Token、CSRF、私有对象存储短期签名 URL。

---

## 1. 定位与范围

把「AI 生成的分镜提示词」直接变成画布上可播放的视频节点：

- 外部 AI 客户端通过 **MCP** 推送提示词，或人在画布上右键手动创建节点；
- 一条提示词 = 一个视频节点，异步生成，进度实时回填；
- 图片节点作为参考图，通过连线与镜头节点形成**多对多**引用，提示词里用 `@图片1` 引用；
- 内置 AI 能力：脚本节点生成分镜、图片节点文生图、提示词一键优化（Ark，未配置密钥时 mock）；
- 默认使用 `seedance`（火山方舟 Seedance 2.0）；设置 `AIGC_PROVIDER=mock` 可切换为 8 秒演示出片，无需密钥即可跑通全链路。

设计目标：**默认单机单实例、无 Redis / 无 MQ / 无 Docker 也能完整运行**；需要水平扩容时，通过配置开启 Redis Pub/Sub 广播，并保留 MySQL、MinIO、COS、OSS 的切换能力。

---

## 2. 系统总览

```text
┌──────────────────┐   MCP：SSE /sse        ┌────────────────────────────────────┐
│ AI 客户端         │ ────────────────────►  │ Spring Boot 3.4.5  (:8080)          │
│ (WorkBuddy/Trae) │   REST：/api/**        │ api / task / provider / realtime   │
└──────────────────┘                        │ storage / ai / mapper / domain     │
                                            └───┬────────┬──────────┬────────────┘
                                                │ JDBC   │ WS       │ HTTP
                                         ┌──────▼────┐ ┌─▼───────┐ ┌▼─────────────┐
                                         │ H2 / MySQL│ │ 浏览器   │ │ 火山方舟 Ark  │
                                         └───────────┘ │ Vue Flow │ │ 文本 / 图片   │
                                                       └──────────┘ └──────────────┘
                                       可选：┌──────────┐
                                             │ Redis    │  Pub/Sub 跨实例广播
                                             └──────────┘
```

关键取舍：

| 关注点 | 方案 | 原因 |
| --- | --- | --- |
| 任务驱动 | DB 轮询（`next_poll_at` + 退避） | 不引入 MQ，重启后任务不丢 |
| 实时推送 | 默认单实例内存广播；可选 Redis Pub/Sub | 单机零依赖，多实例通过配置开启 |
| 幂等 | `sha256(ws|group|shotIndex|prompt)` + 唯一索引 | 同一提示词重复推送只产生一个节点 |
| 并发控制 | 任务状态 CAS + 画布 `version` 乐观锁 | 避免重复提交与多标签页互相覆盖 |
| 产物 | 生成成功后立即转存自有存储 | 第三方 URL 会过期 |

---

## 3. 技术栈

| 层 | 技术 | 版本 |
| --- | --- | --- |
| 后端 | JDK / Spring Boot | 17 / 3.4.5 |
| 安全 | Spring Security | Session + CSRF + Bearer Token |
| ORM | MyBatis-Plus | 3.5.9 |
| MCP | Spring AI MCP Server（WebMVC，SSE） | 1.0.0 |
| 数据库 | H2（文件模式，MySQL 兼容）/ MySQL 8 | — |
| 缓存 / 广播 | Redis（可选，Pub/Sub） | Spring Data Redis |
| AI | 火山方舟 Ark（Seedance 2.0 video / chat / images），脚本与图片未配置密钥走 mock | HTTP |
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
| `api` | `AuthController`、`CanvasController`、`TaskController`、`UploadController`、`MetaController`、`ApiExceptionHandler` | 登录会话、REST 入口、参数校验、异常映射（含 409） |
| `api.dto` | `ShotRequest`、`StoryboardRequest`、`TaskNode`、`ImageNode`、`EdgeView`、`CanvasState`、`CanvasNodeView`、`SnapshotRequest` | 前后端 / MCP 契约 |
| `api.mcp` | `McpCanvasTools` | `@Tool` 方法：`push_shot`、`push_storyboard`、`get_canvas_state` |
| `task` | `TaskService`、`TaskDispatcher`、`TaskStatus`、`DispatchLock` | 业务编排、调度轮询、状态机、快照乐观锁、多实例调度锁 |
| `ai` | `AiService`、`AiProperties` | 脚本生成、文生图、提示词优化（Ark / mock） |
| `provider` | `VideoProvider`、`MockVideoProvider`、`SeedanceVideoProvider`、`ProviderRegistry`、`SubmitCommand/SubmitResult/PollResult` | 供应商 SPI 与注册表；提交带 ratio / resolution / duration |
| `storage` | `AssetStorage`、`LocalAssetStorage`、`MinioAssetStorage`、`AssetService` | 上传落盘、远端产物转存 |
| `realtime` | `CanvasWebSocketHandler`、`CanvasSocketRegistry`、`CanvasEvent`、`CanvasEventRelay`、`RedisCanvasEventRelay`、`RedisCanvasSubscriber` | WebSocket 会话管理；可选 Redis Pub/Sub 跨实例广播 |
| `mapper` | 各 `*Mapper`（MyBatis-Plus） | 数据访问 |
| `domain` | `Workspace`、`Canvas`、`GenerationTask`、`PromptRecord`、`CanvasImage`、`CanvasNode`、`TaskImageRef` | 表实体 |
| `config` | `SecurityConfig`、`SecurityProperties`、`McpAuthenticationFilter`、`SessionConfig`、`WebSocketConfig`、`WebConfig`、`McpToolConfig` | Session/CSRF、MCP Token、多实例 Session、同源与跨域限制、WS 注册、`/files` 静态映射、MCP 工具注册 |
| `common` | `BadRequestException`、`NotFoundException`、`ConflictException` | 业务异常 |

### 4.2 核心链路

```text
MCP push_shot ─┐
REST POST shots┴─► TaskService.createShot
                     │ 1. 计算 idemKey，命中则复用已有节点
                     │ 2. insert generation_task(status=QUEUED)
                     │ 3. 每次提交均 insert prompt_record（含幂等命中）
                     │ 4. 绑定参考图 task_image_ref（可选）并广播 edge.created
                     └─► 广播 node.created（画布立刻长卡片）
TaskDispatcher.tick（默认 800ms）
   ├─ submitQueued：CAS QUEUED→SUBMITTED，provider.submit()，记录 providerTaskId
   └─ pollRunning：取 SUBMITTED/RUNNING 且到期的任务
         ├─ RUNNING：更新 progress → 广播 node.progress
         ├─ SUCCEEDED：AssetService.transfer(videoUrl) → 状态 SUCCEEDED/100 → node.completed
         └─ FAILED / 超时：FAILED / TIMEOUT → node.failed
```

画布内创作链路：

```text
右键菜单 ─► POST /nodes（文本/脚本）
              └─► POST /nodes/{id}/script ─► AiService.chat ─► 分镜列表 ─► 加入画布（createStoryboard）
右键菜单 ─► POST /images/ai ─► AiService.generateImage ─► canvas_image(status=SUCCEEDED) ─► image.created
提示词编辑器 ─► PUT /tasks/{id}/prompt
              ├─ 每次修改均写入 prompt_record
              ├─ regenerate=false：只保存 prompt / model / ratio / resolution / duration
              └─ regenerate=true：resetForRegenerate → 重新入队
提示词优化 ─► POST /ai/prompt/optimize ─► AiService.optimizePrompt ─► 返回文本，不落库
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
- 参数保存（`regenerate=false`）只更新 `prompt / model / ratio / resolution / duration`，不改变任务状态、不消耗生成次数。
- 模型与清晰度联动校验在 `TaskService.normalize*` 完成：`mock` 仅支持 480P/720P，`seedance` 支持 480P/720P/1080P/4K，非法值回退 720P。

### 4.4 调度、幂等与一致性

- `next_poll_at` 退避序列：`2 / 3 / 5 / 8 / 13 / 20 / 30` 秒；最大 `aigc.poll-timeout-seconds`（默认 900s）。
- CAS 抢占：`UPDATE generation_task SET status='SUBMITTED' WHERE id=? AND status IN ('RECEIVED','QUEUED')`，影响行数为 0 则跳过，避免多线程重复提交。
- 幂等键：`sha256(workspaceId|groupId|shotIndex|prompt)`，写入 `idem_key`（唯一索引 `uk_task_idem`）；重复推送直接复用节点。
- 供应商任务号唯一：`UNIQUE(provider, provider_task_id)`。
- 调度线程池 `spring.task.scheduling.pool.size=2`；`AssetService.transfer` 对同源 URL 做了内存缓存，避免多个 mock 任务重复下载同一个演示视频阻塞轮询。
- **多实例调度**：`AIGC_REDIS_ENABLED=true` 时 `TaskDispatcher.tick` 先抢 Redis 锁 `aigc:dispatch:lock`（SET NX + 120s TTL，Lua 校验后释放），同一时刻只有一个实例扫表；未开启 Redis 时直通单实例逻辑。Redis 异常时本轮放行并打 WARN，提交阶段的状态 CAS 仍能兜底，不会重复提交。
- **画布快照乐观锁**：`saveSnapshot(workspaceId, snapshot, baseVersion)` 使用 `UPDATE ... WHERE id=? AND version=?`，影响行数为 0 抛 `ConflictException` → HTTP 409；前端提示并重新拉取最新布局。未传 `baseVersion` 的旧客户端仍按最后写入生效。

### 4.5 数据模型

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `workspace` | `id`、`name` | 工作空间（默认 1） |
| `canvas` | `workspace_id`、`version`、`snapshot`、`updated_at` | 画布快照；`version` 用于多标签页 / 多实例乐观锁 |
| `canvas_node` | `workspace_id`、`type`、`title`、`prompt`、`content`、`status`、`error_msg`、`x`、`y`、`width`、`height` | 文本 / 脚本节点 |
| `generation_task` | `group_id`、`shot_index`、`prompt`、`provider`、`model`、`ratio`、`resolution`、`duration`、`provider_task_id`、`status`、`progress`、`video_url`、`poll_count`、`idem_key`、`next_poll_at` | 视频节点（一条提示词一行） |
| `prompt_record` | `workspace_id`、`task_id`、`prompt`、`created_at` | 镜头提示词提交 / 修改历史；REST、CLI、MCP 统一记录，幂等重复提交也保留 |
| `canvas_image` | `url`、`object_key`、`x`、`y`、`width`、`height`、`prompt`、`status`、`error_msg`、`provider`、`source` | 画布图片节点（`source` 区分 `UPLOAD` 上传 / `AI` 生成） |
| `task_image_ref` | `task_id`、`image_id`、`sort_index`、`UNIQUE(task_id,image_id)` | 图片→镜头引用，一行 = 画布一条连线 |

生成参数默认值：`model` 取供应商默认，`ratio=16:9`，`resolution=720P`，`duration=5`。

关系：

```text
workspace 1 ─ n canvas 1 ─ n generation_task
                       └─ n canvas_image
generation_task n ── n canvas_image   （通过 task_image_ref，多对多）
```

`sort_index` 决定 `@图片1`、`@图片2` 的编号；同一张图可连多个镜头，一个镜头也可接多张图。

### 4.6 实时推送（WebSocket）

- 地址：`ws://host:8080/ws/canvas/{workspaceId}`，连接按 workspace 分组；公网 HTTPS 对应 `wss://`。
- 握手必须经过网页 Session 鉴权；默认仅允许同源，`AIGC_ALLOWED_ORIGINS` 可添加受信任来源。
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

> 广播默认走单实例内存（`CanvasSocketRegistry.deliverLocal`）。多实例部署时设 `AIGC_REDIS_ENABLED=true`：
> `broadcast` 先 publish 到 Redis 频道 `aigc:canvas:events`，各实例的 `RedisCanvasSubscriber` 收到后只推自己持有的连接；
> Redis 不可用或发布失败时自动回退本地推送。频道全局，不受 `REDIS_DATABASE` 影响。

### 4.7 安全与访问控制

当前是**单管理员、单租户**模型，所有登录者共享同一画布，不是多用户权限系统：

- 网页使用账号密码登录，密码仅在服务端通过 BCrypt 校验；管理员密码与 MCP Token 都必须通过环境变量显式配置，无默认密钥。
- 登录成功后使用 `AIGCSESSION` Cookie（HttpOnly、SameSite=Strict）；HTTPS 部署设置 `AIGC_COOKIE_SECURE=true`。
- 默认会话保存在实例内存；多实例部署设 `AIGC_SESSION_REDIS_ENABLED=true`，Session 改存 Redis（命名空间 `aigc:session`），任一实例登录后其它实例可直接识别。
- `/`、`/index.html`、`/assets/**`、登录/CSRF/会话查询接口公开；其余 REST、`/files/**`、WebSocket 握手均要求已认证 Session。
- 写 REST 与登出要求 `X-XSRF-TOKEN` 请求头，Axios 自动读取 `XSRF-TOKEN` Cookie；未通过 CSRF 校验返回 403，未登录返回 401。
- MCP 的 `/sse` 与 `/mcp/message` 只接受独立 `Authorization: Bearer <MCP_API_TOKEN>`，网页 Session 不能替代；这两个端点豁免 CSRF，因为调用方不是浏览器表单。
- 默认仅同源访问。只有前后端分离部署时才配置 `AIGC_ALLOWED_ORIGINS`（逗号分隔），REST CORS 与 WebSocket Origin 共用白名单。

### 4.8 MCP 接入

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
    "aigc-canvas": {
      "type": "sse",
      "url": "https://canvas.example.com/sse",
      "headers": { "Authorization": "Bearer <MCP_API_TOKEN>" }
    }
  }
}
```

工具返回值使用 `JsonNode`，避免 MCP 层对字符串二次转义。真实 Token 只保存在 MCP 客户端与服务端环境变量中，页面仅展示占位符，不下发真实值；`/api/meta` 根据当前请求和反向代理 Forwarded 头生成 MCP 端点。

### 4.9 存储

`aigc.storage.type` 四选一：`local`（默认）/ `minio` / `cos` / `oss`，每个实现通过 `@ConditionalOnProperty` 互斥装配。

- `local`：落盘到 `./data/files`，`/files/**` 与 REST 一样要求网页 Session；
- `minio`：`endpoint` + `access-key/secret-key` + `bucket`，新建桶保持私有，不再设置匿名公共读；已有桶若曾设为公共读，需在 MinIO 控制台移除匿名 GetObject 策略；
- `cos`（腾讯云 COS）：`endpoint`（访问域名）+ `bucket` + `region` + `secret-id/secret-key`，上传对象显式设为 private；
- `oss`（阿里云 OSS）：`endpoint`（地域节点）+ `bucket` + `access-key-id/access-key-secret`，上传对象显式设为 private；
- COS / OSS 都支持可选的 `public-url`（CDN 或自定义域名）作为数据库稳定 URL 的域名；响应实际使用服务商签发的 GET 预签名 URL，桶和 CDN 不应再向匿名用户公开对象；
- `MinIO / COS / OSS` 在组装画布响应、图片节点和供应商参考图时调用 `AssetService.accessUrl()` 动态签名，默认有效期 3600 秒；数据库只保存稳定 URL，不保存签名参数，过期后刷新画布获取新地址；
- 本地参考图的 `/files/...` 仅供网页访问，提交视频供应商前由 `AssetService.providerImageUrl()` 读取文件并转换为 Base64 data URI；公网及签名 URL 原样传递；
- 所有配置支持环境变量覆盖（`AIGC_STORAGE_TYPE`、`AIGC_SIGNED_URL_TTL_SECONDS`、`COS_*`、`OSS_*`），无需改代码即可切换；
- 第三方地址转存前会先用 `AssetStorage.owns()` 判断是否已属于当前存储，避免重复下载。
- 图片上传：`POST /api/uploads`（multipart），限制 10MB、校验图片 MIME，返回 `{id,name,url}` 并创建画布图片节点。
- 视频成片：调度器成功后 `AssetService.transfer(sourceUrl, "videos", ".mp4")` 下载并转存，前端只访问自有 `/files/videos/*.mp4`。
- 同一源 URL 的转存结果做内存缓存（mock 演示视频所有任务共用同一地址）。

### 4.10 关键配置（`application.yml`）

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `8080` | 后端端口 |
| `spring.datasource.url` | `jdbc:h2:file:./data/aigc` | 默认 H2 文件库 |
| `aigc.security.admin-username` | `${AIGC_ADMIN_USERNAME:}` | 管理员用户名，必填，无默认值 |
| `aigc.security.admin-password` | `${AIGC_ADMIN_PASSWORD:}` | 管理员密码，必填，无默认值 |
| `aigc.security.mcp-token` | `${MCP_API_TOKEN:}` | MCP 独立 Token，必填且至少 32 字符 |
| `aigc.security.cookie-secure` | `${AIGC_COOKIE_SECURE:false}` | HTTPS 公网必须设为 true |
| `aigc.security.allowed-origins` | `${AIGC_ALLOWED_ORIGINS:}` | 额外 REST / WS 来源，逗号分隔；同源留空 |
| `aigc.provider.default-name` | `${AIGC_PROVIDER:seedance}` | `mock` / `seedance`，默认 Seedance 2.0 |
| `aigc.provider.mock.seconds` | `8` | mock 出片耗时 |
| `aigc.provider.seedance.api-key` | `${ARK_API_KEY:}` | 火山方舟密钥，通过环境变量或 Git 忽略的 `application-local.yml` 提供 |
| `aigc.provider.seedance.model` | `${ARK_MODEL:doubao-seedance-2-0-260128}` | Seedance 2.0 模型 ID |
| `aigc.provider.seedance.ratio` | `16:9` | 默认视频比例 |
| `aigc.provider.seedance.duration` | `5` | 默认时长（秒） |
| `aigc.provider.seedance.watermark` | `false` | 是否添加水印 |
| `aigc.storage.type` | `local` | `local` / `minio` / `cos` / `oss` |
| `aigc.storage.signed-url-ttl-seconds` | `${AIGC_SIGNED_URL_TTL_SECONDS:3600}` | 私有对象 GET 预签名 URL 有效期 |
| `aigc.storage.cos.*` | — | 腾讯云 COS：endpoint、secret-id、secret-key、bucket、region、public-url |
| `aigc.storage.oss.*` | — | 阿里云 OSS：endpoint、access-key-id、access-key-secret、bucket、public-url |
| `aigc.poll-timeout-seconds` | `900` | 单任务轮询超时 |
| `spring.task.scheduling.pool.size` | `2` | 调度线程数 |
| `aigc.realtime.redis-enabled` | `${AIGC_REDIS_ENABLED:false}` | 多实例时开启 Redis Pub/Sub 广播与调度分布式锁 |
| `aigc.session.redis-enabled` | `${AIGC_SESSION_REDIS_ENABLED:false}` | 多实例时把登录 Session 存入 Redis（命名空间 `aigc:session`） |
| `spring.data.redis.host/port` | `${REDIS_HOST:localhost}` / `${REDIS_PORT:6379}` | Redis 地址 |
| `spring.data.redis.password` | `${REDIS_PASSWORD:}` | Redis 密码，无密码留空 |
| `spring.data.redis.database` | `${REDIS_DATABASE:0}` | Redis 库号 |
| `spring.data.redis.timeout/connect-timeout` | `800ms` | 快速失败并回退本地广播 |
| `aigc.ai.base-url` | `${ARK_BASE_URL:...}` | Ark API 地址 |
| `aigc.ai.api-key` | `${ARK_API_KEY:}` | 为空时脚本 / 图片 / 优化走 mock |
| `aigc.ai.script-model` | `doubao-seed-1-6-250615` | 分镜脚本模型 |
| `aigc.ai.image-model` | `doubao-seedream-3-0-t2i-250415` | 文生图模型 |
| `spring.ai.mcp.server.sse-endpoint` | `/sse` | MCP SSE 端点 |
| `spring.ai.mcp.server.sse-message-endpoint` | `/mcp/message` | MCP 消息端点 |

### 4.11 REST API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/auth/csrf` | 初始化 XSRF Cookie |
| GET | `/api/auth/session` | 查询当前登录状态 |
| POST | `/api/auth/login` | 表单字段 username/password，需 CSRF |
| POST | `/api/auth/logout` | 退出并销毁 Session，需 CSRF |
| GET | `/api/canvas/{workspaceId}` | 画布全量状态（含 version、nodes） |
| GET | `/api/canvas/{workspaceId}/prompt-records` | 最近 200 条镜头提示词记录，按时间倒序 |
| POST | `/api/canvas/{workspaceId}/shots` | 新建镜头（支持 model / ratio / resolution / duration） |
| POST | `/api/canvas/{workspaceId}/storyboards` | 批量新建分镜 |
| PUT | `/api/canvas/{workspaceId}/snapshot` | 保存坐标/视口；`baseVersion` 冲突返回 409 |
| PUT | `/api/canvas/{workspaceId}/order` | 保存分组内排序 |
| POST / PUT / DELETE | `/api/canvas/{workspaceId}/nodes[/{nodeId}]` | 文本 / 脚本节点增改删 |
| POST | `/api/canvas/{workspaceId}/nodes/{nodeId}/script` | AI 生成分镜脚本 |
| POST | `/api/canvas/{workspaceId}/images/ai` | AI 生成图片 |
| POST | `/api/canvas/{workspaceId}/images/{imageId}/generate` | 图片节点重新生成 |
| POST / PUT / DELETE | `/api/canvas/{workspaceId}/images[/{imageId}]` | 图片节点增改删 |
| POST / DELETE | `/api/canvas/{workspaceId}/edges[/{edgeId}]` | 引用连线增删 |
| POST | `/api/canvas/{workspaceId}/ai/prompt/optimize` | 一键优化提示词 |
| PUT | `/api/tasks/{taskId}/prompt` | 改提示词 / 参数；`regenerate=false` 仅保存 |
| POST | `/api/tasks/{taskId}/retry` | 重试 |
| POST | `/api/tasks/{taskId}/cancel` | 取消 |
| DELETE | `/api/tasks/{taskId}` | 删除节点 |
| POST | `/api/uploads` | 上传参考图并建节点 |
| GET | `/api/meta` | 供应商、AI mock 状态、MCP 端点等元信息 |

### 4.12 CLI 客户端（`cli/`）

`cli/aigc.mjs` 是基于现有 REST 接口的零依赖命令行客户端（Node.js 16+，仅用内置模块）：

```bash
node cli/aigc.mjs login --username admin --password ***
node cli/aigc.mjs push --prompt "海边日落" --group "A组"
node cli/aigc.mjs canvas
node cli/aigc.mjs assets --type video
node cli/aigc.mjs upload --file ./a.png
node cli/aigc.mjs rm --id 12
```

- 覆盖 `login / logout / canvas / assets / push / storyboard / upload / rm / rm-image`；
- 登录态（Cookie + 最新 CSRF Token + 服务地址）保存在 `~/.aigc-cli/session.json`；
- 所有写操作自动先请求 `/api/auth/csrf` 再带 `X-XSRF-TOKEN`，登录后 Session 轮换会重新取 Token；
- 鉴权口径与网页一致（Session），MCP Bearer Token 仍只对 `/sse`、`/mcp/message` 生效；
- `--json` 输出原始响应，便于脚本二次处理。

---

## 5. 前端架构

### 5.1 目录

```text
web/src
├── AuthGate.vue             # 登录门禁：会话检查通过后才挂载 App，处理登出与 401
├── App.vue                  # 布局、Vue Flow 画布、图同步、拖拽排序、连线校验、快照保存
├── main.js                  # 根组件挂载 AuthGate，注册 VueFlow 样式
├── api.js                   # axios 封装（Session/CSRF、401 通知、上传 / 节点 / AI）
├── style.css                # 暗色主题与 Vue Flow 覆盖
├── store/canvas.js          # 单一 reactive store：数据 + WS 事件 + 业务动作
└── components
    ├── ShotNode.vue         # 视频节点（16:9 媒体、预览按钮、下方提示词编辑器）
    ├── PromptEditor.vue     # 提示词编辑器：@ 图片、模型 / 比例 / 清晰度 / 时长、全屏、优化
    ├── ImageNode.vue        # 图片节点（右侧源锚点）
    ├── GroupNode.vue        # 分组标签
    ├── CanvasContextMenu.vue# 画布右键菜单（8 项）
    ├── ScriptNode.vue       # 脚本节点（AI 生成分镜）
    ├── TextNode.vue         # 文本节点
    ├── ImageGenNode.vue     # AI 图片节点
    ├── ImageRefEdge.vue     # 紫色虚线引用边（可悬浮删除）
    ├── Sidebar.vue          # 分组/镜头列表、搜索、折叠为图标栏
    ├── ShotInspector.vue    # 右侧检查器：只读详情、参数、重试、取消、删除；可收起
    ├── NewShotDialog.vue    # 新建提示词（@ 引用、粘贴/拖入上传）
    ├── PreviewDialog.vue    # 成片预览
    ├── AssetDialog.vue      # 资产管理：生成视频 / 生成图片 / 上传图片，支持预览与下载
    ├── UsageGuide.vue       # 系统使用说明整页：快速开始 / MCP 接入 / CLI 使用 / 常见问题
    └── McpDialog.vue        # MCP 接入说明
```

主题切换通过 `web/src/theme.js` 与 `style.css` 的 `html[data-theme='light']` 变量覆盖实现，详见 5.9。

### 5.2 状态与事件

`store/canvas.js` 用 `reactive` 维护：`tasks / images / nodes / edges / snapshot / version / selectedTaskId / selectedImageId / previewTask / keyword / connection / toast`。

- `loadAll()`：拉取 `/api/canvas/1` 并整体覆盖，同时记录 `version`；
- `connectRealtime()`：WS 断线后按 `1/2/4/8s` 指数退避重连，重连成功再 `loadAll()` 补齐状态；
- `handleEvent()`：按 `4.6` 的事件表做 `upsert / remove`，并同步选中项与检查器；
- `savePrompt()` / `updateShotSettings()`：编辑提示词或改参数，后者固定 `regenerate=false`；
- 保存快照携带 `state.version`，成功后用响应回写；409 时提示并 `loadAll()`。

### 5.3 画布渲染与布局

- 节点类型：`shot`（镜头卡片）、`image`（图片）、`group`（分组标签）、`text`（文本）、`script`（脚本）。
- 布局常量：`SHOT_W=320`、`SHOT_H=220`、列距 28、行距 `SHOT_H+224=444`；媒体区 320×180（16:9）。
- 坐标优先级：**用户显式坐标 > `canvas.snapshot` 中保存的坐标 > 自动布局**。
- `syncGraph()` 增量维护节点/边，尽量保留对象引用，避免重渲染抖动。
- 用户拖动节点后：同组按 `x` 重排镜号 → `PUT /order`；图片 / 文本节点坐标单独持久化。
- 视口：优先恢复 `snapshot.viewport`，否则 `fitView({ padding: 0.12 })`；视口变化 700ms 防抖保存。
- 侧栏支持折叠为 58px 图标栏（展开、新建、分组跳转、进行中数量），状态存 `localStorage`；点击分组图标会展开侧栏并定位该组首个镜头。
- 检查器可收起为右侧窄条，展开按钮 `« 详情` 浮在画布右上角，状态同样存 `localStorage`。
- 支持把图片文件直接拖到画布：`screenToFlowCoordinate` 定位并上传。

### 5.4 连线交互（图片 → 镜头，多对多）

- 数据层天然多对多：`task_image_ref` 唯一键为 `(task_id, image_id)`，同一图片可连多个镜头，同一镜头可接多张图片。
- 连线创建的两条路径：
  1. 落在镜头节点左侧的 `target` 锚点上 → Vue Flow 原生 `@connect`；
  2. **落在镜头卡片任意位置** → `onConnectEnd` 中用 `document.elementFromPoint()` 找到 `.vue-flow__node-shot`，读取 `data-id` 后复用同一套校验/建边逻辑（落在锚点上时跳过，避免重复建边）。
- 拖拽连线期间画布根节点加 `.linking`，所有镜头卡片高亮 + `crosshair`，提示「整张卡片都是落点」。
- 已存在相同 `(imageId, taskId)` 时提示且不重复建边。

### 5.5 节点内交互（v0.2）

- **视频区**：默认 `controls` 隐藏，悬停出现中央播放按钮；点击视频只内联播放，不再弹窗；右上角 `⤢` 才打开 `PreviewDialog`。
- **提示词编辑器**：位于镜头节点下方，宽 320、整体 `aspect-ratio: 16/9`；工具栏（上传 / 画布参考 / @图片）+ 文本域 + 底部（模型 select、参数汇总按钮、`✦` 优化、发送）。
- **参数面板**：点击汇总按钮展开，比例网格、清晰度按钮组、时长滑块；滑块与面板容器带 `nodrag`，避免被 Vue Flow 节点拖拽抢事件。
- **时长滑块**：拖动时只改本地值，`change` 时调用 `updateShotSettings` 保存，避免接口回填造成“拖不动”。
- **全屏编辑**：`Teleport` 到 body，1040×82vh，`Esc` / 遮罩 / `⤡` 关闭。
- **提示词优化**：`AiService.optimizePrompt` 返回文本，浮层支持复制、重新生成、填充输入框；填充不自动提交。

### 5.6 重连

后端重启期间标签变为「已断开」，恢复后自动回到「实时连接」并补拉画布状态，无需刷新页面（已实测）。

### 5.7 资产管理

顶栏「资产管理」打开 `AssetDialog`，数据直接复用画布状态，不额外请求接口：

- **生成视频**：`tasks` 中 `videoUrl` 非空的镜头，缩略图取 `coverUrl`；
- **生成图片**：`images` 中 `source = AI` 且已有 `url` 的节点；
- **上传图片**：`images` 中 `source = UPLOAD` 的节点（老数据按 `prompt` 是否为空兜底判断）；
- 支持分类筛选、点击预览（图片 / 视频播放）与下载。

`canvas_image.source` 在写入时确定：`UploadController` 上传与外部 URL 引用记 `UPLOAD`，AI 图片节点及重新生成记 `AI`，历史数据由启动脚本回填。

### 5.8 画布多选与批量操作

- **多选**：`Shift + 拖拽` 框选（Vue Flow `selectionKeyCode`），`Ctrl / ⌘ + 点击` 追加选择（`multiSelectionKeyCode`）；分组节点不可选。
- **浮动工具条**：选中任意镜头后出现在画布底部，显示「已选 N 个镜头」，提供重新生成 / 取消 / 删除，`×` 清除选择。
- **批量执行**：分别调用 `retryTasks` / `cancelTasks` / `removeTasks`，内部 `Promise.allSettled` 并发请求并汇总成功/失败数量；删除后同步清理引用边与详情面板选中态。
- **并发说明**：多选提交只是让多个镜头尽快进入队列；真正并行由调度器保证——每 800ms 最多提交 5 个 `QUEUED` 任务，任务之间互不阻塞（见 4.4）。

### 5.9 主题切换与画布缩放

- **主题**：`web/src/theme.js` 维护 `theme`（`dark` / `light`），写入 `document.documentElement.dataset.theme` 并持久化到 `localStorage('aigc.theme')`。
- **实现方式**：`style.css` 中 `:root` 定义夜间设计变量，`html[data-theme='light']` 只覆盖变量（背景、面板、卡片、表面层级、强调文字、遮罩等），组件无需感知主题。
- **入口**：顶栏「白天模式 / 夜间模式」按钮；画布点阵背景与 Web 颜色随主题联动。
- **缩放上限**：Vue Flow `maxZoom` 调整为 `3`，可以把镜头节点放大查看细节；`minZoom` 保持 0.3。

### 5.10 节点快捷键（复制 / 删除）

- **Ctrl / ⌘ + D**：对当前选中的节点直接复制。镜头复制提示词与参数，并复制其全部图片引用连线；图片复制提示词、状态与全部镜头连线；文本 / 脚本复制标题与内容。新节点偏移 `36px` 后自动选中，多选时逐个复制。
- **Alt + 拖动**：拖拽开始时记录原节点坐标，落点处创建副本（镜头 / 图片 / 文本），副本保留提示词与参数但**不复制连线**；原节点回到起始位置。
- **Backspace / Delete**：删除所有选中节点（分组节点除外），按类型分别调用 `removeTask` / `removeImage` / `removeCanvasNode`，镜头与图片的引用连线由后端级联清理。
- **输入保护**：焦点在 `input` / `textarea` / `contenteditable` 时不触发快捷键；Vue Flow 自带 `deleteKeyCode` 已置为 `null`，删除统一走本逻辑。
- **复制镜头注意**：后端幂等键包含 `shotIndex`，副本按分组取下一个序号，因此不会命中幂等返回原任务。

---

## 6. 部署与运行

### 6.1 本地

```powershell
# 后端（Maven 与 java 都必须使用 JDK 17）
cd server
$env:AIGC_ADMIN_USERNAME='admin'
$env:AIGC_ADMIN_PASSWORD='<替换为你的强密码>'
$env:MCP_API_TOKEN='<替换为至少32字符的随机Token>'
$env:ARK_API_KEY='<替换为你的火山方舟密钥>'
# 也可改为在 application-local.yml 手动配置密钥，并在启动参数中使用 --spring.profiles.active=local
# 可选：不调用真实视频接口时改用 mock
# $env:AIGC_PROVIDER='mock'
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

MinIO 模式需设置 `aigc.storage.type=minio` 并配置 endpoint / access-key / secret-key / bucket。桶必须保持私有；早期版本自动创建的公共读策略需要在已有桶上手动移除。

### 6.3 生产注意事项

- 必须通过 HTTPS 暴露网页、REST、MCP 与 WSS，并设 `AIGC_COOKIE_SECURE=true`；不要把 8080 直接暴露到公网。
- 使用 Nginx / 网关同源反向代理 `/api`、`/files`、`/sse`、`/mcp/message`、`/ws/canvas`，转发 `X-Forwarded-Proto/Host` 并配置 WebSocket Upgrade；后端已启用 `forward-headers-strategy=framework`。
- 管理员密码与 `MCP_API_TOKEN` 使用不同的随机值，只放在部署环境 / Secret 管理中，不写入仓库或前端；Token 泄露后应立即轮换。
- H2 控制台默认关闭；对象存储桶保持私有，已有公开读桶需先收紧权限。
- H2 文件库不适合多实例并发，生产应使用 MySQL；
- WebSocket 广播默认单实例；多实例设 `AIGC_REDIS_ENABLED=true` 并配置 `REDIS_HOST/PORT/PASSWORD/DATABASE`，Redis 故障时自动回退本地；
- 调度为进程内 `@Scheduled`；多实例设 `AIGC_REDIS_ENABLED=true` 后由 Redis 分布式锁保证同一时刻只有一个实例扫表；
- 网页 Session 默认存实例内存；多实例需设 `AIGC_SESSION_REDIS_ENABLED=true` 共享登录态（Redis 不可用时将无法登录，需保证 Redis 高可用）；
- MCP 当前为 SSE，会话仍绑定单个实例，多实例需在网关层做会话粘滞；
- 画布快照已做 `version` 乐观锁，冲突返回 409；前端会提示并同步最新布局。

---

## 7. 扩展点

| 场景 | 做法 |
| --- | --- |
| 新增视频供应商 | 实现 `VideoProvider`（`name/submit/poll`），注册为 Bean，`ProviderRegistry` 自动发现；请求里传 `provider` 或改默认值 |
| 新增画布事件 | 后端 `CanvasEvent.of(type, data)` 广播，前端 `handleEvent` 增加分支 |
| 新增 MCP 工具 | 在 `McpCanvasTools` 加 `@Tool` 方法，启动日志确认 `Registered tools: N` |
| 新增模型 / 清晰度 | 前端 `ShotNode.vue` 的选项常量 + 后端 `TaskService.normalize*` 白名单同步修改 |
| 新增 AI 能力 | 在 `AiService` 增加方法（Ark 调用 + mock 兜底），新增接口放 `CanvasController` |
| 新增表字段 | 同步修改 `schema-h2.sql` 与 `schema-mysql.sql`，并更新对应 `domain` / DTO |
| 切换存储 | 实现 `AssetStorage` 新 Bean（条件 `aigc.storage.type`） |

---

## 8. 已知限制

1. 调度已支持 Redis 分布式锁、网页 Session 可选择存 Redis；MCP 的 SSE 会话仍绑定单实例，多实例需网关会话粘滞。
2. H2 仅适合本地/演示。
3. `seedance`、AI 脚本 / 图片 / 优化都需要 Ark API Key（环境变量或 local 配置）；脚本 / 图片 / 优化未配置时回退 mock，视频任务会明确失败，演示时可设置 `AIGC_PROVIDER=mock`。
4. MCP 仅 SSE（受 Spring AI 1.0.0 限制）。
5. 画布快照已做乐观锁；但同一标签页内并发保存仍按最后响应为准，极端情况下会触发一次 409 重载。
6. 导演台 / 智能剪辑 / 音频节点仅为菜单占位，无实际能力。
7. 当前为单管理员、单租户，未提供用户注册、角色权限或按用户隔离工作空间。
8. 预签名 URL 过期后需要刷新画布 / 页面；真实 COS / OSS / MinIO 签名下载需在具备凭据的环境验证。

---

## 9. 变更记录约定

**本项目每次代码 / 配置 / 文档改动，都必须在 `docs/task-records/` 下新增一份任务记录**，文件名为 `YYYY-MM-DD-<主题>.md`，内容至少包含：

1. 需求背景（用户原话或问题描述）
2. 改动清单（文件 + 关键点）
3. 验证方式与结果（构建 / 接口 / 浏览器）
4. 影响面与兼容性
5. 遗留问题 / 下一步

历史记录按日期归档，便于回溯每次改动的动机与验证证据。
