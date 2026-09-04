<script setup lang="ts">
interface Props {
  text?: string
  subtext?: string
  actionText?: string
}
const props = withDefaults(defineProps<Props>(), {
  text: '这里还有点空',
  subtext: '去记录点什么吧',
  actionText: '',
})
const emit = defineEmits<{ (e: 'action'): void }>()
</script>

<template>
  <view class="gz-empty">
    <image
      class="gz-empty__img guozai-breathe"
      src="/static/guozai/state_01_empty.png"
      mode="aspectFit"
    />
    <text class="gz-empty__text">{{ text }}</text>
    <text v-if="subtext" class="gz-empty__subtext">{{ subtext }}</text>
    <view v-if="actionText" class="gz-empty__btn" @click="emit('action')">
      {{ actionText }}
    </view>
  </view>
</template>

<style lang="scss" scoped>
.gz-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80rpx 40rpx;
}
.gz-empty__img {
  width: 320rpx;
  height: 320rpx;
  margin-bottom: 16rpx;
}
.gz-empty__text {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--mrc-text-deep);
  margin-bottom: 8rpx;
}
.gz-empty__subtext {
  font-size: 26rpx;
  color: var(--mrc-text-sub);
  margin-bottom: 32rpx;
}
.gz-empty__btn {
  padding: 20rpx 48rpx;
  background: linear-gradient(135deg, var(--mrc-primary), var(--mrc-primary-deep));
  color: var(--mrc-white);
  font-size: 28rpx;
  font-weight: 600;
  border-radius: 40rpx;
  box-shadow: 0 6rpx 16rpx rgba(253, 145, 132, 0.35);
}
.gz-empty__btn:active {
  transform: scale(0.97);
}
</style>
