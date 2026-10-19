<script setup lang="ts">
import { onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'
import { onMounted, ref } from 'vue'
import { STATIC_BASE_URL } from '@/utils/assets'
import { fetchNutritionReport, type NutritionReport } from '@/api/nutritionReport'
import { ApiError } from '@/api/request'
import { toastError } from '@/utils/toast'
import { navBack } from '@/composables/useNavBar'

definePage({
  name: 'nutrition-report',
  layout: 'default',
  style: { navigationStyle: 'custom', navigationBarTitleText: '本月营养月报' },
})

const router = useRouter()
const loading = ref(true)
const report = ref<NutritionReport | null>(null)
const needMember = ref(false)

onMounted(async () => {
  try {
    report.value = await fetchNutritionReport()
  }
  catch (e: any) {
    // 会员专享走后端结构化错误码（403），不再靠错误文案字符串匹配——后端改一个字也不会误判
    if (e instanceof ApiError && e.code === 403)
      needMember.value = true
    else
      toastError(e, '月报加载失败')
  }
  finally {
    loading.value = false
  }
})

// 月报是最值得晒的内容：补齐与全站一致的分享链路，落地本页
onShareAppMessage(() => {
  const days = report.value?.days || 0
  return {
    title: days > 0 ? `这个月我认真吃了 ${days} 天饭，锅仔给我写了封信` : '锅仔 · 把认真吃饭写成信',
    path: '/pages/nutrition-report/index',
  }
})
onShareTimeline(() => ({ title: '锅仔 · 把认真吃饭写成信' }))

function goMembership() {
  router.push({ name: 'membership' })
}
</script>

<template>
  <view class="nr mrc-hero">
    <wd-navbar title="本月营养月报" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <view v-if="loading" class="nr-state"><text>锅仔正在写这个月的信…</text></view>

    <view v-else-if="needMember" class="nr-state">
      <image :src="`${STATIC_BASE_URL}/static/guozai/action_08_peek.png`" mode="aspectFit" />
      <text class="nr-state__title">本月营养月报为会员专享</text>
      <text class="nr-state__sub">开通后每月一期，锅仔把这个月的认真吃饭写成一封信</text>
      <view class="nr-state__btn" role="button" @click="goMembership">去看看会员</view>
    </view>

    <view v-else-if="!report" class="nr-state">
      <text>这个月还没有足够的记录，多记几顿再来</text>
    </view>

    <template v-else>
      <!-- 数据速览 -->
      <view class="nr-stats">
        <view class="nr-stat">
          <text class="nr-stat__value">{{ report.days }}</text>
          <text class="nr-stat__label">记录天数</text>
        </view>
        <view class="nr-stat">
          <text class="nr-stat__value">{{ report.meals }}</text>
          <text class="nr-stat__label">记下顿数</text>
        </view>
        <view class="nr-stat">
          <text class="nr-stat__value">{{ report.distinctDishes }}</text>
          <text class="nr-stat__label">换着花样</text>
        </view>
        <view class="nr-stat">
          <text class="nr-stat__value">{{ report.longestStreak }}</text>
          <text class="nr-stat__label">最长连续</text>
        </view>
      </view>

      <!-- 锅仔的信 -->
      <view class="nr-letter">
        <view class="nr-letter__stamp">
          <text>锅仔</text>
          <text>食光邮局</text>
        </view>
        <text class="nr-letter__title">{{ report.letterTitle }}</text>
        <text v-for="(p, i) in report.letterParagraphs" :key="i" class="nr-letter__para">{{ p }}</text>
        <view class="nr-letter__sign">
          <text>—— 一直记得你每顿饭的</text>
          <text class="nr-letter__sign-name">锅仔</text>
        </view>
      </view>

      <!-- 数据附录 -->
      <view v-if="report.topDishes.length" class="nr-appendix">
        <view class="nr-appendix__head">
          <text class="nr-appendix__title">这个月最常做</text>
        </view>
        <view v-for="dish in report.topDishes" :key="dish.name" class="nr-appendix__row">
          <text class="nr-appendix__name">{{ dish.name }}</text>
          <view class="nr-appendix__bar">
            <view class="nr-appendix__bar-fill" :style="{ width: `${Math.round((dish.count / report.topDishes[0].count) * 100)}%` }" />
          </view>
          <text class="nr-appendix__count">{{ dish.count }} 次</text>
        </view>
      </view>

      <view v-if="report.topMoods.length" class="nr-appendix">
        <view class="nr-appendix__head">
          <text class="nr-appendix__title">这个月的心情</text>
        </view>
        <view class="nr-moods">
          <view v-for="mood in report.topMoods" :key="mood.tag" class="nr-mood">
            <text class="nr-mood__tag">{{ mood.tag }}</text>
            <text class="nr-mood__count">{{ mood.count }} 次</text>
          </view>
        </view>
      </view>

      <view class="nr-footer-note">只统计你主动记录的内容，每月一期，下月 1 日更新</view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.nr { min-height: 100vh; padding: 0 32rpx; padding-bottom: calc(40rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.nr-state { display: flex; flex-direction: column; align-items: center; gap: 16rpx; padding: 140rpx 40rpx; color: var(--mrc-text-sub); font-size: 26rpx; text-align: center; }
.nr-state image { width: 160rpx; height: 160rpx; opacity: .8; }
.nr-state__title { color: var(--mrc-text-deep); font-size: 30rpx; font-weight: 800; }
.nr-state__sub { max-width: 480rpx; line-height: 1.5; }
.nr-state__btn { margin-top: 12rpx; padding: 18rpx 48rpx; border-radius: 999rpx; background: var(--mrc-primary-grad); color: #fff; font-size: 27rpx; font-weight: 800; box-shadow: var(--mrc-shadow-coral); }

.nr-stats { display: flex; margin: 8rpx 0 24rpx; padding: 26rpx 0; border: 2rpx solid var(--mrc-border-light); border-radius: 30rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.nr-stat { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 8rpx; }
.nr-stat__value { color: var(--mrc-text-strong); font-size: 44rpx; font-weight: 900; line-height: 1; }
.nr-stat__label { color: var(--mrc-text-sub); font-size: 20rpx; }

.nr-letter { position: relative; padding: 44rpx 36rpx 36rpx; border: 2rpx solid var(--mrc-border); border-radius: 24rpx; background: linear-gradient(160deg, #fffdf6, var(--mrc-surface-sun)); box-shadow: var(--mrc-shadow-lift), var(--mrc-gloss); }
.nr-letter__stamp { position: absolute; top: 20rpx; right: 24rpx; display: flex; flex-direction: column; align-items: center; padding: 8rpx 10rpx; border: 3rpx solid rgba(225, 90, 60, .5); border-radius: 8rpx; color: rgba(225, 90, 60, .75); font-size: 16rpx; font-weight: 800; line-height: 1.25; transform: rotate(6deg); }
.nr-letter__title { display: block; color: var(--mrc-text-strong); font-size: 36rpx; font-weight: 800; letter-spacing: 1rpx; }
.nr-letter__para { display: block; margin-top: 22rpx; color: var(--mrc-text-deep); font-size: 25rpx; line-height: 1.85; text-indent: 2em; }
.nr-letter__sign { display: flex; flex-direction: column; align-items: flex-end; gap: 4rpx; margin-top: 28rpx; color: var(--mrc-text-sub); font-size: 22rpx; }
.nr-letter__sign-name { color: var(--mrc-accent); font-size: 26rpx; font-weight: 800; }

.nr-appendix { margin-top: 24rpx; padding: 24rpx 28rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.nr-appendix__head { margin-bottom: 14rpx; }
.nr-appendix__title { color: var(--mrc-text-strong); font-size: 28rpx; font-weight: 800; }
.nr-appendix__row { display: flex; align-items: center; gap: 16rpx; margin-top: 12rpx; }
.nr-appendix__name { flex: 0 0 200rpx; overflow: hidden; color: var(--mrc-text-deep); font-size: 23rpx; text-overflow: ellipsis; white-space: nowrap; }
.nr-appendix__bar { flex: 1; height: 16rpx; border-radius: 999rpx; background: var(--mrc-surface-2); overflow: hidden; }
.nr-appendix__bar-fill { height: 100%; border-radius: 999rpx; background: var(--mrc-primary-grad); }
.nr-appendix__count { flex-shrink: 0; color: var(--mrc-text-sub); font-size: 21rpx; }
.nr-moods { display: flex; flex-wrap: wrap; gap: 14rpx; }
.nr-mood { display: flex; align-items: center; gap: 8rpx; padding: 10rpx 22rpx; border-radius: 999rpx; background: var(--mrc-accent-soft); }
.nr-mood__tag { color: var(--mrc-accent); font-size: 24rpx; font-weight: 800; }
.nr-mood__count { color: var(--mrc-text-sub); font-size: 20rpx; }

.nr-footer-note { padding: 32rpx 20rpx; color: var(--mrc-text-light); font-size: 20rpx; text-align: center; }
</style>
