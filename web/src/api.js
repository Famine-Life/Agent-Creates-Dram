import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 20000 })

http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const message = error?.response?.data?.message || error.message || '请求失败'
    const wrapped = new Error(message)
    wrapped.status = error?.response?.status
    return Promise.reject(wrapped)
  }
)

export const api = {
  meta: () => http.get('/meta'),
  canvas: (workspaceId) => http.get(`/canvas/${workspaceId}`),
  createShot: (workspaceId, body) => http.post(`/canvas/${workspaceId}/shots`, body),
  createStoryboard: (workspaceId, body) => http.post(`/canvas/${workspaceId}/storyboards`, body),
  savePrompt: (taskId, body) => http.put(`/tasks/${taskId}/prompt`, body),
  retry: (taskId) => http.post(`/tasks/${taskId}/retry`),
  cancel: (taskId) => http.post(`/tasks/${taskId}/cancel`),
  removeTask: (taskId) => http.delete(`/tasks/${taskId}`),
  saveSnapshot: (workspaceId, snapshot, baseVersion) =>
    http.put(`/canvas/${workspaceId}/snapshot`, { snapshot, baseVersion }),
  saveOrder: (workspaceId, items) => http.put(`/canvas/${workspaceId}/order`, { items }),
  createEdge: (workspaceId, body) => http.post(`/canvas/${workspaceId}/edges`, body),
  removeEdge: (workspaceId, edgeId) => http.delete(`/canvas/${workspaceId}/edges/${edgeId}`),
  removeImage: (workspaceId, imageId) => http.delete(`/canvas/${workspaceId}/images/${imageId}`),
  updateImage: (workspaceId, imageId, body) => http.put(`/canvas/${workspaceId}/images/${imageId}`, body),
  createCanvasNode: (workspaceId, body) => http.post(`/canvas/${workspaceId}/nodes`, body),
  updateCanvasNode: (workspaceId, nodeId, body) => http.put(`/canvas/${workspaceId}/nodes/${nodeId}`, body),
  removeCanvasNode: (workspaceId, nodeId) => http.delete(`/canvas/${workspaceId}/nodes/${nodeId}`),
  generateScript: (workspaceId, nodeId, prompt) =>
    http.post(`/canvas/${workspaceId}/nodes/${nodeId}/script`, { prompt }),
  createAiImage: (workspaceId, body) => http.post(`/canvas/${workspaceId}/images/ai`, body),
  optimizePrompt: (workspaceId, prompt) =>
    http.post(`/canvas/${workspaceId}/ai/prompt/optimize`, { prompt }),
  generateImage: (workspaceId, imageId, prompt) =>
    http.post(`/canvas/${workspaceId}/images/${imageId}/generate`, { prompt }),
  upload: (workspaceId, file, x, y) => {
    const form = new FormData()
    form.append('file', file)
    form.append('workspaceId', workspaceId)
    let url = `/uploads?workspaceId=${workspaceId}`
    if (x != null && y != null) url += `&x=${Math.round(x)}&y=${Math.round(y)}`
    return http.post(url, form)
  }
}
