<script setup>
import { computed, ref, watch } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import {
  state,
  createStoryboard,
  generateScript,
  removeCanvasNode,
  updateCanvasNode,
  toast
} from '../store/canvas'
import PromptEditor from './PromptEditor.vue'

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false }
})

const adding = ref(false)
const added = ref(false)
const model = ref('')

const node = computed(() => props.data.node)
const running = computed(() => node.value.status === 'RUNNING')
const failed = computed(() => node.value.status === 'FAILED')
const modelOptions = computed(() => state.meta.textModels || [])
const activeModel = computed(() => model.value || state.meta.defaults?.text || '')
const editorVisible = computed(
  () => props.selected || state.selectedNodeId === node.value.id
)

const shots = computed(() => parseShots(node.value.content))
const draftShots = ref([])
const editingShot = ref(null)

watch(
  shots,
  (list) => {
    // 正在编辑时不要被实时推送覆盖，失焦保存后再同步
    if (editingShot.value != null) return
    draftShots.value = list.map((shot) => ({ title: shot.title || '', prompt: shot.prompt || '' }))
  },
  { immediate: true }
)

function parseShots(content) {
  try {
    const list = JSON.parse(content || '[]')
    return Array.isArray(list) ? list : []
  } catch (e) {
    return []
  }
}

async function saveShots() {
  editingShot.value = null
  if (running.value) return
  const content = JSON.stringify(
    draftShots.value
      .map((shot) => ({ title: (shot.title || '').trim(), prompt: (shot.prompt || '').trim() }))
      .filter((shot) => shot.prompt)
  )
  if (content === (node.value.content || '[]')) return
  try {
    await updateCanvasNode(node.value.id, { content })
  } catch (e) {
    toast(`保存分镜失败：${e.message}`)
  }
}

function downloadScript(e) {
  e.stopPropagation()
  const list = draftShots.value.filter((shot) => (shot.prompt || '').trim())
  if (!list.length) return
  const text = list
    .map((shot, index) => `${index + 1}. ${(shot.title || '').trim() || `镜头 ${index + 1}`}\n${shot.prompt.trim()}`)
    .join('\n\n')
  const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `${node.value.title || '分镜脚本'}.txt`
  link.click()
  URL.revokeObjectURL(url)
}

const tips = [
  { label: '剧本生成分镜脚本', prompt: '我有一段剧情，请帮我拆成可直接生成的分镜脚本：' },
  { label: '角色生成分镜脚本', prompt: '请围绕这个角色设计一组分镜：' },
  { label: '自己编写分镜脚本', prompt: '请把下面的分镜描述整理成画面提示词：' }
]

function select() {
  state.selectedNodeId = node.value.id
  state.selectedTaskId = null
  state.selectedImageId = null
}

async function onSubmit(text) {
  try {
    await generateScript(node.value.id, text, activeModel.value)
    added.value = false
  } catch (e) {
    toast(`生成失败：${e.message}`)
  }
}

async function useTip(tip) {
  try {
    await updateCanvasNode(node.value.id, { prompt: tip.prompt })
  } catch (e) {
    toast(e.message)
  }
}

async function addToCanvas() {
  const list = draftShots.value
    .map((shot) => ({ title: (shot.title || '').trim(), prompt: (shot.prompt || '').trim() }))
    .filter((shot) => shot.prompt)
  if (!list.length || adding.value) return
  adding.value = true
  try {
    const group = (node.value.title || `脚本 ${node.value.id}`).replace(/\s+/g, '').slice(0, 32)
    await createStoryboard({
      groupId: group || `脚本${node.value.id}`,
      shots: list
    })
    added.value = true
  } catch (e) {
    toast(`添加到画布失败：${e.message}`)
  } finally {
    adding.value = false
  }
}

async function onDelete(e) {
  e.stopPropagation()
  if (!window.confirm(`删除脚本《${node.value.title}》？已生成的镜头不受影响。`)) return
  try {
    await removeCanvasNode(node.value.id)
    toast('脚本节点已删除')
  } catch (err) {
    toast(`删除失败：${err.message}`)
  }
}
</script>

<template>
  <div class="script-node-wrap" @click="select">
    <div class="script-node nowheel" :class="{ selected }">
      <header class="node-head">
        <span class="node-kind">脚本</span>
        <span class="node-title" :title="node.title">{{ node.title }}</span>
        <span v-if="running" class="pill tone-busy">生成中</span>
        <span v-else-if="failed" class="pill tone-error">失败</span>
        <span v-else-if="shots.length" class="pill tone-done">{{ shots.length }} 镜</span>
        <button
          v-if="draftShots.length"
          class="node-tool"
          title="导出分镜脚本"
          @click.stop="downloadScript"
        >⤓</button>
        <button class="shot-del" title="删除脚本" @click="onDelete">×</button>
      </header>

      <div class="script-body">
        <div v-if="running" class="script-loading">
          <div class="skeleton" />
          <div class="skeleton short" />
          <div class="skeleton" />
          <p class="stage">AI 正在拆解分镜…</p>
        </div>

        <div v-else-if="failed" class="script-empty">
          <div class="err-title">生成失败</div>
          <div class="err-msg">{{ node.errorMsg || '请稍后重试' }}</div>
        </div>

        <template v-else-if="shots.length">
          <ol class="script-shots">
            <li v-for="(shot, index) in draftShots" :key="index">
              <span class="script-index">{{ index + 1 }}</span>
              <div class="script-shot-text">
                <input
                  v-model="shot.title"
                  class="script-shot-title nopan nowheel"
                  placeholder="镜头标题"
                  spellcheck="false"
                  @pointerdown.stop
                  @focus="editingShot = index"
                  @blur="saveShots"
                />
                <textarea
                  v-model="shot.prompt"
                  class="script-shot-prompt nopan nowheel"
                  rows="2"
                  spellcheck="false"
                  placeholder="镜头提示词"
                  @pointerdown.stop
                  @focus="editingShot = index"
                  @blur="saveShots"
                />
              </div>
            </li>
          </ol>
          <button class="btn primary script-add" :disabled="adding || added" @click.stop="addToCanvas">
            {{ added ? '已添加到画布' : adding ? '添加中…' : `添加到画布 · ${draftShots.length} 镜` }}
          </button>
        </template>

        <div v-else class="script-empty">
          <div class="script-glyph">
            <i /><i /><i />
          </div>
          <p class="muted">尝试：</p>
          <button v-for="tip in tips" :key="tip.label" class="script-tip" @click.stop="useTip(tip)">
            {{ tip.label }}
          </button>
        </div>
      </div>

      <Handle type="target" :position="Position.Left" class="script-in-port" />
      <Handle type="source" :position="Position.Right" class="script-port" />
    </div>

    <PromptEditor
      v-if="editorVisible"
      :prompt="node.prompt"
      :images="[]"
      :all-images="state.images"
      :model="activeModel"
      :model-value="activeModel"
      :model-options="modelOptions"
      :chips="[]"
      :busy="running"
      :show-refs="false"
      submit-label="生成脚本"
      placeholder="描述剧情片段、故事，为你生成分镜脚本"
      @update:model-value="model = $event"
      @submit="onSubmit"
    />
  </div>
</template>
