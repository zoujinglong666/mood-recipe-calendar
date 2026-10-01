<script setup lang="ts">
const props = withDefaults(defineProps<{
  /** primary=主渐变（锅仔记忆保存按钮）secondary=浅底 dark=深棕 ghost=危险文字 */
  variant?: 'primary' | 'secondary' | 'dark' | 'ghost'
  block?: boolean
  disabled?: boolean
  loading?: boolean
  ariaLabel?: string
}>(), {
  variant: 'primary',
  block: true,
  disabled: false,
  loading: false,
})

const emit = defineEmits<{ click: [] }>()

function onClick() {
  if (props.disabled || props.loading)
    return
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
.gz-btn { display: flex; align-items: center; justify-content: center; min-height: 96rpx; margin: 0; padding: 0 32rpx; box-sizing: border-box; border: 0; border-radius: 48rpx; color: #fff; font-size: 29rpx; font-weight: 800; line-height: 1.2; text-align: center; }
.gz-btn::after { border: 0; }
.gz-btn--block { width: 100%; }
/* 主按钮：渐变整体加深，避开浅端白字看不清；文字加细阴影拉开对比 */
.gz-btn--primary { background: linear-gradient(135deg, #FF9E6B 0%, #F2542D 100%); color: #fff; text-shadow: 0 1rpx 2rpx rgba(150, 40, 20, .28); box-shadow: var(--mrc-shadow-coral); }
/* 次级按钮：浅底 + 深咖字，明确可读 */
.gz-btn--secondary { background: var(--mrc-surface); color: var(--mrc-text-deep); border: 2rpx solid var(--mrc-border); }
/* 深棕按钮：真·深棕底 + 奶白字 */
.gz-btn--dark { background: linear-gradient(135deg, #5A3323 0%, #3D2519 100%); color: #FFF6EA; box-shadow: 0 12rpx 26rpx rgba(70, 35, 23, .22); }
.gz-btn--ghost { background: transparent; color: var(--mrc-danger, #C0301F); font-weight: 700; }
.gz-btn.is-disabled { opacity: .45; }
.gz-btn--pressed { transform: scale(.985); opacity: .9; }
</style>
