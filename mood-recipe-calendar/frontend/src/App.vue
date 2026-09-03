<script setup lang="ts">
import { useUserStore } from './stores/user'
import { refreshNavMetrics } from './composables/useNavBar'

onLaunch(() => {
  // 适配微信状态栏与胶囊按钮
  refreshNavMetrics()
  // 恢复登录状态
  const userStore = useUserStore()
  userStore.restoreFromStorage()
})
</script>

<style lang="scss">
@use '@wot-ui/ui/styles/theme/index.scss' as *;
@use './styles/theme.scss' as *;

@font-face {
  font-family: 'Resource Han Rounded';
  src: url('/static/fonts/RHR-Regular.woff2') format('woff2'),
       url('/static/fonts/RHR-Regular.ttf') format('truetype');
  font-weight: 400;
  font-display: swap;
}
@font-face {
  font-family: 'Resource Han Rounded';
  src: url('/static/fonts/RHR-Bold.woff2') format('woff2'),
       url('/static/fonts/RHR-Bold.ttf') format('truetype');
  font-weight: 700;
  font-display: swap;
}

page {
  background: var(--mrc-bg);
  font-family: 'Resource Han Rounded', 'Yuanti SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: var(--mrc-text);
}
.page-wraper {
  min-height: calc(100vh - var(--window-top));
  box-sizing: border-box;
  background: var(--mrc-bg);
  font-family: 'Resource Han Rounded', 'Yuanti SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
</style>
