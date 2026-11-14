<script setup>
defineProps({
  data: { type: Object, required: true }
})
</script>

<template>
  <div class="group-node">
    <span class="group-dot" />
    <span class="group-name">{{ data.name }}</span>
    <span class="group-count">{{ data.count }} 镜</span>
  </div>
</template>
