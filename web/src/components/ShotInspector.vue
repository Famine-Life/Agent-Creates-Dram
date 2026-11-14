<script setup>
import { computed } from 'vue'
import {
  state,
  statusOf,
  retryTask,
  cancelTask,
  removeTask,
  toast
} from '../store/canvas'

const emit = defineEmits(['hide'])

const task = computed(() => state.tasks.find((t) => t.id === state.selectedTaskId) || null)
const image = computed(() => state.images.find((i) => i.id === state.selectedImageId) || null)
const status = computed(() => statusOf(task.value))
const active = computed(
  () => task.value && ['RECEIVED', 'QUEUED', 'SUBMITTED', 'RUNNING'].includes(task.value.status)
)
const linkedShots = computed(() => {
  if (!image.value) return []
  const taskIds = state.edges.filter((e) => e.imageId === image.value.id).map((e) => e.taskId)
  return state.tasks.filter((t) => taskIds.includes(t.id))
})

async function guard(action, label) {
  try {
    await action
  } catch (e) {
    toast(`${label}：${e.message}`)
  }
}

function remove() {
  if (!window.confirm(`确定删除镜头《${task.value.title}》？`)) return
  guard(removeTask(task.value.id), '删除失败')
}

function copyPrompt() {
  navigator.clipboard?.writeText(task.value.prompt || '')
  toast('提示词已复制')
}
</script>

<template>
  <aside class="inspector">
    <button class="inspector-hide" title="收起详情" @click="emit('hide')">»</button>
    <template v-if="task">
      <div class="inspector-head">
        <div>
          <p class="muted">镜头详情</p>
          <h2>{{ task.title }}</h2>
        </div>
        <span class="pill" :class="`tone-${status.tone}`">{{ status.text }}</span>
      </div>

      <div class="inspector-body">
        <div v-if="active" class="progress-line">
          <div class="bar"><i :style="{ width: (task.progress || 0) + '%' }" /></div>
          <span>{{ task.progress || 0 }}%</span>
        </div>

        <div v-if="task.errorMsg" class="alert">{{ task.errorMsg }}</div>

        <div class="field">
          <span class="field-label">提示词 <em>· 在节点下方编辑，支持 @ 引用图片</em></span>
          <p class="prompt-read">{{ task.prompt }}</p>
        </div>

        <div class="row">
          <button class="btn ghost" @click="copyPrompt">复制提示词</button>
        </div>

        <dl class="meta">
          <div><dt>分组</dt><dd>{{ task.groupId }}</dd></div>
          <div><dt>镜号</dt><dd>#{{ task.shotIndex }}</dd></div>
          <div><dt>供应商</dt><dd>{{ task.provider || '-' }}</dd></div>
          <div><dt>模型</dt><dd>{{ task.model || '-' }}</dd></div>
          <div><dt>比例</dt><dd>{{ task.ratio || '16:9' }}</dd></div>
          <div><dt>清晰度</dt><dd>{{ task.resolution || '720P' }}</dd></div>
          <div><dt>时长</dt><dd>{{ task.duration ? task.duration + 's' : '-' }}</dd></div>
          <div><dt>任务 ID</dt><dd>{{ task.id }}</dd></div>
          <div><dt>参考图</dt><dd>{{ task.imageIds?.length || 0 }} 张</dd></div>
          <div><dt>提交时间</dt><dd>{{ task.submittedAt ? task.submittedAt.replace('T', ' ') : '-' }}</dd></div>
        </dl>

        <div class="thumbs" v-if="task.imageIds?.length">
          <img
            v-for="(url, index) in task.imageUrls"
            :key="url + index"
            :src="url"
            :alt="`图片${index + 1}`"
            :title="`@图片${index + 1}`"
          />
        </div>

        <div class="row wrap">
          <button v-if="active" class="btn ghost" @click="guard(cancelTask(task.id), '取消失败')">取消任务</button>
          <button v-else class="btn ghost" @click="guard(retryTask(task.id), '重试失败')">重新生成</button>
          <a v-if="task.videoUrl" class="btn ghost" :href="task.videoUrl" target="_blank" rel="noreferrer">下载成片</a>
          <button class="btn danger" @click="remove">删除镜头</button>
        </div>
      </div>
    </template>

    <template v-else-if="image">
      <div class="inspector-head">
        <div>
          <p class="muted">参考图</p>
          <h2>{{ image.name }}</h2>
        </div>
      </div>
      <div class="inspector-body">
        <img class="preview-img" :src="image.url" :alt="image.name" />
        <div v-if="image.prompt" class="field">
          <span class="field-label">图片提示词</span>
          <p class="prompt-read">{{ image.prompt }}</p>
        </div>
        <p class="muted">已连接到 {{ linkedShots.length }} 个镜头</p>
        <ul class="link-list">
          <li v-for="shot in linkedShots" :key="shot.id">{{ shot.title }}</li>
        </ul>
        <p class="hint">提示：从图片右侧圆点拖到镜头卡片左侧，即可建立引用。</p>
      </div>
    </template>

    <div v-else class="inspector-empty">
      <p>选中画布上的镜头或参考图<br />查看详情与操作</p>
    </div>
  </aside>
</template>
