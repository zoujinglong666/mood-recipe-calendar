<script setup lang="ts">
export interface GuozaiChoiceOption {
  value: string
  label: string
}

const props = withDefaults(defineProps<{
  modelValue?: string[]
  options: GuozaiChoiceOption[]
  disabled?: boolean
  ariaLabel?: string
}>(), {
  modelValue: () => [],
  disabled: false,
  ariaLabel: '做饭反馈，可多选',
})

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
  'select': [value: string]
}>()

function toggle(value: string) {
  if (props.disabled)
    return
  const next = props.modelValue.includes(value)
    ? props.modelValue.filter(item => item !== value)
    : [...props.modelValue, value]
  emit('update:modelValue', next)
  emit('select', value)
}
</script>

<template>
  <view class="guozai-choice" role="group" :aria-label="ariaLabel">
    <view
      v-for="option in options"
      :key="option.value"
      class="guozai-choice__item"
      :class="{ 'is-selected': modelValue.includes(option.value), 'is-disabled': disabled }"
      role="checkbox"
      :aria-checked="modelValue.includes(option.value)"
      :aria-label="option.label"
      hover-class="guozai-choice__item--pressed"
      @click="toggle(option.value)"
    >
      <text class="guozai-choice__mark">
        {{ modelValue.includes(option.value) ? '✓' : '+' }}
      </text>
      <text>{{ option.label }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.guozai-choice { display: flex; flex-wrap: wrap; gap: var(--mrc-space-sm); }
.guozai-choice__item { min-height: 88rpx; padding: 0 24rpx; display: inline-flex; align-items: center; gap: 10rpx; box-sizing: border-box; border: 2rpx solid var(--mrc-border); border-radius: var(--mrc-radius-pill); background: var(--mrc-surface); color: var(--mrc-text-deep); font-size: var(--mrc-fs-sub); font-weight: var(--mrc-fw-semibold); transition: transform 150ms ease, opacity 150ms ease; }
.guozai-choice__item.is-selected { border-color: var(--mrc-primary-deep); background: var(--mrc-accent-soft); color: var(--mrc-accent); }
.guozai-choice__item.is-disabled { opacity: .5; pointer-events: none; }
.guozai-choice__item--pressed { transform: scale(.97); opacity: .82; }
.guozai-choice__mark { width: 30rpx; height: 30rpx; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: var(--mrc-surface-sun); font-size: 20rpx; font-weight: var(--mrc-fw-heavy); }
</style>
