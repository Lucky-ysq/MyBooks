import App from './App'
import { createSSRApp } from 'vue'

// uniapp Vue3 标准入口
export function createApp() {
  const app = createSSRApp(App)
  return { app }
}
