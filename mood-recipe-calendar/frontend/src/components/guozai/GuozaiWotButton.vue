<script setup lang="ts">
/**
 * wot 官方 wd-button 的锅仔包装层。
 * 保留 wot 的 type/variant/size/round/block/loading/icon/open-type 等全部能力，
 * 并把主色统一成锅仔珊瑚渐变，与 GuozaiButton 视觉一致。
 *
 * 关键坑：wot 的背景走 CSS 变量（--wot-button-primary-bg）。原先传的值是 `var(--mrc-primary-grad)`，
 * 属于「变量套变量」，在小程序自定义组件里变量继承不可靠，渐变会丢失、退回 wot 默认蓝色。
 * 因此这里改用 custom-style 内联写入渐变（内联优先级最高，必定生效），
 * CSS 变量仅保留为字面量兜底（不再嵌套 var）。
 */
import { computed, ref } from 'vue'

const props = withDefaults(defineProps<{
  /** primary success info warning danger */
  type?: 'primary' | 'success' | 'info' | 'warning' | 'danger'
  /** base plain dashed soft subtle text */
  variant?: 'base' | 'plain' | 'dashed' | 'soft' | 'subtle' | 'text'
  /** mini small medium large */
  size?: 'mini' | 'small' | 'medium' | 'large'
  block?: boolean
  round?: boolean
  hairline?: boolean
  disabled?: boolean
  loading?: boolean
  icon?: string
  /** 微信开放能力，如 getPhoneNumber / chooseAvatar */
  openType?: string
}>(), {
  type: 'primary',
  variant: 'base',
  size: 'medium',
  block: false,
  round: false,
  hairline: false,
  disabled: false,
  loading: false,
  icon: '',
  openType: '',
})

const emit = defineEmits<{ click: [event: any] }>()

/** 与 GuozaiButton primary 完全一致的珊瑚渐变 */
const GRADIENT = 'linear-gradient(135deg, #FF9E6B 0%, #F2542D 100%)'
const GRADIENT_PRESSED = 'linear-gradient(135deg, #F58A50 0%, #E24A22 100%)'

const pressed = ref(false)

/** CSS 变量兜底：值必须是字面量，不能嵌套 var(--xxx) */
const brandVars = {
  '--wot-button-primary-bg': GRADIENT,
  '--wot-button-primary-bg-active': GRADIENT_PRESSED,
  '--wot-button-primary-color': '#fff',
  '--wot-button-primary-color-active': '#fff',
  '--wot-button-primary-plain-color': '#EF5A3C',
  '--wot-button-primary-plain-border': '#EF5A3C',
  '--wot-button-primary-soft-bg': '#FCDCCB',
  '--wot-button-primary-soft-color': '#C63F22',
  '--wot-button-danger-bg': '#C0301F',
  '--wot-button-danger-color': '#fff',
  '--wot-button-radius-full': '999rpx',
}

/**
 * 内联样式：直接写死渐变与文字色，确保渐变一定生效。
 * 只覆盖 primary / danger，success / info / warning 沿用 wot 语义色。
 */
const customStyle = computed(() => {
  const parts: string[] = []
  if (props.type === 'primary') {
    if (props.variant === 'base') {
      // 实心：珊瑚渐变 + 白字；按下时换更深的渐变做反馈
      parts.push(`background: ${pressed.value ? GRADIENT_PRESSED : GRADIENT}`)
      parts.push('color: #fff')
    }
    else if (props.variant === 'soft') {
      parts.push('background: #FCDCCB')
      parts.push('color: #C63F22')
    }
    else {
      // plain / dashed / subtle / text：珊瑚色文字
      parts.push('color: #EF5A3C')
    }
  }
  else if (props.type === 'danger') {
    parts.push('background: #C0301F')
    parts.push('color: #fff')
  }
  return parts.join(';')
})

/** openType 取值表过长，这里统一断言后透传 */
const openTypeValue = computed(() => (props.openType || undefined) as any)

function onClick(event: any) {
  if (props.disabled || props.loading)
    return
  emit('click', event)
}
</script>

<template>
  <wd-button
    :style="brandVars"
    :custom-style="customStyle"
    :type="type"
    :variant="variant"
    :size="size"
    :block="block"
    :round="round"
    :hairline="hairline"
    :disabled="disabled"
    :loading="loading"
    :icon="icon || undefined"
    :open-type="openTypeValue"
    @touchstart="pressed = true"
    @touchend="pressed = false"
    @touchcancel="pressed = false"
    @click="onClick"
  >
    <slot />
  </wd-button>
</template>
