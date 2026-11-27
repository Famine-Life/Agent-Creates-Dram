<script setup>
import { computed, markRaw, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'

import Sidebar from './components/Sidebar.vue'
import ShotInspector from './components/ShotInspector.vue'
import NewShotDialog from './components/NewShotDialog.vue'
import PreviewDialog from './components/PreviewDialog.vue'
import McpDialog from './components/McpDialog.vue'
import AssetDialog from './components/AssetDialog.vue'
import PromptRecordDialog from './components/PromptRecordDialog.vue'
import UsageGuide from './components/UsageGuide.vue'
import ShotNode from './components/ShotNode.vue'
import ImageNode from './components/ImageNode.vue'
import ImageGenNode from './components/ImageGenNode.vue'
import ScriptNode from './components/ScriptNode.vue'
import TextNode from './components/TextNode.vue'
import GroupNode from './components/GroupNode.vue'
import ImageRefEdge from './components/ImageRefEdge.vue'
import LinkEdge from './components/LinkEdge.vue'
import CanvasContextMenu from './components/CanvasContextMenu.vue'
import CanvasMenu from './components/CanvasMenu.vue'

import {
  state,
  loadAll,
  loadWorkspaces,
  createWorkspace,
  connectRealtime,
  disconnectRealtime,
  createEdge,
  removeEdge,
  removeLink,
  moveImage,
  saveOrder,
  uploadImageFile,
  createCanvasNode,
  updateCanvasNode,
  createAiImageNode,
  createImageNode,
  createShot,
  addLink,
  fillScriptPrompt,
  fillImagePrompt,
  fillShotPrompt,
  removeTask,
  removeImage,
  removeCanvasNode,
  retryTasks,
  cancelTasks,
  removeTasks,
  resetCanvas,
  toast
} from './store/canvas'
import { api } from './api'
import { historyBusy, historyTransaction, missingHistoryTarget, recordHistory, undoLatest, withoutHistory } from './store/history'
import { authState, logout as authLogout, refreshAccount } from './store/auth'
import { theme, toggleTheme } from './theme'

const route = useRoute()
const router = useRouter()
const accountMenu = ref(false)

/** 把当前画布 ID 同步到路由，保证地址可分享、刷新后仍打开同一画布 */
function syncCanvasRoute(workspaceId) {
  if (route.name === 'canvas' && Number(route.params.workspaceId) === Number(workspaceId)) return
  router.replace({ name: 'canvas', params: { workspaceId } }).catch(() => {})
}

/** 退出登录：断开实时连接、清空画布与本地缓存后回到登录页 */
async function handleLogout() {
  accountMenu.value = false
  clearTimeout(saveTimer)
  ready = false
  try {
    await authLogout()
  } catch (e) {
    toast(`退出登录失败：${e.message}`)
  }
  resetCanvas()
  router.push({ name: 'home' })
}

const SHOT_W = 320
const SHOT_H = 220
const COL_GAP = 28
const ROW_STEP = SHOT_H + 230
const PAD_X = 72
const PAD_Y = 96
const IMG_W = 148
const IMG_H = 166

const nodeTypes = {
  shot: markRaw(ShotNode),
  image: markRaw(ImageNode),
  imageGen: markRaw(ImageGenNode),
  script: markRaw(ScriptNode),
  text: markRaw(TextNode),
  group: markRaw(GroupNode)
}
const edgeTypes = { ref: markRaw(ImageRefEdge), link: markRaw(LinkEdge) }

const nodes = ref([])
const edges = ref([])
const showNewShot = ref(false)
const showMcp = ref(false)
const showAssets = ref(false)
const showPromptRecords = ref(false)
const showGuide = ref(false)
const emptyDismissed = ref(localStorage.getItem('aigc.empty.dismissed') === '1')
watch(emptyDismissed, (value) => {
  localStorage.setItem('aigc.empty.dismissed', value ? '1' : '0')
})
const fileInput = ref(null)
const canvasWrap = ref(null)
const dragging = ref(false)
const contextMenu = ref(null)
const linkPicker = ref(null)
const batchLink = ref(null)
const batchLinkBusy = ref(false)
const batchTargetInput = ref(null)
// 画布交互模式：select 为框选，pan 为拖动画布
const interactionMode = ref('select')
// 框选模式下按住空格临时切换为拖动画布，松开恢复
const spacePressed = ref(false)
const panOnDrag = computed(() =>
  interactionMode.value === 'pan' || spacePressed.value ? true : [1]
)

function toggleInteractionMode() {
  interactionMode.value = interactionMode.value === 'select' ? 'pan' : 'select'
}

function onKeyup(event) {
  if (event.code === 'Space') spacePressed.value = false
}

function resetSpacePan() {
  spacePressed.value = false
}

/** 打开使用说明并定位到画布快捷键 */
function openGuideKeys() {
  showGuide.value = true
  nextTick(() => document.getElementById('guide-keys')?.scrollIntoView({ block: 'start' }))
}
watch(() => state.workspaceId, () => {
  batchLink.value = null
  linkPicker.value = null
})
let lastCanvasPointer = null
const newShotPosition = ref(null)
const pendingUpload = ref(null)
// 侧栏折叠状态记忆在本地，刷新后保持
const sidebarCollapsed = ref(localStorage.getItem('aigc.sidebar.collapsed') === '1')
watch(sidebarCollapsed, (value) => {
  localStorage.setItem('aigc.sidebar.collapsed', value ? '1' : '0')
})
const deletingCanvas = ref(false)
const inspectorHidden = ref(localStorage.getItem('aigc.inspector.hidden') === '1')
watch(inspectorHidden, (value) => {
  localStorage.setItem('aigc.inspector.hidden', value ? '1' : '0')
})

const explicit = new Map()
let snapshotPositions = {}
let saveTimer = null
let snapshotSaving = null
let ready = false

const { setViewport, getViewport, fitView, viewport, onMoveEnd, onNodesInitialized, screenToFlowCoordinate, onConnectEnd, connectionStartHandle, getSelectedNodes, getSelectedEdges, userSelectionRect, applyEdgeChanges, onSelectionEnd: onSelectionEndHook, removeSelectedElements, findNode } =
  useVueFlow()
// 开启视口渲染后，视野外的节点不会被挂载和测量，不能再等 nodesInitialized；
// 未测量的节点在一键排列时使用默认尺寸兜底。
const canArrange = computed(() => !state.loading && !deletingCanvas.value && !historyBusy.value)
// 刚载入画布时先完整挂载一次，保证尺寸测量和 fitView 正确，随后再按视口渲染
const virtualizeNodes = ref(false)
let virtualizationTimer = null

function scheduleVirtualization(delay) {
  clearTimeout(virtualizationTimer)
  virtualizeNodes.value = false
  virtualizationTimer = setTimeout(() => {
    virtualizeNodes.value = true
  }, delay)
}

/** 等节点挂载并完成 ResizeObserver 测量，供一键排列和镜头聚焦使用 */
function waitForMeasurement() {
  return nextTick().then(
    () => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))
  )
}

const groups = computed(() => {
  const map = new Map()
  for (const task of state.tasks) {
    const key = task.groupId || '默认分组'
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(task)
  }
  return [...map.entries()].map(([name, tasks]) => ({ name, tasks }))
})

const codes = computed(() => {
  const map = {}
  groups.value.forEach((group, groupIndex) => {
    const letter =
      (group.name.match(/[A-Za-z]/) || [])[0]?.toUpperCase() ||
      String.fromCharCode(65 + groupIndex)
    group.tasks.forEach((task) => {
      const index = task.shotIndex || 1
      map[task.id] = `${letter}-${String(index).padStart(2, '0')}`
    })
  })
  return map
})

const connectionText = computed(() => {
  if (state.connection === 'online') return '实时连接'
  if (state.connection === 'connecting') return '连接中'
  return '已断开'
})

/** 当前画布名称：顶部标题与切换提示共用；列表未加载时回落到“默认工作空间” */
const currentCanvasName = computed(
  () => state.workspaces.find((item) => item.id === state.workspaceId)?.name || '默认工作空间'
)

// 画布点阵背景随主题切换，避免白天模式下仍是深色网格
const patternColor = computed(() => (theme.value === 'light' ? '#dfe4ee' : '#24282F'))

// 正从图片锚点拖出连线时给镜头卡片加高亮，提示整张卡片都可落点
const linking = computed(() => connectionStartHandle.value?.nodeId?.startsWith('image-'))

const selectedNodes = computed(() => getSelectedNodes.value.filter((node) => node.type !== 'group'))
// 画布多选：镜头与图片分别显示对应的批量操作
const selectedShots = computed(() => selectedNodes.value.filter((node) => node.type === 'shot'))
const selectedImages = computed(() => selectedNodes.value.filter((node) => node.type === 'image' || node.type === 'imageGen'))
// 连线多选：点击连线选中，Ctrl / Cmd + 点击追加，Backspace 批量删除
const selectedEdges = computed(() => getSelectedEdges.value || [])

// 多选框右侧的批量连线按钮：把所选节点的包围盒换算到画布屏幕坐标，跟随缩放和拖拽
const batchLinkAnchor = computed(() => {
  if (selectedNodes.value.length < 2) return null
  const zoom = viewport.value.zoom || 1
  const offsetX = viewport.value.x || 0
  const offsetY = viewport.value.y || 0
  let left = Infinity
  let top = Infinity
  let right = -Infinity
  let bottom = -Infinity
  for (const node of selectedNodes.value) {
    const position = node.computedPosition || node.position
    if (!position) continue
    const width = node.dimensions?.width ?? node.width ?? 0
    const height = node.dimensions?.height ?? node.height ?? 0
    const nodeLeft = position.x * zoom + offsetX
    const nodeTop = position.y * zoom + offsetY
    left = Math.min(left, nodeLeft)
    top = Math.min(top, nodeTop)
    right = Math.max(right, nodeLeft + width * zoom)
    bottom = Math.max(bottom, nodeTop + height * zoom)
  }
  if (!Number.isFinite(left) || !Number.isFinite(top)) return null
  return { left: right + 12, top: top + (bottom - top) / 2 }
})

// 多选框右侧的 ＋ 手柄：按住拖到目标节点即可批量连线，单击回落到弹窗选择
const batchLinkDrag = ref(null)
let batchLinkHoverEl = null
let batchLinkDragSources = []

function canvasPoint(event) {
  const rect = canvasWrap.value?.getBoundingClientRect()
  return { x: event.clientX - (rect?.left || 0), y: event.clientY - (rect?.top || 0) }
}

function findLinkTarget(clientX, clientY) {
  const element = document.elementFromPoint(clientX, clientY)
  const nodeEl = element?.closest?.('.vue-flow__node')
  const id = nodeEl?.dataset.id
  if (!id || batchLinkDragSources.includes(id)) return null
  const node = findNode(id)
  return node && linkPromptTargets[node.type] ? nodeEl : null
}

function startBatchLinkDrag(event) {
  if (!batchLinkAnchor.value || batchLinkBusy.value || historyBusy.value) return
  event.preventDefault()
  batchLinkDragSources = selectedNodes.value.map((node) => node.id)
  const anchor = batchLinkAnchor.value
  batchLinkDrag.value = {
    start: { x: anchor.left + 12, y: anchor.top },
    point: canvasPoint(event),
    origin: { x: event.clientX, y: event.clientY },
    hoverId: null
  }
  window.addEventListener('pointermove', onBatchLinkDragMove)
  window.addEventListener('pointerup', onBatchLinkDragEnd)
  window.addEventListener('pointercancel', onBatchLinkDragEnd)
}

function onBatchLinkDragMove(event) {
  const drag = batchLinkDrag.value
  if (!drag) return
  const targetEl = findLinkTarget(event.clientX, event.clientY)
  if (targetEl !== batchLinkHoverEl) {
    batchLinkHoverEl?.classList.remove('batch-link-target')
    targetEl?.classList.add('batch-link-target')
    batchLinkHoverEl = targetEl
  }
  batchLinkDrag.value = {
    ...drag,
    point: canvasPoint(event),
    hoverId: targetEl?.dataset.id || null
  }
}

async function onBatchLinkDragEnd(event) {
  window.removeEventListener('pointermove', onBatchLinkDragMove)
  window.removeEventListener('pointerup', onBatchLinkDragEnd)
  window.removeEventListener('pointercancel', onBatchLinkDragEnd)
  const canceled = event.type === 'pointercancel'
  const drag = batchLinkDrag.value
  const sources = batchLinkDragSources
  batchLinkDrag.value = null
  batchLinkDragSources = []
  batchLinkHoverEl?.classList.remove('batch-link-target')
  batchLinkHoverEl = null
  if (!drag || canceled) return
  // 原地单击：沿用弹窗选择目标
  if (!drag.hoverId) {
    if (Math.hypot(event.clientX - drag.origin.x, event.clientY - drag.origin.y) < 4) openBatchLink()
    return
  }
  const target = findNode(drag.hoverId)
  if (!target) return
  const results = sources.map((id) => resolveLink(findNode(id), target))
  const plans = results.filter((plan) => !plan.error)
  if (!plans.length) {
    const firstError = results.find((plan) => plan.error)
    if (firstError) toast(firstError.error)
    return
  }
  // 目标包含分镜选择时仍需弹窗确认写入哪一场
  if (plans.some((plan) => plan.shots)) {
    batchLink.value = { sources, target: target.id, shotChoices: {} }
    await nextTick()
    batchTargetInput.value?.focus()
    return
  }
  await connectBatch(target.id, plans, sources)
}

const batchLinkPath = computed(() => {
  const drag = batchLinkDrag.value
  if (!drag) return ''
  const { start, point } = drag
  const curve = Math.max(40, Math.abs(point.x - start.x) / 2)
  return `M ${start.x} ${start.y} C ${start.x + curve} ${start.y}, ${point.x - curve} ${point.y}, ${point.x} ${point.y}`
})

// Shift + 拖拽框选：把与选框相交的连线一并选中（Vue Flow 默认只选中框内节点及其连线）
let lastSelectionRect = null
watch(userSelectionRect, (rect) => {
  if (rect) lastSelectionRect = { ...rect }
}, { flush: 'sync' })

function edgesIntersectingRect(rect) {
  const zoom = viewport.value.zoom || 1
  const area = {
    x: (rect.x - viewport.value.x) / zoom,
    y: (rect.y - viewport.value.y) / zoom,
    width: rect.width / zoom,
    height: rect.height / zoom
  }
  const ids = []
  for (const group of document.querySelectorAll('.vue-flow__edge')) {
    const path = group.querySelector('path.vue-flow__edge-path')
    if (!path) continue
    const box = path.getBBox()
    if (box.x > area.x + area.width || box.x + box.width < area.x || box.y > area.y + area.height || box.y + box.height < area.y) continue
    const total = path.getTotalLength()
    if (!total) continue
    const steps = Math.max(6, Math.ceil(total / 6))
    for (let i = 0; i <= steps; i += 1) {
      const point = path.getPointAtLength((total * i) / steps)
      if (point.x >= area.x && point.x <= area.x + area.width && point.y >= area.y && point.y <= area.y + area.height) {
        if (group.dataset.id) ids.push(group.dataset.id)
        break
      }
    }
  }
  return ids
}

onSelectionEndHook(() => {
  const rect = lastSelectionRect
  lastSelectionRect = null
  if (!rect || !rect.width || !rect.height) return
  const ids = edgesIntersectingRect(rect)
  if (ids.length) applyEdgeChanges(ids.map((id) => ({ id, type: 'select', selected: true })))
})

async function batchRetry() {
  const ids = selectedShots.value.map((node) => Number(node.id.slice(5)))
  if (!ids.length) return
  await retryTasks(ids)
}

async function batchCancel() {
  const ids = selectedShots.value.map((node) => Number(node.id.slice(5)))
  if (!ids.length) return
  await cancelTasks(ids)
}

function crc32(bytes) {
  let crc = 0xffffffff
  for (const byte of bytes) {
    crc ^= byte
    for (let bit = 0; bit < 8; bit += 1) crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0)
  }
  return (crc ^ 0xffffffff) >>> 0
}

function createZipBlob(files) {
  const encoder = new TextEncoder()
  const localParts = []
  const centralParts = []
  let offset = 0
  const now = new Date()
  const dosTime = (now.getHours() << 11) | (now.getMinutes() << 5) | Math.floor(now.getSeconds() / 2)
  const dosDate = ((now.getFullYear() - 1980) << 9) | ((now.getMonth() + 1) << 5) | now.getDate()
  const write16 = (view, pos, value) => view.setUint16(pos, value, true)
  const write32 = (view, pos, value) => view.setUint32(pos, value, true)

  for (const file of files) {
    const name = encoder.encode(file.name)
    const data = file.data
    const crc = crc32(data)
    const local = new Uint8Array(30 + name.length)
    const localView = new DataView(local.buffer)
    write32(localView, 0, 0x04034b50)
    write16(localView, 4, 20)
    write16(localView, 6, 0x0800)
    write16(localView, 8, 0)
    write16(localView, 10, dosTime)
    write16(localView, 12, dosDate)
    write32(localView, 14, crc)
    write32(localView, 18, data.length)
    write32(localView, 22, data.length)
    write16(localView, 26, name.length)
    local.set(name, 30)
    localParts.push(local, data)

    const central = new Uint8Array(46 + name.length)
    const centralView = new DataView(central.buffer)
    write32(centralView, 0, 0x02014b50)
    write16(centralView, 4, 20)
    write16(centralView, 6, 20)
    write16(centralView, 8, 0x0800)
    write16(centralView, 10, 0)
    write16(centralView, 12, dosTime)
    write16(centralView, 14, dosDate)
    write32(centralView, 16, crc)
    write32(centralView, 20, data.length)
    write32(centralView, 24, data.length)
    write16(centralView, 28, name.length)
    write32(centralView, 42, offset)
    central.set(name, 46)
    centralParts.push(central)
    offset += local.length + data.length
  }

  const centralSize = centralParts.reduce((sum, part) => sum + part.length, 0)
  const end = new Uint8Array(22)
  const endView = new DataView(end.buffer)
  write32(endView, 0, 0x06054b50)
  write16(endView, 8, files.length)
  write16(endView, 10, files.length)
  write32(endView, 12, centralSize)
  write32(endView, 16, offset)
  return new Blob([...localParts, ...centralParts, end], { type: 'application/zip' })
}

async function batchDownloadVideos() {
  const tasks = selectedShots.value
    .map((node) => state.tasks.find((task) => task.id === Number(node.id.slice(5))))
    .filter((task) => task?.videoUrl)
  if (tasks.length >= 3) {
    try {
      const files = await Promise.all(tasks.map(async (task, index) => {
        const response = await fetch(task.videoUrl)
        if (!response.ok) throw new Error(`下载《${task.title || task.id}》失败：HTTP ${response.status}`)
        const filename = (task.title || `shot-${task.id}`).replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_')
        return { name: `${String(index + 1).padStart(2, '0')}-${filename}.mp4`, data: new Uint8Array(await response.arrayBuffer()) }
      }))
      const url = URL.createObjectURL(createZipBlob(files))
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `视频合集-${new Date().toISOString().slice(0, 10)}.zip`
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      setTimeout(() => URL.revokeObjectURL(url), 60_000)
    } catch (error) {
      toast(`打包下载失败：${error.message}`)
    }
    return
  }
  for (const task of tasks) {
    const anchor = document.createElement('a')
    anchor.href = task.videoUrl
    const filename = (task.title || `shot-${task.id}`).replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_')
    anchor.download = `${filename}.mp4`
    anchor.rel = 'noreferrer'
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
  }
  if (!tasks.length) toast('选中的镜头还没有可下载的视频')
}

async function batchRemove() {
  const ids = selectedShots.value.map((node) => Number(node.id.slice(5)))
  if (!ids.length) return
  await removeTasks(ids)
  removeSelectedElements()
}

function batchDownloadImages() {
  const images = selectedImages.value
    .map((node) => state.images.find((image) => image.id === Number(node.id.slice(6))))
    .filter((image) => image?.url)
  for (const image of images) {
    const anchor = document.createElement('a')
    anchor.href = image.url
    anchor.download = image.name || `image-${image.id}`
    anchor.rel = 'noreferrer'
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
  }
}

async function batchRemoveImages() {
  const images = selectedImages.value
    .map((node) => state.images.find((image) => image.id === Number(node.id.slice(6))))
    .filter(Boolean)
  if (!images.length) return
  if (!window.confirm(`确定删除选中的 ${images.length} 张图片？相关连线也会一并移除。`)) return
  let removed = 0
  for (const image of images) {
    try {
      await removeImage(image.id)
      removed += 1
    } catch (error) {
      toast(`删除图片失败：${error.message}`)
    }
  }
  if (removed) toast(`已删除 ${removed} 张图片`)
}

async function batchRemoveEdges() {
  const list = selectedEdges.value
  if (!list.length) return
  let removed = 0
  await historyTransaction('批量删除连线', async () => {
    for (const edge of list) {
      try {
        if (edge.type === 'ref') await removeEdge(edge.data.edgeId)
        else if (edge.type === 'link') removeLink(edge.data.linkId)
        else continue
        removed += 1
      } catch (error) {
        toast(`删除连线失败：${error.message}`)
      }
    }
  })
  if (removed) toast(`已删除 ${removed} 条连线`)
}

function clearSelection() {
  removeSelectedElements()
}

function computeLayout() {
  const map = new Map()
  let y = PAD_Y
  for (const group of groups.value) {
    map.set(`group-${group.name}`, { x: PAD_X, y: y - 36 })
    group.tasks.forEach((task, index) => {
      map.set(`shot-${task.id}`, { x: PAD_X + index * (SHOT_W + COL_GAP), y })
    })
    y += ROW_STEP
  }
  return { map, bottom: y - ROW_STEP + SHOT_H + 200 }
}

const TYPE_ORDER = { text: 0, script: 1, image: 2, imageGen: 2, shot: 3 }
const TYPE_FALLBACK_SIZE = {
  shot: { width: SHOT_W, height: SHOT_H },
  image: { width: IMG_W, height: IMG_H },
  imageGen: { width: IMG_W, height: IMG_H },
  text: { width: 280, height: 140 },
  script: { width: 300, height: 320 }
}

function nodeBox(node) {
  const measured = findNode(node.id)
  const dimensions = measured?.dimensions || node.dimensions || {}
  const fallback = TYPE_FALLBACK_SIZE[node?.type] || { width: 260, height: 160 }
  return {
    width: Math.ceil(dimensions.width || 0) || fallback.width,
    height: Math.ceil(dimensions.height || 0) || fallback.height
  }
}

/**
 * 一键排列：按连线把节点分成若干组，组内按文本 → 脚本 → 图片 → 视频分行，行内超宽自动换行。
 * 若存在 3 对及以上「1 张图片只连 1 个镜头、且该镜头只连这张图片」的简单参考图组合，
 * 先把这些组合按每行 5 组排列（图片在左、镜头在右并垂直居中，使右侧图片锚点与左侧镜头锚点水平对齐），
 * 再排其余复杂连线块和孤立节点。
 */
function arrangedPositions() {
  const positions = new Map()
  const graphNodes = nodes.value.filter((node) => node.type !== 'group')
  const known = new Set(graphNodes.map((node) => node.id))
  const nodeById = new Map(graphNodes.map((node) => [node.id, node]))

  const neighbors = new Map(graphNodes.map((node) => [node.id, new Set()]))
  for (const edge of edges.value) {
    if (!known.has(edge.source) || !known.has(edge.target)) continue
    neighbors.get(edge.source).add(edge.target)
    neighbors.get(edge.target).add(edge.source)
  }

  // 识别简单的「图片 ↔ 镜头」一对一参考图组合
  const pairs = []
  const pairedIds = new Set()
  for (const node of graphNodes) {
    if (node.type !== 'image' && node.type !== 'imageGen') continue
    const linked = neighbors.get(node.id)
    if (linked.size !== 1) continue
    const shotId = [...linked][0]
    const shot = nodeById.get(shotId)
    if (shot?.type !== 'shot' || neighbors.get(shotId).size !== 1) continue
    pairs.push({ image: node, shot })
    pairedIds.add(node.id)
    pairedIds.add(shotId)
  }

  const clusterBounds = new Map()
  let y = PAD_Y
  const pairLayout = pairs.length >= 3

  if (pairLayout) {
    pairs.sort((a, b) => {
      const left = a.shot.data?.task
      const right = b.shot.data?.task
      return (
        String(left?.groupId || '').localeCompare(String(right?.groupId || '')) ||
        (left?.shotIndex ?? 0) - (right?.shotIndex ?? 0) ||
        String(a.shot.id).localeCompare(String(b.shot.id))
      )
    })
    const pairGapX = COL_GAP
    const pairGapY = COL_GAP * 2
    // 使用节点实际尺寸，避免上传图片或镜头卡片比默认尺寸宽时互相重叠
    const pairBoxes = pairs.map((pair) => ({ pair, image: nodeBox(pair.image), shot: nodeBox(pair.shot) }))
    const maxImageWidth = Math.max(...pairBoxes.map((item) => item.image.width))
    const maxShotWidth = Math.max(...pairBoxes.map((item) => item.shot.width))
    const unitWidth = maxImageWidth + pairGapX + maxShotWidth
    const unitHeight = Math.max(...pairBoxes.map((item) => Math.max(item.image.height, item.shot.height)))
    pairBoxes.forEach(({ pair, image, shot }, index) => {
      const column = index % 5
      if (index > 0 && column === 0) y += unitHeight + pairGapY
      const x = PAD_X + column * (unitWidth + COL_GAP)
      // 图片右缘对齐、镜头左缘对齐且整体垂直居中，连线水平等长
      positions.set(pair.image.id, { x: x + maxImageWidth - image.width, y: y + (unitHeight - image.height) / 2 })
      positions.set(pair.shot.id, { x: x + maxImageWidth + pairGapX, y: y + (unitHeight - shot.height) / 2 })
      clusterBounds.set(pair.image.id, { left: x, top: y })
      clusterBounds.set(pair.shot.id, { left: x, top: y })
    })
    y += unitHeight + 48
  }

  const remainingNodes = pairLayout ? graphNodes.filter((node) => !pairedIds.has(node.id)) : graphNodes
  const remainingKnown = new Set(remainingNodes.map((node) => node.id))

  // 并查集：有连线的节点归为同一组
  const roots = new Map(remainingNodes.map((node) => [node.id, node.id]))
  function rootOf(id) {
    let current = id
    while (roots.get(current) !== current) current = roots.get(current)
    while (roots.get(id) !== current) {
      const next = roots.get(id)
      roots.set(id, current)
      id = next
    }
    return current
  }
  const linked = new Set()
  for (const edge of edges.value) {
    if (!remainingKnown.has(edge.source) || !remainingKnown.has(edge.target)) continue
    linked.add(edge.source)
    linked.add(edge.target)
    const sourceRoot = rootOf(edge.source)
    const targetRoot = rootOf(edge.target)
    if (sourceRoot !== targetRoot) roots.set(sourceRoot, targetRoot)
  }

  const clusters = new Map()
  const isolated = []
  for (const node of remainingNodes) {
    if (!linked.has(node.id)) {
      isolated.push(node)
      continue
    }
    const key = rootOf(node.id)
    if (!clusters.has(key)) clusters.set(key, [])
    clusters.get(key).push(node)
  }
  const blocks = [...clusters.values()].sort((a, b) => b.length - a.length)
  if (isolated.length) blocks.push(isolated)

  const maxRowRight = PAD_X + 4 * SHOT_W + 3 * COL_GAP

  for (const members of blocks) {
    const rows = new Map()
    for (const node of members) {
      const order = TYPE_ORDER[node.type] ?? 9
      if (!rows.has(order)) rows.set(order, [])
      rows.get(order).push(node)
    }
    const top = y
    for (const order of [...rows.keys()].sort((a, b) => a - b)) {
      const rowNodes = rows.get(order).sort((a, b) => {
        const left = a.data?.task?.shotIndex ?? 0
        const right = b.data?.task?.shotIndex ?? 0
        return left - right || String(a.id).localeCompare(String(b.id))
      })
      let x = PAD_X
      let rowHeight = 0
      for (const node of rowNodes) {
        const { width, height } = nodeBox(node)
        // 同一类型排成一行，超过一行宽度后换行
        if (x > PAD_X && x + width > maxRowRight) {
          x = PAD_X
          y += rowHeight + COL_GAP
          rowHeight = 0
        }
        positions.set(node.id, { x, y })
        x += width + COL_GAP
        rowHeight = Math.max(rowHeight, height)
      }
      y += rowHeight + COL_GAP * 2
    }
    for (const node of members) clusterBounds.set(node.id, { left: PAD_X, top })
    y += 48
  }

  // 分组标签放到对应镜头所在块的上方，同一块内多个标签向上错开
  const labelRows = new Map()
  for (const group of groups.value) {
    const firstShot = group.tasks[0] ? `shot-${group.tasks[0].id}` : null
    const bounds = firstShot ? clusterBounds.get(firstShot) : null
    if (!bounds) {
      positions.set(`group-${group.name}`, { x: PAD_X, y })
      y += 40
      continue
    }
    const stacked = labelRows.get(bounds.top) || 0
    labelRows.set(bounds.top, stacked + 1)
    positions.set(`group-${group.name}`, { x: bounds.left, y: bounds.top - 34 - stacked * 20 })
  }
  return positions
}

function applyPositions(positions) {
  for (const node of nodes.value) {
    const position = positions.get(node.id)
    if (!position) continue
    explicit.set(node.id, { ...position })
    node.position = { ...position }
  }
}

async function arrangeCanvas() {
  if (!ready || !canArrange.value) return
  const workspaceId = state.workspaceId
  const previousViewport = getViewport()
  // 排列依赖节点实际尺寸，先临时完整挂载，避免视野外节点用默认尺寸导致重叠
  virtualizeNodes.value = false
  await waitForMeasurement()
  if (state.workspaceId !== workspaceId) return
  const positions = arrangedPositions()
  const before = new Map()
  for (const node of nodes.value) {
    const position = positions.get(node.id)
    if (node.position.x !== position.x || node.position.y !== position.y) {
      before.set(node.id, { ...node.position })
    }
  }
  if (before.size) {
    applyPositions(positions)
    recordHistory({
      workspaceId,
      label: '一键排列',
      undo: () => {
        applyPositions(before)
        return setViewport(previousViewport)
      }
    })
    scheduleSave()
  }
  await nextTick()
  if (state.workspaceId !== workspaceId) return
  fitView({ padding: 0.16, duration: 260, maxZoom: 1 })
  scheduleVirtualization(600)
}

function pickPos(id, fallback) {
  return explicit.get(id) || snapshotPositions[id] || fallback
}

function syncGraph() {
  const { map, bottom } = computeLayout()
  const previous = new Map(nodes.value.map((node) => [node.id, node]))
  const next = []
  const ids = new Set()

  for (const group of groups.value) {
    const id = `group-${group.name}`
    const data = { name: group.name, count: group.tasks.length }
    const position = pickPos(id, map.get(id))
    const old = previous.get(id)
    const node = old
      ? Object.assign(old, { position: { ...position }, data })
      : {
          id,
          type: 'group',
          position: { ...position },
          data,
          draggable: false,
          selectable: false,
          focusable: false,
          connectable: false
        }
    next.push(node)
    ids.add(id)
  }

  for (const task of state.tasks) {
    const id = `shot-${task.id}`
    const data = { task, code: codes.value[task.id] }
    const old = previous.get(id)
    const position = pickPos(id, map.get(id))
    const node = old
      ? Object.assign(old, { position: { ...position }, data })
      : { id, type: 'shot', position: { ...position }, data }
    next.push(node)
    ids.add(id)
  }

  // 预计算每张图片的首条连线与每个镜头的图片数，避免图片多时出现 O(图片² × 连线) 的查找
  const firstEdgeByImage = new Map()
  const imagesPerTask = new Map()
  for (const edge of state.edges) {
    if (firstEdgeByImage.has(edge.imageId)) continue
    firstEdgeByImage.set(edge.imageId, edge)
    imagesPerTask.set(edge.taskId, (imagesPerTask.get(edge.taskId) || 0) + 1)
  }

  let counter = 0
  for (const image of state.images) {
    const id = `image-${image.id}`
    let fallback
    if (image.x != null && image.y != null) {
      fallback = { x: image.x, y: image.y }
    } else {
      const edge = firstEdgeByImage.get(image.id)
      const shotPos = edge ? map.get(`shot-${edge.taskId}`) : null
      if (shotPos) {
        const sibling = (imagesPerTask.get(edge.taskId) || 1) - 1
        fallback = { x: shotPos.x + sibling * (IMG_W + 16), y: shotPos.y + SHOT_H + 20 }
      } else {
        fallback = { x: PAD_X + counter * (IMG_W + 18), y: bottom + 16 }
        counter += 1
      }
    }
    const position = pickPos(id, fallback)
    const old = previous.get(id)
    const type = image.prompt != null || image.status ? 'imageGen' : 'image'
    const node =
      old && old.type === type
        ? Object.assign(old, { position: { ...position }, data: { image } })
        : { id, type, position: { ...position }, data: { image } }
    next.push(node)
    ids.add(id)
  }

  let nodeCounter = 0
  for (const item of state.canvasNodes) {
    const id = `node-${item.id}`
    const fallback = {
      x: PAD_X + nodeCounter * (SHOT_W + COL_GAP),
      y: bottom + 40
    }
    nodeCounter += 1
    const position = pickPos(id, fallback)
    const old = previous.get(id)
    const node = old
      ? Object.assign(old, { position: { ...position }, data: { node: item } })
      : { id, type: item.type, position: { ...position }, data: { node: item } }
    next.push(node)
    ids.add(id)
  }

  nodes.value = next

  edges.value = [
    ...state.edges
      .filter((edge) => ids.has(`image-${edge.imageId}`) && ids.has(`shot-${edge.taskId}`))
      .map((edge) => ({
        id: `edge-${edge.id}`,
        type: 'ref',
        source: `image-${edge.imageId}`,
        target: `shot-${edge.taskId}`,
        data: { edgeId: edge.id }
      })),
    ...state.links
      .filter((link) => ids.has(link.source) && ids.has(link.target))
      .map((link) => ({
        id: `linkedge-${link.id}`,
        type: 'link',
        source: link.source,
        target: link.target,
        data: { linkId: link.id }
      }))
  ]
}

watch(() => [state.tasks, state.images, state.canvasNodes, state.edges, state.links], syncGraph, { deep: true })
watch(
  () => [state.links, state.imageSettings],
  () => {
    if (ready) scheduleSave()
  },
  { deep: true }
)
watch(
  () => state.snapshot,
  (snapshot) => {
    snapshotPositions = snapshot?.positions || {}
  },
  { immediate: true }
)

function onNodeDrag({ node }) {
  if (!node) return
  explicit.set(node.id, { x: node.position.x, y: node.position.y })
}

// Alt + 拖动：复制节点但不带连线，原节点留在原位
const COPY_OFFSET = 36
let altDrag = null
let dragStartState = null

function captureMoveState(list) {
  const positions = {}
  for (const node of list) {
    if (node && node.type !== 'group') positions[node.id] = { x: node.position.x, y: node.position.y }
  }
  return {
    positions,
    order: state.tasks.map((task) => ({
      taskId: task.id,
      groupId: task.groupId || '默认分组',
      shotIndex: task.shotIndex
    }))
  }
}

function moveStateChanged(before) {
  if (Object.entries(before.positions).some(([id, position]) => {
    const node = nodes.value.find((item) => item.id === id)
    return node && (Math.abs(node.position.x - position.x) > 0.5 || Math.abs(node.position.y - position.y) > 0.5)
  })) return true
  return before.order.some((item) => {
    const task = state.tasks.find((entry) => entry.id === item.taskId)
    return task && ((task.groupId || '默认分组') !== item.groupId || task.shotIndex !== item.shotIndex)
  })
}

async function restoreMoveState(before) {
  const entries = Object.entries(before.positions)
  for (const [id] of entries) {
    if (!nodes.value.some((node) => node.id === id)) throw missingHistoryTarget('被移动的节点已不存在')
  }
  const hasShot = entries.some(([id]) => id.startsWith('shot-'))
  if (hasShot && before.order.some((item) => !state.tasks.some((task) => task.id === item.taskId))) {
    throw missingHistoryTarget('镜头结构已变化，无法恢复移动')
  }
  for (const [id, position] of entries) {
    explicit.set(id, { ...position })
    const node = nodes.value.find((item) => item.id === id)
    if (node) node.position = { ...position }
  }
  const previousOrder = new Map(before.order.map((item) => [item.taskId, item]))
  const orderChanged = hasShot && before.order.some((item) => {
    const task = state.tasks.find((entry) => entry.id === item.taskId)
    return (task.groupId || '默认分组') !== item.groupId || task.shotIndex !== item.shotIndex
  })
  if (orderChanged) {
    const order = state.tasks.map((task) => previousOrder.get(task.id) || {
      taskId: task.id,
      groupId: task.groupId || '默认分组',
      shotIndex: task.shotIndex
    })
    if (order.length) await saveOrder(order)
  }
  for (const [id, position] of entries) {
    if (id.startsWith('image-')) await moveImage(Number(id.slice(6)), position.x, position.y)
    else if (id.startsWith('node-')) await updateCanvasNode(Number(id.slice(5)), { x: position.x, y: position.y })
  }
  syncGraph()
  scheduleSave()
}

function recordDragStop() {
  const before = dragStartState
  dragStartState = null
  if (!before || !moveStateChanged(before)) return
  recordHistory({
    workspaceId: state.workspaceId,
    label: '移动节点',
    composite: true,
    undo: () => restoreMoveState(before)
  })
}

function onNodeDragStart({ node, event }) {
  altDrag =
    event?.altKey && node && node.type !== 'group'
      ? { id: node.id, position: { x: node.position.x, y: node.position.y } }
      : null
  if (altDrag || !node) {
    dragStartState = null
    return
  }
  const selected = selectedNodes.value
  dragStartState = captureMoveState(selected.some((item) => item.id === node.id) ? selected : [node])
}

async function duplicateNodeUnchecked(node, { x, y, withEdges = true }) {
  if (node.type === 'shot') {
    const task = state.tasks.find((item) => item.id === Number(node.id.slice(5)))
    if (!task) return
    // 原任务可能来自已下线的模型或当前环境未配置密钥的平台，复制时退回当前可用模型
    const requestedModel = task.modelKey || task.model
    const availableModels = state.meta.videoModels || []
    const model = availableModels.some((option) => option.value === requestedModel) ? requestedModel : undefined
    if (requestedModel && !model) toast(`模型 ${requestedModel} 当前不可用，已改用默认模型`)
    const created = await createShot({
      prompt: task.prompt,
      groupId: task.groupId,
      model,
      ratio: task.ratio,
      duration: task.duration,
      resolution: task.resolution,
      draft: true
    })
    explicit.set(`shot-${created.id}`, { x, y })
    if (withEdges) {
      const imageIds = state.edges
        .filter((edge) => edge.taskId === task.id)
        .map((edge) => edge.imageId)
      for (const imageId of imageIds) await createEdge(created.id, imageId)
    }
    return `shot-${created.id}`
  }

  if (node.type === 'image' || node.type === 'imageGen') {
    const image = state.images.find((item) => item.id === Number(node.id.slice(6)))
    if (!image) return
    let created
    if (image.url) {
      created = await createImageNode({
        name: image.name,
        url: image.url,
        x,
        y,
        width: image.width,
        height: image.height,
        prompt: image.prompt,
        status: image.status,
        source: image.source
      })
    } else {
      created = await createAiImageNode({ x, y, prompt: image.prompt })
    }
    // 复制图片生成参数（模型 / 比例 / 清晰度 / 数量），让新节点与原节点设置保持一致；
    // imageSettings 的深度 watcher 会自动触发快照保存，无需额外调用 scheduleSave
    const sourceSettings = state.imageSettings[image.id]
    if (sourceSettings) {
      state.imageSettings = { ...state.imageSettings, [created.id]: { ...sourceSettings } }
    }
    explicit.set(`image-${created.id}`, { x, y })
    if (withEdges) {
      const taskIds = state.edges
        .filter((edge) => edge.imageId === image.id)
        .map((edge) => edge.taskId)
      for (const taskId of taskIds) await createEdge(taskId, created.id)
    }
    return `image-${created.id}`
  }

  if (node.type === 'text' || node.type === 'script') {
    const item = state.canvasNodes.find((entry) => entry.id === Number(node.id.slice(5)))
    if (!item) return
    const created = await createCanvasNode({
      type: item.type,
      title: item.title,
      prompt: item.prompt,
      content: item.content,
      x,
      y,
      width: item.width,
      height: item.height
    })
    explicit.set(`node-${created.id}`, { x, y })
    return `node-${created.id}`
  }
}

function duplicateNode(node, options) {
  return withoutHistory(() => duplicateNodeUnchecked(node, options))
}

function selectOnlyNode(nodeId) {
  nodes.value.forEach((node) => {
    node.selected = node.id === nodeId
  })
}

function isTypingTarget(target) {
  if (!target) return false
  const tag = target.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || target.isContentEditable
}

async function deleteNodes(list) {
  for (const node of list) {
    try {
      if (node.type === 'shot') await removeTask(Number(node.id.slice(5)))
      else if (node.type === 'image' || node.type === 'imageGen') await removeImage(Number(node.id.slice(6)))
      else if (node.type === 'text' || node.type === 'script') await removeCanvasNode(Number(node.id.slice(5)))
    } catch (e) {
      toast(`删除失败：${e.message}`)
    }
  }
}

async function undoCanvasAction() {
  if (historyBusy.value) return
  try {
    const undone = await undoLatest(state.workspaceId)
    if (!undone) return
    syncGraph()
    scheduleSave()
    toast('已撤销上一步操作')
  } catch (error) {
    if (error.historyMissing) {
      toast(`已跳过：${error.message}`)
    } else if (error.historyPartial) {
      try {
        await loadAll()
        syncGraph()
      } catch (e) {
        /* 保留刷新失败提示 */
      }
      toast('撤销未完全执行，已刷新画布')
    } else {
      toast(`撤销失败：${error.message}`)
    }
  }
}

function onKeydown(event) {
  if (batchLink.value) {
    if (event.key === 'Escape') closeBatchLink()
    return
  }
  if (isTypingTarget(event.target)) return
  if (event.code === 'Space') {
    event.preventDefault()
    spacePressed.value = true
    return
  }
  if ((event.ctrlKey || event.metaKey) && !event.shiftKey && event.key.toLowerCase() === 'z') {
    event.preventDefault()
    undoCanvasAction()
    return
  }
  const list = selectedNodes.value
  if (!list.length) {
    // 只选中连线时，Backspace / Delete 批量删除连线
    if (event.key === 'Backspace' || event.key === 'Delete') {
      if (!selectedEdges.value.length) return
      event.preventDefault()
      batchRemoveEdges()
    }
    return
  }
  if ((event.ctrlKey || event.metaKey) && !event.shiftKey && event.key.toLowerCase() === 'd') {
    event.preventDefault()
    for (const node of list) {
      duplicateNode(node, { x: node.position.x + COPY_OFFSET, y: node.position.y + COPY_OFFSET })
        .then((id) => {
          if (id && list.length === 1) selectOnlyNode(id)
        })
        .catch((e) => toast(`复制失败：${e.message}`))
    }
    return
  }
  if (event.key === 'Backspace') {
    event.preventDefault()
    deleteNodes(list)
  }
}

function freezeLayout() {
  for (const node of nodes.value) {
    explicit.set(node.id, { x: node.position.x, y: node.position.y })
  }
}

async function applyOrderFromPositions() {
  const byGroup = new Map()
  for (const node of nodes.value) {
    if (node.type !== 'shot') continue
    const taskId = Number(node.id.slice(5))
    const task = state.tasks.find((item) => item.id === taskId)
    if (!task) continue
    const key = task.groupId || '默认分组'
    if (!byGroup.has(key)) byGroup.set(key, [])
    byGroup.get(key).push({ taskId, x: node.position.x })
  }
  const items = []
  for (const [groupId, list] of byGroup) {
    list.sort((a, b) => a.x - b.x)
    list.forEach((entry, index) => {
      const task = state.tasks.find((item) => item.id === entry.taskId)
      if (!task) return
      if (task.groupId !== groupId || task.shotIndex !== index + 1) {
        items.push({ taskId: entry.taskId, groupId, shotIndex: index + 1 })
      }
    })
  }
  if (!items.length) return
  try {
    await saveOrder(items)
  } catch (e) {
    toast(`保存排序失败：${e.message}`)
  }
}

async function onNodeDragStop({ node }) {
  if (!node) return
  if (altDrag && altDrag.id === node.id) {
    const start = altDrag.position
    const drop = { x: node.position.x, y: node.position.y }
    altDrag = null
    node.position = { ...start }
    explicit.set(node.id, { ...start })
    if (Math.abs(drop.x - start.x) > 2 || Math.abs(drop.y - start.y) > 2) {
      try {
        const id = await duplicateNode(node, { x: drop.x, y: drop.y, withEdges: false })
        if (id) selectOnlyNode(id)
      } catch (e) {
        toast(`复制失败：${e.message}`)
      }
    }
    scheduleSave()
    return
  }
  explicit.set(node.id, { x: node.position.x, y: node.position.y })
  freezeLayout()
  if (node.type === 'shot') {
    await applyOrderFromPositions()
  } else if (node.type === 'image' || node.type === 'imageGen') {
    try {
      await moveImage(Number(node.id.slice(6)), node.position.x, node.position.y)
    } catch (e) {
      /* 位置保存失败不阻塞交互 */
    }
  } else if (node.type === 'text' || node.type === 'script') {
    try {
      await updateCanvasNode(Number(node.id.slice(5)), { x: node.position.x, y: node.position.y })
    } catch (e) {
      /* 位置保存失败不阻塞交互 */
    }
  }
  scheduleSave()
  recordDragStop()
}

function scriptShots(node) {
  try {
    const list = JSON.parse(node.content || '[]')
    return Array.isArray(list) ? list.filter((shot) => typeof shot?.prompt === 'string' && shot.prompt.trim()) : []
  } catch (e) {
    return []
  }
}

const linkPromptTargets = {
  shot: { key: 'task', label: '镜头', write: fillShotPrompt },
  imageGen: { key: 'image', label: '图片', write: fillImagePrompt },
  script: { key: 'node', label: '脚本', write: fillScriptPrompt }
}

function resolveLink(source, target) {
  if (!source || !target || source.id === target.id) return { error: '请选择其他节点作为目标' }
  const link = { source: source.id, target: target.id }

  if (source.type === 'image' || source.type === 'imageGen') {
    if (target.type === 'shot') {
      const imageId = source.data.image.id
      const taskId = target.data.task.id
      if (state.edges.some((edge) => edge.imageId === imageId && edge.taskId === taskId)) {
        return { error: '这两个节点已经连线' }
      }
      return { ...link, addReference: () => createEdge(taskId, imageId) }
    }
    if (target.type !== 'imageGen') return { error: '图片只能连到镜头或 AI 图片节点' }
    if (state.links.some((item) => item.source === link.source && item.target === link.target)) {
      return { error: '这两个节点已经连线' }
    }
    return { ...link, message: '已连接为图生图参考' }
  }

  if (state.links.some((item) => item.source === link.source && item.target === link.target)) {
    return { error: '这两个节点已经连线' }
  }
  if (source.type === 'script') {
    if (target.type !== 'shot') return { error: '脚本只能连到镜头，选中分镜后写入提示词' }
    const shots = scriptShots(source.data.node)
    if (!shots.length) return { error: '脚本还没有分镜，先在上方生成脚本' }
    return {
      ...link,
      title: source.data.node.title || '分镜脚本',
      shots,
      writePrompt: (shot) => fillShotPrompt(target.data.task.id, shot.prompt),
      message: '分镜已写入镜头提示词'
    }
  }
  if (source.type === 'text') {
    const text = (source.data.node.content || '').trim()
    if (!text) return { error: '文本节点还没有内容' }
    const promptTarget = linkPromptTargets[target.type]
    if (!promptTarget) return { error: '文本只能连到脚本、AI 图片或镜头节点' }
    const { key, label, write } = promptTarget
    return {
      ...link,
      writePrompt: () => write(target.data[key].id, text),
      message: `文本已写入${label}提示词`
    }
  }
  return { error: '请从文本、脚本或图片节点开始连线' }
}

async function applyLink(plan, shot) {
  if (plan.addReference) return plan.addReference()
  const workspaceId = state.workspaceId
  if (plan.writePrompt) await plan.writePrompt(shot)
  if (state.workspaceId === workspaceId) addLink(plan.source, plan.target)
}

async function submitSingleLink(plan, shot) {
  try {
    if (plan.writePrompt) {
      await historyTransaction('连线并写入提示词', () => applyLink(plan, shot))
    } else {
      await applyLink(plan)
    }
    if (plan.message) toast(plan.message)
  } catch (e) {
    toast(`连线失败：${e.message}`)
  }
}

async function applyPickedShot(shot) {
  const picker = linkPicker.value
  linkPicker.value = null
  if (!picker) return
  const plan = resolveLink(findNode(picker.source), findNode(picker.target))
  if (plan.error) return toast(plan.error)
  await submitSingleLink(plan, shot)
}

async function onConnect({ source, target }) {
  if (!source || !target || source === target) return
  const plan = resolveLink(findNode(source), findNode(target))
  if (plan.error) return toast(plan.error)
  if (plan.shots) {
    linkPicker.value = plan
    return
  }
  await submitSingleLink(plan)
}

const batchLinkTargets = computed(() => {
  const picker = batchLink.value
  if (!picker || batchLinkBusy.value) return []
  const sources = picker.sources.map((id) => findNode(id))
  const targets = []
  for (const node of nodes.value) {
    if (picker.sources.includes(node.id) || !linkPromptTargets[node.type]) continue
    const plans = sources.map((source) => resolveLink(source, node)).filter((plan) => !plan.error)
    if (!plans.length) continue
    const item = node.data[linkPromptTargets[node.type].key]
    const label = node.type === 'shot'
      ? `${node.data.code} · ${item.title || '镜头'}`
      : `${linkPromptTargets[node.type].label} · ${item.title || item.name || item.id}`
    targets.push({ id: node.id, label, plans })
  }
  return targets
})

const batchLinkTarget = computed(() => batchLinkTargets.value.find((item) => item.id === batchLink.value?.target))
const canSubmitBatchLink = computed(() => !batchLinkBusy.value && batchLinkTarget.value?.plans.every((plan) =>
  !plan.shots || plan.shots[batchLink.value.shotChoices[plan.source]]
))

async function openBatchLink() {
  if (selectedNodes.value.length < 2 || batchLinkBusy.value || historyBusy.value) return
  batchLink.value = { sources: selectedNodes.value.map((node) => node.id), target: '', shotChoices: {} }
  await nextTick()
  batchTargetInput.value?.focus()
}

function closeBatchLink() {
  if (!batchLinkBusy.value) batchLink.value = null
}

async function submitBatchLink() {
  if (!canSubmitBatchLink.value || historyBusy.value) return
  const { id: target, plans } = batchLinkTarget.value
  const { sources, shotChoices } = batchLink.value
  try {
    await connectBatch(target, plans, sources, shotChoices)
  } finally {
    batchLinkBusy.value = false
    closeBatchLink()
  }
}

/** 把多个来源节点按计划连到同一目标；拖动 + 手柄与弹窗确认共用 */
async function connectBatch(targetId, plans, sources, shotChoices = {}) {
  const workspaceId = state.workspaceId
  let connected = 0
  const errors = []
  batchLinkBusy.value = true
  try {
    // 按顺序写入同一目标，保留提示词和参考图的追加顺序。
    await historyTransaction('批量连线', async () => {
      for (const planned of plans) {
        if (state.workspaceId !== workspaceId) break
        const connection = resolveLink(findNode(planned.source), findNode(targetId))
        if (connection.error) continue
        const shot = planned.shots?.[shotChoices[planned.source]]
        try {
          await applyLink(connection, shot)
          connected += 1
        } catch (e) {
          errors.push(e.message)
        }
      }
    })
    if (state.workspaceId === workspaceId) {
      const skipped = sources.length - connected - errors.length
      const parts = [`已连接 ${connected} 个节点`]
      if (skipped) parts.push(`跳过 ${skipped} 个`)
      if (errors.length) parts.push(`${errors.length} 个失败：${errors[0]}`)
      toast(parts.join('，'))
    }
  } finally {
    batchLinkBusy.value = false
  }
}

// 从锚点拖出连线后，松开在目标节点卡片的任意位置都能建立连线
onConnectEnd((event) => {
  const sourceId = connectionStartHandle.value?.nodeId
  if (!sourceId || !event) return
  const point = 'clientX' in event ? event : event.changedTouches?.[0]
  if (!point) return
  const element = document.elementFromPoint(point.clientX, point.clientY)
  // 落在锚点上时已由 @connect 处理，避免重复建边
  if (!element || element.closest('.vue-flow__handle')) return
  const targetId = element.closest('.vue-flow__node')?.getAttribute('data-id')
  if (!targetId) return
  const validTarget = targetId.startsWith('shot-') || targetId.startsWith('image-') || targetId.startsWith('node-')
  if (!validTarget) return
  onConnect({ source: sourceId, target: targetId })
})

function onNodeClick({ node }) {
  if (!node) return
  if (node.type === 'shot') {
    state.selectedTaskId = Number(node.id.slice(5))
    state.selectedImageId = null
    state.selectedNodeId = null
    state.inspectorEdit = false
  } else if (node.type === 'image' || node.type === 'imageGen') {
    state.selectedImageId = Number(node.id.slice(6))
    state.selectedTaskId = null
    state.selectedNodeId = null
  } else if (node.type === 'text' || node.type === 'script') {
    state.selectedNodeId = Number(node.id.slice(5))
    state.selectedTaskId = null
    state.selectedImageId = null
  }
}

// 点击画布空白处：取消选中，收起节点下方的提示词编辑器
function onPaneClick() {
  contextMenu.value = null
  state.selectedTaskId = null
  state.selectedImageId = null
  state.selectedNodeId = null
}

function onSelectionStart() {
  state.selectionBoxActive = true
  state.selectedTaskId = null
  state.selectedImageId = null
  state.selectedNodeId = null
}

function onSelectionEnd() {
  state.selectionBoxActive = false
}

async function focusTask(taskId) {
  state.selectedTaskId = taskId
  state.selectedImageId = null
  const node = nodes.value.find((item) => item.id === `shot-${taskId}`)
  if (!node) return
  // 目标镜头可能在视野外而未挂载，先恢复完整挂载再聚焦
  const needsMount = !node.dimensions?.width || !node.dimensions?.height
  if (needsMount) {
    virtualizeNodes.value = false
    await waitForMeasurement()
  }
  fitView({ nodes: [node], duration: 320, maxZoom: 1.1, padding: 0.6 })
  if (needsMount) scheduleVirtualization(600)
}

/** 组装画布快照：节点坐标 + 视口 + 连线 + 图片生成参数，切换画布前也会先落库 */
function snapshotPayload() {
  const positions = {}
  for (const node of nodes.value) {
    positions[node.id] = { x: Math.round(node.position.x), y: Math.round(node.position.y) }
  }
  let viewport = { x: 0, y: 0, zoom: 1 }
  try {
    if (typeof getViewport === 'function') viewport = getViewport()
  } catch (e) {
    /* 忽略 */
  }
  return { positions, viewport, links: state.links, imageSettings: state.imageSettings }
}

/** 立即保存当前画布快照；带 baseVersion 做乐观锁，切换画布前会先调用它 */
async function saveSnapshotNow() {
  const workspaceId = state.workspaceId
  while (snapshotSaving) await snapshotSaving
  if (!ready || state.workspaceId !== workspaceId) return
  snapshotSaving = api.saveSnapshot(workspaceId, snapshotPayload(), state.version)
    .then((canvas) => {
      if (state.workspaceId === workspaceId) state.version = canvas?.version ?? state.version
    })
    .catch(async (e) => {
      if (state.workspaceId !== workspaceId) return
      if (e.status === 409) {
        try {
          await loadAll()
          if (state.workspaceId !== workspaceId) return
          explicit.clear()
          syncGraph()
          toast('画布已在其他窗口更新，已同步最新布局')
        } catch (err) {
          toast(`同步画布失败：${err.message}`)
        }
      } else {
        toast(`保存画布失败：${e.message}`)
      }
    })
  try {
    await snapshotSaving
  } finally {
    snapshotSaving = null
  }
}

function scheduleSave() {
  if (!ready) return
  clearTimeout(saveTimer)
  saveTimer = setTimeout(saveSnapshotNow, 700)
}

function onPaneContextMenu(event) {
  event.preventDefault()
  let flow = { x: PAD_X, y: PAD_Y }
  try {
    flow = screenToFlowCoordinate({ x: event.clientX, y: event.clientY })
  } catch (e) {
    /* 忽略坐标换算失败 */
  }
  contextMenu.value = { x: event.clientX, y: event.clientY, flow }
}

async function onMenuAction(action) {
  const menu = contextMenu.value
  contextMenu.value = null
  if (!menu) return

  if (action === 'video') {
    try {
      const node = await createShot({ prompt: '' })
      explicit.set(`shot-${node.id}`, { x: menu.flow.x, y: menu.flow.y })
      state.selectedTaskId = node.id
      state.selectedImageId = null
      state.selectedNodeId = null
      syncGraph()
      toast('已添加视频节点，在下方填写提示词后点击生成')
    } catch (e) {
      toast(`添加视频节点失败：${e.message}`)
    }
    return
  }
  if (action === 'upload') {
    pendingUpload.value = { ...menu.flow }
    fileInput.value?.click()
    return
  }

  try {
    if (action === 'text' || action === 'script') {
      const node = await createCanvasNode({
        type: action,
        title: action === 'script' ? '脚本生成器' : '文本',
        x: menu.flow.x,
        y: menu.flow.y
      })
      explicit.set(`node-${node.id}`, { x: menu.flow.x, y: menu.flow.y })
      state.selectedNodeId = node.id
      state.selectedTaskId = null
      state.selectedImageId = null
      syncGraph()
      toast(action === 'script' ? '已添加脚本生成器' : '已添加文本节点')
    } else if (action === 'image') {
      const image = await createAiImageNode({ x: menu.flow.x, y: menu.flow.y, prompt: '' })
      explicit.set(`image-${image.id}`, { x: menu.flow.x, y: menu.flow.y })
      state.selectedImageId = image.id
      state.selectedTaskId = null
      state.selectedNodeId = null
      syncGraph()
      toast('已添加 AI 图片节点，在下方填写提示词')
    }
  } catch (e) {
    toast(`添加节点失败：${e.message}`)
  }
}

function onShotCreated(payload) {
  if (payload?.position && payload?.node) {
    explicit.set(`shot-${payload.node.id}`, { ...payload.position })
    syncGraph()
  }
}

async function onToolbarUpload(event) {
  const files = [...event.target.files].filter((file) => file.type.startsWith('image/'))
  event.target.value = ''
  const point = pendingUpload.value
  pendingUpload.value = null
  for (const [index, file] of files.entries()) {
    try {
      if (point) {
        await uploadImageFile(file, point.x + index * 20, point.y + index * 20)
      } else {
        await uploadImageFile(file)
      }
    } catch (e) {
      toast(e.message)
    }
  }
}

async function onDrop(event) {
  dragging.value = false
  const files = [...(event.dataTransfer?.files || [])].filter((file) => file.type.startsWith('image/'))
  if (!files.length) return
  let point = { x: 120, y: 120 }
  try {
    if (typeof screenToFlowCoordinate === 'function') {
      point = screenToFlowCoordinate({ x: event.clientX, y: event.clientY })
    }
  } catch (e) {
    /* 忽略 */
  }
  for (const [index, file] of files.entries()) {
    try {
      await uploadImageFile(file, point.x + index * 20, point.y + index * 20)
    } catch (e) {
      toast(e.message)
    }
  }
}

function onCanvasPointerMove(event) {
  lastCanvasPointer = { x: event.clientX, y: event.clientY }
}

function pastePoint() {
  let screen = lastCanvasPointer
  if (!screen) {
    const rect = canvasWrap.value?.getBoundingClientRect()
    screen = rect
      ? { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 }
      : { x: window.innerWidth / 2, y: window.innerHeight / 2 }
  }
  try {
    return screenToFlowCoordinate(screen)
  } catch (e) {
    return { x: 120, y: 120 }
  }
}

async function onPaste(event) {
  if (batchLink.value || isTypingTarget(event.target)) return
  const files = [...(event.clipboardData?.items || [])]
    .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
    .map((item) => item.getAsFile())
    .filter(Boolean)
  if (!files.length) return
  event.preventDefault()
  contextMenu.value = null
  const point = pastePoint()
  let uploaded = 0
  for (const [index, file] of files.entries()) {
    try {
      await uploadImageFile(file, point.x + index * 20, point.y + index * 20)
      uploaded += 1
    } catch (e) {
      toast(`粘贴图片失败：${e.message}`)
    }
  }
  if (uploaded) toast(`已粘贴 ${uploaded} 张图片`)
}

let viewApplied = false

function applyInitialView() {
  if (viewApplied) return
  const viewport = state.snapshot?.viewport
  if (viewport && viewport.zoom) {
    viewApplied = true
    setViewport(viewport)
    return
  }
  if (!state.tasks.length && !state.images.length && !state.canvasNodes.length) return
  viewApplied = true
  fitView({ padding: 0.12, duration: 260 })
}

/** 切换画布：先保存当前快照，再重置本地视图与实时连接 */
async function openCanvas(workspaceId) {
  if (!workspaceId || workspaceId === state.workspaceId) return
  const perfOn = localStorage.getItem('aigc.perf') === '1'
  const mark = (label, started) => {
    if (perfOn) console.info(`[perf] ${label}: ${Math.round(performance.now() - started)}ms`)
  }
  let started = performance.now()
  clearTimeout(saveTimer)
  await saveSnapshotNow()
  mark('保存快照', started)
  disconnectRealtime()

  state.workspaceId = workspaceId
  localStorage.setItem('aigc.workspace.id', String(workspaceId))
  syncCanvasRoute(workspaceId)
  state.loading = true
  state.selectedTaskId = null
  state.selectedImageId = null
  state.selectedNodeId = null
  state.previewTask = null
  explicit.clear()
  snapshotPositions = {}
  nodes.value = []
  edges.value = []
  viewApplied = false
  ready = false

  try {
    started = performance.now()
    // 画布数据和画布列表互不依赖，并行请求，省掉一次串行往返
    await Promise.all([loadAll(), loadWorkspaces().catch(() => {})])
    mark('加载画布数据与列表', started)
    scheduleVirtualization(700)
    ready = true
    connectRealtime()
    started = performance.now()
    await nextTick()
    mark('构建并渲染节点', started)
    setTimeout(() => {
      if (state.snapshot?.viewport?.zoom) {
        viewApplied = true
        setViewport(state.snapshot.viewport)
      } else {
        setViewport({ x: 0, y: 0, zoom: 1 })
        applyInitialView()
      }
    }, 120)
    toast(`已切换到「${currentCanvasName.value}」`)
  } catch (e) {
    state.loading = false
    toast(`加载画布失败：${e.message}`)
  }
}

/** 新增空白画布并立即切换过去 */
async function addCanvas() {
  if (deletingCanvas.value) return
  try {
    // 与当前画布同项目新建，避免画布被建到默认项目
    const currentProjectId = state.workspaces.find((item) => item.id === state.workspaceId)?.projectId
    const created = await createWorkspace(currentProjectId)
    await openCanvas(created.id)
  } catch (e) {
    toast(`新增画布失败：${e.message}`)
  }
}

async function renameCanvas({ id, name }) {
  try {
    await api.renameWorkspace(id, name)
    await loadWorkspaces()
    toast(`已重命名为「${name}」`)
  } catch (e) {
    toast(`重命名画布失败：${e.message}`)
  }
}

/** 复制整张画布并切换到副本 */
async function copyCanvas(id) {
  if (deletingCanvas.value) return
  deletingCanvas.value = true
  try {
    const created = await api.copyWorkspace(id)
    await loadWorkspaces()
    await openCanvas(created.id)
    toast(`已复制为「${created.name}」`)
  } catch (e) {
    toast(`复制画布失败：${e.message}`)
  } finally {
    deletingCanvas.value = false
  }
}

/** 新窗口打开画布：复制当前路由到新标签页 */
function openCanvasWindow(id) {
  const { href } = router.resolve({ name: 'canvas', params: { workspaceId: id } })
  window.open(href, '_blank', 'noopener')
}

/** 删除后刷新列表并切换；ready=false 时 openCanvas 不会再保存已删画布 */
async function switchDeletedCanvas(deletedId) {
  const workspaces = await loadWorkspaces()
  const next = workspaces.find((item) => item.id !== deletedId)
  if (!next) throw new Error('没有可切换的画布')
  await openCanvas(next.id)
}

async function deleteCanvas(targetId) {
  const workspaceId = Number(targetId) || state.workspaceId
  if (deletingCanvas.value || !workspaceId || state.workspaces.length <= 1) return
  const name = state.workspaces.find((item) => item.id === workspaceId)?.name || '当前画布'
  if (!window.confirm(`确定删除画布「${name}」？\n\n该画布中的全部镜头、图片、节点、连线和提示词记录将永久删除，且无法恢复。`)) return

  // 删除的不是当前画布：当前编辑状态不受影响，只刷新列表
  if (workspaceId !== state.workspaceId) {
    try {
      await api.deleteWorkspace(workspaceId)
      await loadWorkspaces()
      toast(`已删除画布「${name}」`)
    } catch (e) {
      toast(`删除画布失败：${e.message}`)
    }
    return
  }

  deletingCanvas.value = true
  clearTimeout(saveTimer)
  ready = false
  disconnectRealtime()
  let deleted = false
  try {
    await api.deleteWorkspace(workspaceId)
    deleted = true
    await switchDeletedCanvas(workspaceId)
    toast(`已删除画布「${name}」`)
  } catch (e) {
    if (deleted) {
      window.location.reload()
      return
    }
    ready = true
    connectRealtime()
    toast(`删除画布失败：${e.message}`)
  } finally {
    deletingCanvas.value = false
  }
}

/** 其他标签页删除画布时，仅当前画布需要立即切换 */
async function onWorkspaceDeleted(event) {
  const workspaceId = Number(event.detail?.workspaceId)
  if (!workspaceId || deletingCanvas.value) return
  if (workspaceId !== state.workspaceId) {
    loadWorkspaces().catch(() => {})
    return
  }

  deletingCanvas.value = true
  clearTimeout(saveTimer)
  ready = false
  disconnectRealtime()
  try {
    await switchDeletedCanvas(workspaceId)
    toast('当前画布已被删除，已切换到其他画布')
  } catch (e) {
    window.location.reload()
  } finally {
    deletingCanvas.value = false
  }
}

async function bootstrap() {
  try {
    let workspaces = await loadWorkspaces()
    if (!workspaces.length) workspaces = [await createWorkspace()]
    // 路由参数指定画布，兼容旧版 ?workspace= 分享链接与本地记忆
    const routeRequested = Number(route.params.workspaceId)
    const queryRequested = Number(new URLSearchParams(window.location.search).get('workspace'))
    const requested = Number.isInteger(routeRequested) && routeRequested > 0
      ? routeRequested
      : (Number.isInteger(queryRequested) && queryRequested > 0 ? queryRequested : state.workspaceId)
    const target = workspaces.find((item) => item.id === requested)
    const selected = target || workspaces[0]
    if (selected.id !== state.workspaceId) {
      state.workspaceId = selected.id
    }
    localStorage.setItem('aigc.workspace.id', String(selected.id))
    syncCanvasRoute(selected.id)
    await loadAll()
    scheduleVirtualization(900)
    ready = true
    connectRealtime()
    refreshAccount().catch(() => {})
    onMoveEnd(() => scheduleSave())
    // 等节点测量完成后再自适应视图，避免节点跑到屏幕外
    onNodesInitialized?.(() => setTimeout(applyInitialView, 80))
    setTimeout(applyInitialView, 500)
  } catch (e) {
    state.loading = false
    toast(`加载失败：${e.message}`)
  }
}

// 浏览器前进 / 后退或手动修改路由时切换到对应画布
watch(
  () => route.params.workspaceId,
  (value) => {
    const workspaceId = Number(value)
    if (!Number.isInteger(workspaceId) || workspaceId <= 0) return
    if (workspaceId === state.workspaceId) return
    openCanvas(workspaceId)
  }
)

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('keyup', onKeyup)
  window.addEventListener('blur', resetSpacePan)
  window.addEventListener('paste', onPaste)
  window.addEventListener('aigc:workspace-deleted', onWorkspaceDeleted)
  bootstrap()
})
onBeforeUnmount(() => {
  clearTimeout(virtualizationTimer)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('keyup', onKeyup)
  window.removeEventListener('blur', resetSpacePan)
  window.removeEventListener('paste', onPaste)
  window.removeEventListener('aigc:workspace-deleted', onWorkspaceDeleted)
  // 离开画布（去账户页等）时把未落库的布局保存掉；退出登录时 ready 已为 false
  clearTimeout(saveTimer)
  if (ready) saveSnapshotNow().catch(() => {})
  disconnectRealtime()
})
</script>

<template>
  <div class="app">
    <header class="topbar">
      <div class="brand">
        <span class="logo">◈</span>
        <div class="brand-text">
          <strong>Agent造剧</strong>
          <span class="muted">{{ currentCanvasName }} · 提示词即镜头</span>
        </div>
      </div>

      <div class="topbar-center">
        <span class="pill tone-info supplier-pill">供应商 {{ state.meta.providerLabel || state.meta.provider || 'mock' }}</span>
        <span class="conn" :class="state.connection">
          <i /><span class="conn-text">{{ connectionText }}</span>
        </span>
      </div>

      <div class="topbar-actions">
        <input ref="fileInput" type="file" accept="image/png,image/jpeg,image/webp" multiple hidden @change="onToolbarUpload" />
        <button class="btn ghost" @click="showAssets = true">资产管理</button>
        <button class="btn ghost" @click="showPromptRecords = true">提示词记录</button>
        <button class="btn ghost" @click="fileInput.click()">上传图片</button>
        <button class="btn ghost" @click="showMcp = true">MCP 接入</button>
        <button class="btn ghost" @click="showGuide = true">使用说明</button>
        <button class="btn ghost theme-toggle" @click="toggleTheme">
          {{ theme === 'dark' ? '白天模式' : '夜间模式' }}
        </button>
        <router-link
          v-if="authState.user && !authState.user.emailVerified"
          class="pill tone-error verify-hint"
          to="/account"
          title="完成邮箱验证后才能生成和充值"
        >邮箱未验证</router-link>
        <div v-if="authState.user" class="account-area">
          <button class="account-chip" title="账户菜单" @click="accountMenu = !accountMenu">
            <span class="account-credits">{{ authState.user.availableCredits ?? 0 }} 积分</span>
            <span class="account-member">{{ authState.user.memberName || authState.user.memberCode || '免费版' }}</span>
            <span class="account-caret">▾</span>
          </button>
          <div v-if="accountMenu" class="account-backdrop" @click="accountMenu = false"></div>
          <div v-if="accountMenu" class="account-menu">
            <div class="account-menu-head">
              <strong>{{ authState.user.username }}</strong>
              <span class="muted">{{ authState.user.email }}</span>
            </div>
            <router-link to="/" @click="accountMenu = false">我的项目</router-link>
            <router-link to="/account" @click="accountMenu = false">我的账户</router-link>
            <router-link to="/account/billing" @click="accountMenu = false">会员与充值</router-link>
            <router-link to="/account/tokens" @click="accountMenu = false">MCP Token</router-link>
            <router-link v-if="authState.user.role === 'ADMIN'" to="/admin" @click="accountMenu = false">管理后台</router-link>
            <button class="account-menu-item" @click="handleLogout">退出登录</button>
          </div>
        </div>
        <button v-else class="btn ghost logout" @click="handleLogout">退出登录</button>
      </div>
    </header>

    <div class="workspace">
      <div class="sidebar-column" :class="{ collapsed: sidebarCollapsed }">
        <!-- 画布切换条：点开面板选择画布，面板内 ＋ 新建，行内 “...” 提供重命名 / 复制 / 删除 -->
        <div class="canvas-bar">
          <CanvasMenu
            :workspaces="state.workspaces"
            :current-id="state.workspaceId"
            :collapsed="sidebarCollapsed"
            :busy="deletingCanvas"
            @select="openCanvas"
            @create="addCanvas"
            @rename="renameCanvas"
            @copy="copyCanvas"
            @open-window="openCanvasWindow"
            @delete="deleteCanvas"
          />
        </div>
        <Sidebar
          :collapsed="sidebarCollapsed"
          @new="showNewShot = true"
          @focus="focusTask"
          @toggle="sidebarCollapsed = !sidebarCollapsed"
        />
      </div>

      <main
        ref="canvasWrap"
        class="canvas-wrap"
        :class="{ dragging, linking, 'batch-linking': batchLinkDrag, 'canvas-pan': interactionMode === 'pan' || spacePressed }"
        @dragover.prevent="dragging = true"
        @dragleave="dragging = false"
        @drop.prevent="onDrop"
        @pointermove="onCanvasPointerMove"
        @pointerleave="lastCanvasPointer = null"
      >
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          :node-types="nodeTypes"
          :edge-types="edgeTypes"
          :min-zoom="0.3"
          :max-zoom="3"
          :nodes-draggable="true"
          :only-render-visible-elements="virtualizeNodes"
          :pan-on-drag="panOnDrag"
          :connect-on-click="false"
          :delete-key-code="null"
          :selection-key-code="interactionMode === 'select' ? true : 'Shift'"
          selection-mode="partial"
          :multi-selection-key-code="['Control', 'Meta', 'Shift']"
          @node-drag-start="onNodeDragStart"
          @node-drag="onNodeDrag"
          @node-drag-stop="onNodeDragStop"
          @node-click="onNodeClick"
          @selection-start="onSelectionStart"
          @selection-end="onSelectionEnd"
          @pane-context-menu="onPaneContextMenu"
          @pane-click="onPaneClick"
          @connect="onConnect"
        >
          <Background :gap="18" :size="1" :pattern-color="patternColor" />
          <Controls position="bottom-left">
            <template #top>
              <button
                type="button"
                class="vue-flow__controls-button canvas-mode-toggle"
                :title="interactionMode === 'select'
                  ? '框选模式：拖拽空白处框选节点，按住空格拖动画布（点击切换为移动）'
                  : '移动模式：拖拽画布平移视图（点击切换为框选）'"
                @click="toggleInteractionMode"
              >
                <svg v-if="interactionMode === 'select'" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M5 3l14 8-6 1.6L10 19z" />
                </svg>
                <svg v-else viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M8 12V6a1.5 1.5 0 0 1 3 0v5m0-6a1.5 1.5 0 0 1 3 0v6m0-5a1.5 1.5 0 0 1 3 0v7m0-4a1.5 1.5 0 0 1 3 0v6c0 3.5-2.5 6-6 6h-1.5a5.5 5.5 0 0 1-4.4-2.2L5 14.5a1.6 1.6 0 0 1 2.4-2.1L8 13" />
                </svg>
              </button>
              <button
                type="button"
                class="vue-flow__controls-button canvas-arrange-logo"
                title="一键排列：按连线分组，按文本/脚本/图片/视频分行排列"
                :disabled="!canArrange"
                @click="arrangeCanvas"
              >◈</button>
            </template>
            <template #default>
              <button
                type="button"
                class="vue-flow__controls-button canvas-help-btn"
                title="查看画布快捷键"
                @click="openGuideKeys"
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <rect x="3" y="6" width="18" height="12" rx="2" />
                  <path d="M6.5 10h.01M9.5 10h.01M12.5 10h.01M15.5 10h.01M18 10h.01M7 14h10" />
                </svg>
              </button>
            </template>
          </Controls>
          <MiniMap
            position="bottom-right"
            :pannable="true"
            :zoomable="true"
            node-color="#2a2f38"
            mask-color="rgba(10, 11, 13, 0.72)"
          />
        </VueFlow>

        <svg v-if="batchLinkDrag" class="batch-link-line" aria-hidden="true">
          <path :d="batchLinkPath" />
        </svg>

        <button
          v-if="batchLinkAnchor"
          type="button"
          class="batch-link-float"
          title="拖到目标节点批量连线，单击选择目标"
          :style="{ left: `${batchLinkAnchor.left}px`, top: `${batchLinkAnchor.top}px` }"
          :disabled="batchLinkBusy || historyBusy"
          @pointerdown="startBatchLinkDrag"
        >＋</button>

        <div v-if="dragging" class="drop-hint">松开鼠标上传参考图</div>

        <button
          v-if="inspectorHidden"
          class="inspector-open"
          title="展开详情"
          @click="inspectorHidden = false"
        >« 详情</button>

        <CanvasContextMenu
          v-if="contextMenu"
          :position="{ x: contextMenu.x, y: contextMenu.y }"
          @action="onMenuAction"
          @close="contextMenu = null"
        />

        <div v-if="selectedNodes.length || selectedEdges.length" class="selection-bar">
          <span class="selection-count">已选 {{ selectedNodes.length + selectedEdges.length }} 项<span v-if="selectedShots.length"> · {{ selectedShots.length }} 个镜头</span><span v-if="selectedImages.length"> · {{ selectedImages.length }} 张图片</span><span v-if="selectedEdges.length"> · {{ selectedEdges.length }} 条连线</span></span>
          <template v-if="selectedShots.length">
            <button class="btn ghost sm" @click="batchRetry">重新生成</button>
            <button class="btn ghost sm" @click="batchCancel">取消</button>
            <button class="btn ghost sm" title="1–2 个逐个下载，3 个及以上打包为 ZIP" @click="batchDownloadVideos">下载视频</button>
            <button class="btn danger sm" @click="batchRemove">删除镜头</button>
          </template>
          <template v-if="selectedImages.length">
            <button class="btn ghost sm" @click="batchDownloadImages">下载图片</button>
            <button class="btn danger sm" @click="batchRemoveImages">删除图片</button>
          </template>
          <template v-if="selectedEdges.length">
            <button class="btn danger sm" @click="batchRemoveEdges">删除连线</button>
          </template>
          <button class="icon-btn" title="清除选择" @click="clearSelection">×</button>
        </div>

        <div
          v-if="!state.loading && !state.tasks.length && !state.images.length && !state.canvasNodes.length && !emptyDismissed"
          class="empty-state"
        >
          <button class="icon-btn empty-close" title="关闭" @click="emptyDismissed = true">×</button>
          <h3>右键画布添加节点，或让 AI 客户端推送提示词</h3>
          <p class="muted">在 WorkBuddy / Trae 中通过 MCP 推送分镜，每条提示词都会在这里长成一个视频节点。</p>
          <div class="row">
            <button class="btn ghost" @click="showMcp = true">复制 MCP 配置</button>
            <button class="btn ghost" @click="fileInput.click()">上传参考图</button>
            <button class="btn primary" @click="showNewShot = true">手动新建</button>
          </div>
        </div>
      </main>

      <ShotInspector v-show="!inspectorHidden" @hide="inspectorHidden = true" />
    </div>

    <NewShotDialog
      v-if="showNewShot"
      :position="newShotPosition"
      @created="onShotCreated"
      @close="showNewShot = false; newShotPosition = null"
    />
    <McpDialog v-if="showMcp" @close="showMcp = false" />
    <AssetDialog v-if="showAssets" @close="showAssets = false" />
    <PromptRecordDialog v-if="showPromptRecords" @close="showPromptRecords = false" />
    <UsageGuide v-if="showGuide" @close="showGuide = false" />
    <PreviewDialog v-if="state.previewTask" :task="state.previewTask" @close="state.previewTask = null" />

    <div v-if="batchLink" class="overlay" @click.self="closeBatchLink">
      <form class="dialog" role="dialog" aria-modal="true" aria-labelledby="batch-link-heading" @submit.prevent="submitBatchLink">
        <header class="dialog-head">
          <h2 id="batch-link-heading">批量连线</h2>
          <button type="button" class="icon-btn" aria-label="关闭" :disabled="batchLinkBusy" @click="closeBatchLink">×</button>
        </header>
        <div class="dialog-body">
          <p v-if="batchLinkBusy" class="muted" role="status">正在建立连线…</p>
          <p v-else-if="!batchLinkTargets.length" class="muted">所选节点没有可连接的目标，或已完成连线。</p>
          <template v-else>
            <label class="field">
              <span class="field-label">目标节点</span>
              <select ref="batchTargetInput" v-model="batchLink.target" required>
                <option disabled value="">选择要连到的节点</option>
                <option v-for="target in batchLinkTargets" :key="target.id" :value="target.id">
                  {{ target.label }}（{{ target.plans.length }} 个可连接）
                </option>
              </select>
            </label>
            <template v-if="batchLinkTarget">
              <p v-if="batchLinkTarget.plans.length < batchLink.sources.length" class="muted">
                将跳过 {{ batchLink.sources.length - batchLinkTarget.plans.length }} 个不兼容或已连线的节点。
              </p>
              <template v-for="plan in batchLinkTarget.plans" :key="plan.source">
                <label v-if="plan.shots" class="field">
                  <span class="field-label">{{ plan.title }} · 选择要写入的分镜</span>
                  <select v-model="batchLink.shotChoices[plan.source]" required>
                    <option disabled :value="undefined">选择分镜</option>
                    <option v-for="(shot, index) in plan.shots" :key="index" :value="index">
                      {{ index + 1 }}. {{ shot.title || shot.prompt }}
                    </option>
                  </select>
                </label>
              </template>
            </template>
          </template>
        </div>
        <footer class="dialog-foot">
          <button type="button" class="btn ghost" :disabled="batchLinkBusy" @click="closeBatchLink">取消</button>
          <button type="submit" class="btn primary" :disabled="!canSubmitBatchLink || historyBusy">确认连线</button>
        </footer>
      </form>
    </div>

    <div v-if="linkPicker" class="overlay" @click.self="linkPicker = null">
      <div class="dialog">
        <header class="dialog-head">
          <div>
            <h2>选择要写入的分镜</h2>
            <p class="muted">{{ linkPicker.title }} · 选中后追加到目标镜头提示词</p>
          </div>
          <button class="icon-btn" @click="linkPicker = null">×</button>
        </header>
        <div class="dialog-body link-picker">
          <button
            v-for="(shot, index) in linkPicker.shots"
            :key="index"
            class="link-pick"
            @click="applyPickedShot(shot)"
          >
            <span class="script-index">{{ index + 1 }}</span>
            <span class="link-pick-text">
              <strong>{{ shot.title || `镜头 ${index + 1}` }}</strong>
              <span>{{ shot.prompt }}</span>
            </span>
          </button>
        </div>
      </div>
    </div>

    <div v-if="state.toast" class="toast">{{ state.toast }}</div>
  </div>
</template>
