<script setup lang="ts">
import { navBack } from '@/composables/useNavBar'
import Icon from '@/components/common/Icon.vue'

definePage({
  name: 'about',
  layout: 'tabbar',
  style: { navigationStyle: 'custom', navigationBarTitleText: '关于' },
})
</script>

<template>
  <view class="about-page mrc-hero">
    <wd-navbar title="关于锅仔" left-arrow safe-area-inset-top @click-left="navBack"  custom-style="background-color: transparent !important;" />
    <view class="about-hero">
      <view class="about-hero__copy">
        <text class="about-hero__eyebrow">GUOZAI'S KITCHEN</text>
        <text class="about-hero__title">把每一餐，<br />留成温柔的日常。</text>
        <text class="about-hero__sub">锅仔陪你记录心情，也提醒你认真吃饭。</text>
      </view>
      <image class="about-hero__img guozai-breathe" src="/static/guozai/action_06_glasses.png" mode="aspectFit" />
    </view>

    <view class="about-card">
      <text class="about-card__title">心情菜谱日历</text>
      <text class="about-card__text">一个把心情、好好吃饭与生活记录放在一起的小角落。每一次记录，都不是任务，而是对自己的照顾。</text>
    </view>

    <view class="about-points">
      <view class="about-point"><view class="about-point__icon"><Icon name="heart" :size="36" color="var(--mrc-accent)" /></view><view><text class="about-point__title">跟着心情吃饭</text><text class="about-point__text">让每一道推荐，更贴近今天的你</text></view></view>
      <view class="about-point"><view class="about-point__icon"><Icon name="calendar" :size="36" color="var(--mrc-accent)" /></view><view><text class="about-point__title">把日子装订成册</text><text class="about-point__text">收藏每一餐，也看见生活的变化</text></view></view>
      <view class="about-point"><view class="about-point__icon"><Icon name="heart" :size="36" color="var(--mrc-accent)" /></view><view><text class="about-point__title">隐私与数据</text><text class="about-point__text">只处理你主动提交的内容，不出售个人信息</text></view></view>
    </view>

    <view class="about-card about-card--service">
      <image class="about-card__service-img" src="/static/guozai/action_08_peek.png" mode="aspectFit" />
      <view><text class="about-card__title">需要帮忙？</text><text class="about-card__text">在“我的”页点击反馈建议，即可联系锅仔客服。</text></view>
    </view>

    <view class="about-footer"><text>心情菜谱日历 · v1.0</text><text>好好吃饭，也好好生活</text></view>
  </view>
</template>

<style lang="scss" scoped>
.about-page { min-height: 100vh; padding: 0 32rpx calc(60rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.about-hero { position: relative; min-height: 410rpx; margin-top: 12rpx; overflow: hidden; border: 2rpx solid var(--mrc-border-light); border-radius: 40rpx; background: radial-gradient(circle at 72% 22%, rgba(255, 197, 61, .28), transparent 20%), var(--mrc-surface-peach); box-shadow: var(--mrc-shadow-soft); }
.about-hero__copy { position: relative; z-index: 1; display: flex; flex-direction: column; width: 65%; padding: 48rpx 0 0 32rpx; }.about-hero__eyebrow { color: var(--mrc-accent); font-size: 20rpx; font-weight: 700; letter-spacing: 2rpx; }.about-hero__title { margin-top: 16rpx; color: var(--mrc-text-strong); font-size: 40rpx; font-weight: 700; line-height: 1.35; }.about-hero__sub { margin-top: 16rpx; color: var(--mrc-text-sub); font-size: 23rpx; line-height: 1.6; }.about-hero__img { position: absolute; right: -42rpx; bottom: -12rpx; width: 380rpx; height: 380rpx; }
.about-card { margin-top: 24rpx; padding: 32rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }.about-card__title { display: block; color: var(--mrc-text-deep); font-size: 34rpx; font-weight: 700; }.about-card__text { display: block; margin-top: 16rpx; color: var(--mrc-text-sub); font-size: 27rpx; line-height: 1.7; }
.about-points { display: flex; flex-direction: column; gap: 16rpx; margin-top: 24rpx; }.about-point { display: flex; align-items: center; gap: 20rpx; padding: 24rpx; border-radius: 26rpx; background: var(--mrc-surface); border: 2rpx solid var(--mrc-border-light); }.about-point__icon { display: flex; align-items: center; justify-content: center; width: 72rpx; height: 72rpx; border-radius: 22rpx; background: var(--mrc-surface-peach); }.about-point__title, .about-point__text { display: block; }.about-point__title { color: var(--mrc-text-deep); font-size: 30rpx; font-weight: 700; }.about-point__text { margin-top: 8rpx; color: var(--mrc-text-sub); font-size: 23rpx; }.about-footer { display: flex; flex-direction: column; align-items: center; gap: 12rpx; padding: 48rpx 0 16rpx; color: var(--mrc-text-light); font-size: 22rpx; letter-spacing: 1rpx; }
.about-card--service { display: flex; align-items: center; gap: 20rpx; }.about-card__service-img { width: 110rpx; height: 110rpx; flex-shrink: 0; }
</style>
