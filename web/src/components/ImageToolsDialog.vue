<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { uploadImageFile, toast } from '../store/canvas'

const props = defineProps({ image: { type: Object, required: true } })
const emit = defineEmits(['close'])

const stage = ref(null)
const selection = ref(null)
const dragging = ref(false)
const busy = ref(false)
const selectionStyle = computed(() => {
  if (!selection.value || !stage.value) return {}
  const { x, y, width, height } = selection.value
  return {
    left: `${(x / stage.value.clientWidth) * 100}%`,
    top: `${(y / stage.value.clientHeight) * 100}%`,
    width: `${(width / stage.value.clientWidth) * 100}%`,
    height: `${(height / stage.value.clientHeight) * 100}%`
  }
})

function point(event) {
  const bounds = stage.value.getBoundingClientRect()
  return {
    x: Math.max(0, Math.min(bounds.width, event.clientX - bounds.left)),
    y: Math.max(0, Math.min(bounds.height, event.clientY - bounds.top))
  }
}

function startSelection(event) {
  if (busy.value) return
  event.preventDefault()
  stage.value.setPointerCapture(event.pointerId)
  const p = point(event)
  selection.value = { x: p.x, y: p.y, width: 0, height: 0, anchorX: p.x, anchorY: p.y }
  dragging.value = true
}

function moveSelection(event) {
  if (!dragging.value || !selection.value) return
  const p = point(event)
  const { anchorX, anchorY } = selection.value
  selection.value = {
    x: Math.min(anchorX, p.x),
    y: Math.min(anchorY, p.y),
    width: Math.abs(p.x - anchorX),
    height: Math.abs(p.y - anchorY),
    anchorX,
    anchorY
  }
}

function endSelection() {
  dragging.value = false
}

function loadSource() {
  return new Promise((resolve, reject) => {
    const source = new Image()
    source.crossOrigin = 'anonymous'
    source.onload = () => resolve(source)
    source.onerror = () => reject(new Error('图片读取失败；请确认图片可访问且允许跨域读取'))
    source.src = props.image.url
  })
}

function canvasBlob(canvas) {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => blob ? resolve(blob) : reject(new Error('图片编码失败')), 'image/png')
  })
}

async function uploadCanvas(canvas, suffix) {
  const blob = await canvasBlob(canvas)
  const file = new File([blob], `${props.image.name || '图片'}-${suffix}.png`, { type: 'image/png' })
  await uploadImageFile(file)
}

async function cropImage() {
  if (!selection.value || selection.value.width < 8 || selection.value.height < 8) {
    toast('请先在图片上拖动框选裁剪区域')
    return
  }
  busy.value = true
  try {
    const source = await loadSource()
    const bounds = stage.value.getBoundingClientRect()
    const ratioX = source.naturalWidth / bounds.width
    const ratioY = source.naturalHeight / bounds.height
    const sx = Math.round(selection.value.x * ratioX)
    const sy = Math.round(selection.value.y * ratioY)
    const sw = Math.max(1, Math.round(selection.value.width * ratioX))
    const sh = Math.max(1, Math.round(selection.value.height * ratioY))
    const canvas = document.createElement('canvas')
    canvas.width = Math.min(sw, source.naturalWidth - sx)
    canvas.height = Math.min(sh, source.naturalHeight - sy)
    canvas.getContext('2d').drawImage(source, sx, sy, canvas.width, canvas.height, 0, 0, canvas.width, canvas.height)
    await uploadCanvas(canvas, '裁剪')
    toast('裁剪结果已作为新图片节点添加')
    emit('close')
  } catch (error) {
    toast(`裁剪失败：${error.message}`)
  } finally {
    busy.value = false
  }
}

async function splitGrid() {
  busy.value = true
  try {
    const source = await loadSource()
    const cellWidth = Math.floor(source.naturalWidth / 3)
    const cellHeight = Math.floor(source.naturalHeight / 3)
    if (!cellWidth || !cellHeight) throw new Error('图片尺寸太小，无法切成九宫格')
    for (let row = 0; row < 3; row += 1) {
      for (let col = 0; col < 3; col += 1) {
        const width = col === 2 ? source.naturalWidth - col * cellWidth : cellWidth
        const height = row === 2 ? source.naturalHeight - row * cellHeight : cellHeight
        const canvas = document.createElement('canvas')
        canvas.width = width
        canvas.height = height
        canvas.getContext('2d').drawImage(
          source,
          col * cellWidth, row * cellHeight, width, height,
          0, 0, width, height
        )
        await uploadCanvas(canvas, `九宫格-${row + 1}-${col + 1}`)
      }
    }
    toast('九宫格已切分为 9 张图片节点')
    emit('close')
  } catch (error) {
    toast(`九宫格切分失败：${error.message}`)
  } finally {
    busy.value = false
  }
}

function onKeydown(event) {
  if (event.key === 'Escape' && !busy.value) emit('close')
}

window.addEventListener('keydown', onKeydown)
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <div class="image-tools-backdrop" @click.self="!busy && emit('close')">
      <section class="image-tools-dialog" role="dialog" aria-modal="true" aria-label="图片处理">
        <header class="image-tools-head">
          <div>
            <strong>图片处理</strong>
            <span>拖动鼠标框选裁剪区域，或切分为九宫格</span>
          </div>
          <button class="btn ghost sm" :disabled="busy" @click="emit('close')">关闭</button>
        </header>
        <div
          ref="stage"
          class="image-tools-stage"
          @pointerdown="startSelection"
          @pointermove="moveSelection"
          @pointerup="endSelection"
          @pointercancel="endSelection"
        >
          <img :src="image.url" :alt="image.name" draggable="false" />
          <div v-if="selection" class="image-crop-box" :style="selectionStyle"><span>裁剪区域</span></div>
          <div v-if="busy" class="image-tools-busy">处理中…</div>
        </div>
        <footer class="image-tools-actions">
          <button class="btn ghost" :disabled="busy" @click="splitGrid">九宫格切分</button>
          <button class="btn primary" :disabled="busy" @click="cropImage">裁剪并新增图片</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.image-tools-backdrop {
  position: fixed;
  inset: 0;
  z-index: 120;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(10, 12, 18, 0.76);
}
.image-tools-dialog {
  display: flex;
  flex-direction: column;
  width: min(900px, 94vw);
  max-height: 92vh;
  padding: 18px;
  gap: 14px;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: var(--panel);
  color: var(--text);
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.45);
}
.image-tools-head,
.image-tools-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.image-tools-head div {
  display: grid;
  gap: 4px;
}
.image-tools-head strong { font-size: 16px; }
.image-tools-head span { font-size: 12px; color: var(--muted); }
.image-tools-stage {
  position: relative;
  display: grid;
  place-items: center;
  width: fit-content;
  max-width: 100%;
  max-height: 68vh;
  align-self: center;
  overflow: hidden;
  background: #111;
  cursor: crosshair;
  touch-action: none;
  user-select: none;
}
.image-tools-stage img {
  display: block;
  max-width: 100%;
  max-height: 68vh;
  pointer-events: none;
  object-fit: contain;
}
.image-crop-box {
  position: absolute;
  border: 2px solid #fff;
  outline: 9999px solid rgba(0, 0, 0, 0.48);
  pointer-events: none;
}
.image-crop-box span {
  position: absolute;
  top: 4px;
  left: 4px;
  padding: 2px 5px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.65);
  color: #fff;
  font-size: 11px;
}
.image-tools-busy {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 15px;
}
@media (max-width: 600px) {
  .image-tools-backdrop { padding: 10px; }
  .image-tools-dialog { padding: 12px; }
  .image-tools-actions { flex-direction: column-reverse; align-items: stretch; }
}
</style>
