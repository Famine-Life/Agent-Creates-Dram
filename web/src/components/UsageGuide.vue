<script setup>
import { computed } from 'vue'
import { state, toast } from '../store/canvas'

const emit = defineEmits(['close'])

const mcpEndpoint = computed(() => state.meta.mcpEndpoint || 'http://localhost:8080/sse')

const mcpConfig = computed(() =>
  JSON.stringify(
    {
      mcpServers: {
        'aigc-canvas': {
          type: 'sse',
          url: mcpEndpoint.value,
          headers: { Authorization: 'Bearer <MCP_API_TOKEN>' }
        }
      }
    },
    null,
    2
  )
)

const loginExample = `node cli/aigc.mjs login --username admin --password '你的密码'`

const cliExamples = `# 查看画布与镜头状态
node cli/aigc.mjs canvas

# 推送单个镜头
node cli/aigc.mjs push --prompt "海边日落，少女奔跑，电影感" --group "A组"

# 批量推送分镜（shots.json 为 JSON 数组）
node cli/aigc.mjs storyboard --file shots.json --group "B组"

# 查看素材 / 上传图片 / 删除节点
node cli/aigc.mjs assets --type video
node cli/aigc.mjs upload --file ./ref.png --x 120 --y 80
node cli/aigc.mjs rm --id 12`

function copy(text) {
  navigator.clipboard?.writeText(text)
  toast('已复制到剪贴板')
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
                <td><code>Backspace</code></td>
                <td>删除选中节点</td>
                <td>镜头、图片、文本、脚本均可删除，关联连线一并清理；分组节点不可删除。</td>
              </tr>
              <tr>
                <td><code>Shift + 拖动</code></td>
                <td>框选多个节点</td>
                <td>在画布空白处拖出选框，批量选中后可配合 Ctrl / ⌘ + D 或 Backspace。</td>
              </tr>
              <tr>
                <td><code>Ctrl / ⌘ + 点击</code></td>
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
                <td>退出提示词全屏编辑，或关闭新建镜头弹窗。</td>
              </tr>
            </tbody>
          </table>
          <p class="muted">
            镜头复制会按分组取下一个序号并重新进入生成队列，不会复用原任务的视频；需要保留原画面时请直接使用原节点。
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
            命令行客户端位于 <code>cli/aigc.mjs</code>，只依赖 Node.js 16+ 内置模块，无需安装依赖，也不需要重新构建。
            在项目根目录执行。
          </p>

          <h3>1. 登录并保存会话</h3>
          <div class="guide-code">
            <button class="btn ghost sm" @click="copy(loginExample)">复制</button>
            <pre>{{ loginExample }}</pre>
          </div>
          <p class="muted">
            登录态（Cookie、CSRF Token、服务地址）保存在 <code>~/.aigc-cli/session.json</code>，后续命令无需再次登录。
            服务不是本机 8080 时，追加 <code>--url http://host:8080</code>，或设置环境变量
            <code>AIGC_BASE_URL</code>。
          </p>

          <h3>2. 常用命令</h3>
          <table class="guide-table">
            <thead>
              <tr><th>命令</th><th>作用</th><th>主要参数</th></tr>
            </thead>
            <tbody>
              <tr><td><code>login</code></td><td>登录并保存会话</td><td>--username、--password、--url</td></tr>
              <tr><td><code>logout</code></td><td>退出并清除本地会话</td><td>—</td></tr>
              <tr><td><code>canvas</code></td><td>画布统计与镜头列表</td><td>--workspace、--json</td></tr>
              <tr><td><code>assets</code></td><td>查看素材</td><td>--type video|ai|upload、--json</td></tr>
              <tr><td><code>push</code></td><td>推送单个镜头</td><td>--prompt、--title、--group、--index、--images</td></tr>
              <tr><td><code>storyboard</code></td><td>批量推送分镜</td><td>--file、--group</td></tr>
              <tr><td><code>upload</code></td><td>上传图片并建节点</td><td>--file、--x、--y</td></tr>
              <tr><td><code>rm / rm-image</code></td><td>删除镜头 / 图片节点</td><td>--id</td></tr>
            </tbody>
          </table>

          <h3>3. 示例</h3>
          <div class="guide-code">
            <button class="btn ghost sm" @click="copy(cliExamples)">复制</button>
            <pre>{{ cliExamples }}</pre>
          </div>
          <p class="muted">
            <code>push</code> 还支持模型与生成参数：<code>--model mock|seedance</code>、<code>--ratio 16:9</code>、
            <code>--resolution 720P</code>、<code>--duration 5</code>；需要原始响应时追加 <code>--json</code>。
          </p>
        </section>

        <section id="guide-faq">
          <h2>常见问题</h2>
          <dl class="guide-faq">
            <dt>CLI 提示「登录已失效」</dt>
            <dd>重新执行 <code>node cli/aigc.mjs login ...</code>；服务端重启或会话过期都会导致旧 Cookie 失效。</dd>

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
  font-size: 11px;
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
  font-size: 12px;
  padding: 6px 10px;
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
  font-size: 18px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
}

.guide-content h3 {
  font-size: 13px;
  margin: 20px 0 8px;
}

.guide-content p {
  font-size: 12px;
  line-height: 1.8;
}

.guide-steps {
  margin: 0;
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 12px;
  line-height: 1.75;
}

.guide-content code {
  padding: 1px 5px;
  border-radius: 5px;
  background: var(--card);
  border: 1px solid var(--border);
  font-size: 11px;
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
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: var(--sunken);
  color: var(--text);
  font-size: 11.5px;
  line-height: 1.7;
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
  font-size: 12px;
}

.guide-table th,
.guide-table td {
  text-align: left;
  padding: 8px 10px;
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
  font-size: 13px;
  margin-top: 14px;
}

.guide-faq dd {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--muted);
  line-height: 1.75;
}

@media (max-width: 860px) {
  .guide-nav {
    display: none;
  }
}
</style>
