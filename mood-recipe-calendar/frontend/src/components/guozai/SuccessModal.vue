<script setup lang="ts">
import { STATIC_BASE_URL } from '@/utils/assets'

interface Props {
  visible: boolean
  title?: string
  subtitle?: string
  confirmText?: string
  secondaryText?: string
}
withDefaults(defineProps<Props>(), {
  title: '记录成功！',
  subtitle: '今天也好好吃饭了呢',
  confirmText: '好的',
  secondaryText: '',
})
const emit = defineEmits<{ confirm: [], secondary: [] }>()

function handleConfirm() {
  emit('confirm')
}

function handleSecondary() {
  emit('secondary')
}
</script>

<template>
  <view v-if="visible" class="gz-modal-mask" @click="handleConfirm">
    <view class="gz-modal pop-in" @click.stop>
      <!-- 庆祝锅仔 -->
      <view class="gz-modal__guozai-wrap">
        <image
          class="gz-modal__guozai guozai-spin"
          :src="`${STATIC_BASE_URL}/static/guozai/action_09_celebrate.png`"
          mode="aspectFit"
        />
        <!-- 星星装饰 -->
        <text class="gz-modal__star gz-modal__star--1 star-float">
          ✨
        </text>
        <text class="gz-modal__star gz-modal__star--2 star-float">
          ⭐
        </text>
        <text class="gz-modal__star gz-modal__star--3 star-float">
          ✨
        </text>
      </view>
      <text class="gz-modal__title">
        {{ title }}
      </text>
      <text class="gz-modal__subtitle">
        {{ subtitle }}
      </text>
      <view v-if="$slots.default" class="gz-modal__content">
        <slot />
      </view>
      <view class="gz-modal__actions" :class="{ 'gz-modal__actions--double': secondaryText }">
        <view v-if="secondaryText" class="mrc-btn-outline gz-modal__btn" role="button" :aria-label="secondaryText" @click="handleSecondary">
          {{ secondaryText }}
        </view>
        <view class="mrc-btn-primary gz-modal__btn" role="button" :aria-label="confirmText" @click="handleConfirm">
          {{ confirmText }}
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.gz-modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(90, 62, 43, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}
.gz-modal {
  width: 560rpx;
  background: var(--mrc-white);
  border-radius: 40rpx;
  padding: 48rpx 40rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 20rpx 60rpx rgba(90, 62, 43, 0.2);
}
.gz-modal__guozai-wrap {
  position: relative;
  width: 240rpx;
  height: 240rpx;
  margin-bottom: 24rpx;
}
.gz-modal__guozai {
  width: 240rpx;
  height: 240rpx;
}
.gz-modal__star {
  position: absolute;
  font-size: 36rpx;
}
.gz-modal__star--1 {
  top: 20rpx;
  left: -10rpx;
  animation-delay: 0s;
}
.gz-modal__star--2 {
  top: 0;
  right: 0;
  animation-delay: 0.3s;
}
.gz-modal__star--3 {
  bottom: 40rpx;
  right: -20rpx;
  animation-delay: 0.6s;
}
.gz-modal__title {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
  margin-bottom: 12rpx;
}
.gz-modal__subtitle {
  font-size: 28rpx;
  color: var(--mrc-text-sub);
  margin-bottom: 28rpx;
}
.gz-modal__content { width: 100%; margin-bottom: 28rpx; }
.gz-modal__actions { width: 100%; display: flex; gap: var(--mrc-space-sm); }
.gz-modal__actions--double .gz-modal__btn { flex: 1; min-width: 0; }
.gz-modal__btn {
  height: 96rpx;
  font-size: var(--mrc-fs-body);
  letter-spacing: 0;
}
</style>
