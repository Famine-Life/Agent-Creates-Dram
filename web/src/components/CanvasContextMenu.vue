<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  position: { type: Object, required: true }
})
const emit = defineEmits(['action', 'close'])

const keyword = ref('')
const root = ref(null)
const input = ref(null)

const items = [
  { key: 'text', label: '文本', icon: '≡', group: 'node' },
  { key: 'script', label: '脚本', icon: '⌘', group: 'node' },
  { key: 'image', label: '图片', icon: '▣', group: 'node' },
  { key: 'video', label: '视频', icon: '▷', group: 'node' },
  { key: 'stage', label: '导演台', icon: '◈', group: 'node', badge: 'NEW', disabled: true },
  { key: 'editing', label: '智能剪辑', icon: '✂', group: 'node', badge: 'Beta', disabled: true },
  { key: 'audio', label: '音频', icon: '♫', group: 'node', disabled: true },
  { key: 'upload', label: '上传', icon: '⇧', group: 'resource' },
  { key: 'history', label: '从生成历史选择', icon: '◷', group: 'resource', disabled: true }
]

const filtered = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) return items
  return items.filter((item) => item.label.toLowerCase().includes(text))
})

const nodes = computed(() => filtered.value.filter((item) => item.group === 'node'))
const resources = computed(() => filtered.value.filter((item) => item.group === 'resource'))

const style = computed(() => ({
  left: `${Math.min(props.position.x, window.innerWidth - 236)}px`,
  top: `${Math.min(props.position.y, window.innerHeight - 470)}px`
}))

function pick(item) {
  if (item.disabled) return
  emit('action', item.key)
}

function onDocumentPointerDown(event) {
  if (root.value && !root.value.contains(event.target)) emit('close')
}

onMounted(() => {
  input.value?.focus()
  document.addEventListener('pointerdown', onDocumentPointerDown, true)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown, true)
})
</script>

<template>
  <div ref="root" class="canvas-menu" :style="style" @pointerdown.stop @wheel.stop @contextmenu.prevent>
    <div class="canvas-menu-head">
      <input ref="input" v-model="keyword" type="text" placeholder="添加节点" />
      <span class="canvas-menu-search">⌕</span>
    </div>

    <div class="canvas-menu-list">
      <button
        v-for="item in nodes"
        :key="item.key"
        class="canvas-menu-item"
        :class="{ disabled: item.disabled }"
        @click="pick(item)"
      >
        <span class="cm-icon">{{ item.icon }}</span>
        <span class="cm-label">{{ item.label }}</span>
        <span v-if="item.badge" class="cm-badge">{{ item.badge }}</span>
        <span v-if="item.key === 'script'" class="cm-arrow">›</span>
      </button>

      <template v-if="resources.length">
        <div class="canvas-menu-sep"><span>添加资源</span></div>
        <button
          v-for="item in resources"
          :key="item.key"
          class="canvas-menu-item"
          :class="{ disabled: item.disabled }"
          @click="pick(item)"
        >
          <span class="cm-icon">{{ item.icon }}</span>
          <span class="cm-label">{{ item.label }}</span>
        </button>
      </template>

      <p v-if="!filtered.length" class="canvas-menu-empty">没有匹配的节点</p>
    </div>
  </div>
</template>
