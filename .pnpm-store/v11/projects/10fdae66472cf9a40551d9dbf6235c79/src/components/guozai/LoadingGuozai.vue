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
    <image
      class="gz-loading__img guozai-breathe"
      src="/static/guozai/action_08_peek.png"
      mode="aspectFit"
    />
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
.gz-loading__img {
  width: 200rpx;
  height: 200rpx;
}
.gz-loading__text {
  margin-top: 24rpx;
  font-size: 28rpx;
  color: var(--mrc-text-sub);
}
</style>
