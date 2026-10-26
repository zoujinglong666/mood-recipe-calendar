<template>
  <view class="daily">
    <!-- 头部与全站统一：wd-navbar 透明底 + 左返回（24 个页面同一模式），刊头感下移为 hero 内容区 -->
    <wd-navbar title="今日三餐" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <!-- 加载 / 错误：与全站统一的锅仔状态组件 -->
    <LoadingState v-if="!plan && !error" text="锅仔正在为你搭配今天的三餐…" />
    <ErrorState v-else-if="error" :text="error" subtext="稍等一下再试，锅仔接着搭" @retry="load" />

    <template v-else-if="plan">
      <view class="daily-scroll">
        <!-- 刊头：大日期 + 星期 + 锅仔印章，暖桃光斑打底 -->
        <view class="hero">
          <view class="hero-text">
            <view class="hero-date">
              <text class="hero-date__day">{{ planDay }}</text>
              <text class="hero-date__week">{{ planWeek }}</text>
            </view>
            <text class="hero-sub">忌口与《中国居民膳食指南》，锅仔都替你想好了</text>
          </view>
          <view class="hero-stamp">
            <image class="hero-stamp__img guozai-breathe" :src="`${STATIC_BASE_URL}/static/guozai/action_01_bowl.png`" mode="aspectFit" />
          </view>
        </view>

        <!-- 今日营养达成：大类逐项打勾，缺项灰显；TIP 改规线引用块 -->
        <view v-if="plan.nutrition" class="nutri-card">
          <view class="nutri-label">今日营养达成</view>
          <view class="nutri-headline">{{ plan.nutrition.headline }}</view>
          <view class="nutri-grid">
            <view v-for="item in nutritionChecklist" :key="item.key" class="nutri-check" :class="{ 'is-miss': !item.done }">
              <text class="nutri-check__mark">{{ item.done ? '✓' : '○' }}</text>
              <text class="nutri-check__text">{{ item.label }}</text>
            </view>
          </view>
          <view v-if="plan.nutrition.tip" class="nutri-tip">
            <text class="nutri-tip__text">{{ plan.nutrition.tip }}</text>
            <text class="nutri-tip__tag">{{ plan.nutrition.goalTag || '均衡搭配' }}</text>
          </view>
        </view>

        <!-- 三餐卡片：通栏大图 + 餐别语义色徽章 + 图上换菜 + 信息区 -->
        <view
          v-for="(meal, i) in plan.meals"
          :key="i"
          class="meal-card"
          :class="[{ swapping: swapping[i] }, `meal-card--m${i}`]"
          :style="{ animationDelay: `${0.12 + i * 0.12}s` }"
        >
          <view class="meal-media">
            <image :src="imgHero(mealImage(meal))" mode="aspectFill" lazy-load />
            <view class="meal-media__scrim" />
            <view class="meal-badge" :class="`meal-badge--m${i}`">{{ mealLabel(i) }}</view>
            <view class="meal-swap" role="button" :aria-label="`换一道${mealLabel(i)}`" @click="replaceMeal(i)">
              <text class="meal-swap__icon">↻</text>
              <text class="meal-swap__text">{{ replacing[i] ? '替换中' : '换一道' }}</text>
            </view>
          </view>
          <view class="meal-body">
            <view class="meal-name-row">
              <text class="meal-name">{{ mealParts(meal.name).main }}</text>
              <text v-if="mealParts(meal.name).drink" class="meal-drink-tag">配{{ mealParts(meal.name).drink }}</text>
            </view>
            <view class="meal-meta">
              <text v-if="meal.cookingTime" class="meta-chip">{{ meal.cookingTime }} 分钟</text>
              <text class="meta-chip">{{ meal.difficulty || '家常' }}</text>
              <text class="meta-chip meta-chip--en">{{ MEAL_EN[i] || 'MEAL' }}</text>
            </view>
            <view v-if="meal.description" class="meal-desc">{{ meal.description }}</view>
            <view class="meal-expand" role="button" :aria-expanded="expanded[i] ? 'true' : 'false'" @click="toggle(i)">
              <text class="meal-expand__text">{{ expanded[i] ? '收起食材与做法' : '查看食材与做法' }}</text>
              <text class="meal-expand__arrow" :class="{ 'is-open': expanded[i] }">›</text>
            </view>

            <view v-if="expanded[i]" class="meal-detail">
              <view class="detail-label">准备食材</view>
              <view class="ing-chips">
                <text v-for="(ing, k) in ingredientsFor(meal)" :key="k" class="ing">{{ ing }}</text>
              </view>
              <view class="detail-label step-label">做法</view>
              <view v-for="(step, k) in stepsFor(meal)" :key="k" class="step">
                <text class="step-no">{{ k + 1 }}</text>
                <text class="step-text">{{ step }}</text>
              </view>
              <NutritionCard :name="meal.name" :ingredients="ingredientsFor(meal)" />
            </view>

            <view v-if="guidelineFor(i)" class="meal-basis">
              <view class="basis-head">
                <text class="basis-badge">有据可依</text>
                <text class="basis-title">{{ guidelineFor(i)!.title || '膳食指南建议' }}</text>
              </view>
              <view class="basis-content">{{ guidelineFor(i)!.content }}</view>
              <view v-if="guidelineFor(i)!.source" class="basis-source">来源：{{ guidelineFor(i)!.source }}</view>
            </view>
          </view>
        </view>

        <view class="footer-note">三餐由锅仔依据你的忌口与《中国居民膳食指南》生成</view>
      </view>

      <view v-if="quotaHint" class="quota-hint">{{ quotaHint }}</view>

      <!-- 底部操作栏：主次分明的三级层级 -->
      <view class="action-bar">
        <view class="btn btn-primary" @click="reshuffle">{{ reshuffleLabel }}</view>
        <view class="btn btn-outline" @click="share">{{ sharing ? '生成中…' : '分享图' }}</view>
        <view class="btn btn-text" @click="talkToGuozai">和锅仔聊聊</view>
      </view>
    </template>

    <canvas type="2d" id="dailyMenuShareCanvas" class="share-canvas" />
  </view>
</template>

<script setup lang="ts">
definePage({
  name: 'daily-meal-plan',
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '今日三餐',
  },
})

import { onLoad, onShow } from '@dcloudio/uni-app'
import { ref, reactive, computed } from 'vue'
import { navBack } from '@/composables/useNavBar'
import { imgHero } from '@/utils/imageUrl'
import { fetchDailyMealPlan, replaceDailyMealPlanMeal, generateDailyMealCover, fetchDailyMealPlanQuota, reshuffleDailyMealPlan } from '@/api/dailyMealPlan'
import type { QuotaView } from '@/api/dailyMealPlan'
import { watchAdReward } from '@/api/ad'
import { AD_UNITS } from '@/config/ads'
import NutritionCard from '@/components/guozai/NutritionCard.vue'
import LoadingState from '@/components/guozai/LoadingState.vue'
import ErrorState from '@/components/guozai/ErrorState.vue'
import { splitComboDishName } from '@/api/dailyMenu'
import type { RecipeItem } from '@/api/recipes'
import { STATIC_BASE_URL } from '@/utils/assets'
import { exportDailyMenuShare, saveShareImage } from '@/utils/albumShare'
import type { DailyMenuShareData } from '@/utils/albumShare'
import { toast, toastError, toastSuccess, showLoading, hideLoading } from '@/utils/toast'

const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
/** 头部日期展示：大字「10/25」+ 星期，编辑感来自数字而不是一长串文案 */
const planDay = computed(() => {
  const d = new Date(`${plan.value?.date || ''}T00:00:00`)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getMonth() + 1}/${d.getDate()}`
})
const planWeek = computed(() => {
  const d = new Date(`${plan.value?.date || ''}T00:00:00`)
  if (Number.isNaN(d.getTime())) return ''
  return `周${WEEK[d.getDay()]}`
})

const plan = ref<{
  date: string
  meals: RecipeItem[]
  knowledgePackVersion: string
  guidelineBasis?: { title: string; content: string; source?: string }[]
  nutrition?: { headline: string; tags: string[]; tip: string; goalTag?: string }
} | null>(null)
const error = ref('')
const expanded = reactive<boolean[]>([false, false, false])
const replacing = reactive<boolean[]>([false, false, false])
const swapping = reactive<boolean[]>([false, false, false])
const generating = ref(false)
/** 换一整天额度视图：remaining=-1 表示无限（会员上限或看广告解锁）。 */
const quota = ref<QuotaView | null>(null)

function pulseSwap(i: number) {
  swapping[i] = true
  setTimeout(() => { swapping[i] = false }, 520)
}
const sharing = ref(false)

const MEAL_LABELS = ['早餐', '午餐', '晚餐']
const MEAL_EN = ['BREAKFAST', 'LUNCH', 'DINNER']

function mealLabel(i: number) {
  return MEAL_LABELS[i] || '餐'
}

/* 营养打卡清单：核心四类逐项打勾（tags 为后端启发式判定的已覆盖大类），额外大类命中也列出 */
const CHECK_ITEMS = [
  { key: '谷薯类', label: '主食粗细搭配了' },
  { key: '优质蛋白', label: '蛋白质吃够了' },
  { key: '蔬菜', label: '深色蔬菜上桌了' },
  { key: '奶类', label: '奶或豆浆安排了' },
]
const nutritionChecklist = computed(() => {
  const tags = plan.value?.nutrition?.tags || []
  const rows = CHECK_ITEMS.map(item => ({ key: item.key, label: item.label, done: tags.includes(item.key) }))
  if (tags.includes('大豆及制品')) rows.push({ key: '大豆及制品', label: '豆制品有一席', done: true })
  if (tags.includes('水果')) rows.push({ key: '水果', label: '水果也安排了', done: true })
  return rows
})

function mealImage(meal: RecipeItem): string {
  return meal.image || `${STATIC_BASE_URL}/static/guozai/action_01_bowl.png`
}

/** 早餐组合菜「主食 · 配饮品」拆开展示：标题显示主食，饮品以小标签呈现，读起来是两道。 */
function mealParts(name: string): { main: string, drink: string } {
  return splitComboDishName(name)
}

function guidelineFor(i: number) {
  const p = plan.value?.guidelineBasis?.[i]
  return p && (p.title || p.content) ? p : null
}

function list(raw: any): string[] {
  if (Array.isArray(raw)) return raw.map(String)
  if (typeof raw === 'string' && raw.trim()) {
    try {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) return parsed.map(String)
    }
    catch {}
    return raw.split(/[,，;；\n]+/).map((s: string) => s.trim()).filter(Boolean)
  }
  return []
}

function ingredientsFor(meal: RecipeItem) {
  return list(meal.ingredients).slice(0, 12)
}

function stepsFor(meal: RecipeItem) {
  return list(meal.steps).slice(0, 8)
}

function toggle(i: number) {
  expanded[i] = !expanded[i]
}

const loading = ref(false)
async function load() {
  // onLoad 与 onShow 在首次进入时都会触发 load，串行守卫防止并发双请求
  if (loading.value) return
  loading.value = true
  error.value = ''
  showLoading('锅仔搭配中…')
  try {
    const data = await fetchDailyMealPlan()
    if (!data?.meals?.length) {
      error.value = '暂时没有可用的菜谱，稍后再来看看吧'
      return
    }
    plan.value = data
    expanded[0] = expanded[1] = expanded[2] = false
    backfillCovers()
    await refreshQuota()
  }
  catch (e) {
    error.value = '加载失败，请检查网络后重试'
    toastError(e, '一日三餐加载失败')
  }
  finally {
    loading.value = false
    hideLoading()
  }
}

/** 拉取「换一整天」剩余额度；失败不影响浏览，按钮退化为直接尝试。 */
async function refreshQuota() {
  try {
    quota.value = await fetchDailyMealPlanQuota()
  }
  catch { /* 额度查询失败不阻断浏览 */ }
}

/** 三餐卡懒补图：无真实图（锅仔插画兜底）的餐次逐个补拍专属封面，成功后就地更新；失败保持兜底。 */
const backfilling = ref(false)
async function backfillCovers() {
  if (!plan.value || backfilling.value) return
  backfilling.value = true
  try {
    const date = plan.value.date
    for (let i = 0; i < plan.value.meals.length; i++) {
      if (plan.value.meals[i]?.image) continue
      const res = await generateDailyMealCover(i, date)
      const updated = res?.meals?.[i]
      // 只回写图片且校验菜名：补图请求耗时较长，期间用户可能已「换一道」，
      // 整条覆盖会把旧菜的名称/做法盖到新换的菜上
      if (updated?.image && plan.value && plan.value.meals[i]?.name === updated.name) {
        plan.value.meals[i] = { ...plan.value.meals[i], image: updated.image }
      }
    }
  }
  catch { /* 补图失败不打扰浏览，保留锅仔插画兜底 */ }
  finally {
    backfilling.value = false
  }
}

async function replaceMeal(i: number) {
  if (!plan.value || replacing[i]) return
  replacing[i] = true
  try {
    const res = await replaceDailyMealPlanMeal(i, plan.value.date)
    if (res?.meals?.length) {
      plan.value = res
      pulseSwap(i)
      toast('已为你换一道')
      backfillCovers()
    }
  }
  catch (e) {
    toastError(e, '换菜失败')
  }
  finally {
    replacing[i] = false
  }
}

const reshuffleLabel = computed(() => {
  if (generating.value) return '规划中…'
  if (!quota.value) return '换一整天'
  if (quota.value.remaining <= 0) return '看广告·再换一次'
  return `换一整天（剩${quota.value.remaining}）`
})

const quotaHint = computed(() => {
  if (!quota.value) return ''
  if (quota.value.remaining <= 0) return '今日免费次数已用完，看广告可再换一次'
  if (quota.value.member) return `会员今日还可换一整天 ${quota.value.remaining} 次`
  return `普通用户今日还可换一整天 ${quota.value.remaining} 次`
})

/** 底部按钮：有额度则直接换；额度用完则引导看广告「再看一次换一次」。 */
async function reshuffle() {
  if (!plan.value || generating.value) return
  if (quota.value && quota.value.remaining <= 0) {
    await startAd()
    return
  }
  await doReshuffle()
}

/** 真实发起一次「换一整天」请求（消耗一次额度）。 */
async function doReshuffle() {
  if (!plan.value || generating.value) return
  generating.value = true
  showLoading('重新规划中…')
  try {
    const { plan: newPlan, quota: q } = await reshuffleDailyMealPlan(plan.value.date)
    if (newPlan?.meals?.length) {
      plan.value = newPlan
      quota.value = q
      for (let i = 0; i < 3; i++) pulseSwap(i)
      toastSuccess('已换一整天的搭配')
      backfillCovers()
    }
  }
  catch (e) {
    toastError(e, '重新规划失败')
  }
  finally {
    generating.value = false
    hideLoading()
  }
}

// ---- 看广告解锁「当日无限换一整天」 ----
// 复用全局激励视频 hook（与 meal-agent 对话额度同一套）：单例预加载、显示失败自动
// load→show 重试、看完（isEnded）才发奖、播放中防连点、H5 等环境走 onError 降级。
// 广告位用「通用额度广告」；如后续要为该入口单独建位，只需换 AD_UNITS 里的键。
const reshuffleAd = useRewardedVideoAd(AD_UNITS.QUOTA_REWARD, {
  onReward: async () => {
    // 看完回调：记账兑换一次机会，刷新额度并立即换一整天
    await watchAdReward('RESHUFFLE_UNLIMITED', AD_UNITS.QUOTA_REWARD)
    await refreshQuota()
    funnel('ad_watch', { type: 'RESHUFFLE_UNLIMITED' })
    toastSuccess('已获得一次换一整天机会')
    await doReshuffle()
  },
  onCancel: () => toast('看完广告才能解锁无限换哦'),
  onError: err => toast(err.message || '广告加载失败，请稍后再试'),
})

/** 拉起激励视频；发奖失败（onReward 抛错，如后端拒绝）在这里兜底提示。 */
async function startAd() {
  try {
    await reshuffleAd.show()
  }
  catch (e) {
    toastError(e, '广告奖励发放失败')
  }
}

async function share() {
  if (!plan.value || sharing.value) return
  sharing.value = true
  showLoading('生成分享图…')
  try {
    const input: DailyMenuShareData = {
      date: plan.value.date,
      meals: plan.value.meals.map((m, i) => ({
        name: m.name,
        mealLabel: mealLabel(i),
        image: mealImage(m),
        cookingTime: m.cookingTime,
        difficulty: m.difficulty,
        guideline: guidelineFor(i) || undefined,
      })),
      nutrition: plan.value.nutrition || { headline: '', tags: [], tip: '' },
    }
    const path = await exportDailyMenuShare(input)
    await saveShareImage(path)
    toastSuccess('已保存到相册')
  }
  catch (e) {
    toastError(e, '分享图生成失败')
  }
  finally {
    sharing.value = false
    hideLoading()
  }
}

/** 不满意今天的搭配？把用户引流到锅仔对话，按需重新安排一整周（普通用户不一定知道去 meal-agent 调整） */
function talkToGuozai() {
  uni.navigateTo({ url: '/subpkg-planning/meal-agent/index' })
}

onLoad(() => load())
onShow(() => { if (!plan.value && !error.value) load() })

// 原生分享
// #ifdef MP-WEIXIN
import { onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'
onShareAppMessage(() => ({
  title: '锅仔替我安排了一日三餐，依据《中国居民膳食指南》',
  path: '/subpkg-planning/daily-meal-plan/index',
}))
onShareTimeline(() => ({
  title: '锅仔的一日三餐 · 有图有依据',
}))
// #endif
</script>

<style scoped>
.daily {
  min-height: 100vh;
  background: var(--mrc-bg);
  padding-bottom: 200rpx;
}

/* ============ 刊头（navbar 之下的内容区）：大日期 + 锅仔印章 ============ */
.hero {
  position: relative;
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 12rpx 32rpx 20rpx;
  animation: cardRise .6s cubic-bezier(.22,.61,.36,1) both;
}
/* 顶部暖桃光斑：不用图片，纯渐变；透出在透明 navbar 后面，和全站头部氛围一致 */
.hero::before {
  content: '';
  position: absolute;
  left: -20%;
  right: -20%;
  top: -260rpx;
  height: 520rpx;
  background: radial-gradient(ellipse at 50% 100%, rgba(255, 197, 61, .26) 0%, rgba(255, 197, 61, 0) 62%);
  pointer-events: none;
}
.hero-text { position: relative; z-index: 2; flex: 1; min-width: 0; }
.hero-date { display: flex; align-items: baseline; gap: 12rpx; }
.hero-date__day {
  font-size: 56rpx;
  font-weight: 900;
  color: var(--mrc-text-strong);
  line-height: 1;
  letter-spacing: 1rpx;
}
.hero-date__week {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--mrc-accent);
  letter-spacing: 2rpx;
}
.hero-sub {
  display: block;
  font-size: 23rpx;
  color: var(--mrc-text-sub);
  margin-top: 14rpx;
  line-height: 1.5;
}
/* 锅仔印章：无底插画 + 呼吸动效，与日期大字形成「刊头 + 藏书票」的关系 */
.hero-stamp {
  position: relative;
  z-index: 2;
  flex-shrink: 0;
  width: 168rpx;
  height: 168rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.hero-stamp::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 2rpx solid rgba(239, 90, 60, .22);
}
.hero-stamp__img { width: 128rpx; height: 128rpx; }

/* ============ 今日营养达成 ============ */
.nutri-card {
  position: relative;
  margin: 20rpx 24rpx 0;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 40rpx;
  padding: 30rpx 28rpx;
  box-shadow: var(--mrc-shadow-lift), var(--mrc-gloss);
  animation: cardRise .6s cubic-bezier(.22,.61,.36,1) .1s both;
}
.nutri-label {
  font-size: 22rpx;
  color: var(--mrc-accent);
  font-weight: 800;
  letter-spacing: 3rpx;
}
.nutri-headline {
  font-size: 30rpx;
  font-weight: 800;
  color: var(--mrc-text-strong);
  margin-top: 10rpx;
  line-height: 1.45;
}
/* 两列网格：四项打卡一屏收完，不用长列表 */
.nutri-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14rpx;
  margin-top: 20rpx;
}
.nutri-check {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx 18rpx;
  border-radius: 20rpx;
  background: var(--mrc-surface-mint);
  border: 2rpx solid rgba(40, 194, 160, .22);
}
.nutri-check__mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36rpx;
  height: 36rpx;
  flex: 0 0 36rpx;
  border-radius: 50%;
  background: var(--mrc-mint);
  color: #fff;
  font-size: 20rpx;
  font-weight: 800;
}
.nutri-check__text { color: var(--mrc-text-deep); font-size: 23rpx; font-weight: 700; }
.nutri-check.is-miss { background: var(--mrc-surface-2); border-color: var(--mrc-border-light); }
.nutri-check.is-miss .nutri-check__mark { background: transparent; border: 2rpx solid var(--mrc-border-strong); color: var(--mrc-text-light); }
.nutri-check.is-miss .nutri-check__text { color: var(--mrc-text-light); font-weight: 500; }
/* TIP：珊瑚规线引用块，替掉原来的虚线优惠券框 */
.nutri-tip {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
  margin-top: 20rpx;
  padding: 16rpx 20rpx;
  border-left: 8rpx solid var(--mrc-accent);
  border-radius: 0 18rpx 18rpx 0;
  background: var(--mrc-accent-soft);
}
.nutri-tip__text { flex: 1; color: var(--mrc-text-deep); font-size: 22rpx; line-height: 1.55; }
.nutri-tip__tag {
  flex-shrink: 0;
  font-size: 18rpx;
  font-weight: 800;
  color: #fff;
  background: var(--mrc-accent);
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  margin-top: 2rpx;
}

/* ============ 三餐卡片 ============ */
.meal-card {
  margin: 28rpx 24rpx 0;
  overflow: hidden;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 44rpx;
  box-shadow: var(--mrc-shadow-lift), var(--mrc-gloss);
  animation: cardRise .6s cubic-bezier(.22,.61,.36,1) both;
}
.meal-card.swapping { animation: mealSwap .5s ease; }
/* 通栏大图：全宽 62% 比例，顶部圆角由卡片 overflow:hidden 收口 */
.meal-media {
  position: relative;
  width: 100%;
  height: 0;
  padding-bottom: 62%;
  overflow: hidden;
  background: var(--mrc-surface-peach);
}
.meal-media image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  animation: imgSettle .8s ease both;
}
/* 底部渐变 scrim：让徽章与换菜钮无论图深浅都稳得住 */
.meal-media__scrim {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(30, 16, 8, .18) 0%, rgba(30, 16, 8, 0) 34%, rgba(30, 16, 8, 0) 62%, rgba(30, 16, 8, .34) 100%);
  pointer-events: none;
}
/* 餐别徽章：早=暖阳黄 / 午=珊瑚 / 晚=深珊瑚，同色系内的语义微差 */
.meal-badge {
  position: absolute;
  left: 24rpx;
  top: 24rpx;
  color: #fff;
  font-size: 24rpx;
  font-weight: 900;
  letter-spacing: 4rpx;
  padding: 10rpx 26rpx;
  border-radius: 999rpx;
  border: 2rpx solid rgba(255, 255, 255, .35);
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, .2);
}
.meal-badge--m0 { background: linear-gradient(135deg, #FFC53D, #FFA83D); box-shadow: 0 8rpx 20rpx rgba(255, 168, 61, .4); }
.meal-badge--m1 { background: linear-gradient(135deg, #FF9D82, #FF6B5B); box-shadow: 0 8rpx 20rpx rgba(255, 107, 91, .4); }
.meal-badge--m2 { background: linear-gradient(135deg, #E8652B, #C94418); box-shadow: 0 8rpx 20rpx rgba(201, 68, 24, .4); }
/* 图上换菜：玻璃拟态小钮，从信息区末尾提到媒体区右下，全程可见 */
.meal-swap {
  position: absolute;
  right: 24rpx;
  bottom: 24rpx;
  display: flex;
  align-items: center;
  gap: 10rpx;
  padding: 12rpx 26rpx;
  border-radius: 999rpx;
  background: rgba(255, 252, 247, .88);
  border: 2rpx solid rgba(255, 255, 255, .6);
  box-shadow: 0 8rpx 20rpx rgba(30, 14, 6, .22);
}
.meal-swap:active { transform: scale(.95); }
.meal-swap__icon { font-size: 26rpx; font-weight: 700; color: var(--mrc-accent); line-height: 1; }
.meal-swap__text { font-size: 23rpx; font-weight: 800; color: var(--mrc-accent); }

.meal-body { padding: 26rpx 30rpx 30rpx; }
.meal-name-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
  flex-wrap: wrap;
}
.meal-name {
  font-size: 38rpx;
  font-weight: 900;
  color: var(--mrc-text-strong);
  line-height: 1.3;
}
.meal-drink-tag {
  flex-shrink: 0;
  font-size: 20rpx;
  font-weight: 700;
  color: var(--mrc-accent);
  background: var(--mrc-accent-soft);
  border: 2rpx solid var(--mrc-border-light);
  padding: 5rpx 16rpx;
  border-radius: 999rpx;
}
/* meta 行：小 chips 独立成行，不再中英混排糊成一片 */
.meal-meta { display: flex; align-items: center; gap: 12rpx; margin-top: 14rpx; flex-wrap: wrap; }
.meta-chip {
  font-size: 20rpx;
  font-weight: 700;
  color: var(--mrc-text-sub);
  background: var(--mrc-surface-2);
  border: 2rpx solid var(--mrc-border-light);
  padding: 6rpx 18rpx;
  border-radius: 999rpx;
}
.meta-chip--en { letter-spacing: 2rpx; color: var(--mrc-text-light); }
.meal-desc {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
  margin-top: 14rpx;
  color: var(--mrc-text-sub);
  font-size: 23rpx;
  line-height: 1.55;
}
/* 展开条：通栏细条 + 旋转箭头，比原来的灰色小 pills 更像一个「抽屉把手」 */
.meal-expand {
  margin-top: 20rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18rpx 24rpx;
  border-radius: 20rpx;
  background: var(--mrc-surface-2);
  border: 2rpx solid var(--mrc-border-light);
  transition: transform .15s ease;
}
.meal-expand:active { transform: scale(.98); }
.meal-expand__text { font-size: 24rpx; color: var(--mrc-text-deep); font-weight: 800; }
.meal-expand__arrow {
  font-size: 32rpx;
  color: var(--mrc-accent);
  font-weight: 700;
  line-height: 1;
  transition: transform .25s ease;
}
.meal-expand__arrow.is-open { transform: rotate(90deg); }

.meal-detail {
  margin-top: 24rpx;
  border-top: 2rpx solid var(--mrc-border-light);
  padding-top: 22rpx;
  animation: detailIn .32s ease both;
}
.detail-label {
  font-size: 22rpx;
  font-weight: 800;
  color: var(--mrc-text-deep);
  letter-spacing: 2rpx;
  margin-bottom: 14rpx;
}
.step-label { margin-top: 24rpx; }
.ing-chips { display: flex; flex-wrap: wrap; gap: 10rpx; }
.ing {
  font-size: 21rpx;
  color: var(--mrc-text-deep);
  background: var(--mrc-surface-2);
  border: 2rpx solid var(--mrc-border-light);
  padding: 8rpx 18rpx;
  border-radius: 12rpx;
  font-weight: 600;
}
.step {
  display: flex;
  gap: 14rpx;
  font-size: 23rpx;
  color: var(--mrc-text);
  line-height: 1.65;
  margin-top: 12rpx;
}
.step-no {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36rpx;
  height: 36rpx;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--mrc-accent-soft);
  color: var(--mrc-accent);
  font-weight: 900;
  font-size: 21rpx;
  margin-top: 2rpx;
}
.step-text { flex: 1; }

/* 依据块：规线引用，与 TIP 同语言 */
.meal-basis {
  margin-top: 22rpx;
  background: var(--mrc-surface-2);
  border: 2rpx solid var(--mrc-border-light);
  border-left: 8rpx solid var(--mrc-primary-deep);
  border-radius: 8rpx 22rpx 22rpx 8rpx;
  padding: 18rpx 22rpx;
}
.basis-head { display: flex; align-items: center; gap: 10rpx; }
.basis-badge {
  flex-shrink: 0;
  font-size: 18rpx;
  color: #fff;
  font-weight: 800;
  background: var(--mrc-primary-deep);
  padding: 5rpx 14rpx;
  border-radius: 999rpx;
  letter-spacing: 1rpx;
}
.basis-title {
  font-size: 22rpx;
  font-weight: 800;
  color: var(--mrc-text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.basis-content {
  font-size: 22rpx;
  color: var(--mrc-text-deep);
  line-height: 1.55;
  margin-top: 10rpx;
}
.basis-source { font-size: 20rpx; color: var(--mrc-text-sub); margin-top: 8rpx; }

.footer-note {
  text-align: center;
  font-size: 20rpx;
  color: var(--mrc-text-light);
  padding: 36rpx 40rpx 12rpx;
  line-height: 1.5;
}

.quota-hint {
  text-align: center;
  font-size: 22rpx;
  font-weight: 700;
  color: var(--mrc-accent);
  background: var(--mrc-accent-soft);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 999rpx;
  margin: 20rpx 24rpx 0;
  padding: 12rpx 24rpx;
}

/* ============ 底部操作栏：一级主行动 + 二级次行动 + 三级文字链 ============ */
.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
  background: rgba(248, 236, 218, 0.94);
  backdrop-filter: blur(10px);
  border-top: 2rpx solid var(--mrc-border-light);
}
.btn {
  height: 88rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 27rpx;
  font-weight: 800;
  transition: transform .15s ease;
  white-space: nowrap;
}
.btn:active { transform: scale(.97); }
.btn-primary {
  flex: 1.5;
  background: linear-gradient(135deg, #FFB98C 0%, #FF6B5B 100%);
  color: #fff;
  letter-spacing: 1rpx;
  box-shadow: 0 12rpx 28rpx rgba(255, 107, 91, .38);
}
.btn-outline {
  flex: 1;
  background: var(--mrc-surface);
  color: var(--mrc-accent);
  border: 2rpx solid var(--mrc-border-strong);
}
.btn-text {
  flex: 0 0 auto;
  padding: 0 12rpx;
  background: transparent;
  color: var(--mrc-text-sub);
  font-size: 24rpx;
  font-weight: 700;
}

/* 海报画布：离屏渲染，逻辑尺寸由 JS 设定，CSS 框给足高度防部分机型按 CSS 尺寸裁剪 */
.share-canvas {
  position: fixed;
  left: -9999rpx;
  top: 0;
  width: 750rpx;
  height: 2600rpx;
}

@keyframes cardRise {
  from { opacity: 0; transform: translateY(24rpx); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes mealSwap {
  0% { opacity: .5; transform: scale(.97); }
  60% { opacity: 1; transform: scale(1.01); }
  100% { opacity: 1; transform: scale(1); }
}
@keyframes imgSettle {
  from { transform: scale(1.08); }
  to { transform: scale(1); }
}
@keyframes detailIn {
  from { opacity: 0; transform: translateY(-8rpx); }
  to { opacity: 1; transform: translateY(0); }
}
@media (prefers-reduced-motion: reduce) {
  .cover, .nutri-card, .meal-card, .meal-card.swapping,
  .meal-media image, .meal-detail {
    animation: none !important;
  }
}
</style>
