<script setup lang="ts">
interface Props {
  text?: string
  subtext?: string
  actionText?: string
}
const props = withDefaults(defineProps<Props>(), {
  text: '网络开小差了',
  subtext: '检查一下网络再试试吧',
  actionText: '重新加载',
})
const emit = defineEmits<{ (e: 'retry'): void }>()
</script>

<template>
  <view class="gz-error">
    <image
      class="gz-error__img guozai-breathe"
      src="/static/guozai/state_03_error.png"
      mode="aspectFit"
    />
    <text class="gz-error__text">{{ text }}</text>
    <text v-if="subtext" class="gz-error__subtext">{{ subtext }}</text>
    <view v-if="actionText" class="gz-error__btn" @click="emit('retry')">
      {{ actionText }}
    </view>
  </view>
</template>

<style lang="scss" scoped>
.gz-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80rpx 40rpx;
}
.gz-error__img {
  width: 300rpx;
  height: 300rpx;
  margin-bottom: 16rpx;
}
.gz-error__text {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--mrc-text-deep);
  margin-bottom: 8rpx;
}
.gz-error__subtext {
  font-size: 26rpx;
  color: var(--mrc-text-sub);
  margin-bottom: 32rpx;
}
.gz-error__btn {
  padding: 20rpx 48rpx;
  background: linear-gradient(135deg, var(--mrc-primary), var(--mrc-primary-deep));
  color: var(--mrc-white);
  font-size: 28rpx;
  font-weight: 600;
  border-radius: 40rpx;
  box-shadow: 0 6rpx 16rpx rgba(253, 145, 132, 0.35);
}
.gz-error__btn:active {
  transform: scale(0.97);
}
</style>
