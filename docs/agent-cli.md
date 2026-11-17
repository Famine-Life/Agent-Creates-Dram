# Agent CLI：自动化制作视频与漫剧

项目 CLI 可供 Codex、WorkBuddy 及其他能运行本地命令的 Agent 使用。它直接调用画布服务的 REST API，不依赖 MCP，也不需要额外安装 npm 包。读写画布数据时使用 `--json`，Agent 可以稳定解析返回结果并继续后续步骤。

## 前置条件

1. 启动画布后端，并确认服务地址可访问（默认 `http://localhost:8080`）。
2. 在本机建立 CLI 登录会话。按提示替换占位符，在本地终端执行；不要把密码放进 Agent 指令或项目文件：

   ```sh
   node cli/aigc.mjs login --username "你的账号" --password "你的密码" --url http://localhost:8080
   ```

3. 开始新一轮任务时先检查画布和模型：

   ```sh
   node cli/aigc.mjs canvas --json
   node cli/aigc.mjs models --json
   ```

## 漫剧到分镜视频的工作流

Agent 可按以下步骤自动完成单集或短片的制作：

1. 将故事梗概扩写成连续分镜，确定角色、场景、镜头顺序和每镜头提示词。
2. 用 `create-node --type script` 创建脚本节点，按需调用 `generate-script` 生成或整理脚本。
3. 用 `upload` 导入角色/场景参考图，或用 `create-ai-image` 与 `generate-image` 生成图片参考。记录返回的图片 ID。
4. 根据剧本生成分镜 JSON，为每镜填写提示词、顺序、模型参数和参考图片 ID。
5. 用 `storyboard` 批量创建镜头任务；然后用 `canvas --json` 按任务 ID 轮询，直到任务进入完成或失败等终态。
6. 失败时从 JSON 状态读取错误信息，调整提示词或素材后重试。

例如，把下列内容保存为 `shots.json`。将 `imageIds` 和 `model` 替换成前面步骤返回的有效图片 ID 与 `models --json` 输出中的模型 ID：

```json
{
  "groupId": "漫剧-第1集",
  "shots": [
    {
      "title": "雨夜相遇",
      "shotIndex": 1,
      "prompt": "中景，雨夜的旧街巷，少女撑伞停下脚步，远处暖色灯笼轻轻摇曳，电影感构图，缓慢推进镜头。",
      "imageIds": [123],
      "model": "ark/doubao-seedance-2-0-fast-260128",
      "ratio": "16:9",
      "resolution": "720P",
      "duration": 5
    },
    {
      "title": "抬眸",
      "shotIndex": 2,
      "prompt": "面部近景，少女抬眸望向画面外，雨滴沿伞缘落下，神情由疑惑转为惊讶，浅景深，镜头轻微推近。",
      "imageIds": [123],
      "model": "ark/doubao-seedance-2-0-fast-260128",
      "ratio": "16:9",
      "resolution": "720P",
      "duration": 5
    }
  ]
}
```

批量提交并查询状态：

```sh
node cli/aigc.mjs storyboard --file shots.json --workspace 1 --json
node cli/aigc.mjs canvas --workspace 1 --json
```

任务可能需要排队。Agent 应保留提交结果里的任务 ID，每隔 5–10 秒查询画布状态；确认每个目标任务进入终态后再汇报，并为失败任务说明失败原因。完整 CLI 命令和字段请运行 `node cli/aigc.mjs help` 查看。

## 能力边界

CLI 可创建和管理脚本、图片、参考连线与镜头生成任务，并查询生成结果。它负责分镜级素材生成和任务编排；视频剪辑、转场、配音及整集合成不属于当前 CLI 的功能范围。
