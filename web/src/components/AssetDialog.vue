<script setup>
import { computed, ref } from 'vue'
import { state } from '../store/canvas'

const emit = defineEmits(['close'])

const tab = ref('all')
const preview = ref(null)

// 以服务端 source 为准，老数据按是否有提示词/状态兜底判断
function isAi(image) {
  if (image.source) return image.source === 'AI'
  return image.prompt != null || image.status != null
}

function toAsset(image) {
  return {
    key: `image-${image.id}`,
    kind: 'image',
    source: isAi(image) ? 'AI' : 'UPLOAD',
    id: image.id,
    title: image.name || `图片 ${image.id}`,
    desc: image.prompt || '',
    thumb: image.url,
    url: image.url
  }
}

const videos = computed(() =>
  state.tasks
    .filter((task) => task.videoUrl)
    .map((task) => ({
      key: `task-${task.id}`,
      kind: 'video',
      id: task.id,
      title: task.title || `镜头 ${task.id}`,
      desc: task.prompt || '',
      thumb: task.coverUrl || '',
      url: task.videoUrl
    }))
    .sort((a, b) => b.id - a.id)
)

const generatedImages = computed(() =>
  state.images
    .filter((image) => image.url && isAi(image))
    .map(toAsset)
    .sort((a, b) => b.id - a.id)
)

const uploads = computed(() =>
  state.images
    .filter((image) => image.url && !isAi(image))
    .map(toAsset)
    .sort((a, b) => b.id - a.id)
)

const tabs = computed(() => [
  { key: 'all', label: '全部', count: videos.value.length + generatedImages.value.length + uploads.value.length },
  { key: 'video', label: '生成视频', count: videos.value.length },
  { key: 'ai', label: '生成图片', count: generatedImages.value.length },
  { key: 'upload', label: '上传图片', count: uploads.value.length }
])

const assets = computed(() => {
  if (tab.value === 'video') return videos.value
  if (tab.value === 'ai') return generatedImages.value
  if (tab.value === 'upload') return uploads.value
  return [...videos.value, ...generatedImages.value, ...uploads.value]
})

function kindLabel(item) {
  if (item.kind === 'video') return '生成视频'
  return item.source === 'AI' ? '生成图片' : '上传图片'
}
</script>

<template>
  <div class="overlay" @click.self="emit('close')">
    <div class="dialog wide">
      <header class="dialog-head">
        <div>
          <h2>资产管理</h2>
          <p class="muted">已生成的视频、接口生成的图片，以及自己上传的图片</p>
        </div>
        <button class="icon-btn" @click="emit('close')">×</button>
      </header>

      <div class="asset-tabs">
        <button
          v-for="item in tabs"
          :key="item.key"
          class="asset-tab"
          :class="{ active: tab === item.key }"
          @click="tab = item.key"
        >
          {{ item.label }}<span class="asset-count">{{ item.count }}</span>
        </button>
      </div>

      <div class="dialog-body">
        <div v-if="!assets.length" class="asset-empty muted">
          暂无素材，生成视频 / AI 图片或上传图片后会自动出现在这里。
        </div>
        <div v-else class="asset-grid">
          <article v-for="item in assets" :key="item.key" class="asset-card">
            <button class="asset-thumb" :class="item.kind" @click="preview = item">
              <img v-if="item.thumb" :src="item.thumb" :alt="item.title" loading="lazy" />
              <span v-else class="asset-placeholder">{{ item.kind === 'video' ? '视频' : '图片' }}</span>
              <span class="asset-kind" :class="item.kind === 'video' ? 'video' : item.source === 'AI' ? 'ai' : 'upload'">
                {{ kindLabel(item) }}
              </span>
            </button>
            <div class="asset-meta">
              <strong :title="item.title">{{ item.title }}</strong>
              <span class="muted asset-desc" :title="item.desc">{{ item.desc || '无提示词' }}</span>
              <div class="asset-actions">
                <button class="btn ghost sm" @click="preview = item">预览</button>
                <a class="btn ghost sm" :href="item.url" target="_blank" rel="noreferrer" download>下载</a>
              </div>
            </div>
          </article>
        </div>
      </div>
    </div>

    <div v-if="preview" class="asset-preview" @click.self="preview = null">
      <div class="asset-preview-box">
        <header class="asset-preview-head">
          <div>
            <strong>{{ preview.title }}</strong>
            <span class="muted">{{ kindLabel(preview) }}</span>
          </div>
          <button class="icon-btn" @click="preview = null">×</button>
        </header>
        <video v-if="preview.kind === 'video'" :src="preview.url" controls autoplay playsinline />
        <img v-else :src="preview.url" :alt="preview.title" />
        <footer class="asset-preview-foot">
          <span class="muted asset-desc">{{ preview.desc || '无提示词' }}</span>
          <a class="btn ghost sm" :href="preview.url" target="_blank" rel="noreferrer" download>下载</a>
        </footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.asset-tabs {
  display: flex;
  gap: 6px;
  padding: 10px 18px;
  border-bottom: 1px solid var(--border);
  flex-wrap: wrap;
}

.asset-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
}

.asset-tab:hover {
  color: var(--text);
}

.asset-tab.active {
  color: var(--text);
  border-color: #6c5ce7;
  background: rgba(108, 92, 231, 0.16);
}

.asset-count {
  font-size: 11px;
  opacity: 0.75;
}

.asset-empty {
  padding: 48px 0;
  text-align: center;
  font-size: 13px;
}

.asset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}

.asset-card {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
  background: var(--card);
}

.asset-thumb {
  position: relative;
  display: block;
  width: 100%;
  aspect-ratio: 16 / 10;
  padding: 0;
  border: 0;
  background: var(--sunken);
  cursor: pointer;
  overflow: hidden;
}

.asset-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.asset-placeholder {
  display: grid;
  place-items: center;
  height: 100%;
  color: var(--muted);
  font-size: 12px;
}

.asset-kind {
  position: absolute;
  left: 8px;
  top: 8px;
  padding: 2px 7px;
  border-radius: 999px;
  font-size: 10px;
  color: var(--text);
  background: var(--chip);
  border: 1px solid var(--border);
}

.asset-kind.ai {
  color: var(--accent-text);
}

.asset-kind.upload {
  color: var(--done);
}

.asset-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 9px 10px 10px;
  min-width: 0;
}

.asset-meta strong {
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.asset-desc {
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.asset-actions {
  display: flex;
  gap: 6px;
  margin-top: 4px;
}

.asset-actions .btn {
  text-decoration: none;
}

.asset-preview {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: rgba(3, 4, 6, 0.82);
}

.asset-preview-box {
  width: min(880px, 92vw);
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 14px;
  overflow: hidden;
}

.asset-preview-head,
.asset-preview-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
}

.asset-preview-head {
  border-bottom: 1px solid var(--border);
}

.asset-preview-head > div {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.asset-preview-foot {
  border-top: 1px solid var(--border);
}

.asset-preview-foot .asset-desc {
  min-width: 0;
  white-space: normal;
  max-height: 48px;
  overflow: auto;
}

.asset-preview-box video,
.asset-preview-box img {
  max-height: 68vh;
  width: 100%;
  object-fit: contain;
  background: #000;
  display: block;
}
</style>
