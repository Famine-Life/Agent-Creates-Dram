<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  workspaces: { type: Array, default: () => [] },
  currentId: { type: Number, default: 0 },
  collapsed: { type: Boolean, default: false },
  busy: { type: Boolean, default: false }
})
const emit = defineEmits(['select', 'create', 'rename', 'copy', 'open-window', 'delete'])

const open = ref(false)
const trigger = ref(null)
const panel = ref(null)
const panelStyle = ref({ left: '0px', top: '0px' })
/** 当前展开 “...” 菜单的画布 ID */
const menuId = ref(null)
const renamingId = ref(null)
const renameText = ref('')

const current = computed(() => props.workspaces.find((item) => item.id === props.currentId))
const canDelete = computed(() => props.workspaces.length > 1)

function positionPanel() {
  const rect = trigger.value?.getBoundingClientRect()
  if (!rect) return
  const width = 208
  const left = Math.max(8, Math.min(rect.left, window.innerWidth - width - 8))
  panelStyle.value = { left: `${left}px`, top: `${rect.bottom + 6}px` }
}

function togglePanel() {
  if (props.busy) return
  open.value = !open.value
  menuId.value = null
  renamingId.value = null
  if (open.value) nextTick(positionPanel)
}

function close() {
  open.value = false
  menuId.value = null
  renamingId.value = null
}

function pick(item) {
  if (renamingId.value !== null) return
  if (item.id !== props.currentId) emit('select', item.id)
  close()
}

function toggleRowMenu(item) {
  menuId.value = menuId.value === item.id ? null : item.id
}

/** 菜单项只作用于当前展开 “...” 的那一行 */
function run(action) {
  const id = menuId.value
  if (!id) return
  close()
  emit(action, id)
}

function startRename() {
  const item = props.workspaces.find((entry) => entry.id === menuId.value)
  if (!item) return
  renamingId.value = item.id
  renameText.value = item.name
  menuId.value = null
  nextTick(() => panel.value?.querySelector('.canvas-rename')?.focus())
}

function commitRename(item) {
  if (renamingId.value !== item.id) return
  const name = renameText.value.trim()
  renamingId.value = null
  if (!name || name === item.name) return
  emit('rename', { id: item.id, name })
}

function cancelRename() {
  renamingId.value = null
}

function onDocumentPointerDown(event) {
  if (!open.value) return
  if (panel.value?.contains(event.target) || trigger.value?.contains(event.target)) return
  close()
}

function onKeydown(event) {
  if (event.key === 'Escape' && open.value) close()
}

watch(() => props.workspaces, () => {
  if (menuId.value && !props.workspaces.some((item) => item.id === menuId.value)) {
    menuId.value = null
  }
})

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointerDown, true)
  document.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', positionPanel)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown, true)
  document.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', positionPanel)
})
</script>

<template>
  <button
    ref="trigger"
    class="canvas-trigger"
    :class="{ open, compact: collapsed }"
    :disabled="busy"
    :title="current?.name || '切换画布'"
    @click="togglePanel"
  >
    <template v-if="collapsed">▤</template>
    <template v-else>
      <span class="canvas-trigger-name">{{ current?.name || '画布' }}</span>
      <svg class="canvas-trigger-caret" viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
        <path d="M4 6.2 8 10.2 12 6.2" fill="none" stroke="currentColor" stroke-width="1.8"
              stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </template>
  </button>

  <Teleport to="body">
    <div v-if="open" ref="panel" class="canvas-pop" :style="panelStyle" @pointerdown.stop>
      <div class="canvas-pop-head">
        <span>画布</span>
        <button class="canvas-pop-add" title="新增画布" :disabled="busy" @click="emit('create')">＋</button>
      </div>

      <div class="canvas-pop-list">
        <div
          v-for="item in workspaces"
          :key="item.id"
          class="canvas-row"
          :class="{ active: item.id === currentId, expanded: item.id === menuId }"
          @click="pick(item)"
        >
          <input
            v-if="renamingId === item.id"
            v-model="renameText"
            class="canvas-rename"
            @click.stop
            @keydown.enter.prevent="commitRename(item)"
            @keydown.esc.prevent="cancelRename"
            @blur="commitRename(item)"
          />
          <template v-else>
            <span class="canvas-row-name">{{ item.name }}</span>
            <span class="canvas-row-check">✓</span>
            <button class="canvas-row-more" title="更多操作" @click.stop="toggleRowMenu(item)">
              <svg viewBox="0 0 16 16" width="15" height="15" aria-hidden="true">
                <circle cx="3.4" cy="8" r="1.45" fill="currentColor" />
                <circle cx="8" cy="8" r="1.45" fill="currentColor" />
                <circle cx="12.6" cy="8" r="1.45" fill="currentColor" />
              </svg>
            </button>
          </template>

          <div v-if="item.id === menuId" class="canvas-row-menu" @pointerdown.stop>
            <button class="canvas-row-menu-item" @click.stop="run('open-window')">在新窗口打开</button>
            <button class="canvas-row-menu-item" @click.stop="startRename">重命名画布</button>
            <button class="canvas-row-menu-item" @click.stop="run('copy')">复制画布</button>
            <button
              class="canvas-row-menu-item danger"
              :disabled="!canDelete"
              :title="canDelete ? '' : '至少保留一个画布'"
              @click.stop="run('delete')"
            >删除画布</button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
