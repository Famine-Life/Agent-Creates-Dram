<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'
import { state, toast, copyText } from '../store/canvas'

const emit = defineEmits(['close'])
const records = ref([])
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    records.value = await api.promptRecords(state.workspaceId)
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}

function copy(prompt) {
  copyText(prompt, '提示词已复制')
}

onMounted(load)
</script>

<template>
  <div class="overlay" @click.self="emit('close')">
    <div class="dialog wide">
      <header class="dialog-head">
        <div>
          <h2>提示词记录</h2>
          <p class="muted">REST、CLI、MCP 提交及修改的镜头提示词，最多显示最近 200 条</p>
        </div>
        <button class="icon-btn" @click="emit('close')">×</button>
      </header>

      <div class="record-toolbar">
        <span class="muted">共 {{ records.length }} 条</span>
        <button class="btn ghost sm" :disabled="loading" @click="load">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </div>

      <div class="dialog-body">
        <div v-if="loading && !records.length" class="record-empty muted">正在加载…</div>
        <div v-else-if="error" class="record-empty error-text">{{ error }}</div>
        <div v-else-if="!records.length" class="record-empty muted">暂无提示词记录</div>
        <div v-else class="record-list">
          <article v-for="record in records" :key="record.id" class="record-item">
            <div class="record-meta">
              <div>
                <strong>镜头 #{{ record.taskId }}</strong>
                <span class="muted">记录 #{{ record.id }}</span>
              </div>
              <div class="record-side">
                <time class="muted">{{ formatTime(record.createdAt) }}</time>
                <button class="btn ghost sm" @click="copy(record.prompt)">复制</button>
              </div>
            </div>
            <p class="record-prompt">{{ record.prompt }}</p>
          </article>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.record-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 18px;
  border-bottom: 1px solid var(--border);
  font-size: 12px;
}

.record-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.record-item {
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--card);
}

.record-meta,
.record-meta > div,
.record-side {
  display: flex;
  align-items: center;
  gap: 10px;
}

.record-meta {
  justify-content: space-between;
  font-size: 12px;
}

.record-meta time {
  font-size: 11px;
}

.record-prompt {
  margin: 10px 0 0;
  color: var(--text);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.record-empty {
  padding: 48px 0;
  text-align: center;
  font-size: 13px;
}

.error-text {
  color: var(--danger-text);
}
</style>
