<script setup>
import { computed, markRaw, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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
import CanvasContextMenu from './components/CanvasContextMenu.vue'

import {
  state,
  loadAll,
  connectRealtime,
  disconnectRealtime,
  createEdge,
  moveImage,
  saveOrder,
  uploadImageFile,
  createCanvasNode,
  updateCanvasNode,
  createAiImageNode,
  createImageNode,
  createShot,
  removeTask,
  removeImage,
  removeCanvasNode,
  retryTasks,
  cancelTasks,
  removeTasks,
  toast
} from './store/canvas'
import { api } from './api'
import { theme, toggleTheme } from './theme'

const emit = defineEmits(['logout'])

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
const edgeTypes = { ref: markRaw(ImageRefEdge) }

const nodes = ref([])
const edges = ref([])
const showNewShot = ref(false)
const showMcp = ref(false)
const showAssets = ref(false)
const showPromptRecords = ref(false)
const showGuide = ref(false)
const fileInput = ref(null)
const canvasWrap = ref(null)
const dragging = ref(false)
const contextMenu = ref(null)
let lastCanvasPointer = null
const newShotPosition = ref(null)
const pendingUpload = ref(null)
// 侧栏折叠状态记忆在本地，刷新后保持
const sidebarCollapsed = ref(localStorage.getItem('aigc.sidebar.collapsed') === '1')
watch(sidebarCollapsed, (value) => {
  localStorage.setItem('aigc.sidebar.collapsed', value ? '1' : '0')
})
const inspectorHidden = ref(localStorage.getItem('aigc.inspector.hidden') === '1')
watch(inspectorHidden, (value) => {
  localStorage.setItem('aigc.inspector.hidden', value ? '1' : '0')
})

const explicit = new Map()
let snapshotPositions = {}
let saveTimer = null
let ready = false

const { setViewport, getViewport, fitView, onMoveEnd, onNodesInitialized, screenToFlowCoordinate, onConnectEnd, connectionStartHandle, getSelectedNodes, removeSelectedElements } =
  useVueFlow()

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

// 画布点阵背景随主题切换，避免白天模式下仍是深色网格
const patternColor = computed(() => (theme.value === 'light' ? '#dfe4ee' : '#24282F'))

// 正从图片锚点拖出连线时给镜头卡片加高亮，提示整张卡片都可落点
const linking = computed(() => connectionStartHandle.value?.nodeId?.startsWith('image-'))

// 画布多选：只对镜头节点做批量操作
const selectedShots = computed(() =>
  (getSelectedNodes.value || []).filter((node) => node.type === 'shot')
)

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

async function batchRemove() {
  const ids = selectedShots.value.map((node) => Number(node.id.slice(5)))
  if (!ids.length) return
  await removeTasks(ids)
  removeSelectedElements()
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
    const old = previous.get(id)
    const node = old
      ? Object.assign(old, { position: map.get(id), data })
      : {
          id,
          type: 'group',
          position: map.get(id),
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

  let counter = 0
  for (const image of state.images) {
    const id = `image-${image.id}`
    let fallback
    if (image.x != null && image.y != null) {
      fallback = { x: image.x, y: image.y }
    } else {
      const edge = state.edges.find((item) => item.imageId === image.id)
      const shotPos = edge ? map.get(`shot-${edge.taskId}`) : null
      if (shotPos) {
        const sibling = state.images
          .filter((it) => it.id !== image.id)
          .filter((it) => {
            const e = state.edges.find((x) => x.imageId === it.id)
            return e && e.taskId === edge.taskId
          }).length
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

  edges.value = state.edges
    .filter((edge) => ids.has(`image-${edge.imageId}`) && ids.has(`shot-${edge.taskId}`))
    .map((edge) => ({
      id: `edge-${edge.id}`,
      type: 'ref',
      source: `image-${edge.imageId}`,
      target: `shot-${edge.taskId}`,
      data: { edgeId: edge.id }
    }))
}

watch(() => [state.tasks, state.images, state.canvasNodes, state.edges], syncGraph, { deep: true })
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

function onNodeDragStart({ node, event }) {
  altDrag =
    event?.altKey && node && node.type !== 'group'
      ? { id: node.id, position: { x: node.position.x, y: node.position.y } }
      : null
}

async function duplicateNode(node, { x, y, withEdges = true }) {
  if (node.type === 'shot') {
    const task = state.tasks.find((item) => item.id === Number(node.id.slice(5)))
    if (!task) return
    const created = await createShot({
      prompt: task.prompt,
      groupId: task.groupId,
      model: task.model,
      ratio: task.ratio,
      duration: task.duration,
      resolution: task.resolution
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

function selectOnlyNode(nodeId) {
  nodes.value.forEach((node) => {
    node.selected = node.id === nodeId
  })
}

function selectedActionNodes() {
  return getSelectedNodes.value.filter((node) => node && node.type !== 'group')
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

function onKeydown(event) {
  if (isTypingTarget(event.target)) return
  const list = selectedActionNodes()
  if (!list.length) return
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
}

async function onConnect(connection) {
  const { source, target } = connection
  if (!source?.startsWith('image-') || !target?.startsWith('shot-')) {
    toast('请从图片右侧圆点拖到镜头卡片')
    return
  }
  const imageId = Number(source.slice(6))
  const taskId = Number(target.slice(5))
  if (state.edges.some((edge) => edge.imageId === imageId && edge.taskId === taskId)) {
    toast('这两个节点已经连线')
    return
  }
  try {
    await createEdge(taskId, imageId)
  } catch (e) {
    toast(`连线失败：${e.message}`)
  }
}

// 从图片锚点拖出连线后，松开在镜头卡片的任意位置（不只是左侧小圆点）都能建立引用
onConnectEnd((event) => {
  const sourceId = connectionStartHandle.value?.nodeId
  if (!sourceId?.startsWith('image-') || !event) return
  const point = 'clientX' in event ? event : event.changedTouches?.[0]
  if (!point) return
  const element = document.elementFromPoint(point.clientX, point.clientY)
  // 落在锚点上时已由 @connect 处理，避免重复建边
  if (!element || element.closest('.vue-flow__handle')) return
  const targetId = element.closest('.vue-flow__node-shot')?.getAttribute('data-id')
  if (!targetId) return
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

function focusTask(taskId) {
  state.selectedTaskId = taskId
  state.selectedImageId = null
  const node = nodes.value.find((item) => item.id === `shot-${taskId}`)
  if (node) {
    fitView({ nodes: [node], duration: 320, maxZoom: 1.1, padding: 0.6 })
  }
}

function scheduleSave() {
  if (!ready) return
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
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
    api.saveSnapshot(state.workspaceId, { positions, viewport }, state.version)
      .then((canvas) => {
        state.version = canvas?.version ?? state.version
      })
      .catch(async (e) => {
        // 版本冲突：其他标签页/实例已保存过，拉取最新布局，避免互相覆盖
        if (e.status === 409) {
          toast('画布已在其他窗口更新，已同步最新布局')
          try {
            await loadAll()
          } catch (err) {
            /* 忽略刷新失败 */
          }
        }
      })
  }, 700)
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
    newShotPosition.value = { ...menu.flow }
    showNewShot.value = true
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
  if (isTypingTarget(event.target)) return
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

async function bootstrap() {
  try {
    await loadAll()
    syncGraph()
    ready = true
    connectRealtime()
    onMoveEnd(() => scheduleSave())
    // 等节点测量完成后再自适应视图，避免节点跑到屏幕外
    onNodesInitialized?.(() => setTimeout(applyInitialView, 80))
    setTimeout(applyInitialView, 500)
  } catch (e) {
    state.loading = false
    toast(`加载失败：${e.message}`)
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('paste', onPaste)
  bootstrap()
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('paste', onPaste)
  disconnectRealtime()
  clearTimeout(saveTimer)
})
</script>

<template>
  <div class="app">
    <header class="topbar">
      <div class="brand">
        <span class="logo">◈</span>
        <div>
          <strong>AIGC 画布工作台</strong>
          <span class="muted">默认工作空间 · 提示词即镜头</span>
        </div>
      </div>

      <div class="topbar-center">
        <span class="pill tone-info">供应商 {{ state.meta.provider || 'mock' }}</span>
        <span class="conn" :class="state.connection">
          <i />{{ connectionText }}
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
        <button class="btn ghost logout" @click="emit('logout')">退出登录</button>
      </div>
    </header>

    <div class="workspace">
      <Sidebar
        :collapsed="sidebarCollapsed"
        @new="showNewShot = true"
        @focus="focusTask"
        @toggle="sidebarCollapsed = !sidebarCollapsed"
      />

      <main
        ref="canvasWrap"
        class="canvas-wrap"
        :class="{ dragging, linking }"
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
          :pan-on-drag="true"
          :connect-on-click="false"
          :delete-key-code="null"
          :selection-key-code="'Shift'"
          :multi-selection-key-code="['Control', 'Meta']"
          @node-drag-start="onNodeDragStart"
          @node-drag="onNodeDrag"
          @node-drag-stop="onNodeDragStop"
          @node-click="onNodeClick"
          @pane-context-menu="onPaneContextMenu"
          @pane-click="contextMenu = null"
          @connect="onConnect"
        >
          <Background :gap="18" :size="1" :pattern-color="patternColor" />
          <Controls position="bottom-left" />
          <MiniMap
            position="bottom-right"
            :pannable="true"
            :zoomable="true"
            node-color="#2a2f38"
            mask-color="rgba(10, 11, 13, 0.72)"
          />
        </VueFlow>

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

        <div v-if="selectedShots.length" class="selection-bar">
          <span class="selection-count">已选 {{ selectedShots.length }} 个镜头</span>
          <span class="selection-hint muted">Shift + 拖拽框选，Ctrl + 点击追加</span>
          <button class="btn ghost sm" @click="batchRetry">重新生成</button>
          <button class="btn ghost sm" @click="batchCancel">取消</button>
          <button class="btn danger sm" @click="batchRemove">删除</button>
          <button class="icon-btn" title="清除选择" @click="clearSelection">×</button>
        </div>

        <div
          v-if="!state.loading && !state.tasks.length && !state.images.length && !state.canvasNodes.length"
          class="empty-state"
        >
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

    <div v-if="state.toast" class="toast">{{ state.toast }}</div>
  </div>
</template>
