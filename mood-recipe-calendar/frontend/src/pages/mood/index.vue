<script setup lang="ts">
import { navBack } from '@/composables/useNavBar'
import { ref } from 'vue'
import MoodPicker from '../../components/guozai/MoodPicker.vue'
import { fetchFoodPreference } from '../../api/preferences'
import { ensureLogin } from '../../utils/login'

definePage({
  name: 'mood',
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '选一个心情吧',
  },
})

import { onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'

// 直接本页写生命周期，比 useShare 组合式更可靠
onShareAppMessage(() => {
  const pages = getCurrentPages()
  const route = (pages[pages.length - 1] as any)?.route || ''
  return { title: '锅仔 · 按心情帮你决定今天吃什么', path: `/${route}` }
})
onShareTimeline(() => ({ title: '锅仔 · 按心情帮你决定今天吃什么' }))

const router = useRouter()
const selected = ref('')

async function onConfirm(m: { key: string }) {
  // 选心情的本意就是要推荐，绝不能被 onboarding 拦到「锅仔记忆」记忆管理页。
  // 偏好收集改为非阻塞：仅在标记缺失时静默补查并落标记，任何失败都不阻断推荐跳转。
  if (!uni.getStorageSync('mrc_preference_onboarded')) {
    try {
      await ensureLogin()
      const preference = await fetchFoodPreference()
      if (preference?.onboardingCompleted) uni.setStorageSync('mrc_preference_onboarded', '1')
    } catch {
      // 读取偏好失败不阻断推荐，用户可在「设置 → 锅仔记忆」里补
    }
  }
  router.push({ name: 'recipe', params: { mood: m.key } })
}
</script>

<template>
  <view class="mood-page">
    <wd-navbar title="选一个心情吧" left-arrow safe-area-inset-top @click-left="navBack"  custom-style="background-color: transparent !important;" />

    <MoodPicker
      v-model="selected"
      :hero-height="360"
      title="此刻，你的心情是？"
      confirm-text="就选它，开始推荐"
      @confirm="onConfirm"
    />
  </view>
</template>

<style lang="scss" scoped>
.mood-page {
  min-height: 100vh;
  box-sizing: border-box;
  background: var(--mrc-bg);
  padding: 0 32rpx;
  padding-bottom: calc(160rpx + env(safe-area-inset-bottom));
}
</style>
