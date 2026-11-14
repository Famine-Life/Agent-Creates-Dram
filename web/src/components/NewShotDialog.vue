<script setup>
import { computed, nextTick, reactive, ref } from 'vue'
import { state, createShot, uploadImageFile, toast } from '../store/canvas'

const props = defineProps({
  position: { type: Object, default: null }
})
const emit = defineEmits(['close', 'created'])

const form = reactive({ title: '', groupId: '', prompt: '' })
const images = ref([])
const pickerOpen = ref(false)
const saving = ref(false)
const promptEl = ref(null)
const fileEl = ref(null)

const groups = computed(() => [...new Set(state.tasks.map((t) => t.groupId).filter(Boolean))])

function onPromptInput(event) {
  const element = event.target
  const caret = element.selectionStart
  pickerOpen.value = caret > 0 && element.value[caret - 1] === '@'
}

function insertRef(index) {
  const element = promptEl.value
  const caret = element.selectionStart || form.prompt.length
  const token = `@图片${index + 1}`
  const before = form.prompt.slice(0, Math.max(0, caret - 1))
  const after = form.prompt.slice(caret)
  form.prompt = before + token + after
  pickerOpen.value = false
  nextTick(() => {
    element?.focus()
    const position = (before + token).length
    element?.setSelectionRange(position, position)
  })
}

async function addFiles(files) {
  for (const file of files) {
    if (!file.type.startsWith('image/')) continue
    if (images.value.length >= 6) {
      toast('最多 6 张参考图')
      break
    }
    try {
      const uploaded = await uploadImageFile(file)
      images.value.push(uploaded)
    } catch (e) {
      toast(e.message)
    }
  }
}

async function onFileChange(event) {
  await addFiles([...event.target.files])
  event.target.value = ''
}

async function onPaste(event) {
  const files = [...(event.clipboardData?.files || [])]
  if (files.length) {
    event.preventDefault()
    await addFiles(files)
  }
}

function removeImage(index) {
  const removed = images.value[index]
  images.value.splice(index, 1)
  // 删除图片后清理越界的 @ 引用
  const token = `@图片${index + 1}`
  if (form.prompt.includes(token)) {
    form.prompt = form.prompt.split(token).join('')
    toast('已移除对应引用标记')
  }
  return removed
}

async function submit() {
  if (!form.prompt.trim()) {
    toast('请填写提示词')
    return
  }
  if (/\s/.test(form.groupId)) {
    toast('分组名不要包含空格')
    return
  }
  saving.value = true
  try {
    const node = await createShot({
      prompt: form.prompt.trim(),
      title: form.title.trim() || null,
      groupId: form.groupId.trim() || null,
      shotIndex: null,
      imageIds: images.value.map((item) => item.id)
    })
    emit('created', { node, position: props.position })
    emit('close')
  } catch (e) {
    toast(e.message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="overlay" @click.self="emit('close')" @keydown.esc="emit('close')" @paste="onPaste">
    <div class="dialog">
      <header class="dialog-head">
        <div>
          <h2>新建提示词</h2>
          <p class="muted">提交后画布上会生成一个视频节点，并立即开始排队</p>
        </div>
        <button class="icon-btn" @click="emit('close')">×</button>
      </header>

      <div class="dialog-body">
        <div class="grid-2">
          <label class="field">
            <span class="field-label">标题（选填）</span>
            <input v-model="form.title" type="text" placeholder="例如：A-01 海面空镜" />
          </label>
          <label class="field">
            <span class="field-label">分组</span>
            <input v-model="form.groupId" type="text" list="group-options" placeholder="默认分组" />
            <datalist id="group-options">
              <option v-for="g in groups" :key="g" :value="g" />
            </datalist>
          </label>
        </div>

        <div class="field">
          <span class="field-label">
            参考图（选填，最多 6 张）
            <em>生成后按序号对应提示词里的 @图片n</em>
          </span>
          <div class="upload-zone" @click="fileEl.click()">
            <div v-for="(img, index) in images" :key="img.id" class="thumb" @click.stop>
              <img :src="img.url" :alt="img.name" />
              <span class="thumb-index">{{ index + 1 }}</span>
              <button class="thumb-del" @click.stop="removeImage(index)">×</button>
            </div>
            <div class="thumb add">+ 上传</div>
          </div>
          <input ref="fileEl" type="file" accept="image/png,image/jpeg,image/webp" multiple hidden @change="onFileChange" />
        </div>

        <label class="field prompt-field">
          <span class="field-label">
            提示词
            <em>输入 @ 可插入参考图引用 · Ctrl/⌘ + Enter 提交</em>
          </span>
          <textarea
            ref="promptEl"
            v-model="form.prompt"
            rows="7"
            spellcheck="false"
            placeholder="描述画面内容、镜头运动、光线氛围…"
            @input="onPromptInput"
            @keydown.ctrl.enter.prevent="submit"
            @keydown.meta.enter.prevent="submit"
          />
          <div v-if="pickerOpen && images.length" class="picker">
            <button v-for="(img, index) in images" :key="img.id" class="picker-item" @click="insertRef(index)">
              <img :src="img.url" :alt="img.name" />
              <span>@图片{{ index + 1 }}</span>
            </button>
          </div>
          <div v-else-if="pickerOpen" class="picker empty">还没有参考图，先上传一张</div>
        </label>
      </div>

      <footer class="dialog-foot">
        <span class="muted">{{ form.prompt.length }} / 2000</span>
        <div class="row">
          <button class="btn ghost" @click="emit('close')">取消</button>
          <button class="btn primary" :disabled="saving" @click="submit">
            {{ saving ? '提交中…' : '提交并生成' }}
          </button>
        </div>
      </footer>
    </div>
  </div>
</template>
