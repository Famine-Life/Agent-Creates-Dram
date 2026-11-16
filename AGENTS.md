# AIGC 画布 CLI 使用规则

## 连接与认证

- 使用项目 CLI `node cli/aigc.mjs` 操作画布；此工作流不使用 MCP。
- 所有命令从项目根目录执行，并在读取或修改数据时添加 `--json`，以便稳定解析结果。
- 首次操作或开始新一轮操作前，先运行 `node cli/aigc.mjs canvas --json`，确认服务可达、登录态有效，并检查现有画布内容。
- CLI 使用当前机器保存的登录会话。若命令提示未登录或登录失效，直接向用户提供以下登录命令，让用户自行将占位文字替换为真实账号密码并在本地终端执行：

  ```sh
  node cli/aigc.mjs login --username "你的账号" --password "你的密码" --url http://localhost:8080
  ```

  不得向用户索要真实密码、代填凭据或自行执行登录。若用户连接的不是本机服务，可将 `--url` 替换为实际的画布服务根地址。URL 使用正常的 `http://` 或 `https://` 格式，不要在冒号前添加反斜杠。
- 连接本地或远程服务时，可按需在 `login` 命令中指定 `--url <画布服务根地址>`。不要把 MCP 的 `/sse` 地址或 SSH 地址当作 CLI 服务地址。

## 命令

画布管理：

```sh
node cli/aigc.mjs workspaces --json
node cli/aigc.mjs new-workspace --json
node cli/aigc.mjs copy-workspace --id <画布ID> --json
node cli/aigc.mjs delete-workspace --id <画布ID> --json
```

删除画布会删除其全部内容，系统至少保留一个画布。复制画布会创建一张包含原画布内容的新画布。

查询画布：

```sh
node cli/aigc.mjs canvas --json
```

查询素材时，`--type` 每次选择一个类型：

```sh
node cli/aigc.mjs assets --type video --json
node cli/aigc.mjs assets --type ai --json
node cli/aigc.mjs assets --type upload --json
```

选择或切换节点模型：

```sh
# 查看当前可用模型与默认项；可用 --type video|image|text 筛选
node cli/aigc.mjs models --json

# 只切换已有镜头模型并保存，不触发重新生成
node cli/aigc.mjs set-video-model --id <任务ID> --model <平台/模型ID> --json

# 保存图片节点模型，不触发生成
node cli/aigc.mjs set-image-model --workspace <画布ID> --id <图片ID> --model <平台/模型ID> --json

# 生成 AI 图片时也可临时指定不同模型
node cli/aigc.mjs generate-image --id <图片ID> --prompt "<提示词>" --model <平台/模型ID> --json

# 生成脚本时选择文本模型
node cli/aigc.mjs generate-script --id <脚本节点ID> --model <平台/模型ID> --json
```

从 `models` 输出中选择模型 ID，并原样传给 `--model`，无需手工查配置。`set-image-model` 会把图片模型选择保存到当前画布快照，后续在界面生成时也会使用该模型；命令本身不会提交生成。修改镜头提示词时，`edit-prompt` 会按用户意图更新模型和提示词并重新提交生成；若只切换镜头模型，使用 `set-video-model`。

推送单个镜头：

```sh
node cli/aigc.mjs push --prompt "<提示词>" --title "<标题>" --group "<分组>" --json
```

批量推送分镜：

```sh
node cli/aigc.mjs storyboard --file <shots.json> --group "<分组>" --json
```

`shots.json` 可以是镜头对象数组，也可以是包含 `shots` 数组的对象。每个镜头可包含 `prompt`、`title`、`groupId`、`shotIndex`、`imageIds`、`imageUrls`、`model`、`ratio`、`resolution` 和 `duration`。

上传图片：

```sh
node cli/aigc.mjs upload --file <图片路径> --json
node cli/aigc.mjs upload-url --image-url <图片URL> --name "<节点名称>" --workspace <画布ID> --json
```

`upload` 会把本地图片传到当前服务；`upload-url` 会下载 Agent 提供的 HTTP/HTTPS 图片 URL，再上传到服务端素材存储。两者都会在画布创建图片节点；`--workspace <画布ID>` 指定目标画布，省略时默认画布 1。`create-image --url` 只保存外部 URL，不会导入图片文件。

删除镜头或图片节点：

```sh
node cli/aigc.mjs rm --id <任务ID> --json
node cli/aigc.mjs rm-image --id <图片ID> --json
```

## 任务跟进

- 推送会创建生成任务。记录返回的任务 ID 和标题，然后每隔 5–10 秒运行 `node cli/aigc.mjs canvas --json`，按任务 ID 检查状态。
- 对仍在排队或生成中的任务继续轮询；到达 `SUCCEEDED`、`FAILED`、`TIMEOUT` 或 `CANCELED` 等终态后停止。失败时读取该任务的错误信息作为失败原因。
- 不要只根据推送命令的返回结果宣称生成完成。

## 删除与汇报

- 除非用户明确要求删除指定画布、镜头或图片，否则不得执行 `delete-workspace`、`rm` 或 `rm-image`。用户明确授权删除后，先用画布列表或画布状态核对 ID 与对象类型，再执行对应命令。
- 汇报任务结果时，只提供任务 ID、标题、状态和失败原因（如有）；不要粘贴完整 JSON、会话内容、Cookie、Token 或其他凭据。
