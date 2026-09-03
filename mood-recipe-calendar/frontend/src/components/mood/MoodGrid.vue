<template>
  <view class="mood-grid">
    <view
      v-for="item in items"
      :key="item.label"
      class="mood-item"
      :class="[{ 'mood-item--active': modelValue === item.label }, 'mood-item--' + item.label]"
      @click="$emit('update:modelValue', item.label)"
    >
      <text class="mood-item__emoji">{{ item.emoji }}</text>
      <text class="mood-item__label">{{ item.label }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
export interface MoodItem {
  emoji: string
  label: string
}

withDefaults(
  defineProps<{
    modelValue?: string
    items?: MoodItem[]
  }>(),
  {
    modelValue: '开心',
    items: () => [
      { emoji: '😊', label: '开心' },
      { emoji: '😌', label: '平静' },
      { emoji: '😔', label: '疲惫' },
      { emoji: '😤', label: '焦虑' },
      { emoji: '😢', label: '难过' },
      { emoji: '🤤', label: '嘴馋' },
      { emoji: '🌧️', label: '低落' },
      { emoji: '❤️', label: '想家' },
    ],
  },
)
</script>

<style lang="scss" scoped>
.mood-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20rpx;
}
.mood-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 22rpx 0;
  background: var(--mrc-white);
  border: 3rpx solid var(--mrc-border);
  border-radius: 20rpx;
  transition: all 0.2s;
}
.mood-item__emoji {
  font-size: 44rpx;
  line-height: 1;
}
.mood-item__label {
  font-size: 24rpx;
  color: var(--mrc-text-sub);
}
.mood-item--active {
  border-color: var(--mrc-mood, var(--mrc-accent));
  background: color-mix(in srgb, var(--mrc-mood, var(--mrc-accent)) 14%, #fff);
  transform: scale(1.04);
}
.mood-item--active .mood-item__label {
  color: var(--mrc-text);
  font-weight: 600;
}
</style>
