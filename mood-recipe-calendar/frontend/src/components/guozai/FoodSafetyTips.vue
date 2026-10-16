<script setup lang="ts">
import { ref, watch } from 'vue'
import { fetchFoodTips, type FoodTip } from '@/api/foodSafety'

const props = defineProps<{
  /** 食材名列表（菜谱 ingredients 解析后的数组） */
  ingredients: string[]
}>()

const tips = ref<FoodTip[]>([])
const loading = ref(false)
let seq = 0

const TYPE_META: Record<string, { badge: string, cls: string }> = {
  COOK: { badge: '烹', cls: 'tip--cook' },
  AVOID: { badge: '慎', cls: 'tip--avoid' },
  COMBO: { badge: '搭', cls: 'tip--combo' },
  PICK: { badge: '挑', cls: 'tip--pick' },
}

watch(() => props.ingredients, (list) => {
  const names = (list || []).map(s => String(s).trim()).filter(Boolean)
  if (!names.length) {
    tips.value = []
    return
  }
  const run = ++seq
  loading.value = true
  fetchFoodTips(names)
    .then((result) => {
      if (run !== seq) return
      // 本组件只展示烹调警示/慎食人群/搭配风险；挑选技巧在采购清单展示
      tips.value = result.filter(t => t.type !== 'PICK')
    })
    .catch(() => { if (run === seq) tips.value = [] })
    .finally(() => { if (run === seq) loading.value = false })
}, { immediate: true, deep: false })
</script>

<template>
  <view v-if="tips.length" class="safety-tips" role="note" aria-label="食品安全提示">
    <view class="safety-tips__head">
      <text class="safety-tips__title">食品安全提示</text>
      <text class="safety-tips__sub">照着做更安心</text>
    </view>
    <view v-for="(tip, idx) in tips" :key="tip.type + tip.name + idx" class="safety-tip" :class="TYPE_META[tip.type]?.cls">
      <text class="safety-tip__badge">{{ TYPE_META[tip.type]?.badge || '提' }}</text>
      <view class="safety-tip__main">
        <text class="safety-tip__name">{{ tip.name }}</text>
        <text class="safety-tip__text">{{ tip.text }}</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.safety-tips { margin-top: 24rpx; padding: 22rpx 24rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 24rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.safety-tips__head { display: flex; align-items: baseline; gap: 12rpx; margin-bottom: 14rpx; }
.safety-tips__title { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 800; }
.safety-tips__sub { color: var(--mrc-text-light); font-size: 21rpx; }
.safety-tip { display: flex; align-items: flex-start; gap: 12rpx; padding: 10rpx 0; }
.safety-tip__badge { flex: 0 0 auto; display: flex; align-items: center; justify-content: center; min-width: 36rpx; height: 36rpx; margin-top: 2rpx; border-radius: 10rpx; font-size: 19rpx; font-weight: 800; }
.tip--cook .safety-tip__badge { background: var(--mrc-surface-sun); color: var(--mrc-accent); }
.tip--avoid .safety-tip__badge { background: var(--mrc-color-danger-soft); color: var(--mrc-color-danger); }
.tip--combo .safety-tip__badge { background: var(--mrc-color-danger-soft); color: var(--mrc-color-danger); }
.tip--pick .safety-tip__badge { background: var(--mrc-accent-soft); color: var(--mrc-accent); }
.safety-tip__main { min-width: 0; display: flex; flex-direction: column; gap: 2rpx; }
.safety-tip__name { color: var(--mrc-text); font-size: 23rpx; font-weight: 700; }
.safety-tip__text { color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.55; }
</style>
