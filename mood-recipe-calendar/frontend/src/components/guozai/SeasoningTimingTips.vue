<script setup lang="ts">
import { ref, watch } from 'vue'
import { fetchSeasoningTips, type SeasoningTip } from '@/api/seasoningTiming'

const props = defineProps<{
  /** 食材清单（调料用量都在里面，如「生抽 10 毫升」） */
  ingredients: string[]
  /** 做法步骤（步骤文本里也会提到调料） */
  steps: string[]
}>()

const tips = ref<SeasoningTip[]>([])
const expanded = ref(false)
let seq = 0

watch(() => [props.ingredients, props.steps] as const, ([ingredients, steps]) => {
  const ing = (ingredients || []).map(s => String(s).trim()).filter(Boolean)
  const stp = (steps || []).map(s => String(s).trim()).filter(Boolean)
  if (!ing.length && !stp.length) {
    tips.value = []
    return
  }
  const run = ++seq
  fetchSeasoningTips(ing, stp)
    .then((result) => { if (run === seq) tips.value = result })
    .catch(() => { if (run === seq) tips.value = [] })
}, { immediate: true, deep: false })
</script>

<template>
  <view v-if="tips.length" class="seasoning" role="note" aria-label="调味时机提示">
    <view class="seasoning__head" role="button" :aria-expanded="expanded" aria-label="展开或收起调味时机" @click="expanded = !expanded">
      <view class="seasoning__head-copy">
        <text class="seasoning__title">调味时机 · 什么时候放什么</text>
        <text class="seasoning__sub">{{ tips.length }} 条口诀，照着放不翻车</text>
      </view>
      <text class="seasoning__arrow" :class="{ 'is-open': expanded }">›</text>
    </view>
    <view v-if="expanded" class="seasoning__list">
      <view v-for="(tip, idx) in tips" :key="tip.name + idx" class="seasoning-row">
        <text class="seasoning-row__badge">时</text>
        <view class="seasoning-row__main">
          <view class="seasoning-row__line">
            <text class="seasoning-row__name">{{ tip.name }}</text>
            <text class="seasoning-row__timing">{{ tip.timing }}</text>
          </view>
          <text class="seasoning-row__reason">{{ tip.reason }}</text>
        </view>
      </view>
      <text class="seasoning__foot">原理很简单：盐渗透快所以后放，酒醋香靠挥发所以趁锅热，味精怕高温所以关火前放</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.seasoning {
  margin-top: 24rpx; padding: 22rpx 24rpx;
  border: 2rpx solid var(--mrc-border-light); border-radius: 24rpx;
  background: linear-gradient(160deg, var(--mrc-surface), var(--mrc-surface-sun));
  box-shadow: var(--mrc-shadow-soft);
}
.seasoning__head { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; }
.seasoning__head-copy { display: flex; flex-direction: column; gap: 4rpx; }
.seasoning__title { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 800; }
.seasoning__sub { color: var(--mrc-text-light); font-size: 21rpx; }
.seasoning__arrow { color: var(--mrc-text-light); font-size: 30rpx; transition: transform .2s ease; }
.seasoning__arrow.is-open { transform: rotate(90deg); }
.seasoning__list { display: flex; flex-direction: column; gap: 4rpx; margin-top: 8rpx; }
.seasoning-row { display: flex; align-items: flex-start; gap: 12rpx; padding: 10rpx 0; }
.seasoning-row__badge {
  flex: 0 0 auto; display: flex; align-items: center; justify-content: center;
  min-width: 36rpx; height: 36rpx; margin-top: 2rpx;
  border-radius: 10rpx; background: var(--mrc-accent-soft);
  color: var(--mrc-accent); font-size: 19rpx; font-weight: 800;
}
.seasoning-row__main { min-width: 0; display: flex; flex-direction: column; gap: 2rpx; }
.seasoning-row__line { display: flex; align-items: baseline; flex-wrap: wrap; gap: 6rpx 10rpx; }
.seasoning-row__name { color: var(--mrc-text); font-size: 23rpx; font-weight: 800; }
.seasoning-row__timing { color: var(--mrc-accent); font-size: 22rpx; font-weight: 700; }
.seasoning-row__reason { color: var(--mrc-text-sub); font-size: 21rpx; line-height: 1.5; }
.seasoning__foot { display: block; margin-top: 12rpx; color: var(--mrc-text-light); font-size: 19rpx; line-height: 1.5; }
</style>
