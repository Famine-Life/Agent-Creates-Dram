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
const image = computed(() => props.data.image)
const running = computed(() => image.value.status === 'RUNNING')
const failed = computed(() => image.value.status === 'FAILED')
const ready = computed(() => image.value.status === 'SUCCEEDED' && !!image.value.url)

function select() {
  state.selectedImageId = image.value.id
  state.selectedTaskId = null
  state.selectedNodeId = null
  state.inspectorEdit = false
}

async function onGenerate(text) {
  saving.value = true
  try {
    await generateAiImage(image.value.id, text)
    toast('已提交图片生成')
  } catch (e) {
    toast(`生成失败：${e.message}`)
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
  <div class="image-gen-node" :class="{ selected }" @click="select">
    <header class="node-head">
      <span class="node-kind">图片</span>
      <span class="node-title">{{ image.name }}</span>
      <span v-if="running" class="pill tone-busy">生成中</span>
      <span v-else-if="failed" class="pill tone-error">失败</span>
      <span v-else-if="ready" class="pill tone-done">已完成</span>
      <button class="shot-del" title="删除图片" @click="onDelete">×</button>
    </header>

    <div class="node-media">
      <img v-if="ready" :src="image.url" :alt="image.name" draggable="false" />
      <div v-else class="node-placeholder">
        <div v-if="running" class="ring" :style="{ '--value': 66 }"><span>AI</span></div>
        <template v-else-if="failed">
          <div class="err-title">生成失败</div>
          <div class="err-msg">{{ image.errorMsg || '请调整提示词后重试' }}</div>
        </template>
        <template v-else>
          <span class="node-glyph">✦</span>
          <div class="stage">填写下方提示词，生成图片</div>
        </template>
      </div>
    </div>

    <PromptEditor
      :prompt="image.prompt"
      :images="[]"
      :all-images="state.images"
      :model="state.meta.imageModel || 'AI 图片'"
      :chips="['1024×576']"
      :busy="saving || running"
      :show-refs="false"
      submit-label="生成图片"
      placeholder="描述你想要的画面，例如：雨夜霓虹街头，电影感，浅景深…"
      @submit="onGenerate"
    />

    <Handle type="source" :position="Position.Right" class="image-port" />
  </div>
</template>
