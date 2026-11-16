<script setup>
import { computed, ref } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { state, removeImage, replaceImageFromAsset, replaceImageFromFile, toast } from '../store/canvas'
import ImageToolsDialog from './ImageToolsDialog.vue'

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false }
})

const image = computed(() => props.data.image)
const toolsOpen = ref(false)
const previewing = ref(false)
const dimensions = ref('')
const imageRatio = ref(1)
const nodeWidth = ref(148)
const replaceOpen = ref(false)
const replacing = ref(false)
const replaceMode = ref('')
const fileInput = ref(null)
const replacementAssets = computed(() => state.images.filter((item) => item.id !== image.value.id && item.url))

function readDimensions(event) {
  const { naturalWidth, naturalHeight } = event.currentTarget
  dimensions.value = naturalWidth && naturalHeight ? `${naturalWidth} × ${naturalHeight}` : ''
  if (naturalWidth && naturalHeight) {
    imageRatio.value = naturalWidth / naturalHeight
    const scale = Math.min(220 / naturalWidth, 180 / naturalHeight)
    nodeWidth.value = Math.round(naturalWidth * scale)
  }
}

function select() {
  state.selectedImageId = image.value.id
  state.selectedTaskId = null
  state.inspectorEdit = false
}

async function onDelete(e) {
  e.stopPropagation()
  if (!window.confirm(`删除参考图《${image.value.name}》？它的连线会一起移除，已生成的成片不受影响。`)) return
  try {
    await removeImage(image.value.id)
    toast('参考图已删除')
  } catch (err) {
    toast(`删除失败：${err.message}`)
  }
}

async function chooseAsset(asset) {
  replacing.value = true
  try {
    await replaceImageFromAsset(image.value.id, asset)
    replaceOpen.value = false
    toast('图片已替换')
  } catch (err) {
    toast(`替换失败：${err.message}`)
  } finally {
    replacing.value = false
  }
}

async function onFileChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  replacing.value = true
  try {
    await replaceImageFromFile(image.value.id, file)
    replaceOpen.value = false
    toast('图片已替换')
  } catch (err) {
    toast(`替换失败：${err.message}`)
  } finally {
    replacing.value = false
  }
}
</script>

<template>
  <div class="image-node" :class="{ selected }" :style="{ '--node-width': `${nodeWidth}px` }" @click="select">
    <div class="image-media" :style="{ '--image-ratio': imageRatio }">
      <img :src="image.url" :alt="image.name" draggable="false" @load="readDimensions" />
      <div class="image-tool-actions" @click.stop>
        <button title="放大预览" @click="previewing = true">⤢</button>
        <button title="替换图片" @click="replaceOpen = true; replaceMode = ''">⇄</button>
        <button title="裁剪图片" @click="toolsOpen = true">✂</button>
        <button title="九宫格切分" @click="toolsOpen = true">▦</button>
      </div>
      <button class="image-del" title="删除图片" @click="onDelete">×</button>
    </div>
    <div class="image-foot" :title="image.name">
      <span class="image-name">{{ image.name }}</span>
      <span v-if="dimensions" class="image-dimensions">{{ dimensions }}</span>
    </div>
    <Handle type="source" :position="Position.Right" class="image-port" />
  </div>
  <Teleport to="body">
    <div v-if="previewing" class="image-preview" @click="previewing = false">
      <img :src="image.url" :alt="image.name" />
      <span class="image-preview-hint">点击任意位置关闭</span>
    </div>
    <div v-if="replaceOpen" class="image-preview" @click.self="replaceOpen = false">
      <section class="replace-image-dialog" @click.stop>
        <header>
          <div><strong>替换图片</strong><p>选择新的图片来源</p></div>
          <button class="icon-btn" @click="replaceOpen = false">×</button>
        </header>
        <div v-if="!replaceMode" class="replace-image-options">
          <button class="replace-image-option" @click="replaceMode = 'local'; fileInput?.click()">
            <span class="replace-option-icon">↑</span><span><strong>本地上传</strong><small>从设备中选择图片文件</small></span><span class="replace-option-arrow">›</span>
          </button>
          <button class="replace-image-option" @click="replaceMode = 'assets'">
            <span class="replace-option-icon">▧</span><span><strong>资产库选择</strong><small>使用画布中已有的图片</small></span><span class="replace-option-arrow">›</span>
          </button>
        </div>
        <div v-else class="replace-image-step">
          <button class="btn ghost sm" :disabled="replacing" @click="replaceMode = ''">← 返回</button>
          <button v-if="replaceMode === 'local'" class="btn primary" :disabled="replacing" @click="fileInput?.click()">选择本地图片</button>
        </div>
        <input ref="fileInput" type="file" accept="image/*" hidden @change="onFileChange" />
        <div v-if="replaceMode === 'assets'" class="replace-image-list">
          <button v-for="asset in replacementAssets" :key="asset.id" :disabled="replacing" @click="chooseAsset(asset)">
            <img :src="asset.url" :alt="asset.name" />
            <span>{{ asset.name }}</span>
          </button>
          <p v-if="!replacementAssets.length" class="muted">当前画布没有其他可用图片</p>
        </div>
      </section>
    </div>
  </Teleport>
  <ImageToolsDialog v-if="toolsOpen" :image="image" @close="toolsOpen = false" />
</template>
