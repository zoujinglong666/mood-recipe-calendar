<script setup lang="ts">
/**
 * 餐别选择弹层：首页「今天吃什么」卡片与心情页确认后共用。
 * 餐别必须由用户自己选（早餐/午餐/晚餐），带着 mealType 去推荐页，
 * 推荐才能联动《中国居民膳食指南》的餐别依据。
 *
 * 用法：<MealSheet v-model="show" @select="onPick" />，select 回调携带 BREAKFAST/LUNCH/DINNER。
 */
import { computed } from 'vue'
import { haptic } from '@/utils/haptic'

const props = withDefaults(defineProps<{
  /** 是否展示（v-model） */
  modelValue?: boolean
}>(), { modelValue: false })

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'select', type: 'BREAKFAST' | 'LUNCH' | 'DINNER'): void
}>()

const show = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v),
})

function close() {
  show.value = false
}

function pick(type: 'BREAKFAST' | 'LUNCH' | 'DINNER') {
  haptic('light')
  close()
  emit('select', type)
}
</script>

<template>
  <wd-popup v-model="show" position="bottom" :safe-area-inset-bottom="true" custom-style="border-radius: 40rpx 40rpx 0 0; overflow: hidden; background: var(--mrc-surface);">
    <view class="meal-sheet">
      <view class="meal-sheet__head">
        <text class="meal-sheet__title">今天吃什么</text>
        <text class="meal-sheet__sub">选一顿，锅仔按《中国居民膳食指南》帮你搭配</text>
      </view>
      <view class="meal-sheet__options">
        <button class="meal-option" @click="pick('BREAKFAST')">
          <text class="meal-option__emoji">🌅</text>
          <text class="meal-option__name">早餐</text>
          <text class="meal-option__desc">唤醒一天的元气</text>
        </button>
        <button class="meal-option" @click="pick('LUNCH')">
          <text class="meal-option__emoji">🍚</text>
          <text class="meal-option__name">午餐</text>
          <text class="meal-option__desc">给下午充好电</text>
        </button>
        <button class="meal-option" @click="pick('DINNER')">
          <text class="meal-option__emoji">🌙</text>
          <text class="meal-option__name">晚餐</text>
          <text class="meal-option__desc">好好收尾这一天</text>
        </button>
      </view>
      <button class="meal-sheet__cancel" @click="close">取消</button>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.meal-sheet { padding: 28rpx 28rpx calc(28rpx + env(safe-area-inset-bottom)); }
.meal-sheet__head { padding: 8rpx 8rpx 20rpx; }
.meal-sheet__title { display: block; color: var(--mrc-text-strong); font-size: 30rpx; font-weight: 800; }
.meal-sheet__sub { display: block; margin-top: 6rpx; color: var(--mrc-text-sub); font-size: 22rpx; }
.meal-sheet__options { display: flex; flex-direction: column; gap: 14rpx; }
.meal-option { display: flex; align-items: center; gap: 18rpx; width: 100%; min-height: 116rpx; padding: 18rpx 22rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 24rpx; background: var(--mrc-surface-2); box-shadow: var(--mrc-shadow-soft); box-sizing: border-box; text-align: left; line-height: 1.3; }
.meal-option::after { border: none; }
.meal-option__emoji { font-size: 44rpx; }
.meal-option__name { color: var(--mrc-text-deep); font-size: 30rpx; font-weight: 800; }
.meal-option__desc { margin-left: auto; color: var(--mrc-text-sub); font-size: 21rpx; }
.meal-sheet__cancel { width: 100%; margin-top: 20rpx; min-height: 92rpx; border-radius: 24rpx; color: var(--mrc-text-sub); background: var(--mrc-surface-2); font-size: 27rpx; font-weight: 700; }
.meal-sheet__cancel::after { border: none; }
</style>
