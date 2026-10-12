<template>
  <view class="daily">
    <view v-if="error" class="state">
      <view class="state-emoji">🍽️</view>
      <view class="state-title">{{ error }}</view>
      <wd-button type="primary" @click="load">重试</wd-button>
    </view>

    <template v-else-if="plan">
      <view class="daily-scroll">
        <!-- 封面 -->
        <view class="cover" :style="{ paddingTop: topInset }">
          <view class="cover-back" role="button" aria-label="返回" @click="navBack">
            <text class="cover-back__icon">‹</text>
          </view>
          <view class="cover-text">
            <view class="brand">GUOZAI · DAILY MENU</view>
            <view class="title">锅仔的一日三餐</view>
            <view class="date">{{ formatPlanDate(plan.date) }}</view>
            <view class="subtitle">依据《中国居民膳食指南（2022）》· 锅仔替你决定今天吃什么</view>
          </view>
          <image class="cover-illu" :src="`${STATIC_BASE_URL}/static/guozai/action_01_bowl.png`" mode="aspectFill" />
        </view>

        <!-- 今日营养打卡：覆盖大类逐项打勾，缺项灰显 -->
        <view v-if="plan.nutrition" class="nutri-card">
          <view class="nutri-label">今日营养打卡</view>
          <view class="nutri-headline">{{ plan.nutrition.headline }}</view>
          <view class="nutri-checklist">
            <view v-for="item in nutritionChecklist" :key="item.key" class="nutri-check" :class="{ 'is-miss': !item.done }">
              <text class="nutri-check__mark">{{ item.done ? '✓' : '○' }}</text>
              <text class="nutri-check__text">{{ item.label }}</text>
            </view>
          </view>
          <view v-if="plan.nutrition.tip" class="nutri-tip">
            <text class="nutri-tip__tag">TIP · {{ plan.nutrition.goalTag || '均衡搭配' }}</text>
            <text class="nutri-tip__text">{{ plan.nutrition.tip }}</text>
          </view>
        </view>

        <!-- 三餐卡片：通栏大图 + 信息区 -->
        <view v-for="(meal, i) in plan.meals" :key="i" class="meal-card" :class="{ swapping: swapping[i] }">
          <view class="meal-media">
            <image :src="mealImage(meal)" mode="aspectFill" />
            <view class="meal-badge">{{ mealLabel(i) }}</view>
          </view>
          <view class="meal-body">
            <view class="meal-eyebrow"><text class="meal-eyebrow__en">{{ MEAL_EN[i] || 'MEAL' }}</text><text class="meal-eyebrow__zh"> · {{ mealLabel(i) }} · {{ meal.cookingTime || '--' }} 分钟 · {{ meal.difficulty || '家常' }}</text></view>
            <view class="meal-name">{{ meal.name }}</view>
            <view v-if="meal.description" class="meal-desc">{{ meal.description }}</view>
            <view class="meal-expand-btn" @click="toggle(i)">
              {{ expanded[i] ? '收起做法' : '查看食材 / 做法' }}
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
          </view>

          <view v-if="guidelineFor(i)" class="meal-basis">
            <view class="basis-head">
              <text class="basis-badge">有据可依</text>
              <text class="basis-title">{{ guidelineFor(i)!.title || '膳食指南建议' }}</text>
            </view>
            <view class="basis-content">{{ guidelineFor(i)!.content }}</view>
            <view v-if="guidelineFor(i)!.source" class="basis-source">来源：{{ guidelineFor(i)!.source }}</view>
          </view>

          <view class="meal-actions">
            <view class="mini-btn" @click="replaceMeal(i)">
              {{ replacing[i] ? '替换中…' : '换一道' }}
            </view>
          </view>
          </view>
        </view>

        <view class="footer-note">三餐由锅仔依据你的忌口与《中国居民膳食指南》生成</view>
      </view>

      <!-- 底部操作栏 -->
      <view class="action-bar">
        <view class="btn btn-outline" @click="reshuffle">{{ generating ? '规划中…' : '换一整天' }}</view>
        <view class="btn btn-ghost" @click="talkToGuozai">和锅仔聊聊</view>
        <view class="btn btn-solid" @click="share">{{ sharing ? '生成中…' : '分享图' }}</view>
      </view>
    </template>

    <view v-else class="state">
      <wd-loading color="#FF6B5B" />
      <view class="state-title">锅仔正在为你搭配今天的三餐…</view>
    </view>

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
import { useNavBar, navBack } from '@/composables/useNavBar'
import { fetchDailyMealPlan, replaceDailyMealPlanMeal } from '@/api/dailyMealPlan'
import type { RecipeItem } from '@/api/recipes'
import { STATIC_BASE_URL } from '@/utils/assets'
import { exportDailyMenuShare, saveShareImage } from '@/utils/albumShare'
import type { DailyMenuShareData } from '@/utils/albumShare'
import { toast, toastError, toastSuccess, showLoading, hideLoading } from '@/utils/toast'

// 自定义导航栏下，hero 头部需要留出「状态栏 + 微信胶囊」高度，否则会被胶囊压住。
const nav = useNavBar()
const topInset = computed(() => `${nav.statusBarHeight + nav.navBarHeight + 12}px`)

const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
function formatPlanDate(iso: string): string {
  if (!iso) return ''
  const d = new Date(`${iso}T00:00:00`)
  if (Number.isNaN(d.getTime())) return iso
  return `${d.getMonth() + 1}月${d.getDate()}日 · ${WEEK[d.getDay()]}`
}

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

async function load() {
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
  }
  catch (e) {
    error.value = '加载失败，请检查网络后重试'
    toastError(e, '一日三餐加载失败')
  }
  finally {
    hideLoading()
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
    }
  }
  catch (e) {
    toastError(e, '换菜失败')
  }
  finally {
    replacing[i] = false
  }
}

async function reshuffle() {
  if (!plan.value || generating.value) return
  generating.value = true
  showLoading('重新规划中…')
  try {
    const date = plan.value.date
    for (let i = 0; i < 3; i++) {
      const res = await replaceDailyMealPlanMeal(i, date)
      if (res?.meals?.length) plan.value = res
    }
    for (let i = 0; i < 3; i++) pulseSwap(i)
    toastSuccess('已换一整天的搭配')
  }
  catch (e) {
    toastError(e, '重新规划失败')
  }
  finally {
    generating.value = false
    hideLoading()
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
  uni.navigateTo({ url: '/pages/meal-agent/index' })
}

onLoad(() => load())
onShow(() => { if (!plan.value && !error.value) load() })

// 原生分享
// #ifdef MP-WEIXIN
import { onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'
onShareAppMessage(() => ({
  title: '锅仔替我安排了一日三餐，依据《中国居民膳食指南》',
  path: '/pages/daily-meal-plan/index',
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
  padding-bottom: 180rpx;
}

.cover {
  display: flex;
  align-items: center;
  gap: 20rpx;
  /* 顶部间距由内联 topInset 动态控制（状态栏+胶囊），此处不再写死 */
  padding: 24rpx 32rpx 8rpx;
  animation: cardRise .6s cubic-bezier(.22,.61,.36,1) both;
}
.cover-text { flex: 1; min-width: 0; }
.cover-back {
  flex: 0 0 auto;
  align-self: flex-start;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
  margin: 4rpx 4rpx 0 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.7);
  border: 1rpx solid rgba(139, 107, 85, 0.18);
  box-shadow: 0 4rpx 12rpx rgba(120, 70, 40, 0.08);
}
.cover-back__icon {
  font-size: 40rpx;
  line-height: 1;
  color: #4A3A30;
  font-weight: 700;
  transform: translateY(-2rpx);
}
.cover-back:active { transform: scale(0.94); }
.brand {
  font-size: 20rpx;
  color: var(--mrc-accent);
  letter-spacing: 3rpx;
  font-weight: 700;
}
.title {
  font-size: 46rpx;
  font-weight: 800;
  color: var(--mrc-text-strong);
  margin-top: 10rpx;
}
.date {
  font-size: 24rpx;
  color: var(--mrc-text-sub);
  margin-top: 8rpx;
}
.subtitle {
  font-size: 22rpx;
  color: var(--mrc-text-sub);
  margin-top: 16rpx;
  line-height: 1.5;
}
.cover-illu {
  width: 150rpx;
  height: 150rpx;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--mrc-surface-3);
}

.nutri-card {
  margin: 16rpx 24rpx 0;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 40rpx;
  padding: 28rpx;
  box-shadow: var(--mrc-shadow-lift), var(--mrc-gloss);
  animation: cardRise .6s cubic-bezier(.22,.61,.36,1) .16s both;
}
.nutri-label {
  font-size: 22rpx;
  color: var(--mrc-accent);
  font-weight: 800;
}
.nutri-headline {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--mrc-text-strong);
  margin-top: 10rpx;
  line-height: 1.45;
}
.nutri-checklist { display: flex; flex-direction: column; gap: 12rpx; margin-top: 18rpx; }
.nutri-check { display: flex; align-items: center; gap: 12rpx; }
.nutri-check__mark { display: flex; align-items: center; justify-content: center; width: 34rpx; height: 34rpx; flex: 0 0 34rpx; border-radius: 50%; background: var(--mrc-mint); color: #fff; font-size: 20rpx; font-weight: 800; }
.nutri-check__text { color: var(--mrc-text-deep); font-size: 24rpx; font-weight: 700; }
.nutri-check.is-miss .nutri-check__mark { background: transparent; border: 2rpx solid var(--mrc-border-strong); color: var(--mrc-text-light); }
.nutri-check.is-miss .nutri-check__text { color: var(--mrc-text-light); font-weight: 500; }
.nutri-tip { display: flex; flex-direction: column; gap: 8rpx; margin-top: 20rpx; padding: 18rpx 20rpx; border: 2rpx dashed var(--mrc-accent); border-radius: 18rpx; background: var(--mrc-accent-soft); }
.nutri-tip__tag { color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; letter-spacing: 1rpx; }
.nutri-tip__text { color: var(--mrc-text-deep); font-size: 22rpx; line-height: 1.55; }

.meal-card {
  margin: 24rpx 24rpx 0;
  overflow: hidden;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 40rpx;
  box-shadow: var(--mrc-shadow-lift), var(--mrc-gloss);
  animation: cardRise .6s cubic-bezier(.22,.61,.36,1) both;
}
.meal-card:nth-child(3) { animation-delay: .28s; }
.meal-card:nth-child(4) { animation-delay: .40s; }
.meal-card:nth-child(5) { animation-delay: .52s; }
.meal-card.swapping {
  animation: mealSwap .5s ease;
}
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
.meal-badge {
  position: absolute;
  left: 20rpx;
  top: 20rpx;
  background: var(--mrc-primary-deep);
  color: #fff;
  font-size: 22rpx;
  font-weight: 800;
  letter-spacing: 2rpx;
  padding: 8rpx 22rpx;
  border-radius: 999rpx;
  box-shadow: 0 6rpx 16rpx rgba(255, 107, 91, 0.35);
}
.meal-body {
  padding: 22rpx 28rpx 26rpx;
}
.meal-eyebrow {
  display: flex;
  align-items: baseline;
  font-size: 20rpx;
  color: var(--mrc-accent);
  font-weight: 800;
  letter-spacing: 1rpx;
}
.meal-eyebrow__en { font-size: 21rpx; letter-spacing: 2rpx; }
.meal-eyebrow__zh { color: var(--mrc-text-sub); font-weight: 600; letter-spacing: 0; }
.meal-name {
  font-size: 34rpx;
  font-weight: 800;
  color: var(--mrc-text-strong);
  margin-top: 10rpx;
  line-height: 1.35;
}
.meal-desc {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
  margin-top: 8rpx;
  color: var(--mrc-text-sub);
  font-size: 23rpx;
  line-height: 1.5;
}
.meal-expand-btn {
  margin-top: 18rpx;
  display: inline-flex;
  align-items: center;
  font-size: 22rpx;
  color: var(--mrc-text-deep);
  font-weight: 700;
  background: var(--mrc-surface-2);
  border: 2rpx solid var(--mrc-border-light);
  padding: 10rpx 24rpx;
  border-radius: 999rpx;
  transition: transform .15s ease;
}
.meal-expand-btn:active { transform: scale(.96); }

.meal-detail {
  margin-top: 22rpx;
  border-top: 1rpx solid var(--mrc-border-light);
  padding-top: 20rpx;
  animation: detailIn .32s ease both;
}
.detail-label {
  font-size: 22rpx;
  font-weight: 800;
  color: var(--mrc-text-deep);
  margin-bottom: 12rpx;
}
.step-label { margin-top: 20rpx; }
.ing-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}
.ing {
  font-size: 20rpx;
  color: var(--mrc-text-deep);
  background: var(--mrc-surface-2);
  padding: 6rpx 14rpx;
  border-radius: 10rpx;
}
.step {
  display: flex;
  gap: 12rpx;
  font-size: 22rpx;
  color: var(--mrc-text);
  line-height: 1.6;
  margin-top: 10rpx;
}
.step-no {
  color: var(--mrc-accent);
  font-weight: 800;
  flex-shrink: 0;
}
.step-text { flex: 1; }

.meal-basis {
  margin-top: 20rpx;
  background: var(--mrc-surface-2);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 22rpx;
  padding: 18rpx 20rpx;
}
.basis-head {
  display: flex;
  align-items: center;
  gap: 10rpx;
}
.basis-badge {
  flex-shrink: 0;
  font-size: 18rpx;
  color: #fff;
  font-weight: 800;
  background: var(--mrc-primary-deep);
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
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
  line-height: 1.5;
  margin-top: 10rpx;
}
.basis-source {
  font-size: 20rpx;
  color: var(--mrc-text-sub);
  margin-top: 8rpx;
}

.meal-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 20rpx;
}
.mini-btn {
  font-size: 22rpx;
  color: var(--mrc-accent);
  background: var(--mrc-accent-soft);
  padding: 12rpx 28rpx;
  border-radius: 999rpx;
  font-weight: 700;
  transition: transform .15s ease;
}
.mini-btn:active { transform: scale(.94); }

.footer-note {
  text-align: center;
  font-size: 20rpx;
  color: var(--mrc-text-light);
  padding: 28rpx 40rpx;
  line-height: 1.5;
}

.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  gap: 16rpx;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: rgba(248, 236, 218, 0.96);
  backdrop-filter: blur(10px);
  border-top: 1rpx solid var(--mrc-border-light);
}
.btn {
  flex: 1;
  height: 84rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
  transition: transform .15s ease;
}
.btn:active { transform: scale(.97); }
.btn-outline {
  background: var(--mrc-surface);
  color: var(--mrc-accent);
  border: 2rpx solid var(--mrc-border-strong);
}
.btn-solid {
  background: var(--mrc-primary-deep);
  color: #fff;
  box-shadow: var(--mrc-shadow-coral);
}
.btn-ghost {
  background: var(--mrc-surface);
  color: var(--mrc-text-deep);
  border: 2rpx solid var(--mrc-border-strong);
}

.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 160rpx 60rpx;
  gap: 24rpx;
}
.state-emoji {
  font-size: 80rpx;
}
.state-title {
  font-size: 28rpx;
  color: var(--mrc-text-sub);
  text-align: center;
}

.share-canvas {
  position: fixed;
  left: -9999rpx;
  top: 0;
  width: 750rpx;
  height: 1960rpx;
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
