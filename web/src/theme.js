import { ref, watch } from 'vue'

const KEY = 'aigc.theme'
const initial = localStorage.getItem(KEY) === 'light' ? 'light' : 'dark'

export const theme = ref(initial)

document.documentElement.dataset.theme = initial

watch(theme, (value) => {
  document.documentElement.dataset.theme = value
  localStorage.setItem(KEY, value)
})

export function toggleTheme() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
}
