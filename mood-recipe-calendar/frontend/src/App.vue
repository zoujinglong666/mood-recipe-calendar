<template>
  <PrivacyPopup />
</template>

<script setup lang="ts">
import { useUserStore } from './stores/user'
import { refreshNavMetrics } from './composables/useNavBar'
import PrivacyPopup from './components/PrivacyPopup.vue'

onLaunch(() => {
  // 适配微信状态栏与胶囊按钮
  refreshNavMetrics()
  // 恢复登录状态
  const userStore = useUserStore()
  userStore.restoreFromStorage()
  // 全局监听登录过期：请求拦截器清除 storage 后，同步清除内存状态
  uni.$on('auth:expired', () => {
    userStore.logout()
  })
})
</script>

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
