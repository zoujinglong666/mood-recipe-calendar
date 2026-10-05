<script setup lang="ts">
import { computed, ref } from 'vue'
import { fetchRecommendationHistory, type RecommendationHistoryItem } from '@/api/recipes'
import { ensureLogin } from '@/utils/login'
import { toastError } from '@/utils/toast'
import { STATIC_BASE_URL } from '@/utils/assets'
import { saveCookingDraft } from '@/utils/cookingDraft'

definePage({ name: 'recommendation-history', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '推荐记录' } })

import { useShare } from '@/composables/useShare'

// 让微信胶囊「···」可转发 / 分享到朋友圈
useShare()

const router = useRouter()
const loading = ref(true)
const items = ref<RecommendationHistoryItem[]>([])
const grouped = computed(() => items.value.reduce<Record<string, RecommendationHistoryItem[]>>((result, item) => {
  const key = item.recommendedAt?.slice(0, 10) || '更早以前'
  ;(result[key] ||= []).push(item)
  return result
}, {}))

function dateText(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '刚刚推荐'
}

function open(item: RecommendationHistoryItem) {
  router.push({ name: 'recipe', params: { recipeId: String(item.id) } })
}

function cook(item: RecommendationHistoryItem) {
  saveCookingDraft({ ...item, description: item.description || '', cookingTime: item.cookingTime || 30, difficulty: item.difficulty || '简单', source: item.source === 'AI' ? 'AI' : 'LOCAL', moodTags: '', season: '' }, '满足')
  router.push({ name: 'cooking' })
}

async function load() {
  loading.value = true
  try {
    await ensureLogin()
    items.value = await fetchRecommendationHistory()
  }
  catch (error) {
    toastError(error, '推荐记录加载失败，请重试')
  }
  finally { loading.value = false }
}

onShow(load)
</script>

<template>
  <view class="history-page">
    <wd-navbar title="推荐记录" left-arrow safe-area-inset-top @click-left="router.back" custom-style="background-color: transparent !important;" />
    <view v-if="loading" class="state">锅仔正在翻找以前推荐过的菜…</view>
    <view v-else-if="!items.length" class="state state--empty">
      <image :src="`${STATIC_BASE_URL}/static/guozai/action_08_peek.png`" mode="aspectFit" />
      <text>还没有推荐记录</text>
      <text class="state__sub">让锅仔替你决定一道菜，就会收进这里。</text>
    </view>
    <scroll-view v-else scroll-y class="history-scroll">
      <view v-for="(dayItems, day) in grouped" :key="day" class="day-group">
        <text class="day-label">{{ day }}</text>
        <view v-for="item in dayItems" :key="`${item.id}-${item.recommendedAt}`" class="history-card">
          <image class="history-card__image" :src="item.image || `${STATIC_BASE_URL}/static/guozai/action_01_bowl.png`" mode="aspectFill" />
          <view class="history-card__body" role="button" :aria-label="`查看${item.name}`" @click="open(item)">
            <text class="history-card__source">{{ item.source === 'AI' ? '锅仔智能推荐' : '菜谱库推荐' }}</text>
            <text class="history-card__name">{{ item.name }}</text>
            <text class="history-card__meta">{{ dateText(item.recommendedAt) }} · {{ item.cookingTime || 30 }} 分钟</text>
          </view>
          <view class="history-card__cook" role="button" aria-label="直接去做这道菜" @click.stop="cook(item)">去做</view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<style lang="scss" scoped>
.history-page { min-height: 100vh; padding: 0 28rpx calc(40rpx + env(safe-area-inset-bottom)); box-sizing: border-box; background: var(--mrc-bg); }
.history-scroll { height: calc(100vh - 120rpx); }
.day-group { padding: 22rpx 0 0; }
.day-label { display: block; margin: 0 6rpx 12rpx; color: var(--mrc-text-sub); font-size: 21rpx; font-weight: 800; }
.history-card { display: flex; align-items: center; gap: 18rpx; margin-bottom: 14rpx; padding: 16rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 26rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.history-card__image { width: 132rpx; height: 132rpx; flex: 0 0 auto; border-radius: 20rpx; background: var(--mrc-surface-peach); }
.history-card__body { display: flex; min-width: 0; flex: 1; flex-direction: column; }
.history-card__source { color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; }
.history-card__name { margin-top: 8rpx; overflow: hidden; color: var(--mrc-text-deep); font-size: 29rpx; font-weight: 850; text-overflow: ellipsis; white-space: nowrap; }
.history-card__meta { margin-top: 8rpx; color: var(--mrc-text-sub); font-size: 20rpx; }
.history-card__cook { min-width: 74rpx; padding: 14rpx 12rpx; border-radius: 999rpx; color: var(--mrc-accent); background: var(--mrc-accent-soft); font-size: 21rpx; font-weight: 800; text-align: center; }
.state { display: flex; min-height: 520rpx; align-items: center; justify-content: center; color: var(--mrc-text-sub); font-size: 26rpx; text-align: center; }
.state--empty { flex-direction: column; gap: 14rpx; }.state--empty image { width: 190rpx; height: 190rpx; }.state__sub { font-size: 22rpx; }
</style>
