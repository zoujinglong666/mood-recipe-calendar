<script setup lang="ts">
import { ref } from 'vue'
const props = withDefaults(defineProps<{
  /** primary=主渐变（锅仔记忆保存按钮）secondary=浅底 dark=深棕 ghost=危险文字 */
  variant?: 'primary' | 'secondary' | 'dark' | 'ghost'
  block?: boolean
  disabled?: boolean
  loading?: boolean
  /** 点击节流冷却。默认开启，冷却期内忽略连点，防止快速重复点击导致重复提交/入库 */
  throttle?: boolean
  /** 冷却时长（毫秒），默认 1200。传 :throttle="false" 可关闭 */
  throttleCooldown?: number
  ariaLabel?: string
}>(), {
  variant: 'primary',
  block: true,
  disabled: false,
  loading: false,
  throttle: true,
  throttleCooldown: 1200,
})

const emit = defineEmits<{ click: [] }>()

const cooldownUntil = ref(0)

function onClick() {
  if (props.disabled || props.loading)
    return
  // 节流冷却：冷却期内静默忽略连点，避免数据重复入库
  if (props.throttle && props.throttleCooldown > 0) {
    const now = Date.now()
    if (now < cooldownUntil.value)
      return
    cooldownUntil.value = now + props.throttleCooldown
  }
  emit('click')
}
</script>

<template>
  <button
    class="gz-btn"
    :class="[`gz-btn--${variant}`, { 'gz-btn--block': block, 'is-disabled': disabled || loading }]"
    :disabled="disabled || loading"
    :aria-label="ariaLabel"
    hover-class="gz-btn--pressed"
    @click="onClick"
  >
    <slot>{{ loading ? '处理中…' : '' }}</slot>
  </button>
</template>

<style lang="scss" scoped>
/* 小程序原生 button 默认有内边距与伪元素边框，内容会偏移；这里统一 flex 居中并清掉默认边框 */
.gz-btn { display: flex; align-items: center; justify-content: center; min-height: var(--mrc-touch-min); margin: 0; padding: 0 32rpx; box-sizing: border-box; border: 0; border-radius: 48rpx; color: var(--mrc-color-on-primary); font-size: 29rpx; font-weight: 800; line-height: 1.2; text-align: center; transition: transform var(--mrc-motion-fast), opacity var(--mrc-motion-fast); }
.gz-btn::after { border: 0; }
.gz-btn--block { width: 100%; }
/* 主按钮：渐变整体加深，避开浅端白字看不清；文字加细阴影拉开对比 */
.gz-btn--primary { background: linear-gradient(135deg, var(--mrc-primary) 0%, var(--mrc-color-primary) 100%); color: var(--mrc-color-on-primary); text-shadow: 0 1rpx 2rpx rgba(150, 40, 20, .28); box-shadow: var(--mrc-shadow-coral); }
/* 次级按钮：浅底 + 深咖字，明确可读 */
.gz-btn--secondary { background: var(--mrc-surface); color: var(--mrc-text-deep); border: 2rpx solid var(--mrc-border); }
/* 深棕按钮：真·深棕底 + 奶白字 */
.gz-btn--dark { background: linear-gradient(135deg, #5A3323 0%, #3D2519 100%); color: #FFF6EA; box-shadow: 0 12rpx 26rpx rgba(70, 35, 23, .22); }
.gz-btn--ghost { background: transparent; color: var(--mrc-color-danger); font-weight: 700; }
.gz-btn.is-disabled { opacity: .45; }
.gz-btn--pressed { transform: scale(.985); opacity: .9; }
</style>
