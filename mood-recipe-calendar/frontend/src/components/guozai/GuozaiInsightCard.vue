<script setup lang="ts">
import { STATIC_BASE_URL } from '@/utils/assets'

export interface GuozaiInsightItem {
  type: string
  text: string
  memoryKey?: string | null
}

const props = withDefaults(defineProps<{
  title?: string
  items?: GuozaiInsightItem[]
  variant?: 'learned' | 'recommendation' | 'saved'
}>(), {
  title: '锅仔记住了',
  items: () => [],
  variant: 'learned',
})

const image = computed(() => props.variant === 'recommendation'
  ? `${STATIC_BASE_URL}/static/guozai/action_10_thinking.png`
  : `${STATIC_BASE_URL}/static/guozai/action_09_celebrate.png`)
</script>

<template>
  <view class="guozai-insight" :class="`guozai-insight--${variant}`" aria-live="polite">
    <image class="guozai-insight__image" :src="image" mode="aspectFit" aria-label="锅仔" />
    <view class="guozai-insight__body">
      <text class="guozai-insight__eyebrow">
        {{ variant === 'recommendation' ? '这次真的用上了' : '这一餐之后' }}
      </text>
      <text class="guozai-insight__title">
        {{ title }}
      </text>
      <view v-if="items.length" class="guozai-insight__list">
        <view v-for="item in items" :key="`${item.type}-${item.memoryKey || item.text}`" class="guozai-insight__item">
          <text class="guozai-insight__dot">
            •
          </text><text>{{ item.text }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.guozai-insight { display: flex; align-items: flex-start; gap: 18rpx; width: 100%; padding: var(--mrc-space-md); box-sizing: border-box; border: 2rpx solid var(--mrc-border-light); border-radius: var(--mrc-radius-lg); background: var(--mrc-surface-mint); text-align: left; }
.guozai-insight--recommendation { background: var(--mrc-surface-sun); }
.guozai-insight--saved { background: var(--mrc-surface-2); }
.guozai-insight__image { width: 92rpx; height: 92rpx; flex-shrink: 0; }
.guozai-insight__body { flex: 1; min-width: 0; }
.guozai-insight__eyebrow, .guozai-insight__title { display: block; }
.guozai-insight__eyebrow { color: var(--mrc-accent); font-size: var(--mrc-fs-xs); font-weight: var(--mrc-fw-heavy); letter-spacing: 2rpx; }
.guozai-insight__title { margin-top: 6rpx; color: var(--mrc-text-strong); font-size: var(--mrc-fs-body); font-weight: var(--mrc-fw-heavy); line-height: 1.4; }
.guozai-insight__list { margin-top: 10rpx; }
.guozai-insight__item { display: flex; gap: 8rpx; color: var(--mrc-text); font-size: var(--mrc-fs-sub); line-height: 1.55; }
.guozai-insight__item + .guozai-insight__item { margin-top: 6rpx; }
.guozai-insight__dot { color: var(--mrc-accent); font-weight: var(--mrc-fw-heavy); }
</style>
