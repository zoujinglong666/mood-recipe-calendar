<script setup lang="ts">
interface Props {
  text?: string
}
withDefaults(defineProps<Props>(), {
  text: '锅仔正在准备...',
})
</script>

<template>
  <view class="gz-loading">
    <view class="gz-loading__wrap">
      <image
        class="gz-loading__img"
        src="/static/guozai/state_02_loading.png"
        mode="aspectFit"
      />
      <!-- 旋转的加载环（覆盖在图片的加载环位置） -->
      <view class="gz-loading__ring" />
    </view>
    <text class="gz-loading__text">{{ text }}</text>
  </view>
</template>

<style lang="scss" scoped>
.gz-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80rpx 0;
}
.gz-loading__wrap {
  position: relative;
  width: 280rpx;
  height: 280rpx;
}
.gz-loading__img {
  width: 280rpx;
  height: 280rpx;
  position: relative;
  z-index: 2;
}
.gz-loading__ring {
  position: absolute;
  bottom: 20rpx;
  left: 50%;
  transform: translateX(-50%);
  width: 200rpx;
  height: 200rpx;
  border: 4rpx solid var(--mrc-border);
  border-top-color: var(--mrc-accent);
  border-radius: 50%;
  animation: gz-spin 1.2s linear infinite;
  z-index: 1;
  opacity: 0.6;
}
@keyframes gz-spin {
  from { transform: translateX(-50%) rotate(0deg); }
  to { transform: translateX(-50%) rotate(360deg); }
}
.gz-loading__text {
  margin-top: 16rpx;
  font-size: 28rpx;
  color: var(--mrc-text-sub);
}
@media (prefers-reduced-motion: reduce) {
  .gz-loading__ring {
    animation: none;
  }
}
</style>
