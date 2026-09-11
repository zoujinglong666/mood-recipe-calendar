<script setup lang="ts">
import PrivacyPopup from './components/PrivacyPopup.vue'
import { refreshNavMetrics } from './composables/useNavBar'
import { useUserStore } from './stores/user'

onLaunch(() => {
  // 适配微信状态栏与胶囊按钮
  refreshNavMetrics()
  // 恢复登录状态
  const userStore = useUserStore()
  userStore.restoreFromStorage()
  // 全局监听登录过期：请求拦截器清除 storage 后，同步清除内存状态
  uni.$on('auth:expired', () => {
    userStore.logout()
    const pages = getCurrentPages()
    const current = pages[pages.length - 1] as any
    if (current?.route !== 'pages/login/index')
      uni.reLaunch({ url: '/pages/login/index' })
  })
})
</script>

<template>
  <PrivacyPopup />
</template>

<style lang="scss">
@use '@wot-ui/ui/styles/theme/index.scss' as *;
@use './styles/theme.scss' as *;

page {
  background: var(--mrc-bg);
  font-family: var(--mrc-font-family);
  color: var(--mrc-text);
}
.page-wraper {
  min-height: calc(100vh - var(--window-top));
  box-sizing: border-box;
  background: var(--mrc-bg);
}
</style>
