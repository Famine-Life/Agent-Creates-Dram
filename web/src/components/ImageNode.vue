<script setup>
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { state, removeImage, toast } from '../store/canvas'

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false }
})

const image = computed(() => props.data.image)

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
      <button class="image-del" title="删除图片" @click="onDelete">×</button>
    </div>
    <div class="image-foot" :title="image.name">{{ image.name }}</div>
    <Handle type="source" :position="Position.Right" class="image-port" />
  </div>
</template>
