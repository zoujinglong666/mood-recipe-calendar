<script setup lang="ts">
/**
 * 设计稿整页展示组件：把已验收的高保真设计稿 PNG 作为页面视觉主体
 * 全宽显示，保持原始比例（widthFix），支持多张竖向连排（画册/年报）。
 */
defineProps<{
  imgs: string[]
  /** 圆角容器（可选，画册感） */
  rounded?: boolean
}>()
</script>

<template>
  <view class="dsheet" :class="{ 'dsheet--rounded': rounded }">
    <image
      v-for="(src, i) in imgs"
      :key="i"
      class="dsheet__img"
      :src="src"
      mode="widthFix"
      :style="{ borderRadius: rounded ? '0 0 32rpx 32rpx' : '0' }"
    />
  </view>
</template>

<style lang="scss" scoped>
.dsheet {
  width: 100%;
}
.dsheet__img {
  display: block;
  width: 100%;
}
</style>
