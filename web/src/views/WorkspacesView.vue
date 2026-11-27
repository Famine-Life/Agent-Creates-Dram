<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import AccountShell from '../components/AccountShell.vue'
import { toast } from '../store/canvas'
import { formatDate } from '../utils/format'

const route = useRoute()
const router = useRouter()

const projectId = computed(() => Number(route.params.projectId))
const project = ref(null)
const workspaces = ref([])
const loading = ref(true)
const busy = ref(false)

async function load() {
  loading.value = true
  try {
    const [info, list] = await Promise.all([api.project(projectId.value), api.workspaces(projectId.value)])
    project.value = info
    workspaces.value = list
  } catch (e) {
    toast(`加载项目失败：${e.message}`)
    router.replace({ name: 'home' })
  } finally {
    loading.value = false
  }
}

function open(id) {
  router.push({ name: 'canvas', params: { workspaceId: id } })
}

async function create() {
  if (busy.value) return
  busy.value = true
  try {
    const created = await api.createWorkspace(projectId.value)
    toast(`已创建「${created.name}」`)
    open(created.id)
  } catch (e) {
    toast(`新建画布失败：${e.message}`)
  } finally {
    busy.value = false
  }
}

async function rename(item) {
  const name = window.prompt('重命名画布', item.name)
  if (!name || name.trim() === item.name) return
  try {
    await api.renameWorkspace(item.id, name.trim())
    await load()
    toast(`已重命名为「${name.trim()}」`)
  } catch (e) {
    toast(`重命名失败：${e.message}`)
  }
}

async function copy(item) {
  if (busy.value) return
  busy.value = true
  try {
    const created = await api.copyWorkspace(item.id)
    toast(`已复制为「${created.name}」`)
    open(created.id)
  } catch (e) {
    toast(`复制画布失败：${e.message}`)
  } finally {
    busy.value = false
  }
}

async function remove(item) {
  if (busy.value) return
  if (!window.confirm(`确定删除画布「${item.name}」？\n\n该画布中的镜头、图片、节点与提示词记录将永久删除。`)) return
  busy.value = true
  try {
    await api.deleteWorkspace(item.id)
    await load()
    toast(`已删除画布「${item.name}」`)
  } catch (e) {
    toast(`删除画布失败：${e.message}`)
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <AccountShell subtitle="项目画布">
    <div class="page-header">
      <div>
        <router-link class="back-link" to="/">← 我的项目</router-link>
        <h1 class="page-title">{{ project ? project.name : '项目画布' }}</h1>
        <p class="page-sub">
          这个项目下的画布，可随时切换、复制或删除。
          <span v-if="project" class="muted">共 {{ workspaces.length }} 张</span>
        </p>
      </div>
      <button class="btn primary" :disabled="busy" @click="create">＋ 新建画布</button>
    </div>

    <div v-if="loading" class="loading">正在加载画布…</div>

    <div v-else-if="!workspaces.length" class="card empty">
      这个项目还没有画布，点击右上角新建一张开始创作。
    </div>

    <div v-else class="card-grid">
      <article v-for="item in workspaces" :key="item.id" class="card">
        <div class="section-head">
          <h2>{{ item.name }}</h2>
          <span class="pill tone-info">v{{ item.version }}</span>
        </div>
        <p class="muted">更新于 {{ formatDate(item.updatedAt) }}</p>
        <div class="inline-actions" style="margin-top: 14px">
          <button class="btn primary sm" @click="open(item.id)">打开画布</button>
          <button class="btn ghost sm" @click="rename(item)">重命名</button>
          <button class="btn ghost sm" @click="copy(item)">复制</button>
          <button class="btn danger sm" :disabled="busy" @click="remove(item)">删除</button>
        </div>
      </article>
    </div>
  </AccountShell>
</template>

<style scoped>
.back-link {
  display: inline-block;
  margin-bottom: 8px;
  color: var(--muted);
  font-size: 12px;
  text-decoration: none;
}

.back-link:hover {
  color: var(--text);
}
</style>
