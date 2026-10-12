<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { STATIC_BASE_URL } from '@/utils/assets'
import { fetchRecipeDetail } from '@/api/recipes'
import { saveCookingDraft } from '@/utils/cookingDraft'
import { useWishlist } from '@/composables/useWishlist'
import { fetchFavorites, type FavoriteItem } from '@/api/favorites'
import { toast, toastError } from '@/utils/toast'
import { navigateToLogin } from '@/utils/login'
import { useUserStore } from '@/stores/user'

definePage({
  name: 'wishlist',
  layout: 'default',
  style: { navigationStyle: 'custom', navigationBarTitleText: '想吃清单' },
})

const router = useRouter()
const userStore = useUserStore()
const { toggle } = useWishlist()
const loading = ref(true)
const items = ref<FavoriteItem[]>([])

const loggedIn = computed(() => userStore.isLoggedIn)

onShow(async () => {
  if (!userStore.isLoggedIn) {
    loading.value = false
    items.value = []
    return
  }
  loading.value = true
  try {
    items.value = await fetchFavorites()
  }
  catch (e: any) {
    toastError(e, '想吃清单加载失败')
  }
  finally {
    loading.value = false
  }
})

async function openCook(item: FavoriteItem) {
  try {
    const recipe = await fetchRecipeDetail(item.recipeId)
    saveCookingDraft(recipe, '满足')
    router.push({ name: 'cooking' })
  }
  catch (e: any) {
    toastError(e, '这道菜暂时打不开')
  }
}

async function onHeart(item: FavoriteItem) {
  // 乐观移除；toggle 内部失败会回滚后端状态，此时重新拉列表校正
  const backup = items.value
  items.value = items.value.filter(x => x.recipeId !== item.recipeId)
  await toggle(String(item.recipeId))
  try {
    items.value = await fetchFavorites()
  }
  catch {
    items.value = backup
    toast('取消收藏没成功，请稍后重试')
  }
}

function goLogin() {
  navigateToLogin()
}
function goLibrary() {
  router.push({ name: 'recipe-library' })
}
</script>

<template>
  <view class="wl mrc-hero">
    <wd-navbar title="想吃清单" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="router.back" />

    <view class="wl-banner">
      <text class="wl-banner__title">以后想做的菜，都收在这里</text>
      <text class="wl-banner__sub">抽到的、推荐的、逛到的，先点 ♥ 存着，想吃时一键开做</text>
    </view>

    <view v-if="loading" class="wl-empty"><text>锅仔正在翻清单…</text></view>

    <view v-else-if="!loggedIn" class="wl-empty">
      <image :src="`${STATIC_BASE_URL}/static/guozai/action_08_peek.png`" mode="aspectFit" />
      <text class="wl-empty__title">登录后才能收藏</text>
      <text class="wl-empty__sub">收藏跟着账号走，换设备也不会丢</text>
      <view class="wl-empty__btn" role="button" @click="goLogin">微信登录</view>
    </view>

    <view v-else-if="!items.length" class="wl-empty">
      <image :src="`${STATIC_BASE_URL}/static/guozai/action_08_peek.png`" mode="aspectFit" />
      <text class="wl-empty__title">清单还是空的</text>
      <text class="wl-empty__sub">去菜谱库逛逛，遇到想做的点 ♥ 收进来</text>
      <view class="wl-empty__btn" role="button" @click="goLibrary">去菜谱库看看</view>
    </view>

    <view v-else class="wl-list">
      <view
        v-for="r in items"
        :key="r.recipeId"
        class="wl-item"
        role="button"
        :aria-label="`查看${r.name}做法`"
        @click="openCook(r)"
      >
        <image class="wl-item__img" :src="r.image || `${STATIC_BASE_URL}/static/guozai/action_01_bowl.png`" mode="aspectFill" />
        <view class="wl-item__main">
          <text class="wl-item__name">{{ r.name }}</text>
          <text class="wl-item__desc">{{ r.description }}</text>
          <view class="wl-item__meta">
            <text v-if="r.cookingTime" class="wl-item__tag">{{ r.cookingTime }} 分钟</text>
            <text v-if="r.difficulty" class="wl-item__tag">{{ r.difficulty }}</text>
          </view>
        </view>
        <view
          class="wl-heart is-on"
          role="button"
          aria-label="取消收藏"
          @click.stop="onHeart(r)"
        >♥</view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.wl { min-height: 100vh; padding: 0 32rpx; padding-bottom: calc(40rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.wl-banner { margin: 8rpx 0 24rpx; padding: 26rpx 28rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }
.wl-banner__title { display: block; color: var(--mrc-text-strong); font-size: 32rpx; font-weight: 800; }
.wl-banner__sub { display: block; margin-top: 8rpx; color: var(--mrc-text-sub); font-size: 23rpx; line-height: 1.5; }
.wl-empty { display: flex; flex-direction: column; align-items: center; gap: 16rpx; padding: 120rpx 40rpx; color: var(--mrc-text-sub); font-size: 26rpx; text-align: center; }
.wl-empty image { width: 160rpx; height: 160rpx; opacity: .8; }
.wl-empty__title { color: var(--mrc-text-deep); font-size: 30rpx; font-weight: 800; }
.wl-empty__sub { max-width: 480rpx; line-height: 1.5; }
.wl-empty__btn { margin-top: 12rpx; padding: 18rpx 48rpx; border-radius: 999rpx; background: var(--mrc-primary-grad); color: #fff; font-size: 27rpx; font-weight: 800; box-shadow: var(--mrc-shadow-coral); }
.wl-empty__btn:active { transform: scale(.97); }
.wl-list { display: flex; flex-direction: column; gap: 16rpx; }
.wl-item { display: flex; align-items: center; gap: 20rpx; padding: 18rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); transition: transform .15s ease; }
.wl-item:active { transform: scale(.98); }
.wl-item__img { width: 132rpx; height: 132rpx; flex-shrink: 0; border-radius: 20rpx; background: var(--mrc-surface-2); }
.wl-item__main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8rpx; }
.wl-item__name { color: var(--mrc-text-strong); font-size: 30rpx; font-weight: 800; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wl-item__desc { color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.4; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; line-clamp: 2; }
.wl-item__meta { display: flex; flex-wrap: wrap; gap: 10rpx; }
.wl-item__tag { padding: 4rpx 14rpx; border-radius: 999rpx; background: var(--mrc-surface-2); color: var(--mrc-text-sub); font-size: 18rpx; font-weight: 600; }
.wl-heart { flex-shrink: 0; width: 64rpx; height: 64rpx; display: flex; align-items: center; justify-content: center; border-radius: 50%; font-size: 40rpx; color: var(--mrc-text-light); background: var(--mrc-surface-2); transition: transform .15s ease, color .15s ease; }
.wl-heart.is-on { color: var(--mrc-primary-deep); background: var(--mrc-accent-soft); }
.wl-heart:active { transform: scale(.88); }
</style>
