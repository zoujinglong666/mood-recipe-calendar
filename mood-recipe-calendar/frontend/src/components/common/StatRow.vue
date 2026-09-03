<template>
  <view class="stat-row">
    <view v-for="item in items" :key="item.label" class="stat-row__item">
      <view class="stat-row__value">
        <text class="stat-row__num">{{ item.value }}</text>
        <text v-if="item.unit" class="stat-row__unit">{{ item.unit }}</text>
      </view>
      <text class="stat-row__label">{{ item.label }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
export interface StatItem {
  label: string
  value: number | string
  unit?: string
}

defineProps<{
  items: StatItem[]
}>()
</script>

<style lang="scss" scoped>
.stat-row {
  display: flex;
  background: var(--mrc-white);
  border-radius: var(--mrc-radius-lg);
  padding: 28rpx 0;
  box-shadow: 0 6rpx 20rpx rgba(232, 131, 107, 0.1);
}
.stat-row__item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  position: relative;
}
.stat-row__item + .stat-row__item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 20%;
  height: 60%;
  width: 2rpx;
  background: var(--mrc-border);
}
.stat-row__value {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
}
.stat-row__num {
  font-size: 44rpx;
  font-weight: 700;
  color: var(--mrc-accent);
}
.stat-row__unit {
  font-size: 24rpx;
  color: var(--mrc-text-sub);
}
.stat-row__label {
  font-size: 24rpx;
  color: var(--mrc-text-sub);
}
</style>
