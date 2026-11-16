<script setup>
import { computed, ref } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { state, removeImage, toast } from '../store/canvas'
import ImageToolsDialog from './ImageToolsDialog.vue'

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false }
})

const image = computed(() => props.data.image)
const toolsOpen = ref(false)

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
</script>

<template>
  <div class="image-node" :class="{ selected }" @click="select">
    <div class="image-media">
      <img :src="image.url" :alt="image.name" draggable="false" />
      <div class="image-tool-actions" @click.stop>
        <button title="裁剪图片" @click="toolsOpen = true">✂</button>
        <button title="九宫格切分" @click="toolsOpen = true">▦</button>
      </div>
      <button class="image-del" title="删除图片" @click="onDelete">×</button>
    </div>
    <div class="image-foot" :title="image.name">{{ image.name }}</div>
    <Handle type="source" :position="Position.Right" class="image-port" />
  </div>
  <ImageToolsDialog v-if="toolsOpen" :image="image" @close="toolsOpen = false" />
</template>
