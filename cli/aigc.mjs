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
  assets      查看素材（视频 / 生成图片 / 上传图片）
                [--type video|ai|upload] [--workspace 1] [--json]
  push        推送单个镜头
                --prompt <提示词> [--title <标题>] [--group <分组>] [--index <序号>]
                [--images <图片ID,逗号分隔>] [--model <平台/模型，如 ark/doubao-seedance-2-0-260128>]
                [--ratio 16:9] [--resolution 720P] [--duration 5]
  storyboard  批量推送分镜         --file <shots.json> [--group <分组>]
  upload      上传图片并创建画布节点 --file <图片路径> [--x <坐标>] [--y <坐标>]
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

function buildMultipart(filePath, fields) {
  const boundary = `----aigc${Date.now().toString(16)}`
  const parts = []
  for (const [name, value] of Object.entries(fields)) {
    if (value == null) continue
    parts.push(Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="${name}"\r\n\r\n${value}\r\n`))
  }
  const fileName = path.basename(filePath)
  parts.push(
    Buffer.from(
      `--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="${fileName}"\r\n` +
        `Content-Type: ${mimeOf(fileName)}\r\n\r\n`
    )
  )
  parts.push(fs.readFileSync(filePath))
  parts.push(Buffer.from(`\r\n--${boundary}--\r\n`))
  return { body: Buffer.concat(parts), type: `multipart/form-data; boundary=${boundary}` }
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

async function removeTask(opts) {
  if (!opts.id) fail('请提供 --id 任务ID')
  await api('DELETE', `/api/tasks/${Number(opts.id)}`)
  console.log(`已删除镜头 #${opts.id}`)
}

async function removeImage(opts) {
  if (!opts.id) fail('请提供 --id 图片ID')
  const workspaceId = Number(opts.workspace || 1)
  await api('DELETE', `/api/canvas/${workspaceId}/images/${Number(opts.id)}`)
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
    case 'assets':
      return assets(opts)
    case 'push':
      return push(opts)
    case 'storyboard':
      return storyboard(opts)
    case 'upload':
      return upload(opts)
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
