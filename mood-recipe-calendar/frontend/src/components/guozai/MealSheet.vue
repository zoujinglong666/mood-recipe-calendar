<script setup lang="ts">
/**
 * 餐别选择弹层：首页「今天吃什么」卡片与心情页确认后共用。
 * 餐别必须由用户自己选（早餐/午餐/晚餐），带着 mealType 去推荐页，
 * 推荐才能联动《中国居民膳食指南》的餐别依据。
 *
 * [2026-10-26] 时间预算一级输入（主循环 P1）：可勾选「今晚想快一点」，
 * 推荐将按 20 分钟内能做好的菜打分（select 回调携带 quick 标记）。
 *
 * 用法：<MealSheet v-model="show" @select="onPick" />，select 回调携带 BREAKFAST/LUNCH/DINNER 与 quick。
 */
import { computed, ref } from 'vue'
import { haptic } from '@/utils/haptic'

const props = withDefaults(defineProps<{
  /** 是否展示（v-model） */
  modelValue?: boolean
  /** 所在页面带原生 tabBar：弹层底部要额外让出 tabBar 高度，否则取消按钮会被 tabBar 盖住 */
  withTabbar?: boolean
}>(), { modelValue: false, withTabbar: false })

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'select', type: 'BREAKFAST' | 'LUNCH' | 'DINNER', options: { quick: boolean }): void
}>()

const show = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v),
})

/** 快手模式：本次推荐优先 20 分钟内能搞定的菜（确认后重置，下次打开回到默认） */
const quick = ref(false)

function toggleQuick() {
  haptic('light')
  quick.value = !quick.value
}

function close() {
  show.value = false
}

function pick(type: 'BREAKFAST' | 'LUNCH' | 'DINNER') {
  haptic('light')
  const options = { quick: quick.value }
  quick.value = false
  close()
  emit('select', type, options)
}
</script>

<template>
  <wd-popup v-model="show" position="bottom" :safe-area-inset-bottom="false" custom-style="border-radius: 40rpx 40rpx 0 0; overflow: hidden; background: var(--mrc-surface);">
    <view class="meal-sheet" :style="{ '--meal-sheet-tabbar': withTabbar ? '120rpx' : '0rpx' }">
      <view class="meal-sheet__head">
        <view class="meal-sheet__head-text">
          <text class="meal-sheet__title">今天吃什么</text>
          <text class="meal-sheet__sub">选一顿，锅仔按《中国居民膳食指南》帮你搭配</text>
        </view>
        <view class="meal-sheet__close" role="button" aria-label="关闭" @click="close">✕</view>
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
        <button class="meal-option meal-option--quick" :class="{ 'is-on': quick }" role="switch" :aria-checked="quick" aria-label="今晚想快一点，二十分钟内开饭" @click="toggleQuick">
          <text class="meal-option__emoji">⚡</text>
          <view class="meal-option__copy">
            <text class="meal-option__name">今晚想快一点</text>
            <text class="meal-option__desc">二十分钟就能开饭</text>
          </view>
          <view class="meal-option__switch" :class="{ 'is-on': quick }">
            <view class="meal-option__switch-dot" />
          </view>
        </button>
      </view>
      <button class="meal-sheet__cancel" @click="close">取消</button>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.meal-sheet { padding: 28rpx 28rpx calc(28rpx + env(safe-area-inset-bottom) + var(--meal-sheet-tabbar, 0rpx)); }
.meal-sheet__head { display: flex; align-items: flex-start; gap: 16rpx; padding: 8rpx 8rpx 20rpx; }
.meal-sheet__head-text { flex: 1; min-width: 0; }
.meal-sheet__close { flex-shrink: 0; width: 56rpx; height: 56rpx; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: var(--mrc-surface-2); color: var(--mrc-text-sub); font-size: 26rpx; font-weight: 700; }
.meal-sheet__title { display: block; color: var(--mrc-text-strong); font-size: 30rpx; font-weight: 800; }
.meal-sheet__sub { display: block; margin-top: 6rpx; color: var(--mrc-text-sub); font-size: 22rpx; }
.meal-sheet__options { display: flex; flex-direction: column; gap: 14rpx; }
.meal-option { display: flex; align-items: center; gap: 18rpx; width: 100%; min-height: 116rpx; padding: 18rpx 22rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 24rpx; background: var(--mrc-surface-2); box-shadow: var(--mrc-shadow-soft); box-sizing: border-box; text-align: left; line-height: 1.3; }
.meal-option::after { border: none; }
.meal-option__emoji { font-size: 44rpx; }
.meal-option__name { color: var(--mrc-text-deep); font-size: 30rpx; font-weight: 800; }
.meal-option__desc { margin-left: auto; color: var(--mrc-text-sub); font-size: 21rpx; }
/* 快手模式开关行：文案左对齐成列，右侧自定义 switch（选中态走品牌珊瑚 token） */
.meal-option--quick { background: var(--mrc-surface); }
.meal-option--quick.is-on { border-color: var(--mrc-primary-deep); background: var(--mrc-accent-soft); }
.meal-option__copy { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 4rpx; }
.meal-option__copy .meal-option__desc { margin-left: 0; }
.meal-option--quick .meal-option__name { font-size: 27rpx; }
.meal-option__switch { flex-shrink: 0; width: 84rpx; height: 48rpx; padding: 4rpx; border-radius: 999rpx; background: var(--mrc-border); box-sizing: border-box; transition: background var(--mrc-motion-fast); }
.meal-option__switch.is-on { background: var(--mrc-primary-deep); }
.meal-option__switch-dot { width: 40rpx; height: 40rpx; border-radius: 50%; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-sm); transition: transform var(--mrc-motion-fast); }
.meal-option__switch.is-on .meal-option__switch-dot { transform: translateX(36rpx); }
.meal-sheet__cancel { width: 100%; margin-top: 20rpx; min-height: 92rpx; border-radius: 24rpx; color: var(--mrc-text-sub); background: var(--mrc-surface-2); font-size: 27rpx; font-weight: 700; }
.meal-sheet__cancel::after { border: none; }
</style>
