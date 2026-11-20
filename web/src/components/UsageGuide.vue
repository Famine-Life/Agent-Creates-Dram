<script setup>
import { computed } from 'vue'
import { state, copyText } from '../store/canvas'

const emit = defineEmits(['close'])

const mcpEndpoint = computed(() => state.meta.mcpEndpoint || 'http://localhost:8080/sse')

const mcpConfig = computed(() =>
  JSON.stringify(
    {
      mcpServers: {
        'aigc-canvas': {
          type: 'sse',
          url: mcpEndpoint.value,
          headers: { Authorization: `Bearer ${state.meta.mcpToken || '<MCP_API_TOKEN>'}` }
        }
      }
    },
    null,
    2
  )
)

const localCliSetup = `# 在项目根目录运行；先确认 Node.js 16+
node --version
node cli/aigc.mjs help

# 本地画布服务默认 http://localhost:8080
node cli/aigc.mjs login --username admin --password '你的密码' --url http://localhost:8080
node cli/aigc.mjs canvas`

const remoteCliSetup = `# 画布服务有公网域名 / IP，且端口可从当前机器访问
node cli/aigc.mjs login --username admin --password '你的密码' --url https://aigc.example.com
node cli/aigc.mjs canvas

# 服务只允许 SSH 访问时：先在本机另开终端建立隧道
ssh -L 18080:127.0.0.1:8080 user@your-server
# 隧道保持运行，在 Codex 项目终端中连接本地转发端口
node cli/aigc.mjs login --username admin --password '你的密码' --url http://127.0.0.1:18080`

const cliExamples = `# 查看画布与镜头状态
node cli/aigc.mjs canvas --json

# 列出、新建、复制画布
node cli/aigc.mjs workspaces --json
node cli/aigc.mjs new-workspace --json
node cli/aigc.mjs copy-workspace --id 1 --json

# 查看模型目录（按类型筛选）
node cli/aigc.mjs models --type video
node cli/aigc.mjs models --type image --json

# 推送单个镜头
node cli/aigc.mjs push --prompt "海边日落，少女奔跑，电影感" --title "海边奔跑" --group "A组" --json

# 批量推送分镜（shots.json 为 JSON 数组）
node cli/aigc.mjs storyboard --file shots.json --group "B组" --json

# 分别查询视频、AI 生成图、上传图
node cli/aigc.mjs assets --type video --json
node cli/aigc.mjs assets --type ai --json
node cli/aigc.mjs assets --type upload --json

# 上传图片并创建画布节点
node cli/aigc.mjs upload --file ./ref.png --workspace 2 --x 120 --y 80 --json

# 把 Agent 生成的图片 URL 下载到服务端素材存储并创建节点
node cli/aigc.mjs upload-url --image-url "https://example.com/generated-image.png" --name "AI生成图" --workspace 2 --json

# 连接参考图并在镜头提示词中写入 @图片N 说明
node cli/aigc.mjs link-image --task-id 12 --image-id 34 --description "女主的正面定妆照，用于参考人物造型" --json

# 切换已有镜头的视频模型，不立即重新生成
node cli/aigc.mjs set-video-model --id 12 --model ark/doubao-seedance-2-0-260128 --json

# 保存图片节点模型，但不提交生成
node cli/aigc.mjs set-image-model --workspace 2 --id 386 --model ark/doubao-seedream-5-5-261028 --json

# 为已有 AI 图片节点选择模型并提交生成
node cli/aigc.mjs generate-image --id 34 --prompt "云层上的未来城市" --model ark/seedream-4-0 --json

# 生成脚本节点时选择文本模型
node cli/aigc.mjs generate-script --id 56 --model ark/doubao-seed-1-6-flash --json

# 按连线 ID 解除图片与镜头的关联
node cli/aigc.mjs unlink-image --id 56 --json

# 用户明确要求删除时才执行
node cli/aigc.mjs delete-workspace --id 2 --json
node cli/aigc.mjs rm --id 12 --json
node cli/aigc.mjs rm-image --id 34 --json`

function copy(text) {
  copyText(text)
}
</script>

<template>
  <div class="guide-page">
    <header class="guide-head">
      <div class="guide-brand">
        <span class="guide-logo">◈</span>
        <div>
          <strong>系统使用说明</strong>
          <span class="muted">MCP 接入 · CLI 命令行 · 画布操作</span>
        </div>
      </div>
      <button class="btn ghost" @click="emit('close')">返回工作台</button>
    </header>

    <main class="guide-body">
      <nav class="guide-nav">
        <a href="#guide-quick">快速开始</a>
        <a href="#guide-keys">画布快捷键</a>
        <a href="#guide-mcp">MCP 接入</a>
        <a href="#guide-cli">CLI 使用</a>
        <a href="#guide-faq">常见问题</a>
      </nav>

      <article class="guide-content">
        <section id="guide-quick">
          <h2>快速开始</h2>
          <ol class="guide-steps">
            <li>用管理员账号登录，进入默认工作空间画布。</li>
            <li>在画布空白处右键，新建提示词、图片、文本或脚本节点；也可从顶部「上传图片」添加参考图。</li>
            <li>把图片右侧的圆点拖到镜头卡片上建立引用，提示词中用 <code>@图片1</code> 引用。</li>
            <li>镜头生成完成后，节点内可直接播放；顶部「资产管理」可集中查看视频、生成图片与上传图片。</li>
            <li>外部 AI 客户端可以通过 MCP 推送提示词，脚本或终端可以使用 CLI。</li>
          </ol>
        </section>

        <section id="guide-keys">
          <h2>画布快捷键</h2>
          <p class="muted">先点击节点选中（可多选），再按下面的快捷键；焦点在输入框、文本域或可编辑区域时不会触发。</p>
          <table class="guide-table">
            <thead>
              <tr><th>快捷键</th><th>作用</th><th>说明</th></tr>
            </thead>
            <tbody>
              <tr>
                <td><code>Ctrl / ⌘ + V</code></td>
                <td>粘贴剪贴板图片</td>
                <td>复制截图或图片后在画布粘贴，自动上传并创建图片节点；优先落在鼠标位置，鼠标不在画布时落在画布中心。</td>
              </tr>
              <tr>
                <td><code>Ctrl / ⌘ + D</code></td>
                <td>复制选中节点</td>
                <td>镜头复制提示词与参数并保留全部图片连线；图片复制提示词、状态与镜头连线；文本 / 脚本复制标题与内容。副本偏移 36px 并自动选中。</td>
              </tr>
              <tr>
                <td><code>Alt + 拖动</code></td>
                <td>拖动复制节点</td>
                <td>在落点生成副本，保留提示词但不复制连线，原节点回到原位。</td>
              </tr>
              <tr>
                <td><code>Ctrl / ⌘ + Z</code></td>
                <td>撤销画布操作</td>
                <td>撤销最近一次节点移动、连线、新建或排列等操作。</td>
              </tr>
              <tr>
                <td><code>Backspace</code></td>
                <td>删除选中节点</td>
                <td>镜头、图片、文本、脚本均可删除，关联连线一并清理；分组节点不可删除。</td>
              </tr>
              <tr>
                <td><code>Backspace</code> 或 <code>Delete</code></td>
                <td>删除选中连线</td>
                <td>未选中节点时，删除当前选中的连线。</td>
              </tr>
              <tr>
                <td><code>拖动空白处</code></td>
                <td>框选多个节点</td>
                <td>框选模式下直接拖出选框；移动模式需按住 Shift。多选后从选框右侧的 ＋ 拖到目标节点批量连线，单击 ＋ 打开目标选择弹窗。</td>
              </tr>
              <tr>
                <td><code>鼠标中键拖动</code></td>
                <td>平移画布</td>
                <td>框选 / 移动模式都可以用中键拖动平移视图。</td>
              </tr>
              <tr>
                <td><code>空格 + 拖动</code></td>
                <td>临时平移画布</td>
                <td>框选模式下按住空格拖动，临时切换为平移。</td>
              </tr>
              <tr>
                <td><code>滚轮</code></td>
                <td>缩放画布</td>
                <td>以鼠标位置为中心缩放，也可使用左下角控件组。</td>
              </tr>
              <tr>
                <td><code>Shift + 点击</code> 或 <code>Ctrl / ⌘ + 点击</code></td>
                <td>追加选择</td>
                <td>逐个点选节点，配合框选进行多选。</td>
              </tr>
              <tr>
                <td><code>Ctrl / ⌘ + Enter</code></td>
                <td>提交提示词</td>
                <td>在镜头提示词框或新建镜头弹窗中直接提交生成。</td>
              </tr>
              <tr>
                <td><code>Esc</code></td>
                <td>退出 / 关闭</td>
                <td>退出提示词全屏编辑，或关闭新建镜头、批量连线弹窗。</td>
              </tr>
            </tbody>
          </table>
          <p class="muted">
            镜头复制会按分组取下一个序号并进入待编辑状态，需要手动点击「生成」才会开始；不会复用原任务的视频。左下角工具按钮可切换框选 / 移动模式。
          </p>
        </section>

        <section id="guide-mcp">
          <h2>MCP 接入</h2>
          <p class="muted">
            MCP 通过 SSE 长连接暴露工具，适合 WorkBuddy、Trae 等 AI 客户端直接推送分镜。服务端地址：
            <code>{{ mcpEndpoint }}</code>
          </p>

          <h3>1. 配置服务端 Token</h3>
          <p class="muted">
            启动服务前设置环境变量 <code>MCP_API_TOKEN</code>（至少 32 位）。该 Token 只用于 MCP，不会下发到网页，
            也不要提交到代码仓库。
          </p>

          <h3>2. 客户端配置</h3>
          <div class="guide-code">
            <button class="btn ghost sm" @click="copy(mcpConfig)">复制</button>
            <pre>{{ mcpConfig }}</pre>
          </div>
          <p class="muted">把占位符替换为实际 Token。若使用远程部署，把 URL 换成对外可访问的地址。</p>

          <h3>3. 可用工具</h3>
          <table class="guide-table">
            <thead>
              <tr><th>工具</th><th>参数</th><th>说明</th></tr>
            </thead>
            <tbody>
              <tr>
                <td><code>push_shot</code></td>
                <td>prompt*、title、groupId、shotIndex、imageUrls、imageIds</td>
                <td>推送单个镜头，可带参考图</td>
              </tr>
              <tr>
                <td><code>push_storyboard</code></td>
                <td>shotsJson*、groupId</td>
                <td>批量推送分镜（JSON 数组字符串）</td>
              </tr>
              <tr>
                <td><code>get_canvas_state</code></td>
                <td>workspaceId</td>
                <td>读取画布节点与生成状态，便于 AI 续写</td>
              </tr>
            </tbody>
          </table>
          <p class="muted">顶部「MCP 接入」按钮也可以查看并复制当前服务地址与配置。</p>
        </section>

        <section id="guide-cli">
          <h2>CLI 使用</h2>
          <p class="muted">
            先分清两台机器：<strong>Codex 执行 CLI 的机器</strong>运行 <code>node cli/aigc.mjs</code>，
            <strong>画布服务机器</strong>运行本项目后端并提供 HTTP(S) 地址。CLI 不需要启动独立服务；
            <code>--url</code> 填画布服务地址（网页/API 的根地址），不是 SSH 地址，也不要加 <code>/sse</code>。
            CLI 使用当前登录账号的 Session + CSRF 访问 REST API，MCP Token 不用于 CLI。
          </p>

          <h3>本地：Codex 和画布服务在同一台机器</h3>
          <ol class="guide-steps">
            <li>在 Codex 打开的项目终端进入项目根目录（能看到 <code>cli/</code> 文件夹），确认安装了 Node.js 16 或更新版本。</li>
            <li>服务默认地址 <code>http://localhost:8080</code>。如果后端端口不同，换成实际地址。</li>
            <li>首次运行登录；之后可以直接执行 <code>canvas</code>、<code>push</code> 等命令。</li>
          </ol>
          <div class="guide-code">
            <button class="btn ghost sm" @click="copy(localCliSetup)">复制</button>
            <pre>{{ localCliSetup }}</pre>
          </div>

          <h3>远程：连接服务器上运行的画布服务</h3>
          <ol class="guide-steps">
            <li>CLI 默认在 Codex 的项目终端执行，因此项目目录需包含 <code>cli/aigc.mjs</code>；若 Codex 本身运行在服务器，就在服务器项目目录执行相同命令。</li>
            <li>确保 Codex 所在机器能访问服务端口。公网部署用域名/IP 和 HTTPS；同一内网可用服务器内网 IP。</li>
            <li>登录命令的 <code>--url</code> 填服务器对外的应用根地址，例如 <code>https://aigc.example.com</code>，不要填浏览器页面路径、<code>/api</code> 或 <code>/sse</code>。</li>
            <li>如果端口不对公网开放但 SSH 可达，先建立本地端口转发，再把 CLI 指向转发端口。</li>
          </ol>
          <div class="guide-code">
            <button class="btn ghost sm" @click="copy(remoteCliSetup)">复制</button>
            <pre>{{ remoteCliSetup }}</pre>
          </div>
          <p class="muted">
            连接成功后，服务地址会保存在本机的 <code>~/.aigc-cli/session.json</code>，后续命令自动复用。
            需要切换另一台服务时，再执行一次带新 <code>--url</code> 的 <code>login</code>。服务器需允许 CLI 所在机器访问，
            且 HTTP(S) 反向代理应将请求转发到应用根路径。
          </p>

          <p class="muted">
            每轮开始操作前，先执行 <code>node cli/aigc.mjs canvas --json</code>，确认服务可访问、登录有效并查看当前画布。
            所有读取和修改命令都加 <code>--json</code>。若提示未登录或会话失效，请由用户自行运行
            <code>node cli/aigc.mjs login</code>；不要在对话中索要或粘贴密码。
          </p>

          <h3>常用命令</h3>
          <table class="guide-table">
            <thead>
              <tr><th>命令</th><th>作用</th><th>主要参数</th></tr>
            </thead>
            <tbody>
              <tr><td><code>login</code></td><td>登录并保存会话</td><td>--username、--password、--url</td></tr>
              <tr><td><code>logout</code></td><td>退出并清除本地会话</td><td>—</td></tr>
              <tr><td><code>workspaces</code></td><td>列出所有画布及版本</td><td>--json</td></tr>
              <tr><td><code>new-workspace</code></td><td>新建空白画布</td><td>--json</td></tr>
              <tr><td><code>copy-workspace</code></td><td>复制指定画布及其内容</td><td>--id 画布ID、--json</td></tr>
              <tr><td><code>delete-workspace</code></td><td>删除画布及其全部内容（至少保留一个画布）</td><td>--id 画布ID、--json</td></tr>
              <tr><td><code>models</code></td><td>查看可用模型和默认模型</td><td>--type video|image|text、--json</td></tr>
              <tr><td><code>canvas</code></td><td>画布统计与镜头列表</td><td>--workspace、--json</td></tr>
              <tr><td><code>assets</code></td><td>查看素材；每次查询一种类型</td><td>--type video 或 ai 或 upload、--json</td></tr>
              <tr><td><code>push</code></td><td>推送单个镜头</td><td>--prompt、--title、--group、--index、--images</td></tr>
              <tr><td><code>set-video-model</code></td><td>切换镜头模型，不重新生成</td><td>--id、--model、可选 --workspace</td></tr>
              <tr><td><code>set-image-model</code></td><td>保存图片节点模型，不提交生成</td><td>--id、--model、--workspace</td></tr>
              <tr><td><code>generate-image</code></td><td>选择图片模型并提交生成</td><td>--id、--prompt、--model、--ratio、--resolution</td></tr>
              <tr><td><code>generate-script</code></td><td>选择文本模型并生成脚本</td><td>--id、--model、可选 --prompt</td></tr>
              <tr><td><code>storyboard</code></td><td>批量推送分镜</td><td>--file、--group</td></tr>
              <tr><td><code>upload</code></td><td>上传本地图片并创建画布图片节点</td><td>--file、--workspace、--x、--y、--json</td></tr>
              <tr><td><code>upload-url</code></td><td>下载图片 URL 并上传到服务端素材存储，创建图片节点</td><td>--image-url、--name、--workspace、--x、--y、--json</td></tr>
              <tr><td><code>link-image</code></td><td>连接图片到镜头并写入 @图片N 说明</td><td>--task-id、--image-id、--description（可选）</td></tr>
              <tr><td><code>unlink-image</code></td><td>解除图片与镜头之间的连线</td><td>--id 连线ID</td></tr>
              <tr><td><code>rm / rm-image</code></td><td>删除镜头 / 图片节点</td><td>--id</td></tr>
            </tbody>
          </table>

          <h3>命令示例</h3>
          <div class="guide-code">
            <button class="btn ghost sm" @click="copy(cliExamples)">复制</button>
            <pre>{{ cliExamples }}</pre>
          </div>
          <p class="muted">
            <code>push</code> 还支持模型与生成参数：<code>--model 平台/模型ID</code>、<code>--ratio 16:9</code>、
            <code>--resolution 720P</code>、<code>--duration 5</code>；需要原始响应时追加 <code>--json</code>。
          </p>
          <p class="muted">
            镜头节点可用 <code>set-video-model</code> 仅切换模型并保存，不会重新生成；若要改提示词并重新提交，可用
            <code>edit-prompt --id &lt;任务ID&gt; --prompt "..." --model &lt;平台/模型ID&gt; --json</code>。
            AI 图片节点可通过 <code>set-image-model --workspace &lt;画布ID&gt; --id &lt;图片ID&gt; --model &lt;平台/模型ID&gt;</code> 保存模型选择而不生成；界面也会记住此选择。
            之后生成时会使用节点已保存的模型，也可通过 <code>generate-image --model</code> 临时指定其他模型。
            脚本节点可在 <code>generate-script</code> 时传入 <code>--model</code>。无需手动翻配置：运行
            <code>node cli/aigc.mjs models</code> 可查看视频、图片、文本模型和默认项；使用
            <code>models --type video|image|text</code> 筛选，输出中的模型 ID 可直接传给相应命令。
          </p>
          <p class="muted">
            分镜文件支持 JSON 镜头数组，或 <code>{ "shots": [...] }</code> 格式；镜头可填写提示词、标题、分组、序号、参考图片 ID/URL、模型和生成参数。
            Agent 生成了可访问的图片 URL 时，使用 <code>upload-url --image-url &lt;图片URL&gt;</code>：CLI 会下载图片并上传到画布服务的素材存储，再创建图片节点（单张最大 50 MB，支持 HTTP/HTTPS 和有限重定向）。
            这与 <code>create-image --url &lt;地址&gt;</code> 不同：后者只在节点里保存外部图片地址，不会把文件导入服务端。
            已有节点可用 <code>link-image --task-id &lt;镜头任务ID&gt; --image-id &lt;图片ID&gt; --description "图片内容和用途" --json</code> 连接。
            命令会按镜头中的参考图顺序保存 <code>@图片N：说明</code>。省略说明时，使用图片的生成提示词或名称。
            也可在推送时通过 <code>push --images &lt;图片ID,...&gt;</code> 一并关联。
            若需移除连线，先从画布 JSON 中确认连线 ID，再用 <code>unlink-image --id &lt;连线ID&gt; --json</code>。
            推送后记下任务 ID，每隔 5–10 秒运行 <code>canvas --json</code> 查询，直到任务完成或失败；不要仅凭创建成功就报告生成完成。
            画布复制会复制画布内容并创建新画布；画布删除会移除该画布及其内容，系统至少保留一个画布。
            除非用户明确要求删除指定画布、镜头或图片，否则不要执行 <code>delete-workspace</code>、<code>rm</code> 或 <code>rm-image</code>。
            汇报只列任务 ID、标题、状态和失败原因，不要粘贴完整 JSON。
          </p>
        </section>

        <section id="guide-faq">
          <h2>常见问题</h2>
          <dl class="guide-faq">
            <dt>CLI 提示「登录已失效」</dt>
            <dd>请用户自行执行 <code>node cli/aigc.mjs login</code>；服务端重启或会话过期都会导致旧会话失效。不要询问或代填密码。</dd>

            <dt>CLI 返回 403</dt>
            <dd>通常是 CSRF Token 过期。CLI 会在每次写操作前自动刷新，如仍失败，退出后重新登录。</dd>

            <dt>MCP 连接失败</dt>
            <dd>确认服务端已设置 <code>MCP_API_TOKEN</code>，且客户端请求头为 <code>Authorization: Bearer &lt;token&gt;</code>。</dd>

            <dt>生成的视频打不开</dt>
            <dd>确认任务状态为「已完成」，并使用资产管理或节点内的播放按钮；私有存储会返回短期签名地址。</dd>

            <dt>公网部署要注意什么</dt>
            <dd>启用 HTTPS 并设置 <code>AIGC_COOKIE_SECURE=true</code>，为 MCP 使用独立强 Token，按需配置 <code>AIGC_ALLOWED_ORIGINS</code>。</dd>
          </dl>
        </section>
      </article>
    </main>
  </div>
</template>

<style scoped>
.guide-page {
  position: fixed;
  inset: 0;
  z-index: 55;
  display: flex;
  flex-direction: column;
  background: var(--bg);
  color: var(--text);
}

.guide-head {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 24px;
  border-bottom: 1px solid var(--border);
  background: var(--panel);
}

.guide-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.guide-brand div {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.guide-brand strong {
  font-size: 14px;
}

.guide-brand span {
  font-size: 12px;
}

.guide-logo {
  font-size: 20px;
  color: var(--accent-text);
}

.guide-body {
  flex: 1;
  display: flex;
  gap: 32px;
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 28px 24px 64px;
  overflow-y: auto;
}

.guide-nav {
  position: sticky;
  top: 0;
  align-self: flex-start;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 132px;
  padding-top: 4px;
}

.guide-nav a {
  color: var(--muted);
  text-decoration: none;
  font-size: 13px;
  padding: 7px 10px;
  border-radius: 8px;
  border: 1px solid transparent;
}

.guide-nav a:hover {
  color: var(--text);
  background: var(--card);
  border-color: var(--border);
}

.guide-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.guide-content section {
  scroll-margin-top: 12px;
}

.guide-content h2 {
  font-size: 21px;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border);
}

.guide-content h3 {
  font-size: 16px;
  margin: 22px 0 10px;
}

.guide-content p {
  font-size: 14px;
  line-height: 1.9;
}

.guide-steps {
  margin: 0;
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 14px;
  line-height: 1.85;
}

.guide-content code {
  padding: 1px 5px;
  border-radius: 5px;
  background: var(--card);
  border: 1px solid var(--border);
  font-size: 13px;
}

.guide-code {
  position: relative;
  margin: 8px 0;
}

.guide-code .btn {
  position: absolute;
  top: 8px;
  right: 8px;
}

.guide-code pre {
  margin: 0;
  padding: 16px 18px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: var(--sunken);
  color: var(--text);
  font-size: 13px;
  line-height: 1.8;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.guide-code pre code,
.guide-code code {
  border: 0;
  background: transparent;
  padding: 0;
}

.guide-table {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 14px;
}

.guide-table th,
.guide-table td {
  text-align: left;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
  vertical-align: top;
  line-height: 1.6;
}

.guide-table th {
  color: var(--muted);
  font-weight: 500;
  background: var(--panel);
}

.guide-faq dt {
  font-size: 15px;
  margin-top: 16px;
}

.guide-faq dd {
  margin: 6px 0 0;
  font-size: 14px;
  color: var(--muted);
  line-height: 1.85;
}

@media (max-width: 860px) {
  .guide-nav {
    display: none;
  }
}
</style>
