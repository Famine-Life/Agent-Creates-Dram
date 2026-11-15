# 火山方舟 API 并发与图片限流说明

更新日期：2026-11-16

本文说明项目同时调用 Seedance 视频生成和 Seedream 图片生成 API 时，官方文档所列的限流维度。以下默认直接通过火山方舟模型 API 调用；若使用推理接入点或专属资源，请以接入点配置和控制台显示额度为准。

## Seedance 视频任务并发

火山方舟将视频生成并发按**同一主账号下的同一模型**统计，模型版本不区分。Seedance 2.0 与 Seedance 2.0 fast 有各自的模型 ID，因此官方文档列出的额度按模型分别适用，不是把两个模型合并成一个账号级的 10 个总额度。

| 模型 / 规格 | 企业账号最大并发任务数 | 个人账号最大并发任务数 |
| --- | ---: | ---: |
| Seedance 2.0，非 4K | 10 | 3 |
| Seedance 2.0 fast，非 4K | 10 | 3 |
| Seedance 2.0，4K | 1 | 1 |
| Seedance 2.0 fast，4K | 1 | 1 |

并发上限指处于处理中的任务数。达到上限后，新建的视频任务会进入队列等待。视频创建还有独立的 RPM 限流：普通规格企业账号 600 次/分钟、个人账号 180 次/分钟；4K 为 15 次/分钟。并发和 RPM 是不同指标，需要分别满足。

## Seedream 图片生成限流

图片生成 API 的主要文档限流指标是 **IPM（每分钟生成图片数）**，按账号下的同一图片模型统计，且不同模型的 IPM 额度可能不同。图片生成限流与 Seedance 视频任务并发是不同维度；官方文档没有说明图片生成任务会占用 Seedance 的视频并发名额，也没有在该 API 文档中给出一个统一的图片任务并发上限。

图片组生成时，请求可能生成多张图片；`max_images` 控制单次请求最多生成数量，但它不是并发数。图层拆分等场景还可能按最大输出张数预扣 IPM，最终再按实际生成数量结算。

## 对项目调度的影响

平台配额与项目自身的任务调度是两层限制。即使账号的模型额度允许多个任务并行，项目后端的提交调度、线程数或用户发起速度也可能降低实际并发；反过来，客户端持续提交任务也不会突破平台模型限额。遇到 429 或任务排队时，应同时查看项目任务状态与方舟控制台的模型限额。

## 官方资料

- [Seedance 2.0 文档：视频模型限流与并发](https://docs.volcengine.com/docs/ark/seedance-2-0?lang=zh)
- [方舟模型列表：Model ID 与模型限流](https://docs.volcengine.com/docs/ark/model-list?lang=zh)
- [图片生成 API：IPM 与图片输出参数](https://docs.volcengine.com/docs/ark/image-generation-api?lang=zh)
- [图片生成教程：IPM 限流说明](https://docs.volcengine.com/docs/ark/image-generation-tutorial?lang=zh)

## 项目本地限流配置

项目调度器会先按模型和清晰度检查本地在途任务数，再调用供应商 API。账号类型由 `AIGC_ACCOUNT_TYPE` 设置，取值 `enterprise` 或 `personal`，默认 `enterprise`。普通视频并发默认分别为 10 / 3，4K 默认 1；可通过 `AIGC_ENTERPRISE_VIDEO_CONCURRENCY`、`AIGC_PERSONAL_VIDEO_CONCURRENCY`、`AIGC_VIDEO_4K_CONCURRENCY` 调整。多实例部署须启用共享 Redis 锁，否则各实例无法共同执行同一份并发计数。

本地待提交任务默认上限为 1000（`AIGC_MAX_QUEUED_TASKS`），图片生成另有 2 个工作线程和 100 个待执行槽位（`AIGC_IMAGE_WORKER_THREADS`、`AIGC_IMAGE_QUEUE_CAPACITY`）。图片队列容量不等于平台 IPM 配额；任务仍可能遇到方舟 IPM 限流。

平台配额会随模型版本、账号状态和接入方式调整。部署时应以控制台当前额度为准。
