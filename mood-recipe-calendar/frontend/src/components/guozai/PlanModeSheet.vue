<script setup lang="ts">
/**
 * 排菜方式选择弹层：首页「锅仔管饭」单一入口的二级选择。
 * 智能体（对话式安排）与普通管饭（填口味自动排）融合为一个入口，
 * 具体用哪种由用户在这里选——两种排法并存，不替用户做决定。
 *
 * 用法：<PlanModeSheet v-model="show" @select="onPick" />，select 回调携带 agent/plan。
 */
import { computed } from 'vue'
import { STATIC_BASE_URL } from '@/utils/assets'
import { haptic } from '@/utils/haptic'

const props = withDefaults(defineProps<{
  /** 是否展示（v-model） */
  modelValue?: boolean
}>(), { modelValue: false })

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'select', mode: 'agent' | 'plan'): void
}>()

const show = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v),
})

function close() {
  show.value = false
}

function pick(mode: 'agent' | 'plan') {
  haptic('light')
  close()
  emit('select', mode)
}
</script>

<template>
  <wd-popup v-model="show" position="bottom" :safe-area-inset-bottom="true" custom-style="border-radius: 40rpx 40rpx 0 0; overflow: hidden; background: var(--mrc-surface);">
    <view class="plan-sheet">
      <view class="plan-sheet__head">
        <text class="plan-sheet__title">这一周怎么排</text>
        <text class="plan-sheet__sub">两种排法都行，挑你顺手的</text>
      </view>
      <view class="plan-sheet__options">
        <view class="plan-option plan-option--agent" role="button" aria-label="和锅仔聊着安排一整周" @click="pick('agent')">
          <image class="plan-option__img" :src="`${STATIC_BASE_URL}/static/guozai/action_06_glasses.png`" mode="aspectFit" aria-hidden="true" />
          <view class="plan-option__copy">
            <text class="plan-option__name">和锅仔聊着安排</text>
            <text class="plan-option__desc">说想法就行，锅仔对话式排好一整周</text>
          </view>
          <view class="plan-option__badge">
            智能体
          </view>
        </view>
        <view class="plan-option plan-option--plan" role="button" aria-label="填好口味自动排一周" @click="pick('plan')">
          <image class="plan-option__img" :src="`${STATIC_BASE_URL}/static/guozai/action_02_soup.png`" mode="aspectFit" aria-hidden="true" />
          <view class="plan-option__copy">
            <text class="plan-option__name">填好口味自动排</text>
            <text class="plan-option__desc">选好偏好和预算，一键生成一周菜单</text>
          </view>
          <view class="plan-option__badge plan-option__badge--plain">
            普通管饭
          </view>
        </view>
      </view>
      <view class="plan-sheet__cancel" role="button" aria-label="取消" @click="close">
        取消
      </view>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.plan-sheet { padding: 28rpx 28rpx calc(28rpx + env(safe-area-inset-bottom)); }
.plan-sheet__head { padding: 8rpx 8rpx 20rpx; }
.plan-sheet__title { display: block; color: var(--mrc-text-strong); font-size: 30rpx; font-weight: 800; }
.plan-sheet__sub { display: block; margin-top: 6rpx; color: var(--mrc-text-sub); font-size: 22rpx; }
.plan-sheet__options { display: flex; flex-direction: column; gap: 14rpx; }
.plan-option { display: flex; align-items: center; gap: 18rpx; width: 100%; min-height: 132rpx; padding: 18rpx 22rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 24rpx; box-shadow: var(--mrc-shadow-soft); box-sizing: border-box; }
.plan-option:active { transform: scale(.98); }
.plan-option--agent { background: linear-gradient(135deg, var(--mrc-surface-sun), var(--mrc-surface-peach)); }
.plan-option--plan { background: linear-gradient(135deg, var(--mrc-surface-mint), var(--mrc-surface)); }
.plan-option__img { width: 88rpx; height: 88rpx; flex: 0 0 auto; }
.plan-option__copy { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 6rpx; }
.plan-option__name { color: var(--mrc-text-deep); font-size: 30rpx; font-weight: 850; }
.plan-option__desc { color: var(--mrc-text-sub); font-size: 21rpx; line-height: 1.4; }
.plan-option__badge { flex: 0 0 auto; padding: 6rpx 16rpx; border-radius: 999rpx; color: #fff; background: var(--mrc-primary-grad); font-size: 19rpx; font-weight: 800; }
.plan-option__badge--plain { color: var(--mrc-accent); background: var(--mrc-accent-soft); }
.plan-sheet__cancel { width: 100%; margin-top: 20rpx; min-height: 92rpx; border-radius: 24rpx; color: var(--mrc-text-sub); background: var(--mrc-surface-2); font-size: 27rpx; font-weight: 700; text-align: center; line-height: 92rpx; }
.plan-sheet__cancel:active { transform: scale(.98); }
</style>
