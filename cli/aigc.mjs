#!/usr/bin/env node
/**
 * AIGC 画布工作台命令行客户端。
 *
 * 通过现有的 REST 接口工作：首次 `login` 后把 Cookie 与 CSRF Token 保存在
 * ~/.aigc-cli/session.json，后续命令直接复用登录态。
 *
 * 依赖 Node.js 16+，无需安装任何 npm 包。
 */
import fs from 'node:fs'
import http from 'node:http'
import https from 'node:https'
import os from 'node:os'
import path from 'node:path'

const DEFAULT_BASE = process.env.AIGC_BASE_URL || 'http://localhost:8080'
const SESSION_FILE = path.join(os.homedir(), '.aigc-cli', 'session.json')
const HELP = `AIGC 画布工作台 CLI

用法：
  node cli/aigc.mjs <命令> [参数]

命令：
  login       登录并保存会话
                --username <用户名> --password <密码> [--url <服务地址>]
  logout      退出登录并清除本地会话
  canvas      查看画布统计与镜头列表    [--workspace 1] [--json]
  workspaces  列出画布                 [--json]
  new-workspace 新建空白画布           [--json]
  copy-workspace 复制画布               --id <画布ID> [--json]
  delete-workspace 删除画布             --id <画布ID> [--json]
  models      查看当前可用模型           [--type video|image|text] [--json]
  assets      查看素材（视频 / 生成图片 / 上传图片）
                [--type video|ai|upload] [--workspace 1] [--json]
  push        推送单个镜头
                --prompt <提示词> [--title <标题>] [--group <分组>] [--index <序号>]
                [--images <图片ID,逗号分隔>] [--model <平台/模型，如 ark/doubao-seedance-2-0-260128>]
                [--ratio 16:9] [--resolution 720P] [--duration 5]
  storyboard  批量推送分镜         --file <shots.json> [--group <分组>]
  generate    提交已有镜头生成/重新生成 --id <任务ID> [--json]
  retry       重新生成已有镜头       --id <任务ID> [--json]
  cancel      取消生成任务           --id <任务ID> [--json]
  edit-prompt 编辑提示词与生成参数   --id <任务ID> [--prompt <文本>] [--model <平台/模型>]
                [--ratio 16:9] [--duration 5] [--resolution 720P] [--regenerate] [--json]
  set-video-model 切换镜头模型但不重新生成 --id <任务ID> --model <平台/模型> [--workspace 1] [--json]
  set-image-model 切换图片节点模型但不生成 --id <图片ID> --model <平台/模型> [--workspace 1] [--json]
  prompt-records 查看提示词历史      [--workspace 1] [--json]
  optimize-prompt 优化提示词         --prompt <文本> [--model <模型>] [--workspace 1] [--json]
  upload      上传本地图片并创建图片节点 --file <图片路径> [--workspace 1] [--x <坐标>] [--y <坐标>] [--json]
  upload-url  下载图片 URL 并上传到画布素材 --image-url <图片URL> [--name <名称>] [--workspace 1] [--x <坐标>] [--y <坐标>] [--json]
  snapshot    保存画布快照           --file <snapshot.json> [--version <版本>] [--workspace 1] [--json]
  order       保存镜头顺序           --file <items.json> [--workspace 1] [--json]
  create-image 创建图片节点          [--name <名称>] [--url <地址>] [--prompt <文本>] [--x <坐标>] [--y <坐标>]
  create-ai-image 创建 AI 图片节点   [--prompt <文本>] [--x <坐标>] [--y <坐标>]
  generate-image 生成 AI 图片        --id <图片ID> [--prompt <文本>] [--model <模型>] [--reference-url <地址>]
                [--ratio 1:1] [--resolution 2K] [--count 1] [--json]
  edit-image  更新图片节点           --id <图片ID> [--name <名称>] [--url <地址>] [--prompt <文本>]
                [--x <坐标>] [--y <坐标>] [--width <像素>] [--height <像素>]
  create-node 创建文本/脚本节点      --type text|script [--title <名称>] [--prompt <文本>] [--content <文本>] [--x <坐标>] [--y <坐标>]
  edit-node   更新文本/脚本节点       --id <节点ID> [--title <名称>] [--prompt <文本>] [--content <文本>]
  delete-node 删除文本/脚本节点       --id <节点ID>
  generate-script 生成脚本           --id <节点ID> [--prompt <文本>] [--model <模型>] [--json]
  link-image 关联图片与镜头           --task-id <任务ID> --image-id <图片ID> [--json]
  unlink-image 删除图片与镜头关联     --id <连线ID> [--workspace 1] [--json]
  rm          删除镜头             --id <任务ID>
  rm-image    删除图片节点         --id <图片ID>

通用参数：
  --url <地址>   服务地址，默认 ${DEFAULT_BASE}（也可用 AIGC_BASE_URL）
  --json         输出原始 JSON

示例：
  node cli/aigc.mjs login --username admin --password admin@888
  node cli/aigc.mjs push --prompt "海边日落，少女奔跑，电影感" --group "A组"
  node cli/aigc.mjs assets --type video
`

const session = loadSession()

// ---------------------------------------------------------------- 会话与 HTTP

function loadSession() {
  try {
    return JSON.parse(fs.readFileSync(SESSION_FILE, 'utf8'))
  } catch {
    return { cookie: {} }
  }
}

function saveSession() {
  fs.mkdirSync(path.dirname(SESSION_FILE), { recursive: true })
  fs.writeFileSync(SESSION_FILE, JSON.stringify(session, null, 2))
  try {
    fs.chmodSync(SESSION_FILE, 0o600)
  } catch {
    /* Windows 上忽略 */
  }
}

function baseUrl() {
  return (session.baseUrl || DEFAULT_BASE).replace(/\/+$/, '')
}

function cookieHeader() {
  return Object.entries(session.cookie || {})
    .map(([name, value]) => `${name}=${value}`)
    .join('; ')
}

function storeCookies(list) {
  if (!list) return
  session.cookie = session.cookie || {}
  for (const raw of list) {
    const pair = String(raw).split(';')[0]
    const index = pair.indexOf('=')
    if (index < 0) continue
    const name = pair.slice(0, index).trim()
    const value = pair.slice(index + 1).trim()
    if (value) session.cookie[name] = value
    else delete session.cookie[name]
  }
  saveSession()
}

function request(method, pathname, options = {}) {
  const base = options.base || baseUrl()
  return new Promise((resolve, reject) => {
    const url = new URL(pathname, base)
    const lib = url.protocol === 'https:' ? https : http
    const payload =
      options.body == null ? null : Buffer.isBuffer(options.body) ? options.body : Buffer.from(options.body)
    const headers = { Accept: 'application/json', ...(options.headers || {}) }
    const cookie = cookieHeader()
    if (cookie) headers.Cookie = cookie
    if (payload) headers['Content-Length'] = payload.length
    const req = lib.request(
      {
        method,
        hostname: url.hostname,
        port: url.port || (url.protocol === 'https:' ? 443 : 80),
        path: url.pathname + url.search,
        headers
      },
      (res) => {
        const chunks = []
        res.on('data', (chunk) => chunks.push(chunk))
        res.on('end', () => {
          storeCookies(res.headers['set-cookie'])
          resolve({
            status: res.statusCode,
            headers: res.headers,
            text: Buffer.concat(chunks).toString('utf8')
          })
        })
      }
    )
    req.on('error', reject)
    if (payload) req.write(payload)
    req.end()
  })
}

function parseJson(text) {
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

function trim(text, max = 300) {
  const value = String(text || '').trim()
  return value.length > max ? `${value.slice(0, max)}…` : value
}

async function csrfToken() {
  const res = await request('GET', '/api/auth/csrf')
  if (res.status !== 200) fail(`获取 CSRF Token 失败：HTTP ${res.status}`)
  const data = parseJson(res.text)
  if (!data || !data.token) fail('获取 CSRF Token 失败：响应格式不正确')
  session.csrf = data.token
  saveSession()
  return data.token
}

async function api(method, pathname, options = {}) {
  if (!session.username) {
    fail('尚未登录，请先执行：node cli/aigc.mjs login --username <用户名> --password <密码>')
  }
  const headers = { ...(options.headers || {}) }
  let body = options.body
  if (method !== 'GET' && method !== 'HEAD') {
    headers['X-XSRF-TOKEN'] = await csrfToken()
  }
  if (options.multipart) {
    body = options.multipart.body
    headers['Content-Type'] = options.multipart.type
  } else if (body != null) {
    body = JSON.stringify(body)
    headers['Content-Type'] = 'application/json'
  }
  const res = await request(method, pathname, { body, headers })
  if (res.status === 401) fail('登录已失效，请重新执行：node cli/aigc.mjs login')
  if (res.status === 403) fail(`请求被拒绝（HTTP 403），可能是 CSRF 校验失败：${trim(res.text)}`)
  if (res.status >= 400) fail(`请求失败（HTTP ${res.status}）：${trim(res.text)}`)
  return parseJson(res.text)
}

// ---------------------------------------------------------------- 命令实现

async function login(opts) {
  const username = opts.username || process.env.AIGC_USERNAME
  const password = opts.password || process.env.AIGC_PASSWORD
  if (!username || !password) {
    fail('请提供 --username 与 --password（或设置 AIGC_USERNAME / AIGC_PASSWORD 环境变量）')
  }
  session.baseUrl = (opts.url || process.env.AIGC_BASE_URL || DEFAULT_BASE).replace(/\/+$/, '')
  session.cookie = {}
  session.username = null
  saveSession()

  const token = await csrfToken()
  const res = await request('POST', '/api/auth/login', {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      'X-XSRF-TOKEN': token
    },
    body: `username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`
  })
  if (res.status >= 400) fail(`登录失败（HTTP ${res.status}）：${trim(res.text)}`)

  // 登录成功后 Session 轮换，需要重新获取 CSRF Token
  const check = await request('GET', '/api/auth/session')
  const who = parseJson(check.text)
  if (!who || !who.authenticated) fail('登录失败：服务端未返回已认证状态，请检查账号密码')
  session.username = who.username || username
  await csrfToken()
  console.log(`已登录：${session.username} @ ${session.baseUrl}`)
}

async function logout() {
  if (session.username) {
    try {
      await api('POST', '/api/auth/logout')
    } catch (error) {
      /* 服务端已失效时也继续清理本地会话 */
    }
  }
  const base = session.baseUrl
  session.cookie = {}
  session.username = null
  session.csrf = null
  session.baseUrl = base
  saveSession()
  console.log('已退出登录')
}

async function canvas(opts) {
  const workspaceId = Number(opts.workspace || 1)
  const data = await api('GET', `/api/canvas/${workspaceId}`)
  if (opts.json) return printJson(data)

  const tasks = data.tasks || []
  const images = data.images || []
  const uploads = images.filter((image) => image.source !== 'AI')
  const ai = images.filter((image) => image.source === 'AI')

  console.log(`工作空间 ${workspaceId} · 画布 v${data.version ?? 0}`)
  console.log(`镜头 ${tasks.length} 个 · 图片 ${images.length} 张（上传 ${uploads.length} / 生成 ${ai.length}）`)

  if (!tasks.length) return
  console.log('')
  for (const task of tasks) {
    const progress = task.progress == null ? '' : ` ${task.progress}%`
    const group = task.groupId ? `[${task.groupId}] ` : ''
    const code = task.shotIndex ? `${task.shotIndex}. ` : ''
    const url = task.videoUrl ? ` → ${task.videoUrl}` : ''
    console.log(`#${task.id} ${task.status}${progress}  ${group}${code}${task.title || ''}${url}`)
  }
}

async function assets(opts) {
  const workspaceId = Number(opts.workspace || 1)
  const data = await api('GET', `/api/canvas/${workspaceId}`)
  const tasks = data.tasks || []
  const images = data.images || []

  const videos = tasks
    .filter((task) => task.videoUrl)
    .map((task) => ({ type: 'video', id: task.id, title: task.title || '', url: task.videoUrl }))
  const ai = images
    .filter((image) => image.source === 'AI' && image.url)
    .map((image) => ({ type: 'ai', id: image.id, title: image.name || '', url: image.url }))
  const uploads = images
    .filter((image) => image.source !== 'AI' && image.url)
    .map((image) => ({ type: 'upload', id: image.id, title: image.name || '', url: image.url }))

  const map = { video: videos, ai, upload: uploads }
  if (opts.type && !map[opts.type]) fail('--type 仅支持 video / ai / upload')
  const list = opts.type ? map[opts.type] : [...videos, ...ai, ...uploads]

  if (opts.json) return printJson(list)
  if (!list.length) {
    console.log('暂无素材')
    return
  }
  for (const item of list) {
    console.log(`[${item.type}] #${item.id}  ${item.title}  ${item.url}`)
  }
}

async function workspaces(opts) {
  const list = await api('GET', '/api/workspaces')
  if (opts.json) return printJson(list)
  for (const item of list || []) console.log(`#${item.id} ${item.name} · v${item.version ?? 0}`)
}

async function createWorkspace(opts) {
  const workspace = await api('POST', '/api/workspaces')
  if (opts.json) return printJson(workspace)
  console.log(`已创建画布 #${workspace.id}：${workspace.name}`)
}

async function copyWorkspace(opts) {
  if (!opts.id) fail('请提供 --id 画布ID')
  const id = Number(opts.id)
  if (!Number.isInteger(id) || id <= 0) fail('--id 必须是正整数画布ID')
  const workspace = await api('POST', `/api/workspaces/${id}/copy`)
  if (opts.json) return printJson(workspace)
  console.log(`已复制画布 #${id} → #${workspace.id}：${workspace.name}`)
}

async function deleteWorkspace(opts) {
  if (!opts.id) fail('请提供 --id 画布ID')
  const id = Number(opts.id)
  if (!Number.isInteger(id) || id <= 0) fail('--id 必须是正整数画布ID')
  const result = await api('DELETE', `/api/workspaces/${id}`)
  if (opts.json) return printJson({ ...result, workspaceId: id })
  console.log(`已删除画布 #${id}`)
}

async function models(opts) {
  const types = {
    video: { label: '视频模型', key: 'videoModels' },
    image: { label: '图片模型', key: 'imageModels' },
    text: { label: '文本模型', key: 'textModels' }
  }
  if (opts.type && !types[opts.type]) fail('--type 仅支持 video / image / text')
  const meta = await api('GET', '/api/meta')
  const selected = opts.type ? [opts.type] : Object.keys(types)
  const result = Object.fromEntries(selected.map((type) => [type, {
    label: types[type].label,
    default: meta.defaults?.[type] || null,
    models: meta[types[type].key] || []
  }]))
  if (opts.json) return printJson(opts.type ? result[opts.type] : result)
  for (const type of selected) {
    const catalog = result[type]
    console.log(`${catalog.label}${catalog.default ? `（默认：${catalog.default}）` : ''}`)
    for (const model of catalog.models) {
      const marker = model.value === catalog.default ? ' [默认]' : ''
      console.log(`  ${model.label} · ${model.value}${marker}`)
    }
  }
}

function optionalNumber(opts, key) {
  if (opts[key] == null) return undefined
  const value = Number(opts[key])
  if (!Number.isFinite(value)) fail(`--${key} 必须是数字`)
  return value
}

async function retryTask(opts) {
  if (!opts.id) fail('请提供 --id 任务ID')
  const node = await api('POST', `/api/tasks/${Number(opts.id)}/retry`)
  if (opts.json) return printJson(node)
  console.log(`已提交镜头 #${node.id}（${node.status}）：${node.title || ''}`)
}

async function cancelTask(opts) {
  if (!opts.id) fail('请提供 --id 任务ID')
  const node = await api('POST', `/api/tasks/${Number(opts.id)}/cancel`)
  if (opts.json) return printJson(node)
  console.log(`已取消镜头 #${node.id}（${node.status}）：${node.title || ''}`)
}

async function editPrompt(opts) {
  if (!opts.id) fail('请提供 --id 任务ID')
  const body = {}
  if (opts.prompt != null) body.prompt = String(opts.prompt)
  if (opts.model != null) body.model = String(opts.model)
  if (opts.ratio != null) body.ratio = String(opts.ratio)
  if (opts.duration != null) body.duration = Number(opts.duration)
  if (opts.resolution != null) body.resolution = String(opts.resolution)
  if (opts.regenerate) body.regenerate = true
  if (!Object.keys(body).length) fail('请至少提供 --prompt、--model、--ratio、--duration、--resolution 或 --regenerate')
  const node = await api('PUT', `/api/tasks/${Number(opts.id)}/prompt`, { body })
  if (opts.json) return printJson(node)
  console.log(`已更新镜头 #${node.id}（${node.status}）：${node.title || ''}`)
}

async function setVideoModel(opts) {
  if (!opts.id) fail('请提供 --id 镜头任务ID')
  if (!opts.model) fail('请提供 --model 平台/模型ID')
  const taskId = Number(opts.id)
  if (!Number.isInteger(taskId) || taskId <= 0) fail('--id 必须是正整数镜头任务ID')

  const workspaceIds = opts.workspace
    ? [Number(opts.workspace)]
    : (await api('GET', '/api/workspaces')).map((workspace) => workspace.id)
  let task
  for (const workspaceId of workspaceIds) {
    const canvasData = await api('GET', `/api/canvas/${workspaceId}`)
    task = (canvasData.tasks || []).find((item) => Number(item.id) === taskId)
    if (task) break
  }
  if (!task) fail(`找不到镜头任务 #${taskId}${opts.workspace ? `（画布 #${opts.workspace}）` : ''}`)
  if (!task.prompt) fail(`镜头 #${taskId} 没有可复用的提示词；请使用 edit-prompt 提供 --prompt`)

  const node = await api('PUT', `/api/tasks/${taskId}/prompt`, {
    body: {
      prompt: task.prompt,
      model: String(opts.model),
      ratio: task.ratio,
      duration: task.duration,
      resolution: task.resolution,
      regenerate: false
    }
  })
  if (opts.json) return printJson(node)
  console.log(`已切换镜头 #${node.id} 模型为 ${node.modelKey || node.model}，未重新生成`)
}

async function setImageModel(opts) {
  if (!opts.id) fail('请提供 --id 图片ID')
  if (!opts.model) fail('请提供 --model 平台/模型ID')
  const imageId = Number(opts.id)
  const workspaceId = Number(opts.workspace || 1)
  if (!Number.isInteger(imageId) || imageId <= 0) fail('--id 必须是正整数图片ID')
  if (!Number.isInteger(workspaceId) || workspaceId <= 0) fail('--workspace 必须是正整数画布ID')

  const [canvasData, meta] = await Promise.all([
    api('GET', `/api/canvas/${workspaceId}`),
    api('GET', '/api/meta')
  ])
  const image = (canvasData.images || []).find((item) => Number(item.id) === imageId)
  if (!image) fail(`画布 #${workspaceId} 中找不到图片 #${imageId}`)
  const model = (meta.imageModels || []).find((item) => item.value === String(opts.model))
  if (!model) fail(`当前图片模型目录中找不到：${opts.model}；可运行 node cli/aigc.mjs models --type image`)

  const snapshot = canvasData.snapshot || {}
  const imageSettings = { ...(snapshot.imageSettings || {}) }
  imageSettings[imageId] = { ...(imageSettings[imageId] || {}), model: model.value }
  const updated = await api('PUT', `/api/canvas/${workspaceId}/snapshot`, {
    body: {
      snapshot: { ...snapshot, imageSettings },
      baseVersion: canvasData.version
    }
  })
  const result = {
    workspaceId,
    imageId,
    name: image.name,
    model: model.value,
    modelLabel: model.label,
    generated: false,
    version: updated.version
  }
  if (opts.json) return printJson(result)
  console.log(`已为图片 #${imageId}《${image.name}》设置 ${model.label}（${model.value}），未提交生成`)
}

async function promptRecords(opts) {
  const workspaceId = Number(opts.workspace || 1)
  const list = await api('GET', `/api/canvas/${workspaceId}/prompt-records`)
  if (opts.json) return printJson(list)
  for (const item of list || []) console.log(JSON.stringify(item))
}

async function optimizePrompt(opts) {
  if (!opts.prompt) fail('请提供 --prompt 提示词')
  const workspaceId = Number(opts.workspace || 1)
  const result = await api('POST', `/api/canvas/${workspaceId}/ai/prompt/optimize`, {
    body: { prompt: String(opts.prompt), model: opts.model ? String(opts.model) : undefined }
  })
  if (opts.json) return printJson(result)
  console.log(result.prompt || '')
}

async function readJsonFile(file, label) {
  if (!file) fail(`请提供 --file ${label}`)
  try {
    return JSON.parse(fs.readFileSync(path.resolve(String(file)), 'utf8'))
  } catch (error) {
    fail(`读取 ${label} 失败：${error.message}`)
  }
}

async function saveSnapshot(opts) {
  const snapshot = await readJsonFile(opts.file, 'snapshot.json')
  const workspaceId = Number(opts.workspace || 1)
  const body = { snapshot, baseVersion: optionalNumber(opts, 'version') }
  const result = await api('PUT', `/api/canvas/${workspaceId}/snapshot`, { body })
  if (opts.json) return printJson(result)
  console.log(`已保存画布快照（v${result.version ?? '?'})`)
}

async function saveOrder(opts) {
  const items = await readJsonFile(opts.file, 'items.json')
  if (!Array.isArray(items)) fail('顺序文件必须是数组，字段为 taskId、groupId、shotIndex')
  const workspaceId = Number(opts.workspace || 1)
  const result = await api('PUT', `/api/canvas/${workspaceId}/order`, { body: { items } })
  if (opts.json) return printJson(result)
  console.log(`已保存 ${items.length} 个镜头的顺序`)
}

async function createImage(opts) {
  const workspaceId = Number(opts.workspace || 1)
  const body = {
    name: opts.name ? String(opts.name) : undefined,
    url: opts.url ? String(opts.url) : undefined,
    prompt: opts.prompt ? String(opts.prompt) : undefined,
    x: optionalNumber(opts, 'x'), y: optionalNumber(opts, 'y'),
    width: optionalNumber(opts, 'width'), height: optionalNumber(opts, 'height')
  }
  const image = await api('POST', `/api/canvas/${workspaceId}/images`, { body })
  if (opts.json) return printJson(image)
  console.log(`已创建图片节点 #${image.id}：${image.name || ''}`)
}

async function createAiImage(opts) {
  const workspaceId = Number(opts.workspace || 1)
  const image = await api('POST', `/api/canvas/${workspaceId}/images/ai`, {
    body: { prompt: opts.prompt ? String(opts.prompt) : undefined, x: optionalNumber(opts, 'x'), y: optionalNumber(opts, 'y') }
  })
  if (opts.json) return printJson(image)
  console.log(`已创建 AI 图片节点 #${image.id}：${image.name || ''}`)
}

async function generateImage(opts) {
  if (!opts.id) fail('请提供 --id 图片ID')
  const workspaceId = Number(opts.workspace || 1)
  const body = {
    prompt: opts.prompt ? String(opts.prompt) : undefined,
    model: opts.model ? String(opts.model) : undefined,
    referenceUrl: opts['reference-url'] ? String(opts['reference-url']) : undefined,
    ratio: opts.ratio ? String(opts.ratio) : undefined,
    resolution: opts.resolution ? String(opts.resolution) : undefined,
    count: optionalNumber(opts, 'count')
  }
  const image = await api('POST', `/api/canvas/${workspaceId}/images/${Number(opts.id)}/generate`, { body })
  if (opts.json) return printJson(image)
  console.log(`已提交图片生成 #${image.id}（${image.status || ''}）`)
}

async function editImage(opts) {
  if (!opts.id) fail('请提供 --id 图片ID')
  const body = {}
  for (const key of ['name', 'url', 'prompt']) if (opts[key] != null) body[key] = String(opts[key])
  for (const key of ['x', 'y', 'width', 'height']) if (opts[key] != null) body[key] = optionalNumber(opts, key)
  if (!Object.keys(body).length) fail('请至少提供要更新的图片字段')
  const workspaceId = Number(opts.workspace || 1)
  const image = await api('PUT', `/api/canvas/${workspaceId}/images/${Number(opts.id)}`, { body })
  if (opts.json) return printJson(image)
  console.log(`已更新图片节点 #${image.id}`)
}

async function createNode(opts) {
  if (!['text', 'script'].includes(opts.type)) fail('--type 仅支持 text / script')
  const workspaceId = Number(opts.workspace || 1)
  const body = {
    type: String(opts.type), title: opts.title ? String(opts.title) : undefined,
    prompt: opts.prompt ? String(opts.prompt) : undefined, content: opts.content ? String(opts.content) : undefined,
    x: optionalNumber(opts, 'x'), y: optionalNumber(opts, 'y'),
    width: optionalNumber(opts, 'width'), height: optionalNumber(opts, 'height')
  }
  const node = await api('POST', `/api/canvas/${workspaceId}/nodes`, { body })
  if (opts.json) return printJson(node)
  console.log(`已创建${opts.type}节点 #${node.id}：${node.title || ''}`)
}

async function editNode(opts) {
  if (!opts.id) fail('请提供 --id 节点ID')
  const body = {}
  for (const key of ['type', 'title', 'prompt', 'content']) if (opts[key] != null) body[key] = String(opts[key])
  for (const key of ['x', 'y', 'width', 'height']) if (opts[key] != null) body[key] = optionalNumber(opts, key)
  if (!Object.keys(body).length) fail('请至少提供要更新的节点字段')
  const workspaceId = Number(opts.workspace || 1)
  const node = await api('PUT', `/api/canvas/${workspaceId}/nodes/${Number(opts.id)}`, { body })
  if (opts.json) return printJson(node)
  console.log(`已更新节点 #${node.id}`)
}

async function removeNode(opts) {
  if (!opts.id) fail('请提供 --id 节点ID')
  const workspaceId = Number(opts.workspace || 1)
  const result = await api('DELETE', `/api/canvas/${workspaceId}/nodes/${Number(opts.id)}`)
  if (opts.json) return printJson(result)
  console.log(`已删除节点 #${opts.id}`)
}

async function generateScript(opts) {
  if (!opts.id) fail('请提供 --id 节点ID')
  const workspaceId = Number(opts.workspace || 1)
  const body = { prompt: opts.prompt ? String(opts.prompt) : undefined, model: opts.model ? String(opts.model) : undefined }
  const node = await api('POST', `/api/canvas/${workspaceId}/nodes/${Number(opts.id)}/script`, { body })
  if (opts.json) return printJson(node)
  console.log(`已提交脚本生成 #${node.id}（${node.status || ''}）`)
}

async function linkImage(opts) {
  if (!opts['task-id'] || !opts['image-id']) fail('请提供 --task-id 和 --image-id')
  const workspaceId = Number(opts.workspace || 1)
  const edge = await api('POST', `/api/canvas/${workspaceId}/edges`, {
    body: { taskId: Number(opts['task-id']), imageId: Number(opts['image-id']) }
  })
  if (opts.json) return printJson(edge)
  console.log(`已关联图片 #${edge.imageId} 与镜头 #${edge.taskId}（连线 #${edge.id}）`)
}

async function unlinkImage(opts) {
  if (!opts.id) fail('请提供 --id 连线ID')
  const workspaceId = Number(opts.workspace || 1)
  const result = await api('DELETE', `/api/canvas/${workspaceId}/edges/${Number(opts.id)}`)
  if (opts.json) return printJson(result)
  console.log(`已删除连线 #${opts.id}`)
}

async function push(opts) {
  if (!opts.prompt) fail('请提供 --prompt 提示词')
  const body = { prompt: String(opts.prompt) }
  if (opts.title) body.title = String(opts.title)
  if (opts.group) body.groupId = String(opts.group)
  if (opts.index != null) body.shotIndex = Number(opts.index)
  if (opts.model) body.model = String(opts.model)
  if (opts.ratio) body.ratio = String(opts.ratio)
  if (opts.resolution) body.resolution = String(opts.resolution)
  if (opts.duration != null) body.duration = Number(opts.duration)
  if (opts.images) {
    body.imageIds = String(opts.images)
      .split(',')
      .map((value) => Number(value.trim()))
      .filter((value) => Number.isFinite(value))
  }

  const workspaceId = Number(opts.workspace || 1)
  const node = await api('POST', `/api/canvas/${workspaceId}/shots`, { body })
  if (opts.json) return printJson(node)
  console.log(`已创建镜头 #${node.id}（${node.status}）：${node.title || ''}`)
}

async function storyboard(opts) {
  if (!opts.file) fail('请提供 --file shots.json')
  let shots
  try {
    const raw = JSON.parse(fs.readFileSync(path.resolve(String(opts.file)), 'utf8'))
    shots = Array.isArray(raw) ? raw : raw.shots
  } catch (error) {
    fail(`读取分镜文件失败：${error.message}`)
  }
  if (!Array.isArray(shots) || !shots.length) fail('分镜文件需要是 JSON 数组，或包含 shots 数组')

  const normalized = shots.map((shot) => ({
    prompt: shot.prompt,
    title: shot.title,
    groupId: shot.groupId || (opts.group ? String(opts.group) : undefined),
    shotIndex: shot.shotIndex != null ? Number(shot.shotIndex) : undefined,
    imageIds: shot.imageIds,
    imageUrls: shot.imageUrls,
    model: shot.model,
    ratio: shot.ratio,
    duration: shot.duration != null ? Number(shot.duration) : undefined,
    resolution: shot.resolution
  }))

  const workspaceId = Number(opts.workspace || 1)
  const body = { groupId: opts.group ? String(opts.group) : undefined, shots: normalized }
  const list = await api('POST', `/api/canvas/${workspaceId}/storyboards`, { body })
  if (opts.json) return printJson(list)
  console.log(`已创建 ${list.length} 个镜头：${list.map((node) => `#${node.id}`).join(' ')}`)
}

function mimeOf(name) {
  const ext = path.extname(name).toLowerCase()
  if (ext === '.png') return 'image/png'
  if (ext === '.webp') return 'image/webp'
  if (ext === '.gif') return 'image/gif'
  return 'image/jpeg'
}

function buildMultipartBuffer(fileBuffer, fileName, fields, mimeType = mimeOf(fileName)) {
  const boundary = `----aigc${Date.now().toString(16)}`
  const parts = []
  for (const [name, value] of Object.entries(fields)) {
    if (value == null) continue
    parts.push(Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="${name}"\r\n\r\n${value}\r\n`))
  }
  parts.push(
    Buffer.from(
      `--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="${fileName.replace(/["\r\n]/g, '_')}"\r\n` +
        `Content-Type: ${mimeType}\r\n\r\n`
    )
  )
  parts.push(fileBuffer)
  parts.push(Buffer.from(`\r\n--${boundary}--\r\n`))
  return { body: Buffer.concat(parts), type: `multipart/form-data; boundary=${boundary}` }
}

function buildMultipart(filePath, fields) {
  const fileName = path.basename(filePath)
  return buildMultipartBuffer(fs.readFileSync(filePath), fileName, fields)
}

function downloadImage(imageUrl, redirects = 0) {
  return new Promise((resolve, reject) => {
    let url
    try {
      url = new URL(imageUrl)
    } catch {
      reject(new Error('图片 URL 格式不正确'))
      return
    }
    if (!['http:', 'https:'].includes(url.protocol)) {
      reject(new Error('图片 URL 仅支持 HTTP / HTTPS'))
      return
    }
    const lib = url.protocol === 'https:' ? https : http
    const req = lib.get(url, { headers: { Accept: 'image/*', 'User-Agent': 'AIGC-Canvas-CLI' } }, (res) => {
      if ([301, 302, 303, 307, 308].includes(res.statusCode) && res.headers.location) {
        res.resume()
        if (redirects >= 5) {
          reject(new Error('图片 URL 重定向次数过多'))
          return
        }
        downloadImage(new URL(res.headers.location, url).toString(), redirects + 1).then(resolve, reject)
        return
      }
      if (res.statusCode !== 200) {
        res.resume()
        reject(new Error(`下载图片失败：HTTP ${res.statusCode}`))
        return
      }
      const limit = 50 * 1024 * 1024
      const chunks = []
      let size = 0
      res.on('data', (chunk) => {
        size += chunk.length
        if (size > limit) {
          req.destroy(new Error('图片超过 50 MB，无法通过 CLI 上传'))
          return
        }
        chunks.push(chunk)
      })
      res.on('end', () => resolve({
        buffer: Buffer.concat(chunks),
        contentType: String(res.headers['content-type'] || '').split(';')[0].toLowerCase(),
        url
      }))
    })
    req.setTimeout(30000, () => req.destroy(new Error('下载图片超时（30 秒）')))
    req.on('error', reject)
  })
}

async function upload(opts) {
  if (!opts.file) fail('请提供 --file 图片路径')
  const filePath = path.resolve(String(opts.file))
  if (!fs.existsSync(filePath)) fail(`文件不存在：${filePath}`)

  const workspaceId = Number(opts.workspace || 1)
  const multipart = buildMultipart(filePath, {})
  let query = `?workspaceId=${workspaceId}`
  if (opts.x != null) query += `&x=${Number(opts.x)}`
  if (opts.y != null) query += `&y=${Number(opts.y)}`

  const result = await api('POST', `/api/uploads${query}`, { multipart })
  if (opts.json) return printJson(result)
  console.log(`已上传 #${result.id}：${result.name} → ${result.url}`)
}

async function uploadUrl(opts) {
  if (!opts['image-url']) fail('请提供 --image-url 图片URL')
  const workspaceId = Number(opts.workspace || 1)
  if (!Number.isInteger(workspaceId) || workspaceId <= 0) fail('--workspace 必须是正整数画布ID')
  const downloaded = await downloadImage(String(opts['image-url']))
  if (!downloaded.buffer.length) fail('下载的图片内容为空')
  if (downloaded.contentType && !downloaded.contentType.startsWith('image/')) {
    fail(`URL 返回的不是图片（Content-Type: ${downloaded.contentType}）`)
  }
  let fileName = String(opts.name || path.basename(downloaded.url.pathname) || 'agent-image.png')
  try { fileName = decodeURIComponent(fileName) } catch { /* 保留原始文件名 */ }
  if (!path.extname(fileName)) {
    const ext = { 'image/png': '.png', 'image/webp': '.webp', 'image/gif': '.gif' }[downloaded.contentType] || '.jpg'
    fileName += ext
  }
  const mimeType = downloaded.contentType.startsWith('image/') ? downloaded.contentType : mimeOf(fileName)
  const multipart = buildMultipartBuffer(downloaded.buffer, fileName, {}, mimeType)
  let query = `?workspaceId=${workspaceId}`
  if (opts.x != null) query += `&x=${Number(opts.x)}`
  if (opts.y != null) query += `&y=${Number(opts.y)}`
  const result = await api('POST', `/api/uploads${query}`, { multipart })
  if (opts.json) return printJson(result)
  console.log(`已从 URL 上传 #${result.id}：${result.name} → ${result.url}`)
}

async function removeTask(opts) {
  if (!opts.id) fail('请提供 --id 任务ID')
  const result = await api('DELETE', `/api/tasks/${Number(opts.id)}`)
  if (opts.json) return printJson(result)
  console.log(`已删除镜头 #${opts.id}`)
}

async function removeImage(opts) {
  if (!opts.id) fail('请提供 --id 图片ID')
  const workspaceId = Number(opts.workspace || 1)
  const result = await api('DELETE', `/api/canvas/${workspaceId}/images/${Number(opts.id)}`)
  if (opts.json) return printJson(result)
  console.log(`已删除图片 #${opts.id}`)
}

// ---------------------------------------------------------------- 入口

function parseArgs(argv) {
  const result = { _: [] }
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i]
    if (!arg.startsWith('--')) {
      result._.push(arg)
      continue
    }
    const body = arg.slice(2)
    const eq = body.indexOf('=')
    if (eq >= 0) {
      result[body.slice(0, eq)] = body.slice(eq + 1)
    } else if (argv[i + 1] != null && !argv[i + 1].startsWith('--')) {
      result[body] = argv[i + 1]
      i += 1
    } else {
      result[body] = true
    }
  }
  return result
}

function printJson(value) {
  console.log(JSON.stringify(value, null, 2))
}

function fail(message) {
  console.error(message)
  process.exit(1)
}

async function main() {
  const argv = process.argv.slice(2)
  const command = argv[0]
  const opts = parseArgs(argv.slice(1))

  switch (command) {
    case undefined:
    case 'help':
    case '--help':
    case '-h':
      console.log(HELP)
      return
    case 'login':
      return login(opts)
    case 'logout':
      return logout()
    case 'canvas':
    case 'status':
      return canvas(opts)
    case 'workspaces':
      return workspaces(opts)
    case 'new-workspace':
      return createWorkspace(opts)
    case 'copy-workspace':
      return copyWorkspace(opts)
    case 'delete-workspace':
      return deleteWorkspace(opts)
    case 'models':
      return models(opts)
    case 'assets':
      return assets(opts)
    case 'push':
      return push(opts)
    case 'storyboard':
      return storyboard(opts)
    case 'generate':
    case 'retry':
      return retryTask(opts)
    case 'cancel':
      return cancelTask(opts)
    case 'edit-prompt':
      return editPrompt(opts)
    case 'set-video-model':
      return setVideoModel(opts)
    case 'set-image-model':
      return setImageModel(opts)
    case 'prompt-records':
      return promptRecords(opts)
    case 'optimize-prompt':
      return optimizePrompt(opts)
    case 'upload':
      return upload(opts)
    case 'upload-url':
      return uploadUrl(opts)
    case 'snapshot':
      return saveSnapshot(opts)
    case 'order':
      return saveOrder(opts)
    case 'create-image':
      return createImage(opts)
    case 'create-ai-image':
      return createAiImage(opts)
    case 'generate-image':
      return generateImage(opts)
    case 'edit-image':
      return editImage(opts)
    case 'create-node':
      return createNode(opts)
    case 'edit-node':
      return editNode(opts)
    case 'delete-node':
      return removeNode(opts)
    case 'generate-script':
      return generateScript(opts)
    case 'link-image':
      return linkImage(opts)
    case 'unlink-image':
      return unlinkImage(opts)
    case 'rm':
      return removeTask(opts)
    case 'rm-image':
      return removeImage(opts)
    default:
      console.error(`未知命令：${command}`)
      console.log(HELP)
      process.exit(1)
  }
}

main().catch((error) => fail(error.message || String(error)))
