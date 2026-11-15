<script setup>
import { computed, ref } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { state, removeImage, generateAiImage, toast } from '../store/canvas'
import PromptEditor from './PromptEditor.vue'

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false }
})

const saving = ref(false)
const previewing = ref(false)
const model = ref('')
const image = computed(() => props.data.image)
/** 编辑框中的实时文本；图生图直接用这里的最新提示词，未编辑时即已保存的提示词 */
const draftPrompt = ref(image.value.prompt || '')
const running = computed(() => image.value.status === 'RUNNING')
const queued = computed(() => image.value.status === 'QUEUED')
const failed = computed(() => image.value.status === 'FAILED')
const ready = computed(() => image.value.status === 'SUCCEEDED' && !!image.value.url)
const modelOptions = computed(() => state.meta.imageModels || [])
const activeModel = computed(() => model.value || state.meta.defaults?.image || '')
const editorVisible = computed(
  () => props.selected || state.selectedImageId === image.value.id
)

const RATIO_OPTIONS = ['智能', '1:1', '3:4', '4:3', '16:9', '9:16', '2:3', '3:2', '21:9']
const RESOLUTION_OPTIONS = ['1K', '1.5K', '2K']
const COUNT_OPTIONS = [1, 2, 3, 4, 5, 6, 7, 8, 9]

const settings = computed(
  () => state.imageSettings[image.value.id] || { ratio: '16:9', resolution: '1K', count: 1 }
)

function setSetting(key, value) {
  state.imageSettings = {
    ...state.imageSettings,
    [image.value.id]: { ...settings.value, [key]: value }
  }
}

const downloadName = computed(() => {
  const name = image.value.name || 'ai-image'
  const match = /\.([a-z0-9]+)$/i.exec(image.value.url || '')
  return match ? `${name}.${match[1]}` : `${name}.png`
})

function select() {
  state.selectedImageId = image.value.id
  state.selectedTaskId = null
  state.selectedNodeId = null
  state.inspectorEdit = false
}

async function onGenerate(text) {
  saving.value = true
  try {
    await generateAiImage(image.value.id, text, activeModel.value, undefined, settings.value)
    toast(`已提交图片生成 · ${settings.value.count} 张`)
  } catch (e) {
    toast(`生成失败：${e.message}`)
  } finally {
    saving.value = false
  }
}

/** 以当前图片为参考重新生成（图生图） */
async function onRestyle(e) {
  e.stopPropagation()
  if (!ready.value || saving.value) return
  const prompt = (draftPrompt.value || '').trim()
  if (!prompt) {
    toast('请先在下方填写提示词')
    return
  }
  if (!window.confirm('将基于当前图片重新生成，并替换这张图。继续？')) return
  saving.value = true
  try {
    await generateAiImage(image.value.id, prompt, activeModel.value, image.value.url, settings.value)
    toast('已提交图生图任务')
  } catch (err) {
    toast(`图生图失败：${err.message}`)
  } finally {
    saving.value = false
  }
}

async function onDelete(e) {
  e.stopPropagation()
  if (!window.confirm(`删除《${image.value.name}》？它的连线会一起移除。`)) return
  try {
    await removeImage(image.value.id)
    toast('图片节点已删除')
  } catch (err) {
    toast(`删除失败：${err.message}`)
  }
}
</script>

<template>
  <div class="image-gen-wrap" @click="select">
    <div class="image-gen-node" :class="{ selected }">
      <header class="node-head">
        <span class="node-kind">图片</span>
        <span class="node-title">{{ image.name }}</span>
        <span v-if="queued" class="pill tone-idle">等待本地资源</span>
        <span v-else-if="running" class="pill tone-busy">生成中</span>
        <span v-else-if="failed" class="pill tone-error">失败</span>
        <span v-else-if="ready" class="pill tone-done">已完成</span>
        <button class="shot-del" title="删除图片" @click="onDelete">×</button>
      </header>

      <div class="node-media">
        <img
          v-if="ready"
          :src="image.url"
          :alt="image.name"
          draggable="false"
          title="点击放大预览"
          @click.stop="previewing = true"
        />
        <div v-else class="node-placeholder">
          <div v-if="running || queued" class="ring" :style="{ '--value': 66 }"><span>AI</span></div>
          <template v-else-if="failed">
            <div class="err-title">生成失败</div>
            <div class="err-msg">{{ image.errorMsg || '请调整提示词后重试' }}</div>
          </template>
          <template v-else>
            <span class="node-glyph">✦</span>
            <div class="stage">填写下方提示词，生成图片</div>
          </template>
        </div>

        <div v-if="ready" class="media-actions always">
          <button class="media-btn" title="放大预览" @click.stop="previewing = true">⤢</button>
          <a class="media-btn" :href="image.url" :download="downloadName" title="下载原图" @click.stop>↓</a>
          <button
            class="media-btn"
            title="以当前图为参考重新生成（图生图）"
            :disabled="saving || running || queued"
            @click="onRestyle"
          >↻</button>
        </div>
      </div>

      <Handle type="target" :position="Position.Left" class="image-in-port" />
      <Handle type="source" :position="Position.Right" class="image-port" />
    </div>

    <PromptEditor
      v-if="editorVisible"
      :prompt="image.prompt"
      :images="[]"
      :all-images="state.images"
      :model="activeModel"
      :model-value="activeModel"
      :model-options="modelOptions"
      :show-settings="true"
      :ratio="settings.ratio"
      :ratio-options="RATIO_OPTIONS"
      :resolution="settings.resolution"
      :resolution-options="RESOLUTION_OPTIONS"
      :count="settings.count"
      :count-options="COUNT_OPTIONS"
      :busy="saving || running || queued"
      :show-refs="false"
      submit-label="生成图片"
      placeholder="描述你想要的画面，例如：雨夜霓虹街头，电影感，浅景深…"
      @update:model-value="model = $event"
      @update:prompt="draftPrompt = $event"
      @update:ratio="setSetting('ratio', $event)"
      @update:resolution="setSetting('resolution', $event)"
      @update:count="setSetting('count', $event)"
      @submit="onGenerate"
    />

    <Teleport to="body">
      <div v-if="previewing" class="image-preview" @click="previewing = false">
        <img :src="image.url" :alt="image.name" />
        <span class="image-preview-hint">点击任意位置关闭</span>
      </div>
    </Teleport>
  </div>
</template>
