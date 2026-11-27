<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import AccountShell from '../components/AccountShell.vue'
import { toast } from '../store/canvas'
import { formatDate } from '../utils/format'

const router = useRouter()
const projects = ref([])
const loading = ref(true)
const busy = ref(false)

async function load() {
  loading.value = true
  try {
    projects.value = await api.projects()
  } catch (e) {
    toast(`加载项目失败：${e.message}`)
  } finally {
    loading.value = false
  }
}

function open(id) {
  router.push({ name: 'project', params: { projectId: id } })
}

async function create() {
  if (busy.value) return
  const name = window.prompt('新建项目', `项目 ${projects.value.length + 1}`)
  if (name === null) return
  busy.value = true
  try {
    const created = await api.createProject(name.trim())
    toast(`已创建「${created.name}」`)
    open(created.id)
  } catch (e) {
    toast(`新建项目失败：${e.message}`)
  } finally {
    busy.value = false
  }
}

async function rename(item) {
  const name = window.prompt('重命名项目', item.name)
  if (!name || name.trim() === item.name) return
  try {
    await api.renameProject(item.id, name.trim())
    await load()
    toast(`已重命名为「${name.trim()}」`)
  } catch (e) {
    toast(`重命名失败：${e.message}`)
  }
}

async function remove(item) {
  if (busy.value) return
  if (item.canvasCount > 0) {
    toast(`「${item.name}」下还有 ${item.canvasCount} 张画布，请先删除画布`)
    return
  }
  if (!window.confirm(`确定删除项目「${item.name}」？`)) return
  busy.value = true
  try {
    await api.deleteProject(item.id)
    await load()
    toast(`已删除项目「${item.name}」`)
  } catch (e) {
    toast(`删除项目失败：${e.message}`)
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <AccountShell subtitle="我的项目">
    <div class="page-header">
      <div>
        <h1 class="page-title">我的项目</h1>
        <p class="page-sub">项目用来组织一组画布，点开项目即可查看和管理其中的画布。</p>
      </div>
      <button class="btn primary" :disabled="busy" @click="create">＋ 新建项目</button>
    </div>

    <div v-if="loading" class="loading">正在加载项目…</div>

    <div v-else-if="!projects.length" class="card empty">
      还没有项目，点击右上角新建一个项目开始创作。
    </div>

    <div v-else class="card-grid">
      <article v-for="item in projects" :key="item.id" class="card">
        <div class="section-head">
          <h2>{{ item.name }}</h2>
          <span class="pill tone-info">{{ item.canvasCount }} 张画布</span>
        </div>
        <p class="muted">更新于 {{ formatDate(item.updatedAt) }}</p>
        <div class="inline-actions" style="margin-top: 14px">
          <button class="btn primary sm" @click="open(item.id)">打开项目</button>
          <button class="btn ghost sm" @click="rename(item)">重命名</button>
          <button class="btn danger sm" :disabled="busy" @click="remove(item)">删除</button>
        </div>
      </article>
    </div>
  </AccountShell>
</template>
