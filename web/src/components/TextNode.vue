<script setup>
import { computed, ref, watch } from 'vue'
import { state, updateCanvasNode, removeCanvasNode, toast } from '../store/canvas'

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false }
})

const node = computed(() => props.data.node)
const draft = ref(node.value.content || '')
const focused = ref(false)

watch(
  () => node.value.content,
  (value) => {
    if (!focused.value) draft.value = value || ''
  }
)

function select() {
  state.selectedNodeId = node.value.id
  state.selectedTaskId = null
  state.selectedImageId = null
}

async function save() {
  focused.value = false
  const text = draft.value
  if ((node.value.content || '') === text) return
  try {
    await updateCanvasNode(node.value.id, { content: text })
  } catch (e) {
    toast(`保存文本失败：${e.message}`)
  }
}

async function onDelete(e) {
  e.stopPropagation()
  if (!window.confirm('删除这个文本节点？')) return
  try {
    await removeCanvasNode(node.value.id)
    toast('文本节点已删除')
  } catch (err) {
    toast(`删除失败：${err.message}`)
  }
}
</script>

<template>
  <div class="text-node" :class="{ selected }" @click="select">
    <header class="node-head">
      <span class="node-kind">文本</span>
      <span class="node-title">剧情 / 备注</span>
      <button class="shot-del" title="删除文本" @click="onDelete">×</button>
    </header>
    <textarea
      v-model="draft"
      class="text-body nopan nowheel"
      rows="6"
      spellcheck="false"
      placeholder="写下剧情梗概、角色设定或镜头备注，失焦自动保存…"
      @focus="focused = true"
      @blur="save"
      @pointerdown.stop
    />
  </div>
</template>
