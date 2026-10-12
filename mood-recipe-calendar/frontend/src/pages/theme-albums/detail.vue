<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { STATIC_BASE_URL } from '@/utils/assets'
import { fetchThemeAlbumDetail, type ThemeAlbumDish } from '@/api/themeAlbums'
import { fetchRecipeDetail } from '@/api/recipes'
import { saveCookingDraft } from '@/utils/cookingDraft'
import { useVirtualPurchase } from '@/composables/useVirtualPurchase'
import { toast, toastError } from '@/utils/toast'

definePage({
  name: 'theme-album-detail',
  layout: 'default',
  style: { navigationStyle: 'custom', navigationBarTitleText: '主题专辑' },
})

const router = useRouter()
const { purchasingSku, purchase } = useVirtualPurchase()
const loading = ref(true)
const album = ref<Awaited<ReturnType<typeof fetchThemeAlbumDetail>> | null>(null)

const dishes = computed(() => album.value?.dishes || [])
const priceText = computed(() =>
  album.value?.priceFen != null ? `¥${(album.value.priceFen / 100).toFixed(2)}` : '',
)

onLoad(async (options) => {
  const sku = options?.sku
  if (!sku) {
    toast('专辑不存在')
    router.back()
    return
  }
  loading.value = true
  try {
    album.value = await fetchThemeAlbumDetail(sku)
  }
  catch (e: any) {
    toastError(e, '专辑加载失败')
  }
  finally {
    loading.value = false
  }
})

function buy() {
  if (!album.value || purchasingSku.value)
    return
  purchase(album.value.sku, async () => {
    // 支付成功后重拉详情，立即展示全部菜品
    try {
      album.value = await fetchThemeAlbumDetail(album.value!.sku)
    }
    catch {}
  })
}

async function openDish(dish: ThemeAlbumDish) {
  if (!album.value?.owned) {
    toast('购买后即可查看做法')
    return
  }
  try {
    const recipe = await fetchRecipeDetail(dish.id)
    saveCookingDraft(recipe, '满足')
    router.push({ name: 'cooking' })
  }
  catch (e: any) {
    toastError(e, '这道菜暂时打不开')
  }
}
</script>

<template>
  <view class="tad mrc-hero">
    <wd-navbar title="主题专辑" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="router.back" />

    <view v-if="loading" class="tad-empty"><text>锅仔正在翻开专辑…</text></view>

    <template v-else-if="album">
      <!-- 封面区 -->
      <view class="tad-cover">
        <image class="tad-cover__img" :src="`${STATIC_BASE_URL}${album.cover}`" mode="aspectFill" />
        <view class="tad-cover__mask" />
        <view class="tad-cover__text">
          <text class="tad-cover__title">{{ album.title }}</text>
          <text class="tad-cover__subtitle">{{ album.subtitle }}</text>
          <view v-if="album.owned" class="tad-cover__owned">已拥有 · 长期可看</view>
        </view>
      </view>

      <!-- 介绍卡 -->
      <view class="tad-intro">
        <view class="tad-intro__label">锅仔手记</view>
        <text class="tad-intro__text">{{ album.description }}</text>
        <view class="tad-intro__meta">
          <text>共 {{ album.totalCount }} 道菜</text>
          <text v-if="!album.owned">可试看 {{ album.visibleCount }} 道</text>
        </view>
      </view>

      <!-- 菜谱列表 -->
      <view class="tad-list">
        <view
          v-for="(dish, i) in dishes"
          :key="dish.id"
          class="tad-dish"
          :class="{ 'tad-dish--locked': !album.owned }"
          role="button"
          :aria-label="album.owned ? `查看${dish.name}做法` : '购买后查看做法'"
          @click="openDish(dish)"
        >
          <view class="tad-dish__no">{{ i + 1 }}</view>
          <image class="tad-dish__img" :src="dish.image || `${STATIC_BASE_URL}/static/guozai/action_01_bowl.png`" mode="aspectFill" />
          <view class="tad-dish__main">
            <text class="tad-dish__name">{{ dish.name }}</text>
            <text class="tad-dish__desc">{{ dish.description }}</text>
            <view class="tad-dish__meta">
              <text v-if="dish.cookingTime" class="tad-dish__tag">{{ dish.cookingTime }} 分钟</text>
              <text v-if="dish.difficulty" class="tad-dish__tag">{{ dish.difficulty }}</text>
              <text v-if="!album.owned" class="tad-dish__tag tad-dish__tag--lock">做法已上锁</text>
              <text v-else class="tad-dish__tag tad-dish__tag--go">看做法 ›</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 未购锁定提示 -->
      <view v-if="!album.owned" class="tad-lock">
        <text class="tad-lock__title">还有 {{ album.totalCount - album.visibleCount }} 道菜与完整做法</text>
        <text class="tad-lock__sub">购买后永久解锁，跟着锅仔一道道做</text>
      </view>

      <view class="tad-footer-space" />

      <!-- 底部购买栏 -->
      <view v-if="!album.owned" class="tad-bar">
        <view class="tad-bar__copy">
          <text class="tad-bar__price">{{ priceText }}</text>
          <text class="tad-bar__note">一次购买 · 长期可看</text>
        </view>
        <view
          class="tad-bar__btn"
          :class="{ 'is-disabled': Boolean(purchasingSku) }"
          role="button"
          aria-label="购买专辑"
          @click="buy"
        >
          {{ purchasingSku ? '处理中…' : '立即解锁' }}
        </view>
      </view>
    </template>

    <view v-else class="tad-empty">
      <image :src="`${STATIC_BASE_URL}/static/guozai/action_08_peek.png`" mode="aspectFit" />
      <text>这张专辑暂时打不开</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.tad { min-height: 100vh; padding: 0 32rpx; padding-bottom: calc(160rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.tad-empty { display: flex; flex-direction: column; align-items: center; gap: 20rpx; padding: 120rpx 40rpx; color: var(--mrc-text-sub); font-size: 26rpx; }
.tad-empty image { width: 160rpx; height: 160rpx; opacity: .8; }

.tad-cover { position: relative; margin: 8rpx 0 24rpx; border-radius: 36rpx; overflow: hidden; background: var(--mrc-surface-peach); }
.tad-cover__img { width: 100%; height: 340rpx; }
.tad-cover__mask { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(45, 24, 14, 0) 30%, rgba(45, 24, 14, 0.62) 100%); }
.tad-cover__text { position: absolute; left: 28rpx; right: 28rpx; bottom: 24rpx; display: flex; flex-direction: column; gap: 8rpx; }
.tad-cover__title { color: #fff; font-size: 44rpx; font-weight: 800; letter-spacing: 1rpx; }
.tad-cover__subtitle { color: rgba(255, 245, 230, 0.85); font-size: 24rpx; }
.tad-cover__owned { align-self: flex-start; margin-top: 6rpx; padding: 6rpx 18rpx; border-radius: 999rpx; background: var(--mrc-mint); color: #fff; font-size: 20rpx; font-weight: 700; }

.tad-intro { position: relative; padding: 26rpx 28rpx; border: 2rpx dashed var(--mrc-border-strong); border-radius: 28rpx; background: var(--mrc-surface); }
.tad-intro__label { margin-bottom: 10rpx; color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; letter-spacing: 2rpx; }
.tad-intro__text { color: var(--mrc-text-deep); font-size: 24rpx; line-height: 1.6; }
.tad-intro__meta { display: flex; gap: 20rpx; margin-top: 16rpx; color: var(--mrc-text-light); font-size: 20rpx; }

.tad-list { display: flex; flex-direction: column; gap: 16rpx; margin-top: 24rpx; }
.tad-dish { display: flex; align-items: center; gap: 18rpx; padding: 16rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 26rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); transition: transform .15s ease; }
.tad-dish:active { transform: scale(.98); }
.tad-dish__no { flex-shrink: 0; width: 44rpx; height: 44rpx; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: var(--mrc-surface-sun); color: var(--mrc-accent); font-size: 22rpx; font-weight: 800; }
.tad-dish__img { width: 112rpx; height: 112rpx; flex-shrink: 0; border-radius: 18rpx; background: var(--mrc-surface-2); }
.tad-dish__main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6rpx; }
.tad-dish__name { color: var(--mrc-text-strong); font-size: 29rpx; font-weight: 800; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tad-dish__desc { color: var(--mrc-text-sub); font-size: 21rpx; line-height: 1.4; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 1; line-clamp: 1; }
.tad-dish__meta { display: flex; flex-wrap: wrap; gap: 10rpx; margin-top: 2rpx; }
.tad-dish__tag { padding: 4rpx 14rpx; border-radius: 999rpx; background: var(--mrc-surface-2); color: var(--mrc-text-sub); font-size: 18rpx; font-weight: 600; }
.tad-dish__tag--lock { background: var(--mrc-surface-2); color: var(--mrc-text-light); }
.tad-dish__tag--go { background: var(--mrc-accent-soft); color: var(--mrc-accent); font-weight: 700; }
.tad-dish--locked .tad-dish__img { filter: grayscale(0.35); opacity: .82; }

.tad-lock { display: flex; flex-direction: column; align-items: center; gap: 8rpx; margin-top: 28rpx; padding: 32rpx 40rpx; border-radius: 28rpx; background: var(--mrc-surface-sun); text-align: center; }
.tad-lock__title { color: var(--mrc-text-deep); font-size: 28rpx; font-weight: 800; }
.tad-lock__sub { color: var(--mrc-text-sub); font-size: 22rpx; }

.tad-footer-space { height: 24rpx; }

.tad-bar { position: fixed; left: 0; right: 0; bottom: 0; display: flex; align-items: center; justify-content: space-between; gap: 20rpx; padding: 18rpx 32rpx calc(18rpx + env(safe-area-inset-bottom)); background: rgba(248, 236, 218, 0.96); backdrop-filter: blur(10px); border-top: 1rpx solid var(--mrc-border-light); }
.tad-bar__copy { display: flex; flex-direction: column; gap: 4rpx; }
.tad-bar__price { color: var(--mrc-primary-deep); font-size: 40rpx; font-weight: 800; }
.tad-bar__note { color: var(--mrc-text-sub); font-size: 20rpx; }
.tad-bar__btn { padding: 20rpx 56rpx; border-radius: 999rpx; background: var(--mrc-primary-grad); color: #fff; font-size: 29rpx; font-weight: 800; box-shadow: var(--mrc-shadow-coral); transition: transform .15s ease; }
.tad-bar__btn:active { transform: scale(.96); }
.tad-bar__btn.is-disabled { opacity: .6; pointer-events: none; }
</style>
