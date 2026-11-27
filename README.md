# Agent造剧｜Agent 驱动的视频与漫剧工作台

**Agent造剧**让 Codex、WorkBuddy 等 Agent 不只提供创作建议，还能通过 CLI 直接在项目画布里执行视频与漫剧制作流程。Agent 可以把创意拆成脚本、参考图片和分镜视频任务，提交生成、跟踪进度；创作者则在画布上检查、调整和继续编辑。

以前，创意和分镜写在聊天里，创作者还要手动复制提示词、创建节点、关联参考图，再逐个确认任务状态。现在可以直接告诉 Agent：

```text
帮我制作一集古风漫剧。根据这段故事整理脚本和分镜，准备角色参考图，
把分镜提交为视频生成任务，并把图片与对应镜头关联到同一张画布。
完成后告诉我每个镜头的生成状态和失败原因。
```

Agent 使用项目 CLI 把工作落到真实画布中。你继续决定创作方向、检查生成结果、修改内容并推进下一轮。

## 适合用户

1. 已经使用画布、但想把批量建节点和跟进任务交给 Agent 的创作者。
2. 适合用 Codex、Claude code、WorkBuddy 等工具搭建固定视频工作流的用户。
3. 希望数据私有化、自行扩展第三方API的用户、企业。



## 项目展示

**Agent 从故事创意到漫剧分镜：**

![Codex Agent 生成漫剧素材并查看画布结果](docs/images/agent-storyboard-workflow.png)

**分镜素材、参考图与生成视频在画布中关联管理：**

![漫剧分镜画布与视频节点展示](docs/images/storyboard-canvas-showcase.png)

## 从 Agent 对话到可编辑画布

项目 CLI 将画布操作提供给能运行本地命令的 Agent。通过 JSON 输出，Agent 可以读取节点 ID、素材与生成状态，并据此继续后续步骤：

- 创建和检查画布，查看当前可用模型与素材。
- 创建脚本节点并调用脚本生成；创建 AI 图片节点、上传参考图或生成图片。
- 批量提交分镜视频任务，把参考图片关联到对应镜头。
- 查询任务进度、读取失败原因，并按需取消或重新生成。
- 将脚本、图片和视频任务保留在可继续编辑的画布中。
  
**完整命令、漫剧工作流示例和任务轮询方式见 [Agent CLI 指南](docs/agent-cli.md)。**
  
CLI 当前负责分镜级素材生成和任务编排；视频剪辑、配音、转场和整集合成仍需其他工具完成。
  


## 功能

- Agent 通过 CLI 自动编排脚本、图片参考、分镜视频生成和任务跟踪。
- 也可通过 MCP 或网页界面创建视频镜头和提示词。
- 使用画布节点组织图片、脚本、文本与视频任务，并连接图片参考。
- 对接兼容的文本、图片和视频模型；未配置模型密钥时可使用 Mock 演示能力。
- 支持素材上传、图片处理、批量选择和下载。
- 默认使用 H2，也支持 MySQL；素材可存储在本地或配置的对象存储中。

## 已接入模型

以下模型已在项目配置中登记。实际可用性取决于对应平台账号权限、API Key 和模型服务开通情况；在环境变量中配置平台密钥后，模型会用于对应的生成能力。

| 平台 | 用途 | 模型（模型 ID） |
| --- | --- | --- |
| 火山方舟 | 文本 | Doubao Seed Evolving（`doubao-seed-evolving`）、Doubao Seed 2.1 Pro（`doubao-seed-2-1-pro-260915`）、Doubao Seed 2.1 Lite（`doubao-seed-2-1-lite-260915`）、DeepSeek V4.1 Flash（`deepseek-v4-1-flash-261017`）、DeepSeek V4.1 Flash Economy（`deepseek-v4-1-flash-economy-261017`）、GLM-5.3 Flash（`glm-5-3-flash-260828`） |
| 火山方舟 | 图片 | Seedream 5.5（`doubao-seedream-5-5-261028`）、Seedream 4.0（`doubao-seedream-4-0-20260415`） |
| 火山方舟 | 视频 | Seedance 2.5（`doubao-seedance-2-5-260628`）、Seedance 2.0（`doubao-seedance-2-0-260128`）、Seedance 2.0 Fast（`doubao-seedance-2-0-fast-260128`） |
| OpenAI | 文本、图片 | GPT-4.1（`gpt-4.1`）、GPT Image 1（`gpt-image-1`） |
| 阿里云百炼 | 文本 | Qwen Plus（`qwen-plus`） |
| Mock 演示 | 文本、图片、视频 | Mock 文本（`mock-text`）、Mock 图片（`mock-image`）、Mock 视频（`mock-video`），无需 API Key，用于演示和本地流程验证 |

## 技术栈

- 后端：Java 17、Spring Boot 3.4、Maven
- 前端：Vue 3、Vite、Vue Flow、Node.js
- 数据库：H2（默认）或 MySQL

## 多用户会员制

当前分支已实现多用户 + 会员积分制。首次启动会自动创建管理员、播种三档会员与积分商品，并把单用户时期的画布、素材、任务全部归属到当前管理员，`workspace.id=1` 继续保留。

| 模块 | 已实现 |
| --- | --- |
| **用户体系** | 开放注册、邮箱验证、找回密码；`USER` / `ADMIN` 角色；管理员可从已有用户提升 |
| **数据隔离** | 业务表新增 `user_id`；`WorkspaceAccess` 统一校验画布归属，跨用户访问统一 404；任务幂等唯一键改为 `(user_id, idem_key)` |
| **画布 / 工作空间** | 每个用户可创建多个 workspace；列表只返回本人画布；CLI/MCP 未指定 `--workspace` 时使用当前用户第一个画布 |
| **积分 / 会员系统** | 免费版 / 专业版 / 工作室版，分别对应每日额度、并发、分辨率与调度权重；积分钱包 + 不可变流水 |
| **计费一致性** | 生成前冻结积分，成功实扣，失败/取消退回；`(user_id, type, biz_id)` 唯一键保证只结算一次；成功生成才计入当日额度 |
| **任务调度与并发** | 个人并发（按会员档位）+ provider/model 平台并发双重闸门；按会员权重优先调度；额度耗尽标记 `QUOTA_EXCEEDED`，余额不足标记 `INSUFFICIENT_BALANCE` |
| **MQ 解耦** | 默认数据库事务 Outbox + 进程内唤醒队列，重启可恢复；`AIGC_MQ_TYPE=rabbitmq` 时转发 RabbitMQ；失败重试超限进入死信，管理后台可重投 |
| **REST API 归属校验** | 所有画布、镜头、图片、节点、提示词、文件接口校验当前用户归属；越权返回 404 |
| **WebSocket 鉴权** | 握手阶段绑定登录用户并校验 workspace 归属，未登录或越权直接拒绝；事件仍按 workspace 广播 |
| **MCP Token 体系** | 每个用户可创建/吊销独立 Token，可设置过期时间，明文仅创建时返回一次；旧的全局 `MCP_API_TOKEN` 仅兼容映射到管理员 |
| **对象存储路径** | 新素材按 `{userId}/images/...`、`{userId}/videos/...` 保存；本地文件访问需登录并校验归属 |
| **CLI 登录态** | 本地按“服务地址 + 用户名”保存多个会话；任意命令可加 `--username` 切换；401/402 输出结构化提示 |
| **前端用户界面** | 我的画布、画布工作台、我的账户、会员与充值、MCP Token、管理后台，画布功能保持不变 |
| **支付接入** | 订单状态机 + 回调验签 + 异步到账；默认 Mock 支付，微信支付 V3 Native 与支付宝电脑网站支付可通过环境变量启用 |
| **审计** | 登录、注册、生成、订单、Token、管理员操作写入 `audit_log`；不记录密码、完整 Token 或支付密钥 |

## 本地开发

需要安装 JDK 17、Maven 和 Node.js。

启动后端：

```sh
cd server
mvn spring-boot:run
```

启动前端（另开一个终端）：

```sh
cd web
npm ci
npm run dev
```

打开 <http://127.0.0.1:5173>。前端开发服务器会把 `/api`、`/files`、`/mcp` 和 `/ws` 请求代理到本机 8080 端口。

默认配置使用本地 H2、Mock 模型、`AIGC_MAIL_TYPE=log`（验证链接打印到日志）、`AIGC_PAYMENT_CHANNEL=mock`（仅本机可模拟支付）与 `AIGC_MQ_TYPE=db`（数据库 Outbox）。首次启动由 Flyway 执行 `db/migration/h2` 下的 V1/V2 迁移并初始化管理员。

本地默认管理员为 `admin / admin@888`，可通过 `AIGC_ADMIN_USERNAME`、`AIGC_ADMIN_PASSWORD`、`AIGC_ADMIN_EMAIL` 覆盖；已存在的用户不会被重启覆盖密码。注册时接口会返回 `devVerifyLink`，打开即可完成邮箱验证。

生产环境需要按需配置：

```sh
# 邮件
AIGC_MAIL_TYPE=smtp
AIGC_SMTP_HOST=smtp.example.com
AIGC_SMTP_PORT=465
AIGC_SMTP_USERNAME=no-reply@example.com
AIGC_SMTP_PASSWORD=***

# 微信支付 V3 Native / 支付宝电脑网站支付
AIGC_PAYMENT_CHANNEL=wechat          # 或 alipay / mock
WECHAT_PAY_APP_ID=*** WECHAT_PAY_MCH_ID=*** WECHAT_PAY_SERIAL_NO=***
WECHAT_PAY_PRIVATE_KEY_PATH=/etc/aigc/apiclient_key.pem
WECHAT_PAY_API_V3_KEY=*** WECHAT_PAY_PLATFORM_CERT_PATH=/etc/aigc/wechatpay_cert.pem
WECHAT_PAY_NOTIFY_URL=https://example.com/api/payments/notify/wechat
ALIPAY_APP_ID=*** ALIPAY_PRIVATE_KEY=*** ALIPAY_PUBLIC_KEY=***
ALIPAY_NOTIFY_URL=https://example.com/api/payments/notify/alipay
ALIPAY_RETURN_URL=https://example.com/account/billing

# 可选 RabbitMQ
AIGC_MQ_TYPE=rabbitmq
AIGC_RABBIT_HOST=127.0.0.1 AIGC_RABBIT_PORT=5672
AIGC_RABBIT_USERNAME=*** AIGC_RABBIT_PASSWORD=***
```

支付回调端点必须使用公网 HTTPS；密钥、证书只通过环境变量或受限权限文件注入。不要将个人凭据或 API Key 提交到仓库。

更多设计说明：[会员制产品设计](docs/product-design-membership.md)、[会员制架构设计](docs/architecture-membership.md)、[会员制 UI 设计稿](docs/ui-mockup-membership.html)；单用户版设计保留在 [产品设计](docs/product-design.md)、[架构文档](docs/architecture.md) 与 [UI 设计稿](docs/ui-mockup.html)。

## MCP

后端 MCP 使用 SSE 传输，默认端点为 `/sse` 和 `/mcp/message`。请求需要通过 `Authorization: Bearer <用户 Token>` 认证：登录网页后在「我的账户 → MCP Token」创建，明文仅在创建时展示一次，可设置过期时间并随时吊销。旧的全局 `MCP_API_TOKEN` 仅作为兼容入口映射到管理员账号，生产环境建议关闭并改用个人 Token。

当前提供 32 个工具，覆盖画布管理、模型与素材查询、分镜推送与自动排版、生成/重试/取消、参考图上传与连线、AI 图片生成、文本与脚本节点。与 CLI 相比，MCP 不需要登录，也不支持读取客户端本地文件路径；本地图片可改用 `upload_image` 的 base64 内容或可访问的图片 URL。

客户端配置示例：

```json
{
  "mcpServers": {
    "aigc-canvas": {
      "type": "sse",
      "url": "http://localhost:8080/sse",
      "headers": { "Authorization": "Bearer <MCP_API_TOKEN>" }
    }
  }
}
```

完整工具列表、参数和推荐工作流见应用内「系统使用说明 → MCP 接入」页面。

## 作者

[Wonder（`wonder`）](https://github.com/Famine-Life)

## 支持项目

如果这个项目对你有帮助，欢迎通过以下方式支持作者继续维护和改进项目。

**微信支付**

[![微信支付赞助码](docs/images/wechat-donate-preview.jpg)](docs/images/wechat-donate.jpg)

**支付宝**

[![支付宝赞助码](docs/images/alipay-donate-preview.jpg)](docs/images/alipay-donate.jpg)



## 许可证

本项目使用 MIT 许可证，允许修改和再发布；再发布时请保留版权和许可证声明。详情见 [LICENSE](LICENSE)。
