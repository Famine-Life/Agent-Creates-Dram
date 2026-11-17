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

/** 当前画布名称：顶部标题与切换提示共用；列表未加载时回落到“默认工作空间” */
const currentCanvasName = computed(
  () => state.workspaces.find((item) => item.id === state.workspaceId)?.name || '默认工作空间'
)

// 画布点阵背景随主题切换，避免白天模式下仍是深色网格
const patternColor = computed(() => (theme.value === 'light' ? '#dfe4ee' : '#24282F'))

// 正从图片锚点拖出连线时给镜头卡片加高亮，提示整张卡片都可落点
const linking = computed(() => connectionStartHandle.value?.nodeId?.startsWith('image-'))

// 画布多选：镜头与图片分别显示对应的批量操作
const selectedShots = computed(() =>
  (getSelectedNodes.value || []).filter((node) => node.type === 'shot')
)
const selectedImages = computed(() =>
  (getSelectedNodes.value || []).filter((node) => node.type === 'image' || node.type === 'imageGen')
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
      model: task.modelKey || task.model,
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

async function applyLinkValue(action, label) {
  try {
    await action()
    return true
  } catch (e) {
    toast(`${label}失败：${e.message}`)
    return false
  }
}

function openShotPicker(source, target) {
  const node = state.canvasNodes.find((item) => `node-${item.id}` === source)
  if (!node) return
  let list = []
  try {
    list = JSON.parse(node.content || '[]')
  } catch (e) {
    list = []
  }
  list = Array.isArray(list) ? list.filter((shot) => shot && shot.prompt) : []
  if (!list.length) {
    toast('脚本还没有分镜，先在上方生成脚本')
    return
  }
  linkPicker.value = { source, target, title: node.title || '分镜脚本', shots: list }
}

async function applyPickedShot(shot) {
  const picker = linkPicker.value
  linkPicker.value = null
  if (!picker) return
  const filled = await applyLinkValue(
    () => fillShotPrompt(Number(picker.target.slice(5)), shot.prompt),
    '写入镜头提示词'
  )
  if (!filled) return
  addLink(picker.source, picker.target)
  toast('分镜已写入镜头提示词')
}

async function onConnect(connection) {
  const { source, target } = connection
  if (!source || !target || source === target) return

  if (source.startsWith('image-')) {
    if (!target.startsWith('shot-')) {
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
    return
  }

  if (!source.startsWith('node-')) {
    toast('请从文本 / 脚本 / 图片节点右侧圆点拖出连线')
    return
  }

  const sourceNode = state.canvasNodes.find((item) => `node-${item.id}` === source)
  if (!sourceNode) return
  if (state.links.some((link) => link.source === source && link.target === target)) {
    toast('这两个节点已经连线')
    return
  }

  if (sourceNode.type === 'script') {
    if (!target.startsWith('shot-')) {
      toast('脚本只能连到镜头，选中分镜后写入提示词')
      return
    }
    openShotPicker(source, target)
    return
  }

  const text = (sourceNode.content || '').trim()
  if (!text) {
    toast('文本节点还没有内容')
    return
  }

  if (target.startsWith('node-')) {
    const targetNode = state.canvasNodes.find((item) => `node-${item.id}` === target)
    if (!targetNode || targetNode.type !== 'script') {
      toast('文本只能连到脚本、AI 图片或镜头节点')
      return
    }
    const filled = await applyLinkValue(() => fillScriptPrompt(targetNode.id, text), '写入脚本提示词')
    if (filled) {
      addLink(source, target)
      toast('文本已作为脚本输入')
    }
    return
  }

  if (target.startsWith('image-')) {
    const targetImage = state.images.find((item) => item.id === Number(target.slice(6)))
    if (!targetImage || !(targetImage.prompt != null || targetImage.status)) {
      toast('文本只能连到 AI 图片节点')
      return
    }
    const filled = await applyLinkValue(
      () => fillImagePrompt(Number(target.slice(6)), text),
      '写入图片提示词'
    )
    if (filled) {
      addLink(source, target)
      toast('文本已写入图片提示词')
    }
    return
  }

  if (target.startsWith('shot-')) {
    const filled = await applyLinkValue(
      () => fillShotPrompt(Number(target.slice(5)), text),
      '写入镜头提示词'
    )
    if (filled) {
      addLink(source, target)
      toast('文本已写入镜头提示词')
    }
    return
  }

  toast('文本只能连到脚本、AI 图片或镜头节点')
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

function focusTask(taskId) {
  state.selectedTaskId = taskId
  state.selectedImageId = null
  const node = nodes.value.find((item) => item.id === `shot-${taskId}`)
  if (node) {
    fitView({ nodes: [node], duration: 320, maxZoom: 1.1, padding: 0.6 })
  }
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
  if (!ready) return
  const workspaceId = state.workspaceId
  try {
    const canvas = await api.saveSnapshot(workspaceId, snapshotPayload(), state.version)
    if (state.workspaceId === workspaceId) state.version = canvas?.version ?? state.version
  } catch (e) {
    // 版本冲突：其他标签页/实例已保存过，拉取最新布局，避免互相覆盖
    if (e.status === 409 && state.workspaceId === workspaceId) {
      toast('画布已在其他窗口更新，已同步最新布局')
      try {
        await loadAll()
      } catch (err) {
        /* 忽略刷新失败 */
      }
    }
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

/** 切换画布：先保存当前快照，再重置本地视图与实时连接 */
async function openCanvas(workspaceId) {
  if (!workspaceId || workspaceId === state.workspaceId) return
  clearTimeout(saveTimer)
  await saveSnapshotNow()
  disconnectRealtime()

  state.workspaceId = workspaceId
  localStorage.setItem('aigc.workspace.id', String(workspaceId))
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
    await loadAll()
    await loadWorkspaces().catch(() => {})
    syncGraph()
    ready = true
    connectRealtime()
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
    const created = await createWorkspace()
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

/** 新窗口打开画布：通过 ?workspace= 让新标签页定位到指定画布 */
function openCanvasWindow(id) {
  window.open(`${window.location.pathname}?workspace=${id}`, '_blank', 'noopener')
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
    // “在新窗口打开”通过 ?workspace= 指定画布，优先于本地记忆
    const requested = Number(new URLSearchParams(window.location.search).get('workspace'))
    const target = workspaces.find((item) =>
      item.id === (Number.isInteger(requested) && requested > 0 ? requested : state.workspaceId)
    )
    const selected = target || workspaces[0]
    if (selected.id !== state.workspaceId) {
      state.workspaceId = selected.id
    }
    localStorage.setItem('aigc.workspace.id', String(selected.id))
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
  window.addEventListener('aigc:workspace-deleted', onWorkspaceDeleted)
  bootstrap()
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('paste', onPaste)
  window.removeEventListener('aigc:workspace-deleted', onWorkspaceDeleted)
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
          <span class="muted">{{ currentCanvasName }} · 提示词即镜头</span>
        </div>
      </div>

      <div class="topbar-center">
        <span class="pill tone-info">供应商 {{ state.meta.providerLabel || state.meta.provider || 'mock' }}</span>
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
          selection-mode="partial"
          :multi-selection-key-code="['Control', 'Meta']"
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

        <div v-if="selectedShots.length || selectedImages.length" class="selection-bar">
          <span class="selection-count">已选 {{ selectedShots.length + selectedImages.length }} 项<span v-if="selectedShots.length"> · {{ selectedShots.length }} 个镜头</span><span v-if="selectedImages.length"> · {{ selectedImages.length }} 张图片</span></span>
          <span class="selection-hint muted">Shift + 拖拽框选，Ctrl + 点击追加</span>
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
