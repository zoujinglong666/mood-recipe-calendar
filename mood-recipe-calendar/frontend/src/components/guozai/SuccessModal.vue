<script setup lang="ts">
import { ref, watch } from 'vue'
import { STATIC_BASE_URL } from '@/utils/assets'

interface Props {
  visible: boolean
  title?: string
  subtitle?: string
  confirmText?: string
  secondaryText?: string
}
const props = withDefaults(defineProps<Props>(), {
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

/* ===== 锅仔图加载诊断（临时）=====
 * 图不展示有三类完全不同的原因，控制台只报一个失败看不出是哪类：
 *   1) src 本身为空/拼错 → URL 不对，请求根本没发出去
 *   2) 请求发出但失败    → 网络/域名/防盗链，有 errMsg
 *   3) 请求 200 但看不见 → 尺寸为 0、被遮挡、opacity 为 0（不是加载问题）
 * 所以三条都打：URL、加载结果、加载后的真实布局尺寸。
 */
const GZ_CELEBRATE_URL = `${STATIC_BASE_URL}/static/guozai/action_09_celebrate.png`

/** 屏幕上直接显示状态，免得来回翻控制台 */
const imgStatus = ref('待加载')
const imgDetail = ref('')

function onGuozaiLoad(e: any) {
  const detail = e?.detail || {}
  imgStatus.value = '已加载'
  imgDetail.value = `${detail.width || '?'}×${detail.height || '?'}`
  console.log('[SuccessModal][锅仔图] 加载成功', {
    url: GZ_CELEBRATE_URL,
    natural: `${detail.width}×${detail.height}`,
    detail,
  })
  // 再查一次真实布局尺寸：natural 有值而这里为 0，就说明是 CSS/遮挡问题，不是加载问题
  uni.createSelectorQuery()
    .select('.gz-modal__guozai')
    .boundingClientRect((rect: any) => {
      console.log('[SuccessModal][锅仔图] 渲染尺寸', rect)
      if (!rect || !rect.width)
        imgDetail.value += ' · 渲染尺寸为 0（CSS/遮挡问题）'
    })
    .exec()
}

function onGuozaiError(e: any) {
  const errMsg = e?.detail?.errMsg || e?.detail?.errno || JSON.stringify(e?.detail || {})
  imgStatus.value = '加载失败'
  imgDetail.value = String(errMsg)
  console.error('[SuccessModal][锅仔图] 加载失败', {
    url: GZ_CELEBRATE_URL,
    errMsg: e?.detail?.errMsg,
    detail: e?.detail,
    event: e,
  })
}

// 弹窗打开时先记一次最终 URL：URL 拼错的话，请求压根不会发出，也就不会有 error 回调
watch(() => props.visible, (v) => {
  if (!v)
    return
  imgStatus.value = '待加载'
  imgDetail.value = ''
  console.log('[SuccessModal][锅仔图] 弹窗打开，即将请求:', GZ_CELEBRATE_URL)
})
</script>

<template>
  <view v-if="visible" class="gz-modal-mask" @click="handleConfirm">
    <view class="gz-modal pop-in" @click.stop>
      <!-- 庆祝锅仔 -->
      <view class="gz-modal__guozai-wrap">
        <image
          class="gz-modal__guozai guozai-spin"
          :src="GZ_CELEBRATE_URL"
          mode="aspectFit"
          @load="onGuozaiLoad"
          @error="onGuozaiError"
        />
        <!-- 临时诊断：加载失败时把 URL 和错误直接摊在屏幕上 -->
        <text v-if="imgStatus === '加载失败'" class="gz-modal__img-debug">
          {{ imgStatus }}：{{ imgDetail }}
        </text>
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
/* 临时诊断样式：加载失败时把原因直接显示出来，定位完可整块删除 */
.gz-modal__img-debug {
  position: absolute;
  right: -20rpx;
  bottom: -8rpx;
  left: -20rpx;
  padding: 8rpx 12rpx;
  border-radius: 12rpx;
  background: rgba(196, 71, 50, .9);
  color: #FFF6EA;
  font-size: 16rpx;
  line-height: 1.35;
  word-break: break-all;
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
.gz-modal__btn {
  flex: 1 1 0;
  min-width: 0;
  height: 96rpx;
  font-size: var(--mrc-fs-body);
  letter-spacing: 0;
}
</style>
