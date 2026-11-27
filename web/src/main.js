import { createApp } from 'vue'
import AuthGate from './AuthGate.vue'
import router from './router'

import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'
import './style.css'
import './pages.css'
import './theme'

createApp(AuthGate).use(router).mount('#app')
