<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { state, removeImage, generateAiImage, saveImagePrompt, addLink, removeLink, uploadImageFile, toast } from '../store/canvas'
import PromptEditor from './PromptEditor.vue'
import ImageToolsDialog from './ImageToolsDialog.vue'

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false }
})

const saving = ref(false)
const previewing = ref(false)
const toolsOpen = ref(false)
const image = computed(() => props.data.image)
/** 编辑框中的实时文本；图生图直接用这里的最新提示词，未编辑时即已保存的提示词 */
const draftPrompt = ref(image.value.prompt || '')
const running = computed(() => image.value.status === 'RUNNING')
const queued = computed(() => image.value.status === 'QUEUED')
const failed = computed(() => image.value.status === 'FAILED')
const ready = computed(() => image.value.status === 'SUCCEEDED' && !!image.value.url)
const modelOptions = computed(() => state.meta.imageModels || [])
const editorVisible = computed(
  () => props.selected || state.selectedImageId === image.value.id
)

// 提示词只保存在输入框里会丢，输入时防抖保存到服务端，关闭编辑框时立即保存
let promptTimer = null

function flushPrompt() {
  if (!promptTimer) return
  clearTimeout(promptTimer)
  promptTimer = null
  saveImagePrompt(image.value.id, draftPrompt.value).catch((err) => toast(`提示词保存失败：${err.message}`))
}

function onPromptChange(text) {
  draftPrompt.value = text
  clearTimeout(promptTimer)
  promptTimer = setTimeout(flushPrompt, 600)
}

watch(editorVisible, (visible) => {
  if (!visible) flushPrompt()
})

onBeforeUnmount(flushPrompt)

const RATIO_OPTIONS = ['智能', '1:1', '3:4', '4:3', '16:9', '9:16', '2:3', '3:2', '21:9']
const RESOLUTION_OPTIONS = ['1K', '1.5K', '2K']
const COUNT_OPTIONS = [1, 2, 3, 4, 5, 6, 7, 8, 9]

const settings = computed(
  () => state.imageSettings[image.value.id] || { ratio: '16:9', resolution: '1K', count: 1 }
)
const activeModel = computed(() => settings.value.model || state.meta.defaults?.image || '')

/** 与当前图片节点相连的图片，生成时作为图生图参考 */
const linkedImages = computed(() =>
  state.links
    .filter((link) => link.target === `image-${image.value.id}` && link.source.startsWith('image-'))
    .map((link) => {
      const linked = state.images.find((item) => `image-${item.id}` === link.source)
      return linked ? { ...linked, linkId: link.id } : null
    })
    .filter(Boolean)
)

/** 提示词里的 @图片N 按编号取对应连线图；没有 @ 引用时把全部连线图按顺序作为参考 */
function resolveReferenceUrls(text) {
  const numbers = [...new Set([...(text || '').matchAll(/@图片(\d+)/g)].map((match) => Number(match[1])))]
  const picked = numbers.length
    ? numbers.map((number) => linkedImages.value[number - 1])
    : linkedImages.value
  const urls = []
  for (const item of picked) {
    if (item?.url && !urls.includes(item.url)) urls.push(item.url)
  }
  return urls
}

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
    await generateAiImage(image.value.id, text, activeModel.value, resolveReferenceUrls(text), settings.value)
    toast(`已提交图片生成 · ${settings.value.count} 张`)
  } catch (e) {
    toast(`生成失败：${e.message}`)
  } finally {
    saving.value = false
  }
}

function onLinkImage(source) {
  if (source.id === image.value.id) return
  addLink(`image-${source.id}`, `image-${image.value.id}`)
}

function onUnlinkImage(linked) {
  if (linked.linkId) removeLink(linked.linkId)
}

async function onUpload(file) {
  try {
    const uploaded = await uploadImageFile(file)
    addLink(`image-${uploaded.id}`, `image-${image.value.id}`)
  } catch (e) {
    toast(`上传失败：${e.message}`)
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
    await generateAiImage(image.value.id, prompt, activeModel.value, [image.value.url], settings.value)
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
        <span
          v-if="linkedImages.length"
          class="pill tone-idle"
          :title="`图生图参考：${linkedImages.map((item) => item.name).join('、')}`"
        >参考图 {{ linkedImages.length }}</span>
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
          loading="lazy"
          decoding="async"
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
          <button class="media-btn" title="裁剪或九宫格切分" @click.stop="toolsOpen = true">✂</button>
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
      :prompt="draftPrompt"
      :images="linkedImages"
      :all-images="linkedImages"
      empty-images-text="暂无已连线图片，请先将图片连接到当前图片节点"
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
      submit-label="生成图片"
      placeholder="描述你想要的画面，例如：雨夜霓虹街头，电影感，浅景深…输入 @ 引用已连线图片"
      @link="onLinkImage"
      @unlink="onUnlinkImage"
      @upload="onUpload"
      @update:model-value="setSetting('model', $event)"
      @update:prompt="onPromptChange"
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
    <ImageToolsDialog v-if="toolsOpen && ready" :image="image" @close="toolsOpen = false" />
  </div>
</template>
