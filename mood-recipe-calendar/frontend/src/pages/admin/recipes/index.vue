<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { adminRecipeList, adminRecipeStats, adminRecipeDetail, adminRecipeDelete, adminRecipeGenImage, adminRecipeBatchImage, adminGetGenerationConfig, adminUpdateGenerationConfig, adminRunGenerationNow, type AdminRecipe, type AdminStats, type GenerationConfig } from '@/api/admin'
import { resolveAssetUrl } from '@/api/request'
import { navBack } from '@/composables/useNavBar'
import { toast, toastError, toastSuccess } from '@/utils/toast'

definePage({
  name: 'admin-recipes',
  layout: 'default',
  style: { navigationStyle: 'custom', navigationBarTitleText: '菜谱管理' },
})

const router = useRouter()

const stats = ref<AdminStats | null>(null)
const list = ref<AdminRecipe[]>([])
const loading = ref(false)
const page = ref(1)          // wd-pagination 为 1 起始，请求时换算成后端的 0 起始
const total = ref(0)
const size = 20
const keyword = ref('')
const sourceFilter = ref<'ALL' | 'AI' | 'LOCAL'>('ALL')
const onlyPlaceholder = ref(false)
const genConfig = ref<GenerationConfig | null>(null)
const runningGen = ref(false)
const generating = ref<Record<number, boolean>>({})  // 各菜谱配图进行中状态

const batchCount = ref(10)        // 批量补图数量
const batchRunning = ref(false)   // 批量补图进行中

const SOURCE_OPTIONS = [
  { value: 'ALL', label: '全部来源' },
  { value: 'AI', label: 'AI 生成' },
  { value: 'LOCAL', label: '本地种子' },
] as const

async function loadStats() {
  try {
    stats.value = await adminRecipeStats()
  } catch (e: any) {
    // 非管理员会 403，直接提示后返回
    toastError(e.message || '无权限')
    setTimeout(() => router.back(), 1500)
  }
}

async function loadList() {
  if (loading.value) return
  loading.value = true
  try {
    const res = await adminRecipeList({
      page: page.value - 1,
      size,
      keyword: keyword.value.trim() || undefined,
      source: sourceFilter.value === 'ALL' ? undefined : sourceFilter.value,
      placeholderImage: onlyPlaceholder.value || undefined,
    })
    list.value = res.list
    total.value = res.total
  } catch (e: any) {
    toastError(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 搜索/筛选回到第 1 页 */
function onSearch() {
  page.value = 1
  loadList()
}

function onFilterChange() {
  page.value = 1
  loadList()
}

function goEdit(item: AdminRecipe) {
  router.push({ name: 'admin-recipe-edit', params: { id: String(item.id) }, query: { id: String(item.id) } })
}

/** 缺图口径与后端一致：无图 / /static 占位 / doubaocdn 过期短链 */
function needsImage(img: string) {
  return !img || img.startsWith('/static/') || img.includes('doubaocdn.com')
}

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

/**
 * 配图闭环：POST 触发异步生图（Agnes 文生图→COS 转存→回写）→ 轮询详情取回真实图 URL。
 * 生图 30-90s 超过请求超时，因此不在请求里等结果；轮询 5s × 20 次（约 100s）兜底。
 */
async function genImage(item: AdminRecipe) {
  if (generating.value[item.id]) return
  generating.value[item.id] = true
  try {
    const res = await adminRecipeGenImage(item.id)
    if (!res.started && res.image) {
      item.image = res.image
      toastSuccess('该菜谱已有配图')
      return
    }
    toast('AI 配图生成中，约需 1 分钟')
    for (let i = 0; i < 20; i++) {
      await sleep(5000)
      const detail = await adminRecipeDetail(item.id).catch(() => null)
      if (detail && !needsImage(detail.image)) {
        item.image = detail.image
        toastSuccess('配图生成完成')
        loadStats()
        return
      }
    }
    toast('生成耗时较长，稍后翻页或重新搜索即可看到配图')
  } catch (e: any) {
    toastError(e.message || '配图生成失败')
  } finally {
    generating.value[item.id] = false
  }
}

/** 删除属于不可恢复操作，必须二次确认 */
function askDelete(item: AdminRecipe) {
  uni.showModal({
    title: '删除菜谱',
    content: `确定删除「${item.name}」？删除后不可恢复。`,
    confirmText: '删除',
    confirmColor: '#D94841',
    success: (result) => {
      if (result.confirm) doDelete(item)
    },
  })
}

async function doDelete(item: AdminRecipe) {
  try {
    await adminRecipeDelete(item.id)
    toastSuccess('已删除')
    list.value = list.value.filter(r => r.id !== item.id)
    total.value = Math.max(0, total.value - 1)
    loadStats()
    // 当前页删空且不是第一页时，回退一页
    if (!list.value.length && page.value > 1) {
      page.value -= 1
    }
    loadList()
  } catch (e: any) {
    toastError(e.message || '删除失败')
  }
}

async function loadGenConfig() {
  try {
    genConfig.value = await adminGetGenerationConfig()
  } catch (e) { /* 静默 */ }
}

async function toggleEnabled() {
  if (!genConfig.value) return
  try {
    const res = await adminUpdateGenerationConfig({ enabled: !genConfig.value.enabled })
    genConfig.value = { ...genConfig.value, ...res }
  } catch (e: any) {
    toastError(e.message || '保存失败')
  }
}

async function changeCount(delta: number) {
  if (!genConfig.value) return
  const next = Math.max(1, Math.min(30, genConfig.value.dailyCount + delta))
  try {
    const res2 = await adminUpdateGenerationConfig({ dailyCount: next })
    genConfig.value = { ...genConfig.value, ...res2 }
  } catch (e: any) {
    toastError(e.message || '保存失败')
  }
}

async function runNow() {
  if (runningGen.value) return
  runningGen.value = true
  try {
    const res = await adminRunGenerationNow()
    uni.showModal({ title: '已开始', content: res.message, showCancel: false, confirmText: '好的' })
    await loadGenConfig()
    await loadStats()
    await loadList()
  } catch (e: any) {
    toastError(e.message || '生成失败')
  } finally {
    runningGen.value = false
  }
}

/** 批量补图数量调整（1~100） */
function batchChangeCount(delta: number) {
  batchCount.value = Math.max(1, Math.min(100, batchCount.value + delta))
}

/**
 * 批量生成配图：后端按缺图口径取前 batchCount 道（已有真实图的自动跳过），
 * 逐道异步补拍，接口立即返回已排队条数。生图 30-90s 为后台异步，稍后刷新即可看到配图。
 */
async function batchImages() {
  if (batchRunning.value) return
  batchRunning.value = true
  try {
    const res = await adminRecipeBatchImage(batchCount.value)
    toastSuccess(`已为 ${res.queued} 道缺图菜谱排队生成配图`)
    await loadStats()
    await loadList()
  } catch (e: any) {
    toastError(e.message || '批量生成失败')
  } finally {
    batchRunning.value = false
  }
}

onShow(() => {
  loadStats()
  loadList()
  loadGenConfig()
})
</script>

<template>
  <view class="admin-page">
    <wd-navbar title="菜谱管理中心" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <view class="admin-content">
    <!-- 统计卡片 -->
    <view class="stats-row" v-if="stats">
      <view class="stat-card">
        <text class="stat-num">{{ stats.total }}</text>
        <text class="stat-label">总菜谱</text>
      </view>
      <view class="stat-card warn">
        <text class="stat-num">{{ stats.placeholderImage }}</text>
        <text class="stat-label">占位图</text>
      </view>
      <view class="stat-card">
        <text class="stat-num">{{ stats.ai }}</text>
        <text class="stat-label">AI 生成</text>
      </view>
      <view class="stat-card">
        <text class="stat-num">{{ stats.local }}</text>
        <text class="stat-label">本地种子</text>
      </view>
    </view>

    <!-- 定时任务配置 -->
    <view class="gen-card" v-if="genConfig">
      <view class="gen-header">
        <text class="gen-title">每日自动生成</text>
        <switch :checked="genConfig.enabled" @change="toggleEnabled" color="#EF5A3C" />
      </view>
      <view class="gen-row">
        <text class="gen-label">每天生成数量</text>
        <view class="gen-count">
          <text class="gen-btn" @click="changeCount(-1)">−</text>
          <text class="gen-num">{{ genConfig.dailyCount }} 道</text>
          <text class="gen-btn" @click="changeCount(1)">＋</text>
        </view>
      </view>
      <view class="gen-row" v-if="genConfig.lastRunAt">
        <text class="gen-label">上次运行</text>
        <text class="gen-value">{{ genConfig.lastRunAt?.substring(0,16) }} · 入库 {{ genConfig.lastRunCount }} 道</text>
      </view>
      <view class="gen-row">
        <text class="gen-label">执行时间</text>
        <text class="gen-value">{{ genConfig.cron }}</text>
      </view>
      <button class="gen-run-btn" :disabled="runningGen" @click="runNow">
        {{ runningGen ? '生成中...' : '立即生成一次' }}
      </button>
    </view>

    <!-- 批量生成配图 -->
    <view class="gen-card" v-if="stats">
      <view class="gen-header">
        <text class="gen-title">批量生成配图</text>
        <text class="gen-sub">已有真实图的自动跳过</text>
      </view>
      <view class="gen-row">
        <text class="gen-label">处理缺图数量</text>
        <view class="gen-count">
          <text class="gen-btn" @click="batchChangeCount(-1)">−</text>
          <text class="gen-num">{{ batchCount }} 道</text>
          <text class="gen-btn" @click="batchChangeCount(1)">＋</text>
        </view>
      </view>
      <view class="gen-row">
        <text class="gen-label">当前缺图</text>
        <text class="gen-value">{{ stats.placeholderImage }} 道</text>
      </view>
      <button class="gen-run-btn" :disabled="batchRunning" @click="batchImages">
        {{ batchRunning ? '排队中...' : `批量生成 ${batchCount} 道配图` }}
      </button>
    </view>

    <!-- 搜索栏 -->
    <view class="search-bar">
      <input
        v-model="keyword"
        class="search-input"
        placeholder="搜索菜名..."
        confirm-type="search"
        @confirm="onSearch"
      />
      <button class="search-btn" @click="onSearch">搜索</button>
    </view>

    <!-- 筛选栏 -->
    <view class="filter-bar">
      <view
        v-for="opt in SOURCE_OPTIONS"
        :key="opt.value"
        class="filter-chip pressable"
        :class="{ active: sourceFilter === opt.value }"
        :aria-label="`按${opt.label}筛选`"
        role="button"
        @click="sourceFilter = opt.value; onFilterChange()"
      >{{ opt.label }}</view>
      <view
        class="filter-chip pressable"
        :class="{ active: onlyPlaceholder }"
        role="button"
        aria-label="仅看占位图"
        @click="onlyPlaceholder = !onlyPlaceholder; onFilterChange()"
      >仅看占位图</view>
    </view>

    <!-- 列表 -->
    <view class="recipe-list">
      <view
        v-for="item in list"
        :key="item.id"
        class="recipe-card pressable"
        role="button"
        :aria-label="`编辑菜谱 ${item.name}`"
        @click="goEdit(item)"
      >
        <image
          v-if="item.image"
          :src="resolveAssetUrl(item.image)"
          class="recipe-img"
          mode="aspectFill"
        />
        <view v-else class="recipe-img placeholder">
          <text>无图</text>
        </view>
        <view class="recipe-info">
          <view class="recipe-name-row">
            <text class="recipe-name">{{ item.name }}</text>
            <text v-if="needsImage(item.image) && item.image" class="badge-warn">假图</text>
            <text v-else-if="needsImage(item.image)" class="badge-none">无图</text>
          </view>
          <text class="recipe-desc">{{ item.description || '暂无描述' }}</text>
          <view class="recipe-meta">
            <text class="meta-tag">{{ item.source || '-' }}</text>
            <text class="meta-tag">{{ item.cookingTime || '?' }}分钟</text>
            <text class="meta-tag">{{ item.difficulty || '-' }}</text>
          </view>
          <view class="recipe-actions">
            <view
              v-if="needsImage(item.image)"
              class="action-btn action-btn--primary"
              :class="{ 'is-busy': generating[item.id] }"
              role="button"
              :aria-label="`为${item.name}生成 AI 配图`"
              @click.stop="genImage(item)"
            >{{ generating[item.id] ? '配图中…' : '配图' }}</view>
            <view
              class="action-btn action-btn--danger"
              role="button"
              :aria-label="`删除${item.name}`"
              @click.stop="askDelete(item)"
            >删除</view>
          </view>
        </view>
      </view>

      <view v-if="loading" class="loading-tip">加载中...</view>
      <view v-else-if="!list.length && !loading" class="empty-tip">暂无数据</view>
    </view>

    <!-- 分页：后端真分页（筛选下推），total 为筛选后条数 -->
    <wd-pagination
      v-model="page"
      :total="total"
      :page-size="size"
      show-icon
      hide-if-one-page
      @change="loadList"
    />
    </view>
  </view>
</template>

<style scoped>
/* 与全局主题（theme.scss --mrc-* 令牌）统一：暖奶油底 + 珊瑚主色 + 暖褐文字 */
.admin-page {
  min-height: 100vh;
  min-height: 100dvh;
  box-sizing: border-box;
  font-family: var(--mrc-font-family);
  color: var(--mrc-text);
  background: var(--mrc-bg);
}
.admin-content {
  width: calc(100% - 48rpx);
  max-width: 820rpx;
  margin: 0 auto;
  padding: 20rpx 0 calc(52rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* 统一卡片基底：近白暖调表面 + 浅描边 + 上色柔和投影 */
.stat-card,
.gen-card,
.recipe-card,
.search-input {
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss);
  box-sizing: border-box;
}

/* ---------- 统计卡 ---------- */
.stats-row {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;
}
.stat-card {
  flex: 1;
  border-radius: 28rpx;
  padding: 22rpx 0;
  text-align: center;
}
.stat-card.warn {
  background: var(--mrc-surface-sun);
}
.stat-num {
  display: block;
  font-size: 36rpx;
  font-weight: 800;
  color: var(--mrc-text-strong);
}
.stat-card.warn .stat-num {
  color: var(--mrc-accent);
}
.stat-label {
  font-size: 21rpx;
  color: var(--mrc-text-sub);
}

/* ---------- 搜索 ---------- */
.search-bar {
  display: flex;
  gap: 16rpx;
  margin-bottom: 20rpx;
}
.search-input {
  flex: 1;
  min-width: 0;
  height: 88rpx;
  border-radius: 44rpx;
  padding: 0 30rpx;
  font-size: 28rpx;
  color: var(--mrc-text-deep);
}
.search-btn {
  margin: 0;
  height: 88rpx;
  padding: 0 38rpx;
  border-radius: 44rpx;
  background: var(--mrc-primary-grad);
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
  line-height: 88rpx;
  box-shadow: var(--mrc-shadow-coral), var(--mrc-gloss);
  transition: transform var(--mrc-motion-fast, 180ms ease-out);
}
.search-btn::after,
.gen-run-btn::after {
  border: none;
}
.search-btn:active,
.gen-run-btn:active {
  transform: scale(0.97);
}

/* ---------- 筛选 chip ---------- */
.filter-bar {
  display: flex;
  gap: 14rpx;
  margin-bottom: 22rpx;
  flex-wrap: wrap;
}
.filter-chip {
  padding: 14rpx 28rpx;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: var(--mrc-text-sub);
  transition: background-color 180ms ease, color 180ms ease;
}
.filter-chip.active {
  background: var(--mrc-accent-soft);
  border-color: transparent;
  color: var(--mrc-accent);
}

/* ---------- 菜谱列表 ---------- */
.recipe-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}
.recipe-card {
  display: flex;
  border-radius: 28rpx;
  overflow: hidden;
}
.recipe-img {
  width: 168rpx;
  height: 168rpx;
  flex-shrink: 0;
  background: var(--mrc-surface-2);
}
.recipe-img.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--mrc-text-light);
  font-size: 24rpx;
}
.recipe-info {
  flex: 1;
  min-width: 0;
  padding: 18rpx 22rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  overflow: hidden;
}
.recipe-name-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.recipe-name {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
}
.badge-warn,
.badge-none {
  flex: 0 0 auto;
  font-size: 20rpx;
  font-weight: 600;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
}
.badge-warn {
  background: var(--mrc-accent-soft);
  color: var(--mrc-accent);
}
.badge-none {
  background: var(--mrc-surface-2);
  color: var(--mrc-text-sub);
}
.recipe-desc {
  font-size: 24rpx;
  color: var(--mrc-text-sub);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recipe-meta {
  display: flex;
  gap: 12rpx;
}
.meta-tag {
  font-size: 20rpx;
  color: var(--mrc-text-sub);
  background: var(--mrc-surface-2);
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
}

/* 卡片内操作按钮：.stop 阻断卡片跳转；胶囊小按钮 + 软色底 */
.recipe-actions {
  display: flex;
  gap: 14rpx;
  margin-top: 4rpx;
}
.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 56rpx;
  padding: 0 26rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 700;
}
.action-btn--primary {
  background: var(--mrc-accent-soft);
  color: var(--mrc-accent);
}
.action-btn--primary.is-busy {
  opacity: 0.55;
  pointer-events: none;
}
.action-btn--danger {
  background: var(--mrc-color-danger-soft);
  color: var(--mrc-color-danger);
}

/* 分页条 */
.admin-content > .wd-pagination {
  margin-top: 28rpx;
}

.loading-tip, .empty-tip {
  text-align: center;
  padding: 48rpx;
  color: var(--mrc-text-sub);
  font-size: 26rpx;
}

/* ---------- 定时任务配置 ---------- */
.gen-card {
  border-radius: 28rpx;
  padding: 26rpx;
  margin-bottom: 24rpx;
}
.gen-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}
.gen-title {
  font-size: 30rpx;
  font-weight: 800;
  color: var(--mrc-text-deep);
}
.gen-sub {
  font-size: 21rpx;
  color: var(--mrc-text-sub);
  font-weight: 600;
}
.gen-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12rpx 0;
}
.gen-label {
  font-size: 26rpx;
  color: var(--mrc-text);
}
.gen-value {
  font-size: 24rpx;
  color: var(--mrc-text-sub);
}
.gen-count {
  display: flex;
  align-items: center;
  gap: 20rpx;
}
.gen-btn {
  width: 56rpx;
  height: 56rpx;
  line-height: 56rpx;
  text-align: center;
  background: var(--mrc-surface-2);
  border-radius: 50%;
  font-size: 32rpx;
  font-weight: 700;
  color: var(--mrc-accent);
}
.gen-num {
  font-size: 28rpx;
  font-weight: 800;
  color: var(--mrc-text-deep);
  min-width: 88rpx;
  text-align: center;
}
.gen-run-btn {
  margin-top: 20rpx;
  background: var(--mrc-primary-grad);
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
  border-radius: 44rpx;
  padding: 0;
  line-height: 88rpx;
  box-shadow: var(--mrc-shadow-coral), var(--mrc-gloss);
  transition: transform var(--mrc-motion-fast, 180ms ease-out);
}
.gen-run-btn[disabled] {
  opacity: 0.5;
}

/* 按压反馈（与其他页面一致） */
.pressable {
  transition: transform 180ms ease, opacity 180ms ease;
}
.pressable:active {
  transform: scale(0.97);
}
@media (prefers-reduced-motion: reduce) {
  .pressable,
  .search-btn,
  .gen-run-btn {
    transition: none;
  }
  .pressable:active,
  .search-btn:active,
  .gen-run-btn:active {
    transform: none;
  }
}
</style>
