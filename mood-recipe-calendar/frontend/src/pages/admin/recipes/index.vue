<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { adminRecipeList, adminRecipeStats, adminRecipeDetail, adminRecipeDelete, adminRecipeGenImage, adminRecipeBatchImage, adminRecipeSetImage, adminRecipeBatchSetImage, adminGetGenerationConfig, adminUpdateGenerationConfig, adminRunGenerationNow, type AdminRecipe, type AdminStats, type GenerationConfig } from '@/api/admin'
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
const batchMode = ref<'ai' | 'custom'>('ai')  // 批量配图模式：AI 生成 / 自定义 URL
const customText = ref('')        // 自定义批量：每行「菜名或ID | 图片URL」
const customRunning = ref(false)

const SOURCE_OPTIONS = [
  { value: 'ALL', label: '全部' },
  { value: 'AI', label: 'AI' },
  { value: 'LOCAL', label: '本地' },
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
 * force=true（重新配图）：已有真实图也重拍，轮询成功条件从「有图」变成「图变了」。
 */
async function genImage(item: AdminRecipe, force = false) {
  if (generating.value[item.id]) return
  generating.value[item.id] = true
  const imageBefore = item.image
  try {
    const res = await adminRecipeGenImage(item.id, force)
    if (!res.started && res.image) {
      item.image = res.image
      toastSuccess('该菜谱已有配图')
      return
    }
    toast(force ? '重新配图生成中，约需 1 分钟' : 'AI 配图生成中，约需 1 分钟')
    for (let i = 0; i < 20; i++) {
      await sleep(5000)
      const detail = await adminRecipeDetail(item.id).catch(() => null)
      const changed = detail && detail.image !== imageBefore && !needsImage(detail.image)
      if (changed) {
        item.image = detail!.image
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

/** 重新配图：覆盖现有封面（旧图不保留），二次确认防误触 */
function askRegenImage(item: AdminRecipe) {
  uni.showModal({
    title: '重新配图',
    content: `用最新生图 prompt 为「${item.name}」重拍封面？当前图片会被替换。`,
    confirmText: '重拍',
    success: (res) => {
      if (res.confirm) void genImage(item, true)
    },
  })
}

/** 单个自定义设图：弹输入框贴 URL，立即回写 */
function askSetImage(item: AdminRecipe) {
  uni.showModal({
    title: `自定义配图 · ${item.name}`,
    editable: true,
    placeholderText: '粘贴图片链接（http/https 开头）',
    success: (res) => {
      if (!res.confirm) return
      const url = (res.content || '').trim()
      if (!url) return
      void doSetImage(item, url)
    },
  })
}

async function doSetImage(item: AdminRecipe, url: string) {
  try {
    const res = await adminRecipeSetImage(item.id, url)
    item.image = res.image
    toastSuccess('配图已更新')
    loadStats()
  } catch (e: any) {
    toastError(e.message || '设置失败')
  }
}

/** 解析批量自定义文本：每行「菜名或ID | 图片URL」，也兼容「菜名或ID URL」空格分隔 */
function parseCustomLines(text: string): { key: string, imageUrl: string }[] {
  return text.split('\n')
    .map(l => l.trim())
    .filter(Boolean)
    .map((line) => {
      if (line.includes('|')) {
        const [key, url] = line.split('|')
        return key?.trim() && url?.trim() ? { key: key.trim(), imageUrl: url.trim() } : null
      }
      const m = line.match(/^(\S+)\s+(https?:\/\/\S+)$/)
      return m ? { key: m[1]!, imageUrl: m[2]! } : null
    })
    .filter((v): v is { key: string, imageUrl: string } => Boolean(v))
}

/** 批量自定义配图：整段粘贴 → 逐条解析提交 → 弹窗汇报成功/失败明细 */
async function submitCustomImages() {
  if (customRunning.value) return
  const items = parseCustomLines(customText.value)
  if (!items.length) {
    toastError('没有解析到有效行，检查格式：菜名或ID | 图片URL')
    return
  }
  customRunning.value = true
  try {
    const res = await adminRecipeBatchSetImage(items)
    const failText = res.failed.length
      ? `\n失败 ${res.failed.length} 条：\n` + res.failed.slice(0, 5).map(f => `${f.key}：${f.reason}`).join('\n')
      : ''
    uni.showModal({
      title: '批量配图完成',
      content: `成功更新 ${res.updated} 道${failText}`,
      showCancel: false,
      confirmText: '好的',
    })
    if (res.updated) {
      customText.value = ''
      await loadStats()
      await loadList()
    }
  } catch (e: any) {
    toastError(e.message || '批量设置失败')
  } finally {
    customRunning.value = false
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
      <!-- 统计条：一行四格，数字主导 -->
      <view v-if="stats" class="stats-row">
        <view class="stat-cell">
          <text class="stat-num">{{ stats.total }}</text>
          <text class="stat-label">总菜谱</text>
        </view>
        <view class="stat-cell stat-cell--warn">
          <text class="stat-num">{{ stats.placeholderImage }}</text>
          <text class="stat-label">缺图</text>
        </view>
        <view class="stat-cell">
          <text class="stat-num">{{ stats.ai }}</text>
          <text class="stat-label">AI 生成</text>
        </view>
        <view class="stat-cell">
          <text class="stat-num">{{ stats.local }}</text>
          <text class="stat-label">本地种子</text>
        </view>
      </view>

      <!-- 配图工作台：AI 批量 / 自定义 URL 双模式 -->
      <view v-if="stats" class="tool-card">
        <view class="tool-head">
          <text class="tool-title">配图工作台</text>
          <text class="tool-sub">缺图 {{ stats.placeholderImage }} 道</text>
        </view>
        <view class="mode-tabs">
          <view class="mode-tab" :class="{ active: batchMode === 'ai' }" role="tab" :aria-selected="batchMode === 'ai'" @click="batchMode = 'ai'">
            AI 批量生成
          </view>
          <view class="mode-tab" :class="{ active: batchMode === 'custom' }" role="tab" :aria-selected="batchMode === 'custom'" @click="batchMode = 'custom'">
            自定义图片
          </view>
        </view>

        <!-- AI 批量模式 -->
        <view v-if="batchMode === 'ai'" class="mode-body">
          <view class="tool-row">
            <text class="tool-label">处理缺图数量</text>
            <view class="stepper">
              <text class="stepper-btn" @click="batchChangeCount(-1)">−</text>
              <text class="stepper-num">{{ batchCount }} 道</text>
              <text class="stepper-btn" @click="batchChangeCount(1)">＋</text>
            </view>
          </view>
          <button class="tool-btn" :disabled="batchRunning" @click="batchImages">
            {{ batchRunning ? '排队中...' : `批量生成 ${batchCount} 道配图` }}
          </button>
          <text class="tool-hint">已有真实图的自动跳过；生图约 1 分钟/道，后台异步执行</text>
        </view>

        <!-- 自定义 URL 模式 -->
        <view v-else class="mode-body">
          <textarea
            v-model="customText"
            class="custom-input"
            :maxlength="-1"
            placeholder="每行一条：菜名或ID | 图片URL&#10;例：&#10;酱油焖饭 | https://cdn.example.com/soy-rice.jpg&#10;128 | https://cdn.example.com/2.jpg"
          />
          <button class="tool-btn" :disabled="customRunning" @click="submitCustomImages">
            {{ customRunning ? '提交中...' : '批量设置自定义配图' }}
          </button>
          <text class="tool-hint">按「ID 精确 → 菜名精确 → 菜名包含」依次匹配；失败条目会单独列出原因</text>
        </view>
      </view>

      <!-- 每日自动生成（紧凑） -->
      <view v-if="genConfig" class="tool-card">
        <view class="tool-head">
          <text class="tool-title">每日自动生成</text>
          <switch :checked="genConfig.enabled" @change="toggleEnabled" color="#EF5A3C" />
        </view>
        <view class="tool-row">
          <text class="tool-label">每天数量</text>
          <view class="stepper">
            <text class="stepper-btn" @click="changeCount(-1)">−</text>
            <text class="stepper-num">{{ genConfig.dailyCount }} 道</text>
            <text class="stepper-btn" @click="changeCount(1)">＋</text>
          </view>
        </view>
        <view class="tool-row" v-if="genConfig.lastRunAt">
          <text class="tool-label">上次运行</text>
          <text class="tool-value">{{ genConfig.lastRunAt?.substring(0, 16) }} · 入库 {{ genConfig.lastRunCount }} 道</text>
        </view>
        <view class="tool-row">
          <text class="tool-label">执行时间</text>
          <text class="tool-value">{{ genConfig.cron }}</text>
        </view>
        <button class="tool-btn tool-btn--ghost" :disabled="runningGen" @click="runNow">
          {{ runningGen ? '生成中...' : '立即生成一次' }}
        </button>
      </view>

      <!-- 搜索 + 筛选工具栏 -->
      <view class="toolbar">
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
        <view class="filter-bar">
          <view
            v-for="opt in SOURCE_OPTIONS"
            :key="opt.value"
            class="filter-chip"
            :class="{ active: sourceFilter === opt.value }"
            :aria-label="`按${opt.label}筛选`"
            role="button"
            @click="sourceFilter = opt.value; onFilterChange()"
          >{{ opt.label }}</view>
          <view
            class="filter-chip"
            :class="{ active: onlyPlaceholder }"
            role="button"
            aria-label="仅看缺图"
            @click="onlyPlaceholder = !onlyPlaceholder; onFilterChange()"
          >仅看缺图</view>
        </view>
      </view>

      <!-- 列表 -->
      <view class="recipe-list">
        <view v-for="item in list" :key="item.id" class="recipe-card">
          <view class="recipe-media" @click="goEdit(item)">
            <image
              v-if="item.image"
              :src="resolveAssetUrl(item.image)"
              class="recipe-img"
              mode="aspectFill"
            />
            <view v-else class="recipe-img recipe-img--empty">
              <text>无图</text>
            </view>
            <text v-if="needsImage(item.image) && item.image" class="recipe-flag">假图</text>
            <text v-else-if="needsImage(item.image)" class="recipe-flag">无图</text>
          </view>
          <view class="recipe-body" @click="goEdit(item)">
            <view class="recipe-name-row">
              <text class="recipe-name">{{ item.name }}</text>
              <text class="recipe-id">#{{ item.id }}</text>
            </view>
            <text class="recipe-desc">{{ item.description || '暂无描述' }}</text>
            <view class="recipe-meta">
              <text class="meta-tag">{{ item.source || '-' }}</text>
              <text class="meta-tag">{{ item.cookingTime || '?' }} 分钟</text>
              <text class="meta-tag">{{ item.difficulty || '-' }}</text>
            </view>
            <view class="recipe-actions">
              <view
                class="act-btn act-btn--primary"
                role="button"
                :aria-label="`为${item.name}自定义配图`"
                @click.stop="askSetImage(item)"
              >设图</view>
              <view
                v-if="needsImage(item.image)"
                class="act-btn"
                :class="{ 'is-busy': generating[item.id] }"
                role="button"
                :aria-label="`为${item.name}生成 AI 配图`"
                @click.stop="genImage(item)"
              >{{ generating[item.id] ? '生成中…' : 'AI 配图' }}</view>
              <view
                v-else
                class="act-btn"
                :class="{ 'is-busy': generating[item.id] }"
                role="button"
                :aria-label="`为${item.name}重新生成配图`"
                @click.stop="askRegenImage(item)"
              >{{ generating[item.id] ? '重拍中…' : '重新配图' }}</view>
              <view class="act-btn" role="button" :aria-label="`编辑${item.name}`" @click.stop="goEdit(item)">编辑</view>
              <view class="act-btn act-btn--danger" role="button" :aria-label="`删除${item.name}`" @click.stop="askDelete(item)">删除</view>
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

<style lang="scss" scoped>
/* finesse · register=product(工作流工具体) · mrc token 体系 · SOUL=4 SPECTACLE=1 DENSITY=7 */
.admin-page { min-height: 100vh; background: var(--mrc-bg); }
.admin-content { padding: 20rpx 28rpx calc(48rpx + env(safe-area-inset-bottom)); }

/* ===== 统计条 ===== */
.stats-row { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12rpx; }
.stat-cell {
  display: flex; flex-direction: column; align-items: center; gap: 4rpx;
  padding: 20rpx 8rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 20rpx;
  background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft);
}
.stat-cell--warn { background: linear-gradient(160deg, #fff4ec, #ffe6d8); border-color: rgba(217, 72, 65, 0.18); }
.stat-num { color: var(--mrc-text-strong); font-size: 34rpx; font-weight: 900; line-height: 1.1; }
.stat-cell--warn .stat-num { color: #D94841; }
.stat-label { color: var(--mrc-text-sub); font-size: 20rpx; }

/* ===== 工具卡（配图工作台 / 自动生成） ===== */
.tool-card {
  margin-top: 18rpx; padding: 24rpx 26rpx;
  border: 2rpx solid var(--mrc-border-light); border-radius: 24rpx;
  background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft);
}
.tool-head { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; }
.tool-title { color: var(--mrc-text-strong); font-size: 28rpx; font-weight: 800; }
.tool-sub { color: #D94841; font-size: 21rpx; font-weight: 700; }
.tool-row { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; margin-top: 18rpx; }
.tool-label { color: var(--mrc-text-sub); font-size: 24rpx; }
.tool-value { color: var(--mrc-text-deep); font-size: 23rpx; text-align: right; }
.mode-tabs {
  display: flex; gap: 8rpx; margin-top: 16rpx; padding: 6rpx;
  border-radius: 14rpx; background: var(--mrc-surface-2);
}
.mode-tab {
  flex: 1; display: flex; justify-content: center; padding: 12rpx 0;
  border-radius: 10rpx; color: var(--mrc-text-sub); font-size: 23rpx; font-weight: 700;
}
.mode-tab.active { background: var(--mrc-surface); color: var(--mrc-accent); box-shadow: var(--mrc-shadow-soft); }
.mode-body { margin-top: 4rpx; }
.stepper { display: flex; align-items: center; gap: 18rpx; }
.stepper-btn {
  display: flex; align-items: center; justify-content: center;
  width: 52rpx; height: 52rpx; border-radius: 14rpx;
  background: var(--mrc-surface-2); color: var(--mrc-text-deep); font-size: 30rpx; font-weight: 800;
}
.stepper-btn:active { transform: scale(0.92); }
.stepper-num { min-width: 90rpx; color: var(--mrc-text-strong); font-size: 26rpx; font-weight: 800; text-align: center; }
.tool-btn {
  width: 100%; height: 84rpx; margin: 20rpx 0 0; padding: 0;
  border: none; border-radius: 18rpx; line-height: 84rpx;
  background: var(--mrc-primary-grad); color: #fff; font-size: 26rpx; font-weight: 800;
  box-shadow: var(--mrc-shadow-coral);
}
.tool-btn:disabled { opacity: .6; }
.tool-btn--ghost { background: var(--mrc-surface-2); color: var(--mrc-text-deep); box-shadow: none; }
.custom-input {
  width: 100%; height: 220rpx; margin-top: 16rpx; padding: 18rpx 20rpx;
  border: 2rpx solid var(--mrc-border-light); border-radius: 16rpx;
  background: var(--mrc-surface-2); box-sizing: border-box;
  color: var(--mrc-text-deep); font-size: 23rpx; line-height: 1.6;
}
.tool-hint { display: block; margin-top: 12rpx; color: var(--mrc-text-light); font-size: 20rpx; line-height: 1.5; }

/* ===== 搜索 + 筛选 ===== */
.toolbar { margin-top: 18rpx; padding: 20rpx 24rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 24rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.search-bar { display: flex; gap: 14rpx; }
.search-input {
  flex: 1; height: 72rpx; padding: 0 24rpx;
  border: 2rpx solid var(--mrc-border-light); border-radius: 16rpx;
  background: var(--mrc-surface-2); box-sizing: border-box;
  color: var(--mrc-text-deep); font-size: 24rpx;
}
.search-btn {
  flex: 0 0 auto; width: 128rpx; height: 72rpx; margin: 0; padding: 0;
  border: none; border-radius: 16rpx; line-height: 72rpx;
  background: var(--mrc-primary-grad); color: #fff; font-size: 24rpx; font-weight: 800;
}
.filter-bar { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 16rpx; }
.filter-chip {
  padding: 8rpx 26rpx; border: 2rpx solid var(--mrc-border-light);
  border-radius: 999rpx; background: var(--mrc-surface-2);
  color: var(--mrc-text-sub); font-size: 22rpx; font-weight: 700;
}
.filter-chip.active { border-color: transparent; background: var(--mrc-accent-soft); color: var(--mrc-accent); }

/* ===== 列表卡片 ===== */
.recipe-list { display: flex; flex-direction: column; gap: 16rpx; margin-top: 18rpx; }
.recipe-card {
  display: flex; align-items: stretch; gap: 20rpx; padding: 20rpx;
  border: 2rpx solid var(--mrc-border-light); border-radius: 24rpx;
  background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft);
}
.recipe-media { position: relative; flex: 0 0 176rpx; width: 176rpx; align-self: flex-start; }
.recipe-img { width: 176rpx; height: 176rpx; border-radius: 18rpx; background: var(--mrc-surface-2); }
.recipe-img--empty { display: flex; align-items: center; justify-content: center; color: var(--mrc-text-light); font-size: 22rpx; }
.recipe-flag {
  position: absolute; top: 8rpx; left: 8rpx;
  padding: 2rpx 12rpx; border-radius: 8rpx;
  background: rgba(217, 72, 65, 0.9); color: #fff; font-size: 17rpx; font-weight: 800;
}
.recipe-body { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 8rpx; }
.recipe-name-row { display: flex; align-items: center; gap: 10rpx; }
.recipe-name { flex-shrink: 0; max-width: 320rpx; overflow: hidden; color: var(--mrc-text-strong); font-size: 29rpx; font-weight: 800; white-space: nowrap; text-overflow: ellipsis; }
.recipe-id { flex-shrink: 0; color: var(--mrc-text-light); font-size: 19rpx; }
.recipe-desc {
  display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; line-clamp: 2; overflow: hidden;
  color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.5;
}
.recipe-meta { display: flex; flex-wrap: wrap; gap: 8rpx; margin-top: 2rpx; }
.meta-tag { padding: 3rpx 14rpx; border-radius: 8rpx; background: var(--mrc-surface-2); color: var(--mrc-text-sub); font-size: 18rpx; }

/* 操作行：横贯内容区底部的四枚等宽按钮——层级统一、删除用危险软底色 */
.recipe-actions { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12rpx; margin-top: auto; padding-top: 14rpx; border-top: 2rpx dashed var(--mrc-border-light); }
.act-btn {
  display: flex; align-items: center; justify-content: center;
  height: 60rpx; border: none; border-radius: 14rpx;
  background: var(--mrc-surface-2); color: var(--mrc-text-deep);
  font-size: 22rpx; font-weight: 700;
  transition: transform .12s ease;
}
.act-btn:active { transform: scale(0.94); }
.act-btn--primary { background: var(--mrc-accent-soft); color: var(--mrc-accent); }
.act-btn.is-busy { opacity: .6; }
.act-btn--danger { background: #FDECEC; color: #D94841; }

.loading-tip, .empty-tip { padding: 60rpx 0; color: var(--mrc-text-light); font-size: 24rpx; text-align: center; }
</style>
