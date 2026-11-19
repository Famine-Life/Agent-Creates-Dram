import { ref } from 'vue'

const MAX_STEPS = 50
const MERGE_WINDOW = 1200
const stack = []
let sequence = 0
let transaction = null
let applying = false

export const historyBusy = ref(false)

export function missingHistoryTarget(message) {
  const error = new Error(message)
  error.historyMissing = true
  return error
}

/** 操作成功后记录逆命令；连续文本保存合并时保留最早的 undo。 */
export function recordHistory(entry) {
  if (applying || !entry?.undo) return
  if (transaction) {
    transaction.entries.push(entry)
    return
  }
  const previous = stack[stack.length - 1]
  const now = Date.now()
  if (entry.mergeKey && previous?.mergeKey === entry.mergeKey && now - previous.at <= MERGE_WINDOW) {
    previous.after = entry.after
    previous.at = now
    return
  }
  stack.push({ ...entry, id: ++sequence, at: now })
  if (stack.length > MAX_STEPS) stack.splice(0, stack.length - MAX_STEPS)
}

/** 节点复制等内部组合操作不产生用户历史。 */
export async function withoutHistory(action) {
  const previous = applying
  applying = true
  try {
    return await action()
  } finally {
    applying = previous
  }
}

/** 将批量连线或“连线并写入提示词”合并为一个撤销步骤。 */
export async function historyTransaction(label, action) {
  if (applying || transaction) return action()
  const current = { label, entries: [] }
  transaction = current
  try {
    const result = await action()
    if (current.entries.length) {
      stack.push({
        id: ++sequence,
        at: Date.now(),
        label,
        workspaceId: current.entries[0].workspaceId,
        composite: true,
        undo: async () => {
          for (const entry of [...current.entries].reverse()) await entry.undo()
        }
      })
      if (stack.length > MAX_STEPS) stack.splice(0, stack.length - MAX_STEPS)
    }
    return result
  } finally {
    transaction = null
  }
}

export function clearHistory() {
  stack.length = 0
  transaction = null
}

/** 撤销栈顶；单命令失败保留原栈，复合命令部分失败由调用方刷新画布。 */
export async function undoLatest(workspaceId) {
  if (historyBusy.value) return false
  const entry = stack[stack.length - 1]
  if (!entry) return false
  if (entry.workspaceId !== workspaceId) {
    clearHistory()
    return false
  }
  historyBusy.value = true
  try {
    await withoutHistory(entry.undo)
    if (stack[stack.length - 1] === entry) stack.pop()
    return true
  } catch (error) {
    if (error.historyMissing) {
      if (stack[stack.length - 1] === entry) stack.pop()
    } else if (entry.composite) {
      clearHistory()
      error.historyPartial = true
    }
    throw error
  } finally {
    historyBusy.value = false
  }
}
