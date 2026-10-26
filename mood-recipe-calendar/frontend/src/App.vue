<script setup lang="ts">
import PrivacyPopup from './components/PrivacyPopup.vue'
import { refreshNavMetrics } from './composables/useNavBar'
import { autoCheckUpdate } from './utils/update'
import { useUserStore } from './stores/user'
import { reportClientError } from './utils/errorReporter'

onError((err) => {
  reportClientError('js', err)
})

onLaunch(() => {
  // 全局未处理 Promise 拒绝上报（架构评审 P1-4：此前前端报错完全无感知）
  uni.onUnhandledRejection(({ reason }) => {
    const err = reason as unknown
    if (err instanceof Error) reportClientError('unhandledrejection', err.message, err.stack)
    else reportClientError('unhandledrejection', String(err))
  })
  // 适配微信状态栏与胶囊按钮
  refreshNavMetrics()
  // 静默检查小程序更新（微信后台上传新包后自动分发，零维护）
  autoCheckUpdate()
  // 恢复登录状态
  const userStore = useUserStore()
  userStore.restoreFromStorage()
  // 全局监听登录过期：被动过期，只清凭证（clearSession 不会置 userInitiatedLogout），
  // 以便用户后续经 ensureLogin 静默重登，而非被永久卡在「未登录」。
  uni.$on('auth:expired', () => {
    userStore.clearSession()
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
