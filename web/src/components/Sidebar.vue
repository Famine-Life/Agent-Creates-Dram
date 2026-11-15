<script setup>
import { computed } from 'vue'
import { state, statusOf } from '../store/canvas'

const props = defineProps({
  collapsed: { type: Boolean, default: false }
})

const emit = defineEmits(['new', 'focus', 'toggle'])

const groups = computed(() => {
  const keyword = state.keyword.trim().toLowerCase()
  const list = keyword
    ? state.tasks.filter(
        (t) =>
          (t.title || '').toLowerCase().includes(keyword) ||
          (t.prompt || '').toLowerCase().includes(keyword)
      )
    : state.tasks
  const map = new Map()
  for (const task of list) {
    const key = task.groupId || '默认分组'
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(task)
  }
  return [...map.entries()].map(([name, tasks]) => ({ name, tasks }))
})

const running = computed(
  () => state.tasks.filter((t) => ['RECEIVED', 'QUEUED', 'SUBMITTING', 'SUBMITTED', 'PROVIDER_QUEUED', 'RUNNING'].includes(t.status)).length
)

const selectedGroup = computed(
  () => state.tasks.find((t) => t.id === state.selectedTaskId)?.groupId
)

function codeOf(task) {
  const index = state.tasks.findIndex((t) => t.id === task.id)
  return `#${task.shotIndex ?? index + 1}`
}

/** 图标栏里点击分组：展开侧栏并定位到该组第一个镜头 */
function pickGroup(group) {
  if (props.collapsed) emit('toggle')
  if (group.tasks.length) emit('focus', group.tasks[0].id)
}
</script>

<template>
  <aside v-if="!collapsed" class="sidebar">
    <div class="panel-head">
      <div>
        <h2>提示词</h2>
        <p class="muted">{{ state.tasks.length }} 个镜头 · {{ running }} 个进行中</p>
      </div>
      <div class="head-actions">
        <button class="btn primary sm" @click="emit('new')">新建</button>
        <button class="icon-btn" title="折叠为图标栏" @click="emit('toggle')">«</button>
      </div>
    </div>

    <input v-model="state.keyword" class="search" type="search" placeholder="搜索标题或提示词" />

    <div class="group-list">
      <section v-for="group in groups" :key="group.name" class="group-block">
        <div class="group-head">
          <span class="group-dot" />
          <span>{{ group.name }}</span>
          <span class="muted">{{ group.tasks.length }}</span>
        </div>
        <button
          v-for="task in group.tasks"
          :key="task.id"
          class="prompt-item"
          :class="{ active: state.selectedTaskId === task.id }"
          @click="emit('focus', task.id)"
        >
          <span class="pi-code">{{ codeOf(task) }}</span>
          <span class="pi-title" :title="task.title">{{ task.title }}</span>
          <span class="dot" :class="`tone-${statusOf(task).tone}`" />
        </button>
      </section>
      <p v-if="!groups.length" class="empty-hint">还没有提示词，点击「新建」或让 AI 客户端推送。</p>
    </div>
  </aside>

  <aside v-else class="sidebar collapsed">
    <button class="rail-btn" title="展开提示词栏" @click="emit('toggle')">»</button>
    <button class="rail-btn primary" title="新建提示词" @click="emit('new')">＋</button>
    <div class="rail-sep" />
    <button
      v-for="group in groups"
      :key="group.name"
      class="rail-group"
      :class="{ active: selectedGroup === group.name }"
      :title="`${group.name} · ${group.tasks.length} 个镜头`"
      @click="pickGroup(group)"
    >
      <span>{{ group.name.slice(0, 1).toUpperCase() }}</span>
      <em>{{ group.tasks.length }}</em>
    </button>
    <div class="rail-spacer" />
    <span v-if="running" class="rail-running" :title="`${running} 个进行中`">{{ running }}</span>
  </aside>
</template>
