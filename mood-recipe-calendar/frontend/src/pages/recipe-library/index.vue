<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'

import { STATIC_BASE_URL } from '@/utils/assets'
import { fetchMoodTags, fetchRecipeLibrary, type RecipeItem } from '@/api/recipes'
import { saveCookingDraft } from '@/utils/cookingDraft'
import { navBack } from '@/composables/useNavBar'
import { useWishlist } from '@/composables/useWishlist'
import { toastError } from '@/utils/toast'

definePage({
  name: 'recipe-library',
  layout: 'default',
  style: { navigationStyle: 'custom', navigationBarTitleText: '锅仔藏菜阁' },
})

const router = useRouter()
const { isFav, toggle, syncFromServer } = useWishlist()
function keyOf(r: RecipeItem) {
  return String(r.id ?? r.name)
}
const loading = ref(true)
const loadingMore = ref(false)
const finished = ref(false)          // 已加载全部
const recipes = ref<RecipeItem[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const keyword = ref('')
const sourceFilter = ref<'ALL' | 'AI' | 'LOCAL'>('ALL')
const timeFilter = ref<'ALL' | 'FAST' | 'SLOW'>('ALL')
const moodFilter = ref<string>('ALL')
/** 心情筛选面板数据源（后端全表聚合的标签，失败静默不影响列表） */
const moodTags = ref<string[]>([])

const SOURCE_OPTIONS = [
  { value: 'ALL', label: '全部' },
  { value: 'AI', label: '锅仔新创' },
  { value: 'LOCAL', label: '阁藏经典' },
] as const
const TIME_OPTIONS = [
  { value: 'ALL', label: '不限时长' },
  { value: 'FAST', label: '快手 ≤30分' },
  { value: 'SLOW', label: '慢工 >30分' },
] as const

const hasFilters = computed(() =>
  !!keyword.value.trim() || sourceFilter.value !== 'ALL' || timeFilter.value !== 'ALL' || moodFilter.value !== 'ALL',
)

// ---------- 场景合辑（借鉴竞品「宝盒」：筛选即合辑，点开直达沉浸流，免运营） ----------
const COLLECTIONS = [
  { emoji: '⚡', label: '快手下饭', sub: '30 分钟内端上桌', time: 'FAST', mood: 'ALL' },
  { emoji: '🍲', label: '汤羹暖胃', sub: '温柔一锅', time: 'ALL', mood: '想家' },
  { emoji: '🎉', label: '宴客硬菜', sub: '撑得住场面', time: 'ALL', mood: '期待' },
  { emoji: '🌙', label: '深夜治愈', sub: '一个人也要好好吃', time: 'ALL', mood: '低落' },
  { emoji: '🍚', label: '满足家常', sub: '踏实的熟悉味', time: 'ALL', mood: '满足' },
] as const
const activeCollection = ref('')
/** 应用合辑 = 一次点按预设好整套筛选（关键词清空），沉浸流直接开逛 */
function applyCollection(col: (typeof COLLECTIONS)[number]) {
  activeCollection.value = col.label
  keyword.value = ''
  sourceFilter.value = 'ALL'
  timeFilter.value = col.time
  moodFilter.value = col.mood
  loadPage(true)
}
/** 任何手动筛选调整都退出合辑高亮（合辑只是预设入口，不锁定状态） */
watch([sourceFilter, timeFilter, moodFilter], () => {
  const matched = COLLECTIONS.find(c => c.time === timeFilter.value && c.mood === moodFilter.value && sourceFilter.value === 'ALL')
  activeCollection.value = matched?.label || ''
})

/** 路由 query 可能被编码多层（wot-ui router + 小程序各编一次），循环解到没有 %XX 为止 */
function safeDecodeParam(v: string): string {
  let out = v
  for (let i = 0; i < 3; i++) {
    if (!/%[0-9a-fA-F]{2}/.test(out)) break
    try {
      const next = decodeURIComponent(out)
      if (next === out) break
      out = next
    }
    catch { break }
  }
  return out
}

/** 支持外部入口带预设进入：冰箱页「现有食材可做」带食材名，干饭人格带心情，Dietary 页可带 time */
onLoad((query) => {
  const kw = typeof query?.keyword === 'string' && query.keyword ? safeDecodeParam(query.keyword) : ''
  if (kw) {
    keyword.value = kw
    activeCollection.value = ''
  }
  if (typeof query?.mood === 'string' && query.mood) {
    moodFilter.value = safeDecodeParam(query.mood)
    activeCollection.value = ''
  }
  if (query?.time === 'FAST' || query?.time === 'SLOW')
    timeFilter.value = query.time
  // 首屏必须自己拉一次：loading 初始为 true 会把 onShow 的加载守卫拦下来，
  // 此前只有 onLoad 设筛选、onShow 又被守卫跳过，导致不点搜索/筛选永远停在加载态
  loadPage(true)
})

// ---------- 沉浸竖滑流（抖音式） ----------
/** 当前卡片下标；滑到距尾部 3 张内自动翻页，滑动浏览与分页懒加载无缝衔接 */
const current = ref(0)
function onSwiperChange(e: any) {
  current.value = e?.detail?.current ?? 0
  if (!finished.value && !loadingMore.value && current.value >= recipes.value.length - 3)
    loadPage(false)
}

/** 组装当前筛选的请求参数（筛选全部下推后端） */
function buildParams(resetPage: number) {
  return {
    page: resetPage - 1,
    size,
    keyword: keyword.value.trim() || undefined,
    source: sourceFilter.value === 'ALL' ? undefined : sourceFilter.value,
    time: timeFilter.value === 'ALL' ? undefined : timeFilter.value,
    mood: moodFilter.value === 'ALL' ? undefined : moodFilter.value,
  }
}

/** 请求序号守卫：筛选快速切换/触底并发时只让最后一次响应生效 */
let reqSeq = 0

/**
 * 分页加载：reset=true 重建列表（首屏/筛选变化），否则触底追加下一页。
 * 后端分页 + SQL 筛选，不再一次性拉全量。
 */
async function loadPage(reset: boolean) {
  if (reset) {
    page.value = 1
    finished.value = false
    loading.value = true
    current.value = 0 // 换筛选回到第一张
  }
  else {
    if (loadingMore.value || finished.value || loading.value) return
    loadingMore.value = true
    page.value += 1
  }
  const seq = ++reqSeq
  const targetPage = page.value
  try {
    const res = await fetchRecipeLibrary(buildParams(targetPage))
    if (seq !== reqSeq) return // 已被更新的请求取代，丢弃
    if (reset) {
      recipes.value = res.list
    }
    else {
      recipes.value = [...recipes.value, ...res.list]
    }
    total.value = res.total
    if (!res.list.length || res.list.length < size) finished.value = true
  }
  catch (e: any) {
    if (seq !== reqSeq) return
    if (reset) toastError(e, '藏菜阁打了个盹，再试试')
    else page.value = Math.max(1, page.value - 1) // 加载失败回退页码，可再次触底重试
  }
  finally {
    if (seq === reqSeq) {
      loading.value = false
      loadingMore.value = false
    }
  }
}

/** 筛选变化回到第 1 页重建列表 */
function onFilterChange() {
  loadPage(true)
}

/** 键盘确认搜索：跳过防抖等待立即请求 */
function onSearchConfirm() {
  if (keywordTimer) {
    clearTimeout(keywordTimer)
    keywordTimer = null
  }
  loadPage(true)
}

/** 关键词防抖搜索（本地输入即时，400ms 后下推后端） */
let keywordTimer: ReturnType<typeof setTimeout> | null = null
let skipKeywordWatch = false
watch(keyword, () => {
  if (skipKeywordWatch) {
    skipKeywordWatch = false
    return
  }
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    keywordTimer = null
    loadPage(true)
  }, 400)
})
onUnmounted(() => {
  if (keywordTimer) clearTimeout(keywordTimer)
})

/** 触底加载已由竖滑流的滑动翻页承接（页面 overflow:hidden 不滚动，onReachBottom 不会触发） */

/** 心情筛选面板：低频条件收纳（PRD §6.6），点选即时生效沿用原有提交语义 */
const filterPanelOpen = ref(false)
const moodSummary = computed(() => moodFilter.value === 'ALL' ? '' : moodFilter.value)
function pickMood(m: string) {
  if (moodFilter.value === m) return
  moodFilter.value = m
  onFilterChange()
}

/** 图片加载失败兜底：网络失败时清空 URL，模板回落到锅仔插画 */
function onImgError(r: RecipeItem) {
  if (r.image) r.image = ''
}

onShow(async () => {
  syncFromServer()
  fetchMoodTags().then(tags => { moodTags.value = tags }).catch(() => {})
  if (recipes.value.length || loading.value)
    return
  loadPage(true)
})

function resetFilters() {
  if (keywordTimer) {
    clearTimeout(keywordTimer)
    keywordTimer = null
  }
  skipKeywordWatch = keyword.value !== '' // 置空会触发防抖 watch，跳过避免重复请求
  keyword.value = ''
  sourceFilter.value = 'ALL'
  timeFilter.value = 'ALL'
  moodFilter.value = 'ALL'
  loadPage(true)
}

function openCook(r: RecipeItem) {
  saveCookingDraft(r, '满足')
  router.push({ name: 'cooking' })
}
</script>

<template>
  <view class="library mrc-hero">
    <wd-navbar title="锅仔藏菜阁" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <view class="library-search">
      <wd-icon name="search" size="32rpx" color="var(--mrc-text-sub)" />
      <input
        v-model="keyword"
        class="library-search__field"
        placeholder="报个菜名或食材，锅仔去阁里翻，比如「番茄」"
        placeholder-class="library-search__placeholder"
        confirm-type="search"
        @confirm="onSearchConfirm"
      />
      <text v-if="keyword" class="library-search__clear" @click="keyword = ''">×</text>
    </view>

    <!-- 场景合辑：一次点按预设好筛选，直接开逛（借鉴竞品宝盒，用筛选预设实现，免运营） -->
    <scroll-view class="lib-collections" scroll-x>
      <view class="lib-collections__row">
        <view
          v-for="col in COLLECTIONS"
          :key="col.label"
          class="lib-col-card"
          :class="{ 'lib-col-card--active': activeCollection === col.label }"
          role="button"
          :aria-label="`打开合辑：${col.label}`"
          @click="applyCollection(col)"
        >
          <text class="lib-col-card__emoji">{{ col.emoji }}</text>
          <text class="lib-col-card__name">{{ col.label }}</text>
          <text class="lib-col-card__sub">{{ col.sub }}</text>
        </view>
      </view>
    </scroll-view>

    <scroll-view class="library-chips" scroll-x>
      <view class="library-chips__row">
        <view
          v-for="opt in SOURCE_OPTIONS"
          :key="opt.value"
          class="library-chip"
          :class="{ 'library-chip--active': sourceFilter === opt.value }"
          role="button"
          @click="sourceFilter = opt.value; onFilterChange()"
        >{{ opt.label }}</view>
        <view
          v-for="opt in TIME_OPTIONS"
          :key="opt.value"
          class="library-chip"
          :class="{ 'library-chip--active': timeFilter === opt.value }"
          role="button"
          @click="timeFilter = opt.value; onFilterChange()"
        >{{ opt.label }}</view>
        <!-- 心情筛选入口：低频条件收纳进底部面板（PRD §6.6），已选时高亮并显示摘要 -->
        <view
          class="library-chip library-chip--filter"
          :class="{ 'library-chip--active': !!moodSummary }"
          role="button"
          :aria-label="moodSummary ? `已选心情 ${moodSummary}，点按修改` : '打开心情筛选'"
          @click="filterPanelOpen = true"
        >{{ moodSummary ? `心情 · ${moodSummary}` : '心情筛选 ⌄' }}</view>
      </view>
    </scroll-view>

    <view class="library-count">
      <text>阁里共藏 {{ total }} 道菜</text>
      <text v-if="hasFilters" class="library-count__reset" @click="resetFilters">清空筛选</text>
    </view>

    <view v-if="loading" class="library-empty"><text>锅仔正在阁里翻箱倒柜…</text></view>
    <view v-else-if="!recipes.length" class="library-empty">
      <image :src="`${STATIC_BASE_URL}/static/guozai/action_08_peek.png`" mode="aspectFit" />
      <text>阁里没翻到这道菜，换个词让锅仔再找找</text>
    </view>

    <!-- 沉浸竖滑流：全屏海报卡上下滑切换，滑近尾部自动翻页 -->
    <swiper
      v-else
      class="library-swiper"
      vertical
      :current="current"
      @change="onSwiperChange"
    >
      <swiper-item v-for="(r, index) in recipes" :key="r.id || r.name" class="lib-slide">
        <!-- 只渲染当前卡与相邻卡，避免整列表全屏图片带来的渲染压力 -->
        <template v-if="Math.abs(index - current) <= 1">
          <view class="lib-card" role="button" :aria-label="`查看${r.name}做法`">
            <image
              class="lib-card__img"
              :src="r.image || `${STATIC_BASE_URL}/static/guozai/action_01_bowl.png`"
              mode="aspectFill"
              @error="onImgError(r)"
            />
            <view class="lib-card__mask" />
            <view class="lib-card__info">
              <view class="lib-card__title-row">
                <text class="lib-card__name">{{ r.name }}</text>
                <text class="lib-card__src">{{ r.source === 'AI' ? '锅仔新创' : '阁藏经典' }}</text>
              </view>
              <text v-if="r.description" class="lib-card__desc">{{ r.description }}</text>
              <view class="lib-card__meta">
                <text v-if="r.cookingTime" class="lib-card__tag">{{ r.cookingTime }} 分钟</text>
                <text v-if="r.difficulty" class="lib-card__tag">{{ r.difficulty }}</text>
                <text class="lib-card__tag">上下滑动换一道</text>
              </view>
              <view class="lib-card__cta" role="button" :aria-label="`查看${r.name}做法`" @click.stop="openCook(r)">
                看做法 ›
              </view>
            </view>
            <!-- 悬浮收藏：竖排在右侧，抖音式交互位 -->
            <view
              class="lib-fav"
              :class="{ 'is-on': isFav(keyOf(r)) }"
              role="button"
              :aria-label="isFav(keyOf(r)) ? '取消收藏' : '加入想吃清单'"
              @click.stop="toggle(keyOf(r))"
            >♥</view>
          </view>
        </template>
        <view v-else class="lib-card lib-card--placeholder" />
      </swiper-item>
    </swiper>

    <!-- 加载状态浮层：不占布局，叠在滑动流上 -->
    <view v-if="!loading && recipes.length" class="library-toast" :class="{ 'is-visible': loadingMore || finished }">
      <text>{{ loadingMore ? '锅仔踮脚去够下一页…' : finished ? `阁底翻完啦，共 ${total} 道菜` : '' }}</text>
    </view>

    <!-- 心情筛选面板：低频条件收纳（PRD §6.6），点选即时生效与原提交语义一致 -->
    <wd-popup v-model="filterPanelOpen" position="bottom" :safe-area-inset-bottom="true" custom-style="border-radius: 40rpx 40rpx 0 0; background: var(--mrc-surface);">
      <view class="filter-panel">
        <view class="filter-panel__head">
          <text class="filter-panel__title">按心情逛</text>
          <text class="filter-panel__done" role="button" @click="filterPanelOpen = false">完成</text>
        </view>
        <view v-if="moodTags.length" class="filter-panel__chips">
          <view
            class="library-chip"
            :class="{ 'library-chip--active': moodFilter === 'ALL' }"
            role="button"
            @click="pickMood('ALL')"
          >全部心情</view>
          <view
            v-for="m in moodTags"
            :key="m"
            class="library-chip"
            :class="{ 'library-chip--active': moodFilter === m }"
            role="button"
            @click="pickMood(m)"
          >{{ m }}</view>
        </view>
        <view v-else class="filter-panel__empty">心情标签还在积累中，先用搜索找找吧</view>
      </view>
    </wd-popup>
  </view>
</template>

<style lang="scss" scoped>
/* 页面固定高度 flex 布局：搜索/筛选占头部，竖滑流撑满剩余空间（页面本身不滚动） */
.library { height: 100vh; display: flex; flex-direction: column; padding: 0 32rpx calc(20rpx + env(safe-area-inset-bottom)); box-sizing: border-box; overflow: hidden; }
.library-search { display: flex; align-items: center; gap: 16rpx; height: 88rpx; padding: 0 24rpx; margin: 8rpx 0 20rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 999rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.library-search__field { flex: 1; font-size: 28rpx; color: var(--mrc-text-deep); text-align: left; }
.library-search__placeholder { color: var(--mrc-text-light); }
.library-search__clear { width: 44rpx; height: 44rpx; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: var(--mrc-surface-2); color: var(--mrc-text-sub); font-size: 34rpx; }

/* 场景合辑：参考竞品宝盒的「合集卡」观感，主题化——竖版小卡横滑 */
.lib-collections { white-space: nowrap; margin-bottom: 14rpx; }
.lib-collections__row { display: inline-flex; gap: 14rpx; padding: 4rpx 2rpx; }
.lib-col-card {
  flex: 0 0 auto;
  width: 168rpx;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  padding: 16rpx 18rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 22rpx;
  background: var(--mrc-surface);
  box-shadow: var(--mrc-shadow-soft);
  transition: all .15s ease;
}
.lib-col-card--active { border-color: transparent; background: var(--mrc-accent-soft); }
.lib-col-card__emoji { font-size: 34rpx; line-height: 1.2; }
.lib-col-card__name { color: var(--mrc-text-deep); font-size: 24rpx; font-weight: 800; }
.lib-col-card__sub { color: var(--mrc-text-light); font-size: 18rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lib-col-card--active .lib-col-card__sub { color: var(--mrc-accent); }

.library-chips { white-space: nowrap; margin-bottom: 14rpx; }
.library-chips__row { display: inline-flex; gap: 14rpx; padding: 4rpx 2rpx; }
.library-chip { flex: 0 0 auto; padding: 12rpx 26rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 999rpx; background: var(--mrc-surface); color: var(--mrc-text-sub); font-size: 24rpx; font-weight: 700; transition: all .15s ease; }
.library-chip--active { color: #fff; background: var(--mrc-primary-grad); border-color: transparent; box-shadow: var(--mrc-shadow-coral); }

.library-count { display: flex; align-items: center; justify-content: space-between; padding: 4rpx 6rpx 18rpx; color: var(--mrc-text-sub); font-size: 24rpx; }
.library-count__reset { color: var(--mrc-accent); font-weight: 700; }

.library-empty { display: flex; flex-direction: column; align-items: center; gap: 20rpx; padding: 120rpx 0; color: var(--mrc-text-sub); font-size: 26rpx; }
.library-empty image { width: 160rpx; height: 160rpx; opacity: .8; }
.library-more { display: flex; justify-content: center; padding: 24rpx 0 8rpx; color: var(--mrc-text-light); font-size: 24rpx; }

/* ---------- 沉浸竖滑流（抖音式） ---------- */
.library-swiper { flex: 1; min-height: 0; border-radius: 28rpx; overflow: hidden; background: var(--mrc-surface-2); }
.lib-slide { height: 100%; }
.lib-card { position: relative; width: 100%; height: 100%; overflow: hidden; }
.lib-card--placeholder { background: var(--mrc-surface-2); }
.lib-card__img { position: absolute; inset: 0; width: 100%; height: 100%; background: var(--mrc-surface-2); }
/* 底部渐变遮罩：信息区可读性（无论图片亮暗） */
.lib-card__mask { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(44, 24, 16, 0) 42%, rgba(44, 24, 16, .42) 62%, rgba(44, 24, 16, .82) 100%); }
.lib-card__info { position: absolute; left: 0; right: 0; bottom: 0; display: flex; flex-direction: column; gap: 12rpx; padding: 28rpx 28rpx calc(30rpx + env(safe-area-inset-bottom)); }
.lib-card__title-row { display: flex; align-items: center; gap: 14rpx; }
.lib-card__name { color: #fff; font-size: 44rpx; font-weight: 800; letter-spacing: 1rpx; text-shadow: 0 2rpx 12rpx rgba(44, 24, 16, .5); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lib-card__src { flex: 0 0 auto; padding: 5rpx 16rpx; border-radius: 999rpx; background: var(--mrc-accent); color: #fff; font-size: 19rpx; font-weight: 800; }
.lib-card__desc { color: rgba(255, 255, 255, .92); font-size: 24rpx; line-height: 1.5; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; line-clamp: 2; }
.lib-card__meta { display: flex; flex-wrap: wrap; gap: 10rpx; }
.lib-card__tag { padding: 6rpx 18rpx; border-radius: 999rpx; background: rgba(255, 255, 255, .18); color: rgba(255, 255, 255, .92); font-size: 19rpx; font-weight: 600; }
.lib-card__cta {
  align-self: flex-start;
  display: flex;
  align-items: center;
  min-height: 80rpx;
  margin-top: 6rpx;
  padding: 0 40rpx;
  border-radius: 999rpx;
  background: var(--mrc-primary-grad);
  color: #fff;
  font-size: 28rpx;
  font-weight: 800;
  box-shadow: var(--mrc-shadow-coral);
  transition: transform .15s ease;
}
.lib-card__cta:active { transform: scale(.95); }

/* 悬浮收藏：右侧竖排（抖音式交互位），白描边玻璃感 */
.lib-fav {
  position: absolute;
  right: 24rpx;
  bottom: 220rpx;
  width: 92rpx;
  height: 92rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 44rpx;
  color: #fff;
  background: rgba(255, 255, 255, .18);
  border: 2rpx solid rgba(255, 255, 255, .55);
  backdrop-filter: blur(6rpx);
  transition: transform .15s ease, color .15s ease, background-color .15s ease;
}
.lib-fav.is-on { color: var(--mrc-primary-deep); background: var(--mrc-surface); border-color: transparent; }
.lib-fav:active { transform: scale(.86); }

/* 加载提示浮层：叠在滑动流底部，不占布局 */
.library-toast {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(30rpx + env(safe-area-inset-bottom));
  display: flex;
  justify-content: center;
  pointer-events: none;
  opacity: 0;
  transition: opacity .2s ease;
}
.library-toast.is-visible { opacity: 1; }
.library-toast text { padding: 10rpx 26rpx; border-radius: 999rpx; background: rgba(44, 24, 16, .6); color: #fff; font-size: 22rpx; }

/* 筛选入口 chip：与主分类 chip 同规格，已选时珊瑚高亮 */
.library-chip--filter { border-style: dashed; }
.library-chip--filter.library-chip--active { border-style: solid; }

/* 心情筛选底部面板 */
.filter-panel { padding: 30rpx 32rpx calc(30rpx + env(safe-area-inset-bottom)); }
.filter-panel__head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 22rpx; }
.filter-panel__title { color: var(--mrc-text-strong); font-size: 31rpx; font-weight: 800; }
.filter-panel__done { padding: 10rpx 30rpx; border-radius: 999rpx; background: var(--mrc-primary-grad); color: #fff; font-size: 24rpx; font-weight: 700; }
.filter-panel__done:active { opacity: .8; }
.filter-panel__chips { display: flex; flex-wrap: wrap; gap: 14rpx; }
.filter-panel__chips .library-chip { padding: 14rpx 30rpx; }
.filter-panel__empty { padding: 30rpx 0; color: var(--mrc-text-light); font-size: 24rpx; text-align: center; }
</style>
