/** 页面共用的展示格式化：金额、时间与业务枚举文案 */

export function formatCents(cents) {
  if (cents == null || Number.isNaN(Number(cents))) return '-'
  const value = Number(cents) / 100
  return `¥${Number.isInteger(value) ? value : value.toFixed(2)}`
}

export function formatDate(value) {
  if (!value) return '-'
  const text = String(value).replace('T', ' ')
  return text.length > 19 ? text.slice(0, 19) : text
}

export function formatDay(value) {
  if (!value) return '-'
  const text = String(value).replace('T', ' ')
  return text.slice(0, 10)
}

export const ORDER_STATUS = {
  PENDING: { text: '待支付', tone: 'busy' },
  PAYING: { text: '支付中', tone: 'busy' },
  PAID: { text: '已支付', tone: 'done' },
  CLOSED: { text: '已关闭', tone: 'idle' }
}

export const LEDGER_TYPE = {
  RESERVE: '冻结',
  CAPTURE: '消耗',
  RELEASE: '释放',
  RECHARGE: '充值',
  MEMBER_GRANT: '会员发放',
  ADJUST: '调整',
  INSUFFICIENT: '余额不足'
}

export const LEDGER_STATUS = {
  FROZEN: '冻结中',
  SETTLED: '已结算',
  FAILED: '失败'
}

export const CHANNEL_TEXT = {
  mock: '模拟支付',
  wechat: '微信支付',
  alipay: '支付宝'
}

export const PLAN_TEXT = {
  FREE: '免费版',
  PRO: '专业版',
  STUDIO: '工作室版'
}

export function orderStatus(status) {
  return ORDER_STATUS[status] || { text: status || '-', tone: 'idle' }
}
