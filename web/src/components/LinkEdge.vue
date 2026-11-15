<script setup>
import { computed, ref } from 'vue'
import { BaseEdge, EdgeLabelRenderer, getBezierPath } from '@vue-flow/core'
import { removeLink, toast } from '../store/canvas'

const props = defineProps({
  id: { type: String, required: true },
  sourceX: { type: Number, required: true },
  sourceY: { type: Number, required: true },
  targetX: { type: Number, required: true },
  targetY: { type: Number, required: true },
  sourcePosition: { type: String, required: true },
  targetPosition: { type: String, required: true },
  markerEnd: { type: String, default: '' },
  data: { type: Object, default: () => ({}) }
})

// 多根节点组件无法自动继承属性，显式关闭避免 Vue 警告
defineOptions({ inheritAttrs: false })

const hovered = ref(false)

const path = computed(() =>
  getBezierPath({
    sourceX: props.sourceX,
    sourceY: props.sourceY,
    sourcePosition: props.sourcePosition,
    targetX: props.targetX,
    targetY: props.targetY,
    targetPosition: props.targetPosition
  })
)

function remove() {
  try {
    removeLink(props.data.linkId)
    toast('连线已删除，已写入的提示词保留')
  } catch (e) {
    toast(`删除连线失败：${e.message}`)
  }
}
</script>

<template>
  <BaseEdge
    :id="id"
    :path="path[0]"
    :marker-end="markerEnd"
    :style="{ stroke: hovered ? '#4fd1a5' : '#3aa583', strokeWidth: hovered ? 2.4 : 1.8 }"
  />
  <path
    :d="path[0]"
    fill="none"
    stroke="transparent"
    stroke-width="18"
    style="cursor: pointer"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false"
  />
  <EdgeLabelRenderer>
    <button
      v-if="hovered"
      class="edge-del"
      title="删除连线"
      :style="{ transform: `translate(-50%, -50%) translate(${path[1]}px, ${path[2]}px)` }"
      @click.stop="remove"
      @mouseenter="hovered = true"
      @mouseleave="hovered = false"
    >
      ×
    </button>
  </EdgeLabelRenderer>
</template>
