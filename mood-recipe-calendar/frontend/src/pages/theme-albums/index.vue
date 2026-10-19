<script setup lang="ts">
import { ref } from 'vue'
import { onShareAppMessage, onShareTimeline, onShow } from '@dcloudio/uni-app'
import { STATIC_BASE_URL } from '@/utils/assets'
import { fetchThemeAlbums, type ThemeAlbumItem } from '@/api/themeAlbums'
import { toastError } from '@/utils/toast'
import { navBack } from '@/composables/useNavBar'

definePage({
  name: 'theme-albums',
  layout: 'default',
  style: { navigationStyle: 'custom', navigationBarTitleText: '主题专辑' },
})

const router = useRouter()
const loading = ref(true)
const albums = ref<ThemeAlbumItem[]>([])

onShow(async () => {
  loading.value = true
  try {
    albums.value = await fetchThemeAlbums()
  }
  catch (e: any) {
    toastError(e, '专辑加载失败')
  }
  finally {
    loading.value = false
  }
})

function openAlbum(album: ThemeAlbumItem) {
  router.push({ name: 'theme-album-detail', params: { sku: album.sku } })
}

// 专辑是适合转发的付费内容，补齐与全站一致的分享链路（此前两页均不可转发）
onShareAppMessage(() => ({ title: '锅仔主题专辑 · 一整套认真搭配的菜单', path: '/pages/theme-albums/index' }))
onShareTimeline(() => ({ title: '锅仔主题专辑 · 一整套认真搭配的菜单' }))
</script>

<template>
  <view class="ta mrc-hero">
    <wd-navbar title="主题专辑" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <view class="ta-banner">
      <text class="ta-banner__title">照着做，就行</text>
      <text class="ta-banner__sub">锅仔按主题把菜谱编排成册，一次购买，长期可看</text>
    </view>

    <view v-if="loading" class="ta-empty"><text>锅仔正在装订专辑…</text></view>

    <view v-else-if="!albums.length" class="ta-empty">
      <image :src="`${STATIC_BASE_URL}/static/guozai/action_08_peek.png`" mode="aspectFit" />
      <text>专辑还在装订中，稍后再来看看</text>
    </view>

    <view v-else class="ta-list">
      <view
        v-for="album in albums"
        :key="album.sku"
        class="ta-card"
        role="button"
        :aria-label="`打开专辑${album.title}`"
        @click="openAlbum(album)"
      >
        <image class="ta-card__cover" :src="`${STATIC_BASE_URL}${album.cover}`" mode="aspectFill" />
        <view class="ta-card__main">
          <view class="ta-card__title-row">
            <text class="ta-card__title">{{ album.title }}</text>
            <view v-if="album.owned" class="ta-card__owned">已拥有</view>
          </view>
          <text class="ta-card__subtitle">{{ album.subtitle }}</text>
          <view class="ta-card__foot">
            <text class="ta-card__count">{{ album.dishCount }} 道菜</text>
            <text v-if="!album.owned && album.priceFen != null" class="ta-card__price">¥{{ (album.priceFen / 100).toFixed(2) }}</text>
            <text v-else-if="album.owned" class="ta-card__price ta-card__price--owned">随时可看</text>
          </view>
        </view>
        <text class="ta-card__arrow">›</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.ta { min-height: 100vh; padding: 0 32rpx; padding-bottom: calc(40rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.ta-banner { padding: 16rpx 8rpx 24rpx; }
.ta-banner__title { display: block; color: var(--mrc-text-strong); font-size: 36rpx; font-weight: 800; }
.ta-banner__sub { display: block; margin-top: 8rpx; color: var(--mrc-text-sub); font-size: 23rpx; line-height: 1.5; }
.ta-empty { display: flex; flex-direction: column; align-items: center; gap: 20rpx; padding: 120rpx 40rpx; color: var(--mrc-text-sub); font-size: 26rpx; text-align: center; }
.ta-empty image { width: 160rpx; height: 160rpx; opacity: .8; }
.ta-list { display: flex; flex-direction: column; gap: 20rpx; }
.ta-card { display: flex; align-items: center; gap: 22rpx; padding: 20rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 32rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); transition: transform .15s ease; }
.ta-card:active { transform: scale(.98); }
.ta-card__cover { width: 168rpx; height: 168rpx; flex-shrink: 0; border-radius: 22rpx; background: var(--mrc-surface-peach); }
.ta-card__main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 10rpx; }
.ta-card__title-row { display: flex; align-items: center; gap: 12rpx; }
.ta-card__title { color: var(--mrc-text-strong); font-size: 32rpx; font-weight: 800; }
.ta-card__owned { flex-shrink: 0; padding: 4rpx 14rpx; border-radius: 999rpx; background: var(--mrc-mint); color: #fff; font-size: 17rpx; font-weight: 700; }
.ta-card__subtitle { color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.45; }
.ta-card__foot { display: flex; align-items: baseline; gap: 16rpx; margin-top: 4rpx; }
.ta-card__count { color: var(--mrc-text-light); font-size: 20rpx; }
.ta-card__price { color: var(--mrc-primary-deep); font-size: 30rpx; font-weight: 800; }
.ta-card__price--owned { color: var(--mrc-mint); font-size: 22rpx; font-weight: 700; }
.ta-card__arrow { flex-shrink: 0; color: var(--mrc-text-light); font-size: 44rpx; }
</style>
