<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { STATIC_BASE_URL } from '@/utils/assets'
import { fetchAllRecipes, type RecipeItem } from '@/api/recipes'
import { saveCookingDraft } from '@/utils/cookingDraft'
import { navBack } from '@/composables/useNavBar'
import { useWishlist } from '@/composables/useWishlist'
import { toastError } from '@/utils/toast'

definePage({
  name: 'recipe-library',
  layout: 'default',
  style: { navigationStyle: 'custom', navigationBarTitleText: '菜谱库' },
})

const router = useRouter()
const { isFav, toggle, syncFromServer } = useWishlist()
function keyOf(r: RecipeItem) {
  return String(r.id ?? r.name)
}
const loading = ref(true)
const recipes = ref<RecipeItem[]>([])
const keyword = ref('')
const sourceFilter = ref<'ALL' | 'AI' | 'LOCAL'>('ALL')
const timeFilter = ref<'ALL' | 'FAST' | 'SLOW'>('ALL')
const moodFilter = ref<string>('ALL')

const SOURCE_OPTIONS = [
  { value: 'ALL', label: '全部' },
  { value: 'AI', label: 'AI 菜' },
  { value: 'LOCAL', label: '本地菜' },
] as const
const TIME_OPTIONS = [
  { value: 'ALL', label: '不限时长' },
  { value: 'FAST', label: '快手 ≤30分' },
  { value: 'SLOW', label: '慢工 >30分' },
] as const

/** 从全部菜谱动态提取心情标签（最多取 12 个），供标签云筛选 */
const moodTags = computed(() => {
  const set = new Set<string>()
  for (const r of recipes.value) {
    for (const t of (r.moodTags || '').split(',')) {
      const s = t.trim()
      if (s) set.add(s)
    }
  }
  return [...set].slice(0, 12)
})

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return recipes.value.filter((r) => {
    if (kw && !`${r.name} ${r.description} ${r.ingredients}`.toLowerCase().includes(kw))
      return false
    if (sourceFilter.value !== 'ALL' && r.source !== sourceFilter.value)
      return false
    if (timeFilter.value === 'FAST' && (r.cookingTime || 0) > 30)
      return false
    if (timeFilter.value === 'SLOW' && (r.cookingTime || 0) <= 30)
      return false
    if (moodFilter.value !== 'ALL' && !(r.moodTags || '').split(',').map(s => s.trim()).includes(moodFilter.value))
      return false
    return true
  })
})

onShow(async () => {
  syncFromServer()
  if (recipes.value.length)
    return
  loading.value = true
  try {
    const all = await fetchAllRecipes()
    // 防御性去重：后端已按菜名去重，旧缓存/兜底数据再按菜名过滤一次，保证列表不出现两道同名菜
    const seen = new Set<string>()
    recipes.value = all.filter((r) => {
      if (seen.has(r.name))
        return false
      seen.add(r.name)
      return true
    })
  }
  catch (e: any) {
    toastError(e, '菜谱库加载失败')
  }
  finally {
    loading.value = false
  }
})

function resetFilters() {
  keyword.value = ''
  sourceFilter.value = 'ALL'
  timeFilter.value = 'ALL'
  moodFilter.value = 'ALL'
}

function openCook(r: RecipeItem) {
  saveCookingDraft(r, '满足')
  router.push({ name: 'cooking' })
}
</script>

<template>
  <view class="library mrc-hero">
    <wd-navbar title="菜谱库" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <view class="library-search">
      <wd-icon name="search" size="32rpx" color="#A1826A" />
      <input
        v-model="keyword"
        class="library-search__field"
        placeholder="搜菜名、食材或做法，比如「番茄」"
        placeholder-class="library-search__placeholder"
        confirm-type="search"
      />
      <text v-if="keyword" class="library-search__clear" @click="keyword = ''">×</text>
    </view>

    <scroll-view class="library-chips" scroll-x>
      <view class="library-chips__row">
        <view
          v-for="opt in SOURCE_OPTIONS"
          :key="opt.value"
          class="library-chip"
          :class="{ 'library-chip--active': sourceFilter === opt.value }"
          role="button"
          @click="sourceFilter = opt.value"
        >{{ opt.label }}</view>
        <view
          v-for="opt in TIME_OPTIONS"
          :key="opt.value"
          class="library-chip"
          :class="{ 'library-chip--active': timeFilter === opt.value }"
          role="button"
          @click="timeFilter = opt.value"
        >{{ opt.label }}</view>
      </view>
    </scroll-view>

    <scroll-view v-if="moodTags.length" class="library-chips" scroll-x>
      <view class="library-chips__row">
        <view
          class="library-chip"
          :class="{ 'library-chip--active': moodFilter === 'ALL' }"
          role="button"
          @click="moodFilter = 'ALL'"
        >全部心情</view>
        <view
          v-for="m in moodTags"
          :key="m"
          class="library-chip"
          :class="{ 'library-chip--active': moodFilter === m }"
          role="button"
          @click="moodFilter = m"
        >{{ m }}</view>
      </view>
    </scroll-view>

    <view class="library-count">
      <text>共 {{ filtered.length }} 道菜</text>
      <text v-if="keyword || sourceFilter !== 'ALL' || timeFilter !== 'ALL' || moodFilter !== 'ALL'" class="library-count__reset" @click="resetFilters">清空筛选</text>
    </view>

    <view v-if="loading" class="library-empty"><text>锅仔正在翻菜谱…</text></view>
    <view v-else-if="!filtered.length" class="library-empty">
      <image :src="`${STATIC_BASE_URL}/static/guozai/action_08_peek.png`" mode="aspectFit" />
      <text>没有匹配的菜，换个关键词试试</text>
    </view>

    <view v-else class="library-list">
      <view
        v-for="r in filtered"
        :key="r.id || r.name"
        class="library-item"
        role="button"
        :aria-label="`查看${r.name}做法`"
        @click="openCook(r)"
      >
        <image class="library-item__img" :src="r.image || `${STATIC_BASE_URL}/static/guozai/action_01_bowl.png`" mode="aspectFill" />
        <view class="library-item__main">
          <text class="library-item__name">{{ r.name }}</text>
          <text class="library-item__desc">{{ r.description }}</text>
          <view class="library-item__meta">
            <text v-if="r.cookingTime" class="library-item__tag">{{ r.cookingTime }} 分钟</text>
            <text v-if="r.difficulty" class="library-item__tag">{{ r.difficulty }}</text>
            <text class="library-item__tag library-item__tag--src">{{ r.source === 'AI' ? 'AI' : '本地' }}</text>
          </view>
        </view>
        <view
          class="library-heart"
          :class="{ 'is-on': isFav(keyOf(r)) }"
          role="button"
          :aria-label="isFav(keyOf(r)) ? '取消收藏' : '加入想吃清单'"
          @click.stop="toggle(keyOf(r))"
        >♥</view>
        <text class="library-item__arrow">›</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.library { min-height: 100vh; padding: 0 32rpx; padding-bottom: calc(40rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.library-search { display: flex; align-items: center; gap: 16rpx; height: 88rpx; padding: 0 24rpx; margin: 8rpx 0 20rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 999rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.library-search__field { flex: 1; font-size: 28rpx; color: var(--mrc-text-deep); text-align: left; }
.library-search__placeholder { color: var(--mrc-text-light); }
.library-search__clear { width: 44rpx; height: 44rpx; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: var(--mrc-surface-2); color: var(--mrc-text-sub); font-size: 34rpx; }

.library-chips { white-space: nowrap; margin-bottom: 14rpx; }
.library-chips__row { display: inline-flex; gap: 14rpx; padding: 4rpx 2rpx; }
.library-chip { flex: 0 0 auto; padding: 12rpx 26rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 999rpx; background: var(--mrc-surface); color: var(--mrc-text-sub); font-size: 24rpx; font-weight: 700; transition: all .15s ease; }
.library-chip--active { color: #fff; background: var(--mrc-primary-grad); border-color: transparent; box-shadow: var(--mrc-shadow-coral); }

.library-count { display: flex; align-items: center; justify-content: space-between; padding: 4rpx 6rpx 18rpx; color: var(--mrc-text-sub); font-size: 24rpx; }
.library-count__reset { color: var(--mrc-accent); font-weight: 700; }

.library-empty { display: flex; flex-direction: column; align-items: center; gap: 20rpx; padding: 120rpx 0; color: var(--mrc-text-sub); font-size: 26rpx; }
.library-empty image { width: 160rpx; height: 160rpx; opacity: .8; }

.library-list { display: flex; flex-direction: column; gap: 16rpx; }
.library-item { display: flex; align-items: center; gap: 20rpx; padding: 18rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); transition: transform .15s ease; }
.library-item:active { transform: scale(.98); }
.library-item__img { width: 132rpx; height: 132rpx; flex-shrink: 0; border-radius: 20rpx; background: var(--mrc-surface-2); }
.library-item__main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8rpx; }
.library-item__name { color: var(--mrc-text-strong); font-size: 30rpx; font-weight: 800; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.library-item__desc { color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.4; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; line-clamp: 2; }
.library-item__meta { display: flex; flex-wrap: wrap; gap: 10rpx; }
.library-item__tag { padding: 4rpx 14rpx; border-radius: 999rpx; background: var(--mrc-surface-2); color: var(--mrc-text-sub); font-size: 18rpx; font-weight: 600; }
.library-item__tag--src { background: var(--mrc-surface-sun); color: var(--mrc-accent); }
.library-item__arrow { flex-shrink: 0; color: var(--mrc-text-light); font-size: 44rpx; }
.library-heart { flex-shrink: 0; width: 60rpx; height: 60rpx; display: flex; align-items: center; justify-content: center; border-radius: 50%; font-size: 38rpx; color: var(--mrc-text-light); background: var(--mrc-surface-2); transition: transform .15s ease, color .15s ease; }
.library-heart.is-on { color: var(--mrc-primary-deep); background: var(--mrc-accent-soft); }
.library-heart:active { transform: scale(.88); }
</style>
