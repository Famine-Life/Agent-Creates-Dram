import { reactive } from 'vue'
import { api } from '../api'

/** 默认工作空间 ID，与后端初始化脚本写入的 workspace.id=1 对应 */
const DEFAULT_WORKSPACE_ID = 1

/**
 * 读取上次打开的画布 ID：优先用 localStorage 记住的选择，失效时回落到默认画布。
 * workspaceId 决定所有接口路径与 WebSocket 频道，因此必须在初始化时确定。
 */
function initialWorkspaceId() {
  const stored = Number(localStorage.getItem('aigc.workspace.id'))
  return Number.isInteger(stored) && stored > 0 ? stored : DEFAULT_WORKSPACE_ID
}

export const STATUS = {
  IDLE: { text: '待编辑', tone: 'idle' },
  RECEIVED: { text: '已接收', tone: 'idle' },
  QUEUED: { text: '排队中', tone: 'idle' },
  SUBMITTING: { text: '正在提交', tone: 'info' },
  SUBMITTED: { text: '已提交', tone: 'info' },
  PROVIDER_QUEUED: { text: '等待平台资源', tone: 'idle' },
  RUNNING: { text: '生成中', tone: 'busy' },
  SUBMIT_UNKNOWN: { text: '提交结果待确认', tone: 'error' },
  SUCCEEDED: { text: '已完成', tone: 'done' },
  FAILED: { text: '失败', tone: 'error' },
  TIMEOUT: { text: '超时', tone: 'error' },
  CANCELED: { text: '已取消', tone: 'idle' }
}

export function statusOf(task) {
  return STATUS[task?.status] || { text: task?.status || '-', tone: 'idle' }
}

export const state = reactive({
  /** 当前画布所属的工作空间 ID，切换画布时整体替换 */
  workspaceId: initialWorkspaceId(),
  /** 可切换的画布列表，来自 GET /api/workspaces */
  workspaces: [],
  meta: {},
  tasks: [],
  images: [],
  canvasNodes: [],
  edges: [],
  /** 文本 / 脚本节点到下游节点的连线，随画布快照保存 */
  links: [],
  /** AI 图片生成参数（模型 / 比例 / 清晰度 / 数量），随画布快照保存 */
  imageSettings: {},
  snapshot: null,
  version: 0,
  selectedTaskId: null,
  selectedImageId: null,
  selectedNodeId: null,
  selectionBoxActive: false,
  inspectorEdit: false,
  previewTask: null,
  keyword: '',
  connection: 'connecting',
  loading: true,
  toast: ''
})

let toastTimer = null
export function toast(message) {
  if (!message) return
  state.toast = String(message)
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (state.toast = ''), 3600)
}

/**
 * 跨上下文可靠复制：优先 Clipboard API（需要安全上下文），
 * 不可用时回退到 textarea + execCommand('copy')，兼容 HTTP 与旧浏览器。
 * @param {string} text 要复制的文本
 * @param {string} [successMsg] 复制成功的提示文案，默认"已复制到剪贴板"
 */
export async function copyText(text, successMsg = '已复制到剪贴板') {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      toast(successMsg)
      return true
    }
    // Fallback: 创建隐藏 textarea 执行 execCommand
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    ta.style.pointerEvents = 'none'
    document.body.appendChild(ta)
    ta.focus()
    ta.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    if (ok) {
      toast(successMsg)
      return true
    }
    throw new Error('execCommand copy returned false')
  } catch (err) {
    toast(`复制失败：${err.message || '请手动 Ctrl+C'}`)
    return false
  }
}

export function applyCanvas(canvas) {
  state.snapshot = canvas?.snapshot || null
  state.version = canvas?.version ?? 0
  state.tasks = canvas?.tasks || []
  state.images = canvas?.images || []
  state.canvasNodes = canvas?.nodes || []
  state.edges = canvas?.edges || []
}

export async function loadAll() {
  const [meta, canvas] = await Promise.all([api.meta(), api.canvas(state.workspaceId)])
  state.meta = meta
  applyCanvas(canvas)
  state.links = Array.isArray(canvas?.snapshot?.links) ? canvas.snapshot.links : []
  state.imageSettings = canvas?.snapshot?.imageSettings || {}
  state.loading = false
}

/** 拉取画布列表，用于侧栏的画布切换下拉 */
export async function loadWorkspaces() {
  const list = await api.workspaces()
  state.workspaces = Array.isArray(list) ? list : []
  return state.workspaces
}

/** 新建空白画布，返回创建后的工作空间 */
export async function createWorkspace() {
  const created = await api.createWorkspace()
  state.workspaces = [...state.workspaces, created].sort((a, b) => a.id - b.id)
  return created
}

function upsert(list, item) {
  const index = list.findIndex((it) => it.id === item.id)
  if (index >= 0) list.splice(index, 1, item)
  else list.push(item)
}

function handleEvent(type, data) {
  switch (type) {
    case 'node.created':
    case 'node.updated':
    case 'node.progress':
    case 'node.completed':
      upsert(state.tasks, data)
      if (type === 'node.completed') toast(`镜头《${data.title || data.id}》生成完成`)
      break
    case 'node.failed':
      upsert(state.tasks, data)
      toast(`镜头《${data.title || data.id}》生成失败：${data.errorMsg || '未知原因'}`)
      break
    case 'node.deleted':
      state.tasks = state.tasks.filter((t) => t.id !== data.taskId)
      state.edges = state.edges.filter((e) => e.taskId !== data.taskId)
      pruneLinks(`shot-${data.taskId}`)
      if (state.selectedTaskId === data.taskId) state.selectedTaskId = null
      break
    case 'image.created':
    case 'image.updated':
      upsert(state.images, data)
      break
    case 'image.deleted':
      state.images = state.images.filter((i) => i.id !== data.imageId)
      state.edges = state.edges.filter((e) => e.imageId !== data.imageId)
      pruneLinks(`image-${data.imageId}`)
      if (state.selectedImageId === data.imageId) state.selectedImageId = null
      break
    case 'edge.created':
      upsert(state.edges, data)
      break
    case 'edge.deleted':
      state.edges = state.edges.filter((e) => e.id !== data.edgeId)
      break
    case 'canvas_node.created':
    case 'canvas_node.updated':
      upsert(state.canvasNodes, data)
      break
    case 'canvas_node.deleted':
      state.canvasNodes = state.canvasNodes.filter((n) => n.id !== data.nodeId)
      pruneLinks(`node-${data.nodeId}`)
      if (state.selectedNodeId === data.nodeId) state.selectedNodeId = null
      break
    case 'workspace.deleted':
      state.workspaces = state.workspaces.filter((item) => item.id !== data.workspaceId)
      window.dispatchEvent(new CustomEvent('aigc:workspace-deleted', { detail: data }))
      break
    case 'workspace.changed':
      loadWorkspaces().catch(() => {})
      break
    default:
      break
  }
}

// ------------------------------------------------------------------ 节点连线（文本 / 脚本 -> 下游）

let linkSeq = 0

function pruneLinks(nodeId) {
  if (!state.links.some((link) => link.source === nodeId || link.target === nodeId)) return
  state.links = state.links.filter((link) => link.source !== nodeId && link.target !== nodeId)
}

/** 建立文本 / 脚本节点到下游节点的连线；重复连线直接返回已有记录 */
export function addLink(source, target) {
  const existing = state.links.find((link) => link.source === source && link.target === target)
  if (existing) return existing
  linkSeq += 1
  const link = { id: `link-${Date.now()}-${linkSeq}`, source, target }
  state.links.push(link)
  return link
}

export function removeLink(linkId) {
  state.links = state.links.filter((link) => link.id !== linkId)
}

/** 把文本追加到脚本节点的提示词（连线传值） */
export async function fillScriptPrompt(nodeId, text) {
  const node = state.canvasNodes.find((item) => item.id === nodeId)
  if (!node || !text) return
  const prompt = node.prompt ? `${node.prompt}\n\n${text}` : text
  return updateCanvasNode(nodeId, { prompt })
}

/** 把文本追加到 AI 图片节点的提示词（连线传值） */
export async function fillImagePrompt(imageId, text) {
  const image = state.images.find((item) => item.id === imageId)
  if (!image || !text) return
  const prompt = image.prompt ? `${image.prompt}\n\n${text}` : text
  const updated = await api.updateImage(state.workspaceId, imageId, { prompt })
  upsert(state.images, updated)
  return updated
}

/** 保存 AI 图片节点提示词，只保存不触发重新生成 */
const promptSaveSeq = new Map()

export async function saveImagePrompt(imageId, prompt) {
  const image = state.images.find((item) => item.id === imageId)
  if (!image) return
  const text = prompt == null ? '' : String(prompt)
  if ((image.prompt || '') === text) return
  image.prompt = text
  const seq = (promptSaveSeq.get(imageId) || 0) + 1
  promptSaveSeq.set(imageId, seq)
  const updated = await api.updateImage(state.workspaceId, imageId, { prompt: text })
  // 只接受最后一次保存的结果，避免旧请求回填覆盖新输入
  if (promptSaveSeq.get(imageId) === seq) upsert(state.images, updated)
  return updated
}

/** 把文本追加到镜头提示词，只保存不触发重新生成（连线传值） */
export async function fillShotPrompt(taskId, text) {
  const task = state.tasks.find((item) => item.id === taskId)
  if (!task || !text) return
  const prompt = task.prompt ? `${task.prompt}\n\n${text}` : text
  const node = await api.savePrompt(taskId, {
    prompt,
    regenerate: false,
    model: task.modelKey || task.model || task.provider,
    ratio: task.ratio || '16:9',
    duration: task.duration || 5,
    resolution: task.resolution || '720P'
  })
  upsert(state.tasks, node)
  return node
}

// ------------------------------------------------------------------ WebSocket

let socket = null
let retryCount = 0
let reconnectTimer = null
let stopped = false

export function connectRealtime() {
  stopped = false
  openSocket()
}

export function disconnectRealtime() {
  stopped = true
  clearTimeout(reconnectTimer)
  socket?.close()
  socket = null
}

function openSocket() {
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  // WebSocket 频道绑定具体画布：切换画布时必须重建连接，避免收到上一张画布的事件
  const url = `${protocol}://${location.host}/ws/canvas/${state.workspaceId}`
  state.connection = 'connecting'
  const ws = new WebSocket(url)
  socket = ws

  // 每次回调都校验 socket 身份：切换画布后旧连接的回调直接丢弃，防止其自动重连到新状态
  ws.onopen = async () => {
    if (socket !== ws) return
    state.connection = 'online'
    retryCount = 0
    try {
      // 断线期间可能漏掉事件，重连后全量刷新一次
      await loadAll()
    } catch (e) {
      /* 忽略，等待下一次重连 */
    }
  }

  ws.onmessage = (event) => {
    if (socket !== ws) return
    try {
      const payload = JSON.parse(event.data)
      if (payload.type === 'connected' || payload.type === 'pong') return
      handleEvent(payload.type, payload.data)
    } catch (e) {
      console.warn('无法解析 WebSocket 消息', event.data)
    }
  }

  ws.onerror = () => {
    if (socket !== ws) return
    if (state.connection !== 'offline') state.connection = 'offline'
  }

  ws.onclose = () => {
    if (socket !== ws || stopped) return
    state.connection = 'offline'
    retryCount += 1
    const delay = Math.min(8000, 1000 * Math.pow(2, retryCount - 1))
    clearTimeout(reconnectTimer)
    reconnectTimer = setTimeout(() => {
      state.connection = 'connecting'
      openSocket()
    }, delay)
  }
}

// ------------------------------------------------------------------ 业务动作

export async function createShot(payload) {
  const node = await api.createShot(state.workspaceId, payload)
  upsert(state.tasks, node)
  state.selectedTaskId = node.id
  toast('已创建镜头，正在排队生成')
  return node
}

export async function createStoryboard(payload) {
  const list = await api.createStoryboard(state.workspaceId, payload)
  list.forEach((node) => upsert(state.tasks, node))
  if (list.length) {
    state.selectedTaskId = list[0].id
    state.selectedImageId = null
    state.selectedNodeId = null
  }
  toast(`已添加 ${list.length} 个镜头到画布`)
  return list
}

export async function savePrompt(taskId, prompt, options = {}) {
  const node = await api.savePrompt(taskId, { prompt, regenerate: true, ...options })
  upsert(state.tasks, node)
  toast('提示词已保存，重新开始生成')
}

/** 只保存模型 / 比例 / 时长，不触发重新生成 */
export async function updateShotSettings(taskId, patch) {
  const task = state.tasks.find((item) => item.id === taskId)
  if (!task) return null
  const node = await api.savePrompt(taskId, {
    prompt: task.prompt,
    regenerate: false,
    model: task.modelKey || task.model || task.provider,
    ratio: task.ratio || '16:9',
    duration: task.duration || 5,
    resolution: task.resolution || '720P',
    ...patch
  })
  upsert(state.tasks, node)
  return node
}

export async function retryTask(taskId) {
  const node = await api.retry(taskId)
  upsert(state.tasks, node)
  toast('已重新提交生成')
}

export async function cancelTask(taskId) {
  const node = await api.cancel(taskId)
  upsert(state.tasks, node)
  toast('任务已取消')
}

export async function removeTask(taskId) {
  await api.removeTask(taskId)
  state.tasks = state.tasks.filter((t) => t.id !== taskId)
  pruneLinks(`shot-${taskId}`)
  if (state.selectedTaskId === taskId) state.selectedTaskId = null
  toast('镜头已删除')
}

async function runBatch(taskIds, action, label) {
  const results = await Promise.allSettled(taskIds.map((taskId) => action(taskId)))
  const ok = results.filter((item) => item.status === 'fulfilled').length
  const failed = results.length - ok
  toast(failed ? `${label}：成功 ${ok} 个，失败 ${failed} 个` : `${label}：${ok} 个镜头`)
  return { ok, failed }
}

export async function retryTasks(taskIds) {
  return runBatch(
    taskIds,
    async (taskId) => {
      const node = await api.retry(taskId)
      upsert(state.tasks, node)
    },
    '已重新提交'
  )
}

export async function cancelTasks(taskIds) {
  return runBatch(
    taskIds,
    async (taskId) => {
      const node = await api.cancel(taskId)
      upsert(state.tasks, node)
    },
    '已取消'
  )
}

export async function removeTasks(taskIds) {
  return runBatch(
    taskIds,
    async (taskId) => {
      await api.removeTask(taskId)
      state.tasks = state.tasks.filter((t) => t.id !== taskId)
      state.edges = state.edges.filter((e) => e.taskId !== taskId)
      if (state.selectedTaskId === taskId) state.selectedTaskId = null
    },
    '已删除'
  )
}

export async function uploadImageFile(file, x, y) {
  const result = await api.upload(state.workspaceId, file, x, y)
  upsert(state.images, {
    id: result.id,
    name: result.name,
    url: result.url,
    x: x ?? null,
    y: y ?? null,
    width: 148,
    height: 166,
    source: 'UPLOAD'
  })
  return result
}

export async function replaceImageFromAsset(imageId, asset) {
  const updated = await api.updateImage(state.workspaceId, imageId, {
    sourceImageId: asset.id,
    name: asset.name,
    url: asset.url,
    source: asset.source || 'UPLOAD'
  })
  upsert(state.images, updated)
  return updated
}

export async function replaceImageFromFile(imageId, file) {
  const uploaded = await uploadImageFile(file)
  try {
    const updated = await replaceImageFromAsset(imageId, uploaded)
    await api.removeImage(state.workspaceId, uploaded.id)
    state.images = state.images.filter((image) => image.id !== uploaded.id)
    return updated
  } catch (error) {
    await removeImage(uploaded.id)
    throw error
  }
}

export async function createEdge(taskId, imageId) {
  const edge = await api.createEdge(state.workspaceId, { taskId, imageId })
  upsert(state.edges, edge)
  return edge
}

export async function removeEdge(edgeId) {
  await api.removeEdge(state.workspaceId, edgeId)
  state.edges = state.edges.filter((e) => e.id !== edgeId)
}

export async function removeImage(imageId) {
  await api.removeImage(state.workspaceId, imageId)
  state.images = state.images.filter((i) => i.id !== imageId)
  state.edges = state.edges.filter((e) => e.imageId !== imageId)
  pruneLinks(`image-${imageId}`)
}

export async function moveImage(imageId, x, y) {
  const image = await api.updateImage(state.workspaceId, imageId, { x, y })
  upsert(state.images, image)
}

export async function saveOrder(items) {
  const canvas = await api.saveOrder(state.workspaceId, items)
  applyCanvas(canvas)
}

// ------------------------------------------------------------------ 画布节点 / AI 生成

export async function createCanvasNode(payload) {
  const node = await api.createCanvasNode(state.workspaceId, payload)
  upsert(state.canvasNodes, node)
  return node
}

export async function updateCanvasNode(nodeId, payload) {
  const node = await api.updateCanvasNode(state.workspaceId, nodeId, payload)
  upsert(state.canvasNodes, node)
  return node
}

export async function removeCanvasNode(nodeId) {
  await api.removeCanvasNode(state.workspaceId, nodeId)
  state.canvasNodes = state.canvasNodes.filter((n) => n.id !== nodeId)
  pruneLinks(`node-${nodeId}`)
  if (state.selectedNodeId === nodeId) state.selectedNodeId = null
}

export async function generateScript(nodeId, prompt, model) {
  const node = await api.generateScript(state.workspaceId, nodeId, prompt, model)
  upsert(state.canvasNodes, node)
  return node
}

export async function createAiImageNode(payload) {
  const image = await api.createAiImage(state.workspaceId, payload)
  upsert(state.images, image)
  return image
}

/** 直接创建图片节点（复制已有图片时使用） */
export async function createImageNode(payload) {
  const image = await api.createImage(state.workspaceId, payload)
  upsert(state.images, image)
  return image
}

export async function generateAiImage(imageId, prompt, model, referenceUrl, settings) {
  const image = await api.generateImage(state.workspaceId, imageId, prompt, model, referenceUrl, settings)
  upsert(state.images, image)
  return image
}

/** 把画布图片关联到镜头，用于提示词里的 @图片n */
export async function linkImage(taskId, imageId) {
  return createEdge(taskId, imageId)
}

export async function unlinkImage(edgeId) {
  return removeEdge(edgeId)
}
