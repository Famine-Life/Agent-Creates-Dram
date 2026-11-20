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

const toolGroups = [
  {
    title: '画布与查询',
    tools: [
      { name: 'get_canvas_state', desc: '读取画布节点与生成状态' },
      { name: 'list_workspaces', desc: '列出画布与版本' },
      { name: 'create_workspace', desc: '新建空白画布' },
      { name: 'rename_workspace', desc: '重命名画布' },
      { name: 'copy_workspace', desc: '复制画布' },
      { name: 'delete_workspace', desc: '删除画布（需用户确认）' },
      { name: 'list_models', desc: '查看可用视频 / 图片 / 文本模型' },
      { name: 'list_assets', desc: '查看成片与图片素材' },
      { name: 'list_prompt_records', desc: '查看提示词历史' },
      { name: 'optimize_prompt', desc: '优化提示词' },
      { name: 'save_canvas_snapshot', desc: '保存画布快照' },
      { name: 'save_shot_order', desc: '保存镜头分组与排序' }
    ]
  },
  {
    title: '镜头',
    tools: [
      { name: 'push_shot', desc: '创建单个镜头并提交生成' },
      { name: 'push_storyboard', desc: '批量分镜并自动排版' },
      { name: 'update_shot_prompt', desc: '改提示词与参数，可重新生成' },
      { name: 'retry_shot', desc: '重新生成镜头' },
      { name: 'cancel_shot', desc: '取消生成' },
      { name: 'set_video_model', desc: '切换视频模型（不生成）' },
      { name: 'delete_shot', desc: '删除镜头（需用户确认）' }
    ]
  },
  {
    title: '图片',
    tools: [
      { name: 'upload_image', desc: '上传参考图（URL 或 base64）' },
      { name: 'create_image_node', desc: '创建外部图片节点' },
      { name: 'create_ai_image_node', desc: '创建 AI 图片节点' },
      { name: 'generate_image', desc: '提交 AI 图片生成' },
      { name: 'update_image_node', desc: '修改图片节点' },
      { name: 'set_image_model', desc: '切换图片模型（不生成）' },
      { name: 'link_image', desc: '连线并写入 @图片N 说明' },
      { name: 'unlink_image', desc: '删除图片连线' },
      { name: 'delete_image_node', desc: '删除图片节点（需用户确认）' }
    ]
  },
  {
    title: '文本与脚本',
    tools: [
      { name: 'create_canvas_node', desc: '创建文本 / 脚本节点' },
      { name: 'update_canvas_node', desc: '修改文本 / 脚本节点' },
      { name: 'delete_canvas_node', desc: '删除节点（需用户确认）' },
      { name: 'generate_script', desc: '生成分镜脚本' }
    ]
  }
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
          <div v-for="group in toolGroups" :key="group.title" class="tool-group">
            <h4 class="tool-group-title">{{ group.title }}</h4>
            <ul class="tool-list">
              <li v-for="tool in group.tools" :key="tool.name">
                <code>{{ tool.name }}</code>
                <span class="muted">{{ tool.desc }}</span>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
