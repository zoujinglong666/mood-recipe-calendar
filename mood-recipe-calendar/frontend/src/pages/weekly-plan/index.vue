<script setup lang="ts">
import type { WeeklyPlan, WeeklyPlanSummary } from '@/api/weeklyPlans'
import { ref } from 'vue'
import { generateWeeklyPlan, getCurrentPlan, getWeeklyPlanHistory, toggleWeeklyPlanFavorite } from '@/api/weeklyPlans'
import { navBack } from '@/composables/useNavBar'
import { toastError } from '@/utils/toast'

definePage({ name: 'weekly-plan', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '锅仔备餐小本' } })

const router = useRouter()
const people = ref(3)
const healthGoal = ref('BALANCED')
const generating = ref(false)
const loading = ref(true)
const showComposer = ref(false)
const showHistory = ref(false)
const currentPlan = ref<WeeklyPlan>()
const history = ref<WeeklyPlanSummary[]>([])
const goals = [{ value: 'BALANCED', label: '吃得均衡' }, { value: 'FITNESS', label: '健身增肌' }, { value: 'LEAN', label: '轻盈减脂' }]

function dateLabel(value?: string) {
  if (!value)
    return '刚刚安排'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '之前安排' : `${date.getMonth() + 1}月${date.getDate()}日安排`
}

function openPlan(id?: number) {
  router.push({ name: 'weekly-plan-detail', query: id ? { id: String(id) } : {} })
}

async function load() {
  loading.value = true
  const [current, saved] = await Promise.allSettled([getCurrentPlan(), getWeeklyPlanHistory()])
  currentPlan.value = current.status === 'fulfilled' ? current.value : undefined
  history.value = saved.status === 'fulfilled' ? saved.value : []
  loading.value = false
}

onShow(load)

async function generate() {
  if (generating.value)
    return
  generating.value = true
  try {
    const plan = await generateWeeklyPlan({ people: people.value, days: 7, healthGoal: healthGoal.value })
    router.replace({ name: 'weekly-plan-detail', query: { id: String(plan.id) } })
  }
  catch (error) {
    toastError(error, '锅仔暂时没排好这一周，请重试')
  }
  finally {
    generating.value = false
  }
}

async function toggleFavorite(id: number) {
  try {
    const plan = await toggleWeeklyPlanFavorite(id)
    history.value = history.value.map(item => item.id === id ? { ...item, favorite: plan.favorite } : item)
    if (currentPlan.value?.id === id)
      currentPlan.value = plan
  }
  catch (error) {
    toastError(error, '收藏更新失败')
  }
}
</script>

<template>
  <view class="plan-page">
    <wd-navbar title="锅仔备餐小本" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <view class="plan-hero">
      <image src="/static/guozai/action_10_thinking.png" mode="aspectFit" aria-label="思考中的锅仔" />
      <view>
        <text class="eyebrow">
          少想一点今天吃什么
        </text>
        <text class="hero-title">
          这一周，交给锅仔安排
        </text>
        <text class="hero-copy">
          每一份计划都会留下来，想吃时再翻出来。
        </text>
      </view>
    </view>

    <view v-if="loading" class="loading" aria-live="polite">
      锅仔正在整理你的备餐本…
    </view>

    <template v-else>
      <view v-if="currentPlan" class="current-card">
        <view class="section-head">
          <view>
            <text class="section-kicker">
              当前计划
            </text>
            <text class="current-title">
              {{ currentPlan.days[0]?.dishName || '这一周的晚餐' }}
            </text>
          </view>
          <text class="date-label">
            {{ dateLabel(currentPlan.createdAt) }}
          </text>
        </view>
        <text class="current-copy">
          已安排 {{ currentPlan.days.length }} 天晚餐，打开继续做饭或采购。
        </text>
        <view class="current-actions">
          <view class="secondary-action" role="button" aria-label="重新生成一周晚餐" @click="showComposer = true">
            重新生成
          </view>
          <view class="primary-action" role="button" aria-label="查看当前周计划" @click="openPlan()">
            查看这周 ›
          </view>
        </view>
      </view>

      <view v-else class="empty-card">
        <text class="empty-title">
          还没有备餐计划
        </text>
        <text>告诉锅仔几个人吃饭，它会先为你排好这一周。</text>
        <view class="primary-action" role="button" @click="showComposer = true">
          开始安排 ›
        </view>
      </view>

      <view class="history-entry" role="button" aria-label="查看历史备餐计划" @click="showHistory = !showHistory">
        <view>
          <text class="history-title">
            历史备餐计划
          </text>
          <text class="history-copy">
            {{ history.length }} 份计划，可以收藏常吃的组合
          </text>
        </view>
        <text class="history-arrow" :class="{ 'history-arrow--open': showHistory }">
          ⌄
        </text>
      </view>

      <view v-if="showHistory" class="history-list">
        <view v-for="item in history" :key="item.id" class="history-card" role="button" :aria-label="`查看${dateLabel(item.createdAt)}的计划`" @click="openPlan(item.id)">
          <view class="history-card__main">
            <text class="history-card__date">
              {{ dateLabel(item.createdAt) }}
            </text>
            <text class="history-card__dish">
              {{ item.days[0]?.dishName || '一周晚餐计划' }}
            </text>
            <text class="history-card__copy">
              {{ item.days.length }} 天晚餐 · {{ item.days.slice(1, 3).map(day => day.dishName).join('、') }}
            </text>
          </view>
          <view class="favorite" role="button" :aria-label="item.favorite ? '取消收藏这份计划' : '收藏这份计划'" @click.stop="toggleFavorite(item.id)">
            {{ item.favorite ? '★' : '☆' }}
          </view>
        </view>
      </view>

      <view v-if="showComposer" class="composer">
        <view class="composer-head">
          <view>
            <text class="section-kicker">
              新的备餐计划
            </text>
            <text class="composer-title">
              这次想怎么吃？
            </text>
          </view>
          <text role="button" class="close" aria-label="收起重新生成设置" @click="showComposer = false">
            ×
          </text>
        </view>
        <view class="plan-card">
          <text class="title">
            几个人吃晚饭？
          </text>
          <view class="counter">
            <text role="button" aria-label="减少用餐人数" @click="people = Math.max(1, people - 1)">
              −
            </text>
            <text>{{ people }} 人</text>
            <text role="button" aria-label="增加用餐人数" @click="people = Math.min(8, people + 1)">
              ＋
            </text>
          </view>
        </view>
        <view class="plan-card">
          <text class="title">
            这阵子想怎么吃？
          </text>
          <view class="goals">
            <view v-for="goal in goals" :key="goal.value" :class="{ selected: healthGoal === goal.value }" role="button" :aria-pressed="healthGoal === goal.value" @click="healthGoal = goal.value">
              {{ goal.label }}
            </view>
          </view>
        </view>
        <text class="notice">
          默认安排 7 天晚餐，会自动带入口味、忌口和健康目标。菜价随地区与季节变化。
        </text>
        <button class="generate" :disabled="generating" @click="generate">
          {{ generating ? '锅仔正在安排菜单…' : '生成新的这一周' }}
        </button>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.plan-page { min-height: 100vh; padding: 0 32rpx calc(56rpx + env(safe-area-inset-bottom)); box-sizing: border-box; color: var(--mrc-text); background: var(--mrc-bg); }
.plan-hero, .current-card, .empty-card, .history-entry, .history-card, .composer { border: 2rpx solid var(--mrc-border-light); border-radius: 30rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.plan-hero { display: flex; align-items: center; gap: 16rpx; padding: 26rpx; background: linear-gradient(135deg, var(--mrc-surface-sun), var(--mrc-surface-peach)); }
.plan-hero image { width: 142rpx; height: 142rpx; flex: 0 0 auto; }.plan-hero text, .current-card text, .empty-card text, .history-entry text, .history-card text, .composer text { display: block; }
.eyebrow, .section-kicker, .history-card__date { color: var(--mrc-accent); font-size: 21rpx; font-weight: 800; letter-spacing: 1rpx; }.hero-title, .current-title, .empty-title, .composer-title { margin-top: 5rpx; color: var(--mrc-text-strong); font-size: 32rpx; font-weight: 800; }.hero-copy, .current-copy, .history-copy, .history-card__copy, .notice { margin-top: 8rpx; color: var(--mrc-text-sub); font-size: 23rpx; line-height: 1.55; }
.loading { padding: 100rpx 0; color: var(--mrc-text-sub); text-align: center; }.current-card, .empty-card, .composer { margin-top: 22rpx; padding: 28rpx; }.section-head, .current-actions, .history-entry, .composer-head, .history-card { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; }.date-label { color: var(--mrc-text-sub); font-size: 21rpx; white-space: nowrap; }.current-actions { margin-top: 24rpx; }.primary-action, .secondary-action { min-height: 84rpx; display: flex; align-items: center; justify-content: center; border-radius: 22rpx; font-size: 25rpx; font-weight: 800; }.primary-action { flex: 1; padding: 0 22rpx; background: var(--mrc-primary); color: var(--mrc-surface); }.secondary-action { min-width: 170rpx; border: 2rpx solid var(--mrc-border-light); color: var(--mrc-text); }.empty-card .primary-action { margin-top: 24rpx; }.history-entry { min-height: 112rpx; margin-top: 22rpx; padding: 16rpx 26rpx; box-sizing: border-box; }.history-title { color: var(--mrc-text-strong); font-size: 27rpx; font-weight: 800; }.history-arrow { color: var(--mrc-text-sub); font-size: 34rpx; transition: transform 180ms cubic-bezier(.23, 1, .32, 1); }.history-arrow--open { transform: rotate(180deg); }.history-list { margin-top: 14rpx; }.history-card { min-height: 126rpx; margin-top: 12rpx; padding: 20rpx 22rpx; }.history-card__main { min-width: 0; flex: 1; }.history-card__dish { margin-top: 5rpx; color: var(--mrc-text-strong); font-size: 27rpx; font-weight: 800; }.history-card__copy { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }.favorite { width: 88rpx; min-height: 88rpx; display: flex; align-items: center; justify-content: center; color: var(--mrc-accent); font-size: 42rpx; }.composer { padding: 28rpx; }.close { width: 76rpx; height: 76rpx; color: var(--mrc-text-sub); font-size: 48rpx; line-height: 68rpx; text-align: center; }.plan-card { margin-top: 24rpx; }.title { color: var(--mrc-text-strong); font-size: 27rpx; font-weight: 800; }.counter { display: flex; align-items: center; justify-content: space-between; margin-top: 18rpx; }.counter text { width: 82rpx; height: 82rpx; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: var(--mrc-surface-peach); font-size: 34rpx; font-weight: 800; }.counter text:nth-child(2) { width: auto; background: transparent; color: var(--mrc-accent); font-size: 32rpx; }.goals { display: flex; gap: 12rpx; margin-top: 18rpx; }.goals view { flex: 1; min-height: 82rpx; display: flex; align-items: center; justify-content: center; padding: 8rpx; border: 2rpx solid var(--mrc-border); border-radius: 20rpx; font-size: 22rpx; font-weight: 700; text-align: center; }.goals .selected { border-color: var(--mrc-primary); background: var(--mrc-surface-peach); color: var(--mrc-accent); }.notice { padding: 22rpx 4rpx; }.generate { width: 100%; min-height: 96rpx; border: 0; border-radius: 48rpx; background: var(--mrc-primary-grad); color: #fff; font-size: 29rpx; font-weight: 800; }.generate::after { border: 0; }
.primary-action:active, .secondary-action:active, .history-entry:active, .history-card:active, .favorite:active, .counter text:active, .goals view:active { transform: scale(.98); opacity: .82; }.primary-action, .secondary-action, .history-entry, .history-card, .favorite, .counter text, .goals view { transition: transform 160ms ease-out, opacity 160ms ease-out; } @media (prefers-reduced-motion: reduce) { .primary-action, .secondary-action, .history-entry, .history-card, .favorite, .counter text, .goals view, .history-arrow { transition: none; } }
</style>
