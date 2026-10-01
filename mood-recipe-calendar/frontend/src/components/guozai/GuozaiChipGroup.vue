<script setup lang="ts">
import { computed } from 'vue'

export interface GuozaiChipOption {
  value: string
  label: string
}

const props = withDefaults(defineProps<{
  /** 单选时传 string，多选时传 string[] */
  modelValue?: string | string[]
  options: GuozaiChipOption[]
  /** 是否多选，默认单选 */
  multiple?: boolean
  /** 网格列数；0 = 自适应换行并整体居中 */
  columns?: number
  disabled?: boolean
  ariaLabel?: string
}>(), {
  modelValue: '',
  multiple: false,
  columns: 0,
  disabled: false,
  ariaLabel: '选项',
})

const emit = defineEmits<{
  'update:modelValue': [value: string | string[]]
  'change': [value: string | string[]]
}>()

const selected = computed<string[]>(() => props.multiple
  ? (Array.isArray(props.modelValue) ? props.modelValue : [])
  : [String(props.modelValue ?? '')])

function isSelected(value: string) {
  return selected.value.includes(value)
}

function toggle(value: string) {
  if (props.disabled)
    return
  if (props.multiple) {
    const current = Array.isArray(props.modelValue) ? props.modelValue : []
    const next = current.includes(value)
      ? current.filter(item => item !== value)
      : [...current, value]
    emit('update:modelValue', next)
    emit('change', next)
    return
  }
  emit('update:modelValue', value)
  emit('change', value)
}
</script>

<template>
  <view
    class="chip-group"
    :class="{ 'chip-group--grid': columns > 0 }"
    :style="columns > 0 ? { gridTemplateColumns: `repeat(${columns}, 1fr)` } : {}"
    role="group"
    :aria-label="ariaLabel"
  >
    <view
      v-for="option in options"
      :key="option.value"
      class="chip"
      :class="{ 'chip--selected': isSelected(option.value), 'chip--disabled': disabled }"
      :role="multiple ? 'checkbox' : 'radio'"
      :aria-checked="isSelected(option.value)"
      :aria-label="option.label"
      hover-class="chip--pressed"
      @click="toggle(option.value)"
    >
      {{ option.label }}
    </view>
  </view>
</template>

<style lang="scss" scoped>
.chip-group { display: flex; flex-wrap: wrap; justify-content: center; gap: 14rpx; }
.chip-group--grid { display: grid; }
.chip { min-height: 88rpx; display: flex; align-items: center; justify-content: center; box-sizing: border-box; padding: 0 12rpx; border: 2rpx solid var(--mrc-border); border-radius: 44rpx; background: var(--mrc-surface); color: #4A3022; font-size: 24rpx; font-weight: 700; transition: transform 150ms ease, background-color 200ms ease; }
.chip--selected { border-color: var(--mrc-accent); background: #FCDCCB; color: #C63F22; font-weight: 800; }
.chip--disabled { opacity: .5; pointer-events: none; }
.chip--pressed { transform: scale(.96); }
</style>
