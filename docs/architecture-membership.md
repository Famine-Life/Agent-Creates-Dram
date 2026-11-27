# 架构设计：多用户会员制（v1.0）

> 本文对应 `feature/multi-user-membership` 分支。单用户版架构见 `docs/architecture.md`，两者并存。

## 1. 总体结构

```text
浏览器 / CLI / MCP 客户端
        │  Session Cookie + CSRF  /  Bearer Token
        ▼
Spring Security ──► Auth / Account / Admin Controller ──► User & Billing Service
        │                                                    │
        │                                                    ├─► MySQL / H2（Flyway V1+V2）
        │                                                    ├─► WalletService（冻结/实扣/退回）
        │                                                    └─► PaymentService（订单状态机）
        ▼
Canvas / Task / Node Controller ──► TaskService ──► TaskDispatcher ──► Provider Adapter
        │                              │                 ▲
        │                              └─► Outbox ───────┘（DB 唤醒 / 可选 RabbitMQ）
        ▼
WebSocket（握手校验 workspace 归属）
```

设计原则：

1. **单实例可运行**：默认 H2 + 本地文件 + Mock 支付 + 日志邮件 + DB Outbox，不依赖 Redis / RabbitMQ / SMTP / 商户号。
2. **多实例可扩展**：Redis Session 与 Pub/Sub、RabbitMQ、COS/OSS/MinIO、真实支付按配置启用。
3. **资金与任务一致**：任务、冻结流水、Outbox 在同一数据库事务内提交；状态更新使用 CAS。
4. **归属校验在服务层**：所有入口统一校验当前用户与画布/任务/图片/节点的归属，越权按 404 处理。

## 2. 代码模块

| 包/模块 | 职责 |
| --- | --- |
| `config` | Security、Session、WebSocket、CORS、登录限流、RabbitMQ 条件装配 |
| `user` | `app_user`、注册/验证/找回、数据库 UserDetailsService、用户上下文、MCP Token、管理员初始化 |
| `member` | 会员档位、权益解析、清晰度限制、默认播种 |
| `billing` | 钱包、冻结/实扣/退回、日额度、计价、账户摘要 |
| `payment` | 订单状态机、Mock / 微信 V3 / 支付宝渠道、回调验签与到账 |
| `mq` | 事务 Outbox、进程内唤醒队列、可选 RabbitMQ 发布/消费 |
| `audit` | 登录、生成、订单、Token、管理员操作审计 |
| `workspace` | 画布归属校验（`WorkspaceAccess`）、画布复制/删除 |
| `task` | 画布、镜头、图片、文本/脚本节点、生成状态、计费接入 |
| `realtime` | WebSocket 握手、画布事件注册表、Redis 广播可选 |
| `storage` | 本地 / MinIO / COS / OSS 存储，用户目录隔离，第三方产物转存 |
| `api` | REST 控制器；`api/mcp` 为 MCP SSE 工具 |

## 3. 数据模型

### 3.1 用户与安全

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `app_user` | username/username_lower、email/email_lower、password_hash、role、status、email_verified、member_code、member_expires_at | 用户与会员快照；用户名/邮箱唯一 |
| `auth_token` | user_id、purpose、token_hash、expires_at、used_at | 邮箱验证 / 找回密码，一次性、哈希存储 |
| `mcp_token` | user_id、name、token_hash、token_prefix、expires_at、revoked_at | 个人 MCP Token，明文仅创建时返回 |

### 3.2 会员、钱包与用量

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `member_plan` | code、price_cents、duration_days、daily_credits、max_concurrent、max_resolution、dispatch_weight | 三档会员，首次启动播种，后台可改 |
| `credit_package` | code、credits、price_cents | 积分商品 |
| `wallet` | user_id、available_credits、frozen_credits、version | 可用/冻结分离，原子条件更新 |
| `credit_ledger` | user_id、type、biz_id、amount、status | 不可变流水，`(user_id,type,biz_id)` 唯一 |
| `usage_counter` | user_id、period_type、period_key、calls、credits | 日额度统计，成功生成才累计 |

### 3.3 订单与事件

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `payment_order` | order_no、product_type/code、product_snapshot、amount_cents、status、channel、third_party_trade_no、version | 订单与商品快照 |
| `payment_notify_log` | order_no、channel、signature_valid、result | 回调排障，不记录密钥 |
| `event_outbox` | event_id、aggregate_id、event_type、status、attempts | 事务 Outbox |
| `event_inbox` | event_id、consumer | 消费幂等（预留扩展） |
| `audit_log` | user_id、action、target_type/id、ip、detail | 审计 |

### 3.4 业务表归属

`workspace`、`canvas`、`generation_task`、`prompt_record`、`canvas_image`、`canvas_node`、`task_image_ref` 均增加 `user_id`；`generation_task` 唯一键由 `idem_key` 改为 `(user_id, idem_key)`，并保留 `(provider, provider_task_id)` 唯一。

`generation_task` 增加 `billing_op_id` 与 `credit_cost`，记录本次尝试的计费快照。

## 4. 认证与租户隔离

### 4.1 登录链路

1. `DbUserDetailsService` 按用户名或邮箱（小写）查询 `app_user`，返回 `AuthUser(id, role, status, emailVerified)`。
2. Spring Security 使用 BCrypt 校验，Session 保存登录态；CSRF 使用 Cookie 仓储。
3. 登录成功事件更新 `last_login_at` 并写审计；登录失败沿用 IP 渐进限流。
4. `/api/auth/session` 返回认证状态与 `AccountSummary`（角色、会员、积分、今日用量）。

### 4.2 归属模型

- `UserContext` 从 SecurityContext 读取 `AuthUser`。
- `WorkspaceAccess.requireOwned(workspaceId)` 校验 `workspace.user_id == 当前用户`（迁移遗留的空 `user_id` 仅管理员可访问）。
- `TaskService.requireTask`、`CanvasNodeService.requireNode/requireImage` 在请求线程校验行归属；调度与异步线程没有登录上下文时放行。
- 画布下的所有数据均以 `workspaceId` 为边界，画布归属校验通过后，列表查询天然隔离。
- 系统级流程（调度、支付回调、迁移、Outbox）不使用请求用户上下文，避免误加租户条件。

> 实现说明：没有引入全局 MyBatis-Plus 租户插件，而是采用“服务层统一归属校验 + 显式 `user_id` 过滤”。原因是调度、支付回调、Outbox 等系统查询需要跨用户执行，全局插件容易在这些线程中漏过滤或错误过滤。该选择在保证隔离的前提下降低了对既有查询的侵入。

## 5. 钱包与计费

### 5.1 账户生命周期

```text
提交生成 ──► 校验（邮箱/状态/额度） ──► reserve（available → frozen）
                                              │
              成功 ──► capture（frozen → 实扣，写入 CAPTURE，累计当日额度）
              失败/超时/取消 ──► release（frozen → available，写入 RELEASE）
              结果未知 ──► 保持 frozen，等待人工核对
```

- `reserve` 使用 `available_credits >= amount` 的原子 UPDATE，余额不足写入 `INSUFFICIENT` 失败流水并抛出 402。
- `capture/release` 先读取 `RESERVE` 流水状态，只有 `FROZEN` 才执行；执行后把 `RESERVE` 置为 `SETTLED`。
- 所有写入以 `(user_id, type, biz_id)` 唯一键兜底，重复调用安全。
- 每次生成尝试有独立 `billing_op_id`；重试先结算旧尝试（成功不退款、失败退款），再为新尝试冻结。

### 5.2 计价

- 配置：`aigc.billing.video-costs`、`image-costs`、`text-cost`、`fallback-*`。
- 视频：`ceil(基准价 × 实际时长 / 5)`；图片：`单价 × 张数`；文本：固定单价。
- 模型未配置时使用兜底价，避免出现免费生成。

## 6. 调度与 MQ

### 6.1 调度闸门

`TaskDispatcher.submitQueued()` 每轮：

1. 读取 `RECEIVED/QUEUED` 且到期的候选任务；
2. 优先处理 Outbox/RabbitMQ 唤醒的任务；
3. 按会员 `dispatch_weight` 降序排序；
4. 统计每个用户当前活跃任务数，达到 `max_concurrent` 则跳过（任务保持 `QUEUED`）；
5. 再校验 provider/model 与分辨率对应的平台并发；
6. 提交成功后更新状态、记录供应商任务号；模型级 429 时本轮停止继续提交该模型。

轮询阶段保持原有退避、`SUBMIT_UNKNOWN`、超时与 Redis 分布式锁逻辑；成功、失败、超时分别触发钱包实扣或退回。

### 6.2 Outbox

```text
createShot（同一事务）
  ├─ insert generation_task
  ├─ reserve 积分 + 写流水
  └─ insert event_outbox(TASK_QUEUED)
OutboxWorker（1s）
  ├─ PENDING → PROCESSING（CAS）
  ├─ 放入 TaskWakeupQueue
  ├─ 可选：发布 RabbitMQ
  └─ PROCESSED / 失败重试 / 超过 8 次进入 DEAD
```

- 默认 `AIGC_MQ_TYPE=db`：只依赖数据库，重启后未处理事件继续消费。
- `AIGC_MQ_TYPE=rabbitmq`：同一 Outbox 通过 RabbitTemplate 发布，消费者放入同一唤醒队列；消息内容只有任务 ID。
- 调度仍保留数据库扫描兜底，消息丢失不会导致任务永久不执行。
- 管理后台可查看 DEAD 事件并重投。

## 7. WebSocket、MCP 与文件

### 7.1 WebSocket

- 连接地址：`/ws/canvas/{workspaceId}`。
- `CanvasHandshakeInterceptor` 在握手时读取登录用户，未登录直接拒绝。
- `CanvasWebSocketHandler` 建立连接时查询 `workspace` 归属，越权以 `POLICY_VIOLATION` 关闭。
- 事件注册表按 `workspaceId` 分组；由于加入前已校验归属，workspace 唯一 ID 足以隔离。
- 多实例开启 Redis 时，广播事件携带 workspace，订阅端只转发给本机同 workspace 连接。

### 7.2 MCP

- `McpTokenService` 按 SHA-256 哈希查询 Token，校验吊销与过期，更新最后使用时间，映射为 `AuthUser`。
- 旧的全局 `MCP_API_TOKEN` 仅在常量匹配时映射到管理员，保留过渡兼容。
- MCP 工具未指定画布时使用当前用户第一个画布；指定时校验归属。

### 7.3 文件与对象存储

- 新图片/视频/封面使用 `{userId}/images|videos|covers/...` 对象键。
- 本地 `/files/**` 由 `FileAccessController` 提供：必须登录；路径首段为用户 ID 时校验相等，旧路径按 `canvas_image.url` 或任务视频/封面 URL 记录校验归属；管理员可访问全部。
- 对象存储保持私有，前端通过短期签名 URL 访问。
- 第三方产物必须转存自有存储；转存失败抛出异常，任务进入失败/重试流程，禁止保存第三方 URL。

## 8. 支付

### 8.1 状态机

```text
PENDING ──支付中──► PAYING ──验签+金额校验──► PAID（钱包/会员入账）
   │                   │
   └──── 超时/取消 ─────┴──► CLOSED
```

- 下单保存商品快照；金额以服务端商品配置为准，客户端不能传入价格。
- 回调统一走 `PaymentService.handlePaid(orderNo, channel, amountCents, tradeNo)`：
  1. 订单不存在 → `ORDER_NOT_FOUND`；
  2. 已支付 → `DUPLICATE`（直接返回成功）；
  3. 金额不符 → `AMOUNT_MISMATCH`（记录人工核对）；
  4. 已关单 → `CLOSED_AFTER_PAY`（记录人工核对）；
  5. `PENDING/PAYING → PAID` CAS 成功后，在同一事务充值或升级会员。
- Mock 支付仅当 `aigc.payment.mock-enabled=true` 且订单渠道为 mock 时可用。
- 微信 V3：Native 下单、`Wechatpay-*` 头验签、AES-GCM 解密回调资源。
- 支付宝：电脑网站支付表单、RSA2 签名、异步通知验签。

> 实现说明：支付适配采用与 IJPay 2.9.7 等价的直连协议实现（RSA2/RSA 签名、AES-GCM 解密、表单签名），未引入 IJPay 依赖，以避免其部分模块与 Spring Boot 3 / jakarta 命名空间的兼容风险。渠道接口已独立封装，后续可无缝替换为 IJPay。

## 9. 数据库迁移

- Flyway 管理 `db/migration/h2` 与 `db/migration/mysql` 两套脚本。
- V1 为单用户版基线，V2 为多用户会员制：
  - 业务表新增可空 `user_id`；
  - `generation_task` 增加计费字段并重建幂等唯一键；
  - 新建用户、Token、会员、商品、钱包、流水、用量、订单、回调日志、审计、Outbox/Inbox 表。
- 已有库没有 Flyway 历史时使用 `baseline-on-migrate=true`，以 V1 为基线只执行 V2。
- 启动引导程序创建管理员、播种会员与积分商品，并把 `user_id` 为空的旧数据统一归属管理员。
- 已知限制：V2 未强制把 `user_id` 改为 `NOT NULL`，以兼容旧数据与管理员初始化顺序；应用层所有查询均要求归属。

## 10. 配置矩阵

| 配置 | 本地默认 | 生产建议 |
| --- | --- | --- |
| `AIGC_MAIL_TYPE` | `log` | `smtp` + SMTP 凭据 |
| `AIGC_PAYMENT_CHANNEL` | `mock` | `wechat` / `alipay` + 商户凭据与 HTTPS 回调 |
| `AIGC_MQ_TYPE` | `db` | `rabbitmq`（高并发） |
| `AIGC_STORAGE_TYPE` | `local`（测试） | `cos` / `oss` / `minio`，私有桶 + 签名 URL |
| `AIGC_REDIS_ENABLED` | `false` | 多实例时 `true` |
| `AIGC_SESSION_REDIS_ENABLED` | `false` | 多实例时 `true` |
| `AIGC_ADMIN_*` | `admin/admin@888/admin@local` | 环境变量注入强密码 |

## 11. 已知限制与后续

1. 真实 SMTP、微信/支付宝回调、RabbitMQ、MySQL 5.7 需在具备凭据与实例的环境追加联通验收；本地仅验证 Mock/日志/DB 路径。
2. 单用户旧文件路径仍可被已登录用户访问，归属按数据库记录校验；建议后续统一迁移到用户目录。
3. 账号禁用或重置密码后，已存在的 Session 不会立即失效（Session 未集中吊销）；可在后续引入 Session 注册表。
4. 余额不足/额度耗尽目前直接返回 402，不创建任务；如产品需要“失败任务卡片”，可改为落库后标记 `INSUFFICIENT_BALANCE/QUOTA_EXCEEDED`。
5. 团队协作、退款发票、自动续费、复杂风控后台未包含在本版本。
