<script setup>
defineProps({
  task: { type: Object, required: true }
})
const emit = defineEmits(['close'])
</script>

<template>
  <div class="overlay" @click.self="emit('close')">
    <div class="dialog wide">
      <header class="dialog-head">
        <div>
          <h2>{{ task.title }}</h2>
          <p class="muted">镜头 #{{ task.shotIndex }} · {{ task.groupId }}</p>
        </div>
        <button class="icon-btn" @click="emit('close')">×</button>
      </header>
      <video class="preview-video" :src="task.videoUrl" controls autoplay playsinline />
      <footer class="dialog-foot">
        <p class="muted prompt-echo">{{ task.prompt }}</p>
        <div class="row">
          <a class="btn ghost" :href="task.videoUrl" target="_blank" rel="noreferrer">下载成片</a>
          <button class="btn primary" @click="emit('close')">关闭</button>
        </div>
      </footer>
    </div>
  </div>
</template>
