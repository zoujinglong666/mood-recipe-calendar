<template>
  <view class="app-nav" :class="{ 'app-nav--overlay': overlay }">
    <view v-if="canGoBack" class="app-nav__back" @click="goBack">
      <text class="app-nav__arrow">‹</text>
    </view>
    <view v-else class="app-nav__back" />
    <text v-if="title" class="app-nav__title">{{ title }}</text>
    <view v-else class="app-nav__title" />
    <view class="app-nav__right" @click="emit('nav-right')">
      <image v-if="!overlay && rightIcon === 'guozai'" class="app-nav__guozai" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
      <Icon v-else-if="!overlay && rightIcon === 'share'" name="share" :size="40" color="var(--mrc-text)" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Icon from './Icon.vue'

declare const getCurrentPages: () => any[]

const props = defineProps<{
  title: string
  showBack?: boolean
  /** 浮层模式：透明背景，仅用于覆盖在设计稿整图上 */
  overlay?: boolean
  /** 右侧图标：guozai（默认）/ share */
  rightIcon?: 'guozai' | 'share'
}>()

const emit = defineEmits<{ (e: 'nav-right'): void }>()

const canGoBack = computed(() => {
  try {
    return typeof getCurrentPages === 'function' && getCurrentPages().length > 1
  } catch {
    return false
  }
})

function goBack() {
  if (typeof getCurrentPages === 'function' && getCurrentPages().length > 1) {
    uni.navigateBack()
  } else {
    uni.switchTab({ url: '/pages/index/index' })
  }
}
</script>

<style lang="scss" scoped>
.app-nav {
  display: flex;
  align-items: center;
  height: 100rpx;
  padding: 0 16rpx;
  position: relative;
  box-sizing: border-box;
  background: transparent;
  z-index: 20;
}
.app-nav__back {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.app-nav__arrow {
  font-size: 48rpx;
  color: var(--mrc-text);
  line-height: 1;
}
.app-nav--overlay .app-nav__back {
  background: rgba(255, 255, 255, 0.75);
  border-radius: 50%;
  margin-top: calc(env(safe-area-inset-top));
}
.app-nav--overlay .app-nav__arrow {
  color: var(--mrc-accent);
}
.app-nav__title {
  flex: 1;
  text-align: center;
  font-size: 36rpx;
  font-weight: 600;
  color: var(--mrc-text);
  letter-spacing: 2rpx;
}
.app-nav__right {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.app-nav__guozai {
  width: 72rpx;
  height: 72rpx;
}
</style>
