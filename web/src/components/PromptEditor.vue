<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { api } from '../api'
import { WORKSPACE_ID, toast } from '../store/canvas'

const props = defineProps({
  prompt: { type: String, default: '' },
  images: { type: Array, default: () => [] },
  allImages: { type: Array, default: () => [] },
  placeholder: { type: String, default: '描述画面内容、镜头运动、光线氛围…输入 @ 引用图片' },
  model: { type: String, default: '' },
  chips: { type: Array, default: () => [] },
  busy: { type: Boolean, default: false },
  submitLabel: { type: String, default: '生成' },
  showSend: { type: Boolean, default: true },
  showRefs: { type: Boolean, default: true },
  showSettings: { type: Boolean, default: false },
  modelValue: { type: String, default: '' },
  modelOptions: { type: Array, default: () => [] },
  ratio: { type: String, default: '16:9' },
  ratioOptions: { type: Array, default: () => [] },
  duration: { type: [Number, String], default: 5 },
  durationOptions: { type: Array, default: () => [] },
  resolution: { type: String, default: '720P' },
  resolutionOptions: { type: Array, default: () => [] }
})

const emit = defineEmits([
  'submit',
  'link',
  'unlink',
  'upload',
  'update:modelValue',
  'update:ratio',
  'update:duration',
  'update:resolution'
])

const draft = ref(props.prompt || '')
const focused = ref(false)
const pickerOpen = ref(false)
const pickerAnchor = ref(0)
const fullscreen = ref(false)
const settingsOpen = ref(false)
const optimizing = ref(false)
const optimized = ref('')
const textarea = ref(null)
const fileInput = ref(null)

watch(
  () => props.prompt,
  (value) => {
    if (!focused.value) draft.value = value || ''
  }
)

const availableImages = computed(() =>
  (props.allImages || []).filter((image) => image && (image.url || image.status === 'RUNNING'))
)

const linkedIds = computed(() => new Set((props.images || []).map((image) => image.id)))

function indexOfLinked(image) {
  return (props.images || []).findIndex((item) => item.id === image.id)
}

function openPicker(at) {
  pickerAnchor.value = at == null ? draft.value.length : at
  pickerOpen.value = true
}

function onInput(event) {
  const element = event.target
  const value = element.value
  const caret = element.selectionStart || 0
  if (caret > 0 && value[caret - 1] === '@') {
    openPicker(caret - 1)
  } else {
    pickerOpen.value = false
  }
}

function pick(image) {
  const caret = pickerAnchor.value
  let index = indexOfLinked(image)
  if (index < 0) {
    emit('link', image)
    index = (props.images || []).length
  }
  const token = `@图片${index + 1} `
  const prefix = draft.value.slice(0, caret)
  const suffix = draft.value.slice(caret).replace(/^@/, '')
  draft.value = prefix + token + suffix
  pickerOpen.value = false
  nextTick(() => {
    const position = (prefix + token).length
    textarea.value?.focus()
    textarea.value?.setSelectionRange(position, position)
  })
}

function addReference() {
  const element = textarea.value
  openPicker(element?.selectionStart ?? draft.value.length)
}

function onUpload(event) {
  const files = [...(event.target.files || [])].filter((file) => file.type.startsWith('image/'))
  event.target.value = ''
  files.forEach((file) => emit('upload', file))
}

function submit() {
  const text = draft.value.trim()
  if (!text || props.busy) return
  pickerOpen.value = false
  emit('submit', text)
}

function toggleFullscreen(event) {
  event?.stopPropagation()
  fullscreen.value = !fullscreen.value
}

function onBlur() {
  focused.value = false
}

const modelLabel = computed(() => {
  const option = (props.modelOptions || []).find((item) => item.value === props.modelValue)
  return option?.label || props.modelValue || props.model
})

const durationMin = computed(() => Number(props.durationOptions[0] ?? 4))
const durationMax = computed(() => Number(props.durationOptions[props.durationOptions.length - 1] ?? 15))

// 拖动时用本地值，松手后再保存，避免接口回填打断拖动
const durationDraft = ref(Number(props.duration) || 5)
watch(
  () => props.duration,
  (value) => {
    durationDraft.value = Number(value) || 5
  }
)

function onDurationInput(event) {
  durationDraft.value = Number(event.target.value)
}

function onDurationChange() {
  emit('update:duration', durationDraft.value)
}

async function runOptimize() {
  const text = draft.value.trim()
  if (!text || optimizing.value) return
  settingsOpen.value = false
  optimizing.value = true
  try {
    const result = await api.optimizePrompt(WORKSPACE_ID, text)
    optimized.value = result.prompt || ''
  } catch (e) {
    toast(`提示词优化失败：${e.message}`)
  } finally {
    optimizing.value = false
  }
}

function applyOptimized() {
  draft.value = optimized.value
  optimized.value = ''
  nextTick(() => {
    const position = draft.value.length
    textarea.value?.focus()
    textarea.value?.setSelectionRange(position, position)
  })
}

function copyOptimized() {
  navigator.clipboard?.writeText(optimized.value)
  toast('优化后的提示词已复制')
}

/** 按比例画一个占位小方框 */
function ratioStyle(item) {
  const [w, h] = String(item).split(':').map(Number)
  if (!w || !h) return { width: '14px', height: '14px' }
  const base = 15
  return w >= h
    ? { width: `${base}px`, height: `${Math.round((base * h) / w)}px` }
    : { width: `${Math.round((base * w) / h)}px`, height: `${base}px` }
}
</script>

<template>
  <Teleport to="body" :disabled="!fullscreen">
    <div v-if="fullscreen" class="np-fs-mask" @click="fullscreen = false" />
    <div
      class="node-prompt nodrag"
      :class="{ 'is-fullscreen': fullscreen }"
      @pointerdown.stop
      @wheel.stop
      @keydown.esc.stop="fullscreen = false"
    >
      <button class="np-full" :title="fullscreen ? '退出全屏' : '全屏编辑'" @click="toggleFullscreen">
        {{ fullscreen ? '⤡' : '⤢' }}
      </button>
      <div v-if="showRefs" class="np-bar">
      <button class="np-tool" @click="fileInput.click()">＋ 上传</button>
      <button class="np-tool" @click="addReference">画布参考</button>
      <button class="np-tool" @click="addReference">@ 图片</button>
      <div class="np-thumbs">
        <div
          v-for="(image, index) in images"
          :key="image.id"
          class="np-thumb"
          :title="`@图片${index + 1} · ${image.name}`"
        >
          <img :src="image.url" :alt="image.name" />
          <span class="np-thumb-index">{{ index + 1 }}</span>
          <button class="np-thumb-del" @click.stop="emit('unlink', image)">×</button>
        </div>
      </div>
    </div>
    <input v-if="showRefs" ref="fileInput" type="file" accept="image/*" hidden @change="onUpload" />

    <div class="np-input-wrap">
      <textarea
        ref="textarea"
        v-model="draft"
        class="np-input nopan nowheel"
        rows="4"
        spellcheck="false"
        :placeholder="placeholder"
        @focus="focused = true"
        @blur="onBlur"
        @input="onInput"
        @keydown.ctrl.enter.prevent="submit"
        @keydown.meta.enter.prevent="submit"
      />
      <div v-if="pickerOpen" class="np-picker nopan nowheel">
        <template v-if="availableImages.length">
          <button
            v-for="image in availableImages"
            :key="image.id"
            class="np-picker-item"
            @mousedown.prevent="pick(image)"
          >
            <img v-if="image.url" :src="image.url" :alt="image.name" />
            <span v-else class="np-picker-holder" />
            <span class="np-picker-name">{{ image.name }}</span>
            <span v-if="linkedIds.has(image.id)" class="np-picker-tag">已引用</span>
          </button>
        </template>
        <p v-else class="np-picker-empty">画布上还没有图片，先上传一张</p>
      </div>
    </div>

    <div class="np-foot">
      <template v-if="showSettings && modelOptions.length">
        <select
          class="np-select np-select-model"
          :value="modelValue"
          title="选择模型"
          @change="emit('update:modelValue', $event.target.value)"
        >
          <option v-for="option in modelOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
        <button
          class="np-chip np-chip-btn"
          :class="{ active: settingsOpen }"
          title="比例 · 清晰度 · 时长"
          @click.stop="settingsOpen = !settingsOpen"
        >{{ ratio }} · {{ resolution }} · {{ duration }}s</button>
        <button
          class="np-ai"
          :class="{ busy: optimizing }"
          title="一键优化提示词"
          :disabled="optimizing || !draft.trim()"
          @click.stop="runOptimize"
        >
          <span v-if="optimizing" class="np-spinner" />
          <span v-else>✦</span>
        </button>
      </template>
      <span v-else-if="model" class="np-model" :title="modelLabel">{{ modelLabel }}</span>
      <template v-if="!showSettings">
        <span v-for="chip in chips" :key="chip" class="np-chip">{{ chip }}</span>
        <span class="np-count">{{ draft.length }}</span>
      </template>
      <button v-if="showSend" class="np-send" :disabled="busy || !draft.trim()" :title="submitLabel" @click="submit">
        <span v-if="busy" class="np-spinner" />
        <span v-else>↑</span>
      </button>
    </div>

    <div
      v-if="settingsOpen && showSettings"
      class="np-settings nopan nowheel nodrag"
      @pointerdown.stop
      @wheel.stop
    >
      <div class="np-set">
        <div class="np-set-title">比例</div>
        <div class="np-ratio-grid">
          <button
            v-for="item in ratioOptions"
            :key="item"
            class="np-ratio"
            :class="{ active: item === ratio }"
            @click="emit('update:ratio', item)"
          >
            <span class="np-ratio-box"><i :style="ratioStyle(item)" /></span>
            <span>{{ item }}</span>
          </button>
        </div>
      </div>
      <div class="np-set">
        <div class="np-set-title">清晰度</div>
        <div class="np-res-grid">
          <button
            v-for="item in resolutionOptions"
            :key="item"
            class="np-res-btn"
            :class="{ active: item === resolution }"
            @click="emit('update:resolution', item)"
          >{{ item }}</button>
        </div>
      </div>
      <div class="np-set">
        <div class="np-set-title">视频时长 <em>{{ durationDraft }}s</em></div>
        <input
          class="np-range nopan nodrag"
          type="range"
          title="拖动调整时长"
          :min="durationMin"
          :max="durationMax"
          step="1"
          :value="durationDraft"
          @input="onDurationInput"
          @change="onDurationChange"
        />
      </div>
    </div>

    <div
      v-if="optimized"
      class="np-optimized nopan nowheel nodrag"
      @pointerdown.stop
      @wheel.stop
    >
      <div class="np-opt-head">
        <span>优化后的提示词</span>
        <div class="np-opt-tools">
          <button class="np-opt-icon" title="复制" @click="copyOptimized">⧉</button>
          <button class="np-opt-icon" title="关闭" @click="optimized = ''">×</button>
        </div>
      </div>
      <p class="np-opt-text">{{ optimized }}</p>
      <div class="np-opt-foot">
        <button class="np-opt-btn ghost" :disabled="optimizing" @click="runOptimize">
          <span v-if="optimizing" class="np-spinner" />
          <span v-else>↻</span>
          重新生成
        </button>
        <button class="np-opt-btn primary" @click="applyOptimized">填充到输入框</button>
      </div>
    </div>
    </div>
  </Teleport>
</template>
