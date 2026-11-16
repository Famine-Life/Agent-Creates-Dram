<script setup>
import { computed } from 'vue'
import { state, copyText } from '../store/canvas'

const emit = defineEmits(['close'])

const endpoint = computed(() => state.meta.mcpEndpoint || 'http://localhost:8080/sse')

const config = computed(() =>
  JSON.stringify(
    {
      mcpServers: {
        'aigc-canvas': {
          type: 'sse',
          url: endpoint.value,
          headers: {
            Authorization: `Bearer ${state.meta.mcpToken || '<MCP_API_TOKEN>'}`
          }
        }
      }
    },
    null,
    2
  )
)

const tools = [
  { name: 'push_shot', desc: '推送单个镜头：prompt / title / groupId / shotIndex / imageUrls / imageIds' },
  { name: 'push_storyboard', desc: '批量推送分镜：shotsJson 为 JSON 数组字符串' },
  { name: 'get_canvas_state', desc: '读取画布节点与生成状态，便于 AI 续写' }
]

function copy(text) {
  copyText(text)
}
</script>

<template>
  <div class="overlay" @click.self="emit('close')">
    <div class="dialog">
      <header class="dialog-head">
        <div>
          <h2>MCP 接入</h2>
          <p class="muted">在 WorkBuddy / Trae 里添加下面的 MCP 服务器即可推送提示词</p>
        </div>
        <button class="icon-btn" @click="emit('close')">×</button>
      </header>

      <div class="dialog-body">
        <div class="field">
          <span class="field-label">服务端点</span>
          <div class="code-row">
            <code>{{ endpoint }}</code>
            <button class="btn ghost sm" @click="copy(endpoint)">复制</button>
          </div>
        </div>

        <div class="field">
          <span class="field-label">客户端配置</span>
          <pre class="code-block">{{ config }}</pre>
          <p class="muted">将占位符替换为服务端环境变量 MCP_API_TOKEN 的值。Token 不会下发到网页，请勿提交到代码仓库。</p>
          <button class="btn ghost sm" @click="copy(config)">复制配置</button>
        </div>

        <div class="field">
          <span class="field-label">可用工具</span>
          <ul class="tool-list">
            <li v-for="tool in tools" :key="tool.name">
              <code>{{ tool.name }}</code>
              <span class="muted">{{ tool.desc }}</span>
            </li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>
