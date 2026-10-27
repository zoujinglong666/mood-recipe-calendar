<script setup lang="ts">
import { navBack } from '@/composables/useNavBar'
import { ref } from 'vue'
import MoodPicker from '../../components/guozai/MoodPicker.vue'
import MealSheet from '../../components/guozai/MealSheet.vue'
import GuozaiOnboardingSheet from '../../components/guozai/GuozaiOnboardingSheet.vue'
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
/** 餐别与心情一样由用户自己选：确认心情后弹餐别选择器，选完一起带去推荐页 */
const showMealSheet = ref(false)
/** 冷启动画像问卷：新用户首日一次性的引导，完成/跳过都放行（只弹一次见 onConfirm） */
const showOnboarding = ref(false)
const pendingMood = ref('')

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
  pendingMood.value = m.key
  // [2026-10-27] 冷启动画像问卷：只弹一次的契约——展示时立刻落 asked 标记，
  // 之后完成/跳过/下滑关闭都绝不再自动弹（问卷本身可跳过，不硬拦）。
  if (!uni.getStorageSync('mrc_preference_onboarded') && !uni.getStorageSync('mrc_onboarding_asked')) {
    uni.setStorageSync('mrc_onboarding_asked', '1')
    funnel('onboarding_shown', { from: 'mood' })
    showOnboarding.value = true
    return
  }
  showMealSheet.value = true
}

/** 问卷完成/跳过都放行去选餐别；完成后同步落 onboarding 标记（老逻辑不再补查） */
function onOnboardingDone(result: { completed: boolean }) {
  showOnboarding.value = false
  funnel(result.completed ? 'onboarding_completed' : 'onboarding_skipped', {})
  if (result.completed) uni.setStorageSync('mrc_preference_onboarded', '1')
  showMealSheet.value = true
}

/** 心情+餐别都齐了才跳推荐页；mood 走 query（params 不进 URL，推荐页读的是 query.mood） */
function onPickMeal(type: 'BREAKFAST' | 'LUNCH' | 'DINNER', options?: { quick?: boolean }) {
  funnel('meal_sheet_pick', { meal: type, from: 'mood', quick: options?.quick ? 1 : 0 })
  const mood = pendingMood.value || '开心'
  const quick: Record<string, string> = options?.quick ? { quick: '1' } : {}
  router.push({ name: 'recipe', params: { mood, ...quick }, query: { mood, mealType: type, ...quick } })
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

    <MealSheet v-model="showMealSheet" @select="onPickMeal" />
    <GuozaiOnboardingSheet v-model="showOnboarding" @done="onOnboardingDone" />
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
