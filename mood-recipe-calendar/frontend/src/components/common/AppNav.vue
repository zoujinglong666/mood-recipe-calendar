<template>
  <view
    class="app-nav"
    :class="{
      'app-nav--overlay': overlay,
      'app-nav--sticky': sticky,
      'app-nav--bordered': bordered,
      'app-nav--hide-back': !canGoBack && !showBack,
    }"
    :style="[navStyle, bgStyle]"
  >
    <!-- 左侧：返回按钮 / 自定义插槽 -->
    <view class="app-nav__side app-nav__side--left">
      <slot name="left">
        <view v-if="canGoBack || showBack" class="app-nav__back" @click="goBack">
          <text class="app-nav__arrow">‹</text>
        </view>
      </slot>
    </view>

    <!-- 标题：绝对居中于整屏，避开左右插槽与右侧胶囊 -->
    <view class="app-nav__title">
      <slot name="title">
        <text v-if="title" class="app-nav__title-text">{{ title }}</text>
      </slot>
    </view>

    <!-- 右侧：默认锅仔 / 分享，right 动态避让胶囊，可插槽覆盖 -->
    <view class="app-nav__side app-nav__side--right" :style="{ right: rightOffset }" @click="emit('nav-right')">
      <slot name="right">
        <image
          v-if="!overlay && rightIconResolved === 'guozai'"
          class="app-nav__guozai"
          src="/static/guozai/mood_01_happy.png"
          mode="aspectFit"
        />
        <Icon v-else-if="!overlay && rightIconResolved === 'share'" name="share" :size="40" color="var(--mrc-text)" />
        <view v-else class="app-nav__side--empty" />
      </slot>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import Icon from './Icon.vue'
import { useNavBar, refreshNavMetrics } from '@/composables/useNavBar'

declare const getCurrentPages: () => any[]

const props = defineProps<{
  title?: string
  /** 是否显示返回按钮；默认根据页面栈自动判断（>1 层时显示） */
  showBack?: boolean
  /** 浮层模式：透明背景，仅用于覆盖在设计稿整图上 */
  overlay?: boolean
  /** 是否固定到页面顶部（滚动时保持） */
  sticky?: boolean
  /** 背景色（css 色值），不传则透明；overlay 模式下忽略 */
  bg?: string
  /** 是否显示底部细线 */
  bordered?: boolean
  /** 右侧图标：guozai / share */
  rightIcon?: 'guozai' | 'share'
}>()

const emit = defineEmits<{ (e: 'nav-right'): void }>()

// 右侧图标默认锅仔
const rightIconResolved = computed(() => props.rightIcon ?? 'guozai')

// 状态栏高度 + 右上角胶囊适配：顶部预留状态栏
const nav = useNavBar()
const navStyle = computed(() => ({
  paddingTop: `${nav.statusBarHeight}px`,
  height: `${nav.navBarHeight}px`,
}))
// 右侧内容动态避开胶囊：距屏右缘 = 胶囊右侧空隙 + 8px
const rightOffset = computed(() => `${nav.capsuleRightGap + 8}px`)
const bgStyle = computed(() => {
  if (props.overlay) return {}
  if (props.bg) return { background: props.bg }
  return {}
})

onMounted(refreshNavMetrics)

const canGoBack = computed(() => {
  if (props.showBack === true) return true
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
  position: relative;
  box-sizing: border-box;
  z-index: 20;
}
.app-nav--sticky {
  position: sticky;
  top: 0;
}
.app-nav--bordered {
  box-shadow: 0 2rpx 0 0 var(--mrc-border-light);
}

/* 左右两侧（固定宽度，避免挤压标题） */
.app-nav__side {
  position: absolute;
  top: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  z-index: 2;
}
.app-nav__side--left {
  left: 16rpx;
}
.app-nav__side--right {
  right: 8rpx;
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
}
.app-nav--overlay .app-nav__arrow {
  color: var(--mrc-accent);
}

/* 标题绝对居中于整屏，不受左右插槽 / 胶囊影响 */
.app-nav__title {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  top: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  max-width: 60%;
  z-index: 1;
}
.app-nav__title-text {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--mrc-text);
  letter-spacing: 2rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 右侧 */
.app-nav__guozai {
  width: 72rpx;
  height: 72rpx;
}
.app-nav__side--empty {
  width: 72rpx;
  height: 72rpx;
}
</style>
