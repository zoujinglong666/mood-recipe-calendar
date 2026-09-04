<script setup lang="ts">
defineProps<{
  name: 'camera' | 'dice' | 'heart' | 'calendar' | 'book' | 'user' | 'back' | 'cart' | 'share' | 'clock' | 'flame' | 'gear' | 'list' | 'moon' | 'phone'
  size?: number
  color?: string
}>()
</script>

<template>
  <view class="icon" :style="{ width: (size || 32) + 'rpx', height: (size || 32) + 'rpx', color: color || 'currentColor' }">
    <!-- 相机（复古线性） -->
    <svg v-if="name === 'camera'" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
      <rect x="5" y="14" width="38" height="26" rx="5" />
      <circle cx="24" cy="27" r="8" />
      <circle cx="24" cy="27" r="3.5" />
      <path d="M17 14l3-6h8l3 6" />
    </svg>
    <!-- 骰子（透明底，线条+点） -->
    <svg v-else-if="name === 'dice'" viewBox="0 0 48 48" fill="none">
      <rect x="7" y="7" width="34" height="34" rx="8" stroke="currentColor" stroke-width="2.5" />
      <circle cx="17" cy="17" r="2.8" fill="currentColor" />
      <circle cx="31" cy="17" r="2.8" fill="currentColor" />
      <circle cx="24" cy="24" r="2.8" fill="currentColor" />
      <circle cx="17" cy="31" r="2.8" fill="currentColor" />
      <circle cx="31" cy="31" r="2.8" fill="currentColor" />
    </svg>
    <!-- 爱心（空心） -->
    <svg v-else-if="name === 'heart'" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M24 40C24 40 8 29 8 18.5C8 13 12.5 9 17.5 9C20.5 9 22.8 10.5 24 13C25.2 10.5 27.5 9 30.5 9C35.5 9 40 13 40 18.5C40 29 24 40 24 40Z" />
    </svg>
    <!-- 日历 -->
    <svg v-else-if="name === 'calendar'" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
      <rect x="6" y="10" width="36" height="32" rx="4" />
      <path d="M6 18h36M16 6v8M32 6v8" />
    </svg>
    <!-- 画册/书 -->
    <svg v-else-if="name === 'book'" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M8 10h14a4 4 0 014 4v24a4 4 0 00-4-4H8z" />
      <path d="M40 10H26a4 4 0 00-4 4v24a4 4 0 014-4h14z" />
    </svg>
    <!-- 用户 -->
    <svg v-else-if="name === 'user'" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
      <circle cx="24" cy="16" r="7" />
      <path d="M10 40c0-7 6-12 14-12s14 5 14 12" />
    </svg>
    <!-- 返回 -->
    <svg v-else-if="name === 'back'" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M28 10l-12 14 12 14" />
    </svg>
    <!-- 购物车 -->
    <svg v-else-if="name === 'cart'" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
      <circle cx="18" cy="39" r="3" />
      <circle cx="36" cy="39" r="3" />
      <path d="M6 8h6l4 22h20l4-14H12" />
    </svg>
    <!-- 分享 -->
    <svg v-else-if="name === 'share'" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
      <circle cx="18" cy="36" r="5" />
      <circle cx="36" cy="18" r="5" />
      <circle cx="36" cy="36" r="5" />
      <path d="M22.5 33.5L31.5 20.5" />
    </svg>
    <!-- 时钟 -->
    <svg v-else-if="name === 'clock'" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
      <circle cx="24" cy="24" r="16" />
      <path d="M24 14v10l7 4" />
    </svg>
    <!-- 火焰/难度 -->
    <svg v-else-if="name === 'flame'" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M24 6c2 8-6 10-6 18a6 6 0 0012 0c0-4-3-6-3-10 2 2 4 4 6 6 2-4 1-8 1-10-3 0-7-2-10-4z" />
    </svg>
    <!-- 齿轮设置 -->
    <svg v-else-if="name === 'gear'" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
      <circle cx="24" cy="24" r="8" />
      <path d="M24 4v6M24 38v6M4 24h6M38 24h6M10 10l4 4M34 34l4 4M38 10l-4 4M14 34l-4 4" />
    </svg>
    <!-- 列表 -->
    <svg v-else-if="name === 'list'" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
      <rect x="8" y="10" width="32" height="6" rx="3" />
      <rect x="8" y="21" width="32" height="6" rx="3" />
      <rect x="8" y="32" width="32" height="6" rx="3" />
    </svg>
    <!-- 月亮（深色模式） -->
    <svg v-else-if="name === 'moon'" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M30 8a16 16 0 1010 20 13 13 0 01-10-20z" />
    </svg>
    <!-- 手机（跟随系统） -->
    <svg v-else-if="name === 'phone'" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
      <rect x="12" y="6" width="24" height="36" rx="4" />
      <path d="M20 38h8" />
    </svg>
  </view>
</template>

<style scoped>
.icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.icon svg {
  width: 100%;
  height: 100%;
  display: block;
}
</style>
