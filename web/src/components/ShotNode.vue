<script setup>
import { computed, nextTick, ref } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import {
  state,
  statusOf,
  retryTask,
  cancelTask,
  removeTask,
  savePrompt,
  updateShotSettings,
  removeEdge,
  uploadImageFile,
  linkImage,
  toast
} from '../store/canvas'
import PromptEditor from './PromptEditor.vue'

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false }
})

const DEFAULT_RATIOS = ['16:9', '9:16', '1:1', '4:3', '3:4', '21:9']
const DEFAULT_DURATIONS = [4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15]

const saving = ref(false)
const videoEl = ref(null)
const started = ref(false)
const hovering = ref(false)
const captureMenuOpen = ref(false)
const capturingFrame = ref(false)
const task = computed(() => props.data.task)
const status = computed(() => statusOf(task.value))
const errorMessage = computed(() => formatErrorMessage(task.value.errorMsg))
const active = computed(() => ['RECEIVED', 'QUEUED', 'SUBMITTING', 'SUBMITTED', 'PROVIDER_QUEUED', 'RUNNING'].includes(task.value.status))
const isDraft = computed(() => task.value.status === 'IDLE')
const ready = computed(() => task.value.status === 'SUCCEEDED' && !!task.value.videoUrl)
const editorVisible = computed(() => !state.selectionBoxActive && state.selectedTaskId === task.value.id)
const refCount = computed(() => task.value.imageIds?.length || 0)
const progress = computed(() => Math.max(0, Math.min(100, task.value.progress || 0)))
/** 视频模型由后端 /api/meta 按平台下发，节点不再硬编码 */
const videoModels = computed(() => state.meta.videoModels || [])
const model = computed(() => task.value.modelKey || task.value.model || '')
const modelOption = computed(() => videoModels.value.find((item) => item.value === model.value))
const modelLabel = computed(() => modelOption.value?.label || task.value.model || model.value || '-')
const stageText = computed(() => {
  if (['QUEUED', 'RECEIVED', 'PROVIDER_QUEUED'].includes(task.value.status)) {
    if (task.value.status === 'PROVIDER_QUEUED') return '等待火山方舟平台资源'
    return `使用 ${modelLabel.value} 模型排队中`
  }
  if (task.value.status === 'SUBMIT_UNKNOWN') return '提交结果不确定，请确认方舟控制台后再重试'
  return `使用 ${modelLabel.value} 模型生成中`
})
const ratio = computed(() => task.value.ratio || '16:9')
const duration = computed(() => task.value.duration || 5)
const resolution = computed(() => task.value.resolution || '720P')
const ratioOptions = computed(() =>
  modelOption.value?.ratios?.length ? modelOption.value.ratios : DEFAULT_RATIOS
)
const durationOptions = computed(() =>
  modelOption.value?.durations?.length ? modelOption.value.durations : DEFAULT_DURATIONS
)
const resolutionOptions = computed(() =>
  modelOption.value?.resolutions?.length ? modelOption.value.resolutions : ['480P', '720P']
)

function formatErrorMessage(value) {
  const raw = String(value || '请稍后重试')
  const jsonStart = raw.indexOf('{')
  const jsonEnd = raw.lastIndexOf('}')
  if (jsonStart >= 0 && jsonEnd > jsonStart) {
    try {
      const payload = JSON.parse(raw.slice(jsonStart, jsonEnd + 1))
      let error = payload.error || payload
      if (typeof error === 'string') error = JSON.parse(error)
      const code = error.code || error.Code || ''
      const message = error.message || error.Message || ''
      const requestId = error.request_id || error.requestId || error.RequestId ||
        /(?:ARK\s*)?request\s*id\s*[:=]\s*([a-z0-9-]+)/i.exec(`${message} ${raw}`)?.[1]
      let readable = message
      if (code === 'InputImageSensitiveContentDetected.PrivacyInformation') {
        readable = '参考图片可能包含真人，平台因此拦截了请求。请更换为插画或非真人参考图后重试。'
      } else if (code.startsWith('InputImageSensitiveContentDetected')) {
        readable = '参考图片触发了平台安全检查。请更换图片或调整画面内容后重试。'
      }
      const cleanMessage = readable ? String(readable).replace(/\s*request\s*id\s*[:=]\s*[a-z0-9-]+\.?/ig, '').trim() : '模型服务请求失败，请调整素材或提示词后重试。'
      return requestId ? `${cleanMessage} 请求 ID：${requestId}` : cleanMessage
    } catch {
      // Fall through to remove any code suffix from older, already-saved task errors.
    }
  }
  return raw.replace(/\s*（错误码：[^）]*）/g, '').replace(/\s*\(error code:\s*[^)]*\)/ig, '').trim()
}

/** 按连线顺序排列的参考图，顺序即提示词里的 @图片n */
const linkedImages = computed(() => {
  const list = []
  for (const edge of state.edges.filter((item) => item.taskId === task.value.id)) {
    const image = state.images.find((item) => item.id === edge.imageId)
    if (image) list.push({ ...image, edgeId: edge.id })
  }
  return list
})

function select() {
  state.selectedTaskId = task.value.id
  state.selectedImageId = null
  state.selectedNodeId = null
}

async function guard(action, label) {
  try {
    await action
  } catch (e) {
    toast(`${label}失败：${e.message}`)
  }
}

async function onDelete(e) {
  e.stopPropagation()
  if (!window.confirm(`确定删除镜头《${task.value.title}》？未完成的生成会被取消。`)) return
  await guard(removeTask(task.value.id), '删除')
}

function openPreview(e) {
  e?.stopPropagation()
  if (ready.value) {
    state.previewTask = task.value
  }
}

/** 未点击前不显示进度条，点击播放后才显示原生控制条 */
function playInline(e) {
  e?.stopPropagation()
  started.value = true
  nextTick(() => videoEl.value?.play())
}

async function onSubmitPrompt(text) {
  saving.value = true
  try {
    await savePrompt(task.value.id, text, {
      model: model.value,
      ratio: ratio.value,
      duration: duration.value,
      resolution: resolution.value
    })
  } catch (e) {
    toast(`保存失败：${e.message}`)
  } finally {
    saving.value = false
  }
}

function onRetry() {
  if (task.value.status === 'SUBMIT_UNKNOWN'
      && !window.confirm('提交结果不确定。请先在方舟控制台检查任务，确认没有已接收的生成任务后再重试，以免重复生成。继续？')) {
    return
  }
  guard(retryTask(task.value.id), '重新生成')
}

async function saveSettings(patch) {
  try {
    await updateShotSettings(task.value.id, patch)
  } catch (e) {
    toast(`保存参数失败：${e.message}`)
  }
}

/** 切换模型时，若当前清晰度不被新模型支持，自动回退到 720P */
function onModelChange(value) {
  const patch = { model: value }
  const options = videoModels.value.find((item) => item.value === value)?.resolutions || []
  if (!options.includes(resolution.value)) {
    patch.resolution = options.includes('720P') ? '720P' : options[0]
  }
  saveSettings(patch)
}

async function onLinkImage(image) {
  if (task.value.imageIds?.includes(image.id)) return
  try {
    await linkImage(task.value.id, image.id)
  } catch (e) {
    toast(`引用图片失败：${e.message}`)
  }
}

function seekVideo(time) {
  const video = videoEl.value
  if (!video || Math.abs(video.currentTime - time) < 0.02) return Promise.resolve()
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      video.removeEventListener('seeked', onSeeked)
      reject(new Error('视频定位超时'))
    }, 8000)
    function onSeeked() {
      clearTimeout(timeout)
      resolve()
    }
    video.addEventListener('seeked', onSeeked, { once: true })
    video.currentTime = time
  })
}

async function captureFrame(kind) {
  const video = videoEl.value
  if (!video || !video.videoWidth || capturingFrame.value) return
  capturingFrame.value = true
  captureMenuOpen.value = false
  const originalTime = video.currentTime
  const wasPlaying = !video.paused
  try {
    video.pause()
    if (kind === 'first') await seekVideo(0.05)
    if (kind === 'last') await seekVideo(Math.max(0, video.duration - 0.08))
    await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    const canvas = document.createElement('canvas')
    canvas.width = video.videoWidth
    canvas.height = video.videoHeight
    canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height)
    const blob = await new Promise((resolve, reject) => {
      canvas.toBlob((value) => value ? resolve(value) : reject(new Error('截图编码失败')), 'image/png')
    })
    const label = { current: '当前帧', first: '首帧', last: '尾帧' }[kind]
    const baseName = (task.value.title || `镜头${task.value.id}`).replace(/[\\/:*?"<>|]/g, '_')
    const file = new File([blob], `${baseName}-${label}.png`, { type: 'image/png' })
    await uploadImageFile(file)
    toast(`${label}已保存为图片节点`)
  } catch (error) {
    toast(`截取失败：${error.message || '视频无法读取当前帧'}`)
  } finally {
    try {
      await seekVideo(originalTime)
      if (wasPlaying) await video.play()
    } catch { /* 即使恢复播放失败也保留截图结果 */ }
    capturingFrame.value = false
  }
}

async function onUnlinkImage(image) {
  if (!image.edgeId) return
  try {
    await removeEdge(image.edgeId)
  } catch (e) {
    toast(`取消引用失败：${e.message}`)
  }
}

async function onUpload(file) {
  try {
    const uploaded = await uploadImageFile(file)
    await linkImage(task.value.id, uploaded.id)
  } catch (e) {
    toast(`上传失败：${e.message}`)
  }
}

async function onCancel() {
  try {
    await cancelTask(task.value.id)
  } catch (e) {
    toast(`取消失败：${e.message}`)
  }
}
</script>

<template>
  <div class="shot-node-wrap" @click="select">
    <div class="shot-node" :class="[`tone-${status.tone}`, { selected: editorVisible }]">
      <Handle type="target" :position="Position.Left" class="shot-port" />

      <header class="shot-head">
        <span class="shot-code">{{ data.code }}</span>
        <span class="shot-title" :title="task.title">{{ task.title }}</span>
        <span class="pill" :class="`tone-${status.tone}`">{{ status.text }}</span>
        <button class="shot-del" title="删除镜头" @click="onDelete">×</button>
      </header>

      <div class="shot-media" @mouseenter="hovering = true" @mouseleave="hovering = false">
        <video
          v-if="ready"
          ref="videoEl"
          :src="task.videoUrl + '#t=0.1'"
          class="nowheel nopan"
          preload="metadata"
          :controls="started"
          @pointerdown.stop
          @click.stop
        />
        <button
          v-if="ready && !started"
          class="media-play"
          :class="{ show: hovering }"
          title="播放"
          @click="playInline"
        >▶</button>
        <div v-if="!ready" class="shot-placeholder">
          <div v-if="active" class="ring" :style="{ '--value': progress }">
            <span>{{ progress }}%</span>
          </div>
          <template v-if="isDraft">
            <div class="err-title">待编辑</div>
            <div class="err-msg">在下方填写提示词后点击「生成」</div>
          </template>
          <template v-else-if="task.status === 'FAILED' || task.status === 'TIMEOUT' || task.status === 'SUBMIT_UNKNOWN'">
            <div class="err-title">{{ task.status === 'SUBMIT_UNKNOWN' ? '提交结果待确认' : '生成失败' }}</div>
            <div class="err-msg">{{ errorMessage }}</div>
          </template>
          <template v-else-if="!active">
            <div class="err-title">{{ status.text }}</div>
            <div class="err-msg">点击「重新生成」再试一次</div>
          </template>
          <div v-else class="stage">{{ stageText }}</div>
          <button
            v-if="active"
            class="cancel-btn"
            title="取消当前生成任务"
            @click.stop="onCancel"
          >取消任务</button>
        </div>

        <div v-if="ready || task.status === 'FAILED' || task.status === 'TIMEOUT' || task.status === 'SUBMIT_UNKNOWN'" class="media-actions">
          <button v-if="ready" class="media-btn" title="预览" @click="openPreview">⤢</button>
          <a v-if="ready" class="media-btn" :href="task.videoUrl" target="_blank" rel="noreferrer" title="下载" @click.stop>↓</a>
          <div v-if="ready" class="frame-capture-wrap" @click.stop>
            <button class="media-btn" :disabled="capturingFrame" title="截图：当前帧、首帧或尾帧" @click="captureMenuOpen = !captureMenuOpen">◎</button>
            <div v-if="captureMenuOpen" class="frame-capture-menu">
              <button :disabled="capturingFrame" @click="captureFrame('current')">截取当前帧</button>
              <button :disabled="capturingFrame" @click="captureFrame('first')">截取首帧</button>
              <button :disabled="capturingFrame" @click="captureFrame('last')">截取尾帧</button>
            </div>
          </div>
          <button
            v-else
            class="media-btn"
            :title="task.status === 'SUBMIT_UNKNOWN' ? '确认方舟控制台没有该任务后再重试' : '重新生成'"
            @click.stop="onRetry"
          >↻</button>
        </div>

        <span v-if="refCount" class="ref-badge">参考图 ×{{ refCount }}</span>
      </div>

      <div v-if="active" class="shot-progress"><i :style="{ width: progress + '%' }" /></div>
    </div>

    <PromptEditor
      v-if="editorVisible"
      :prompt="task.prompt"
      :images="linkedImages"
      :all-images="state.images"
      :model="model"
      :model-value="model"
      :model-options="videoModels"
      :ratio="ratio"
      :ratio-options="ratioOptions"
      :duration="duration"
      :duration-options="durationOptions"
      :resolution="resolution"
      :resolution-options="resolutionOptions"
      :show-settings="true"
      :busy="saving"
      submit-label="生成"
      placeholder="描述画面内容、镜头运动、光线氛围…输入 @ 引用图片"
      @submit="onSubmitPrompt"
      @update:model-value="onModelChange"
      @update:ratio="saveSettings({ ratio: $event })"
      @update:duration="saveSettings({ duration: Number($event) })"
      @update:resolution="saveSettings({ resolution: $event })"
      @link="onLinkImage"
      @unlink="onUnlinkImage"
      @upload="onUpload"
    />
  </div>
</template>
