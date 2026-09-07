<script setup lang="ts">
import { navBack } from '@/composables/useNavBar'
import { ref } from 'vue'
import MoodPicker from '../../components/guozai/MoodPicker.vue'
import { fetchFoodPreference } from '../../api/preferences'
import { ensureLogin } from '../../utils/login'
import { toast, toastError, toastSuccess } from '../../utils/toast'

definePage({
  name: 'mood',
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '选一个心情吧',
  },
})

const router = useRouter()
const selected = ref('')

async function onConfirm(m: { key: string }) {
  if (uni.getStorageSync('mrc_preference_onboarded')) {
    router.push({ name: 'recipe', query: { mood: m.key } })
    return
  }
  try {
    await ensureLogin()
    const preference = await fetchFoodPreference()
    if (preference.onboardingCompleted) {
      uni.setStorageSync('mrc_preference_onboarded', '1')
      router.push({ name: 'recipe', query: { mood: m.key } })
    } else {
      router.push({ name: 'preferences', query: { from: 'onboarding', mood: m.key } })
    }
  } catch (e: any) {
    toastError(e, '暂时无法读取锅仔记忆')
  }
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
