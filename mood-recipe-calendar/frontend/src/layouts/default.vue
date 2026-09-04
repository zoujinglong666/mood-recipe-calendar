<script lang="ts" setup>
import { useManualTheme } from '@/composables/useManualTheme'

const { theme, themeVars } = useManualTheme()
</script>

<script lang="ts">
export default {
  options: {
    addGlobalClass: true,
    virtualHost: true,
    styleIsolation: 'shared',
  },
}
</script>

<template>
  <wd-config-provider :theme="theme" :theme-vars="themeVars">
    <view :class="{ 'mrc-dark': theme === 'dark' }" class="mrc-layout-root">
      <slot />
    </view>
  </wd-config-provider>
</template>

<style lang="scss">
.mrc-layout-root {
  min-height: 100vh;
  box-sizing: border-box;
}
</style>
