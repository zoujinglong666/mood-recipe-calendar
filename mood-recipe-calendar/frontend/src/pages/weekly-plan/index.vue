<script setup lang="ts">
import type { PlanDay, WeeklyPlan, WeeklyPlanSummary } from '@/api/weeklyPlans'
import { ref } from 'vue'
import { generateWeeklyPlan, getCurrentPlan, getWeeklyPlanHistory, requestWeeklyPlanCompletionNotice, toggleWeeklyPlanFavorite } from '@/api/weeklyPlans'
import { navBack } from '@/composables/useNavBar'
import { toastError } from '@/utils/toast'

definePage({ name: 'weekly-plan', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '锅仔备餐小本' } })

const router = useRouter()
const people = ref(3)
const dishesPerDay = ref(2)
const cookingDays = ref([0, 1, 2, 3, 4, 5, 6])
const healthGoal = ref('BALANCED')
const generating = ref(false)
const loading = ref(true)
const showComposer = ref(false)
const showHistory = ref(false)
const currentPlan = ref<WeeklyPlan>()
const history = ref<WeeklyPlanSummary[]>([])
const goals = [{ value: 'BALANCED', label: '均衡吃' }, { value: 'FITNESS', label: '练得好' }, { value: 'LEAN', label: '轻一点' }]
const dishCounts = [{ value: 1, label: '1 道', copy: '简单吃' }, { value: 2, label: '2 道', copy: '吃得完整' }, { value: 3, label: '3 道', copy: '吃得丰盛' }]
const weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

function dishesOf(day?: PlanDay) {
  return day?.dishes?.length ? day.dishes : day ? [{ name: day.dishName, ingredients: day.ingredients, steps: day.steps, fallbackImageUrl: day.fallbackImageUrl }] : []
}

function dateLabel(value?: string) {
  if (!value)
    return '刚刚安排'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '之前安排' : `${date.getMonth() + 1}月${date.getDate()}日`
}

function openPlan(id?: number) {
  router.push({ name: 'weekly-plan-detail', query: id ? { id: String(id) } : {} })
}

function toggleCookingDay(index: number) {
  if (cookingDays.value.includes(index)) {
    if (cookingDays.value.length === 1) {
      toastError(null, '至少选一天，锅仔才知道何时为你开火')
      return
    }
    cookingDays.value = cookingDays.value.filter(day => day !== index)
    return
  }
  cookingDays.value = [...cookingDays.value, index].sort((a, b) => a - b)
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
    const notify = await requestWeeklyPlanCompletionNotice()
    const plan = await generateWeeklyPlan({ people: people.value, days: cookingDays.value.length, cookingDays: cookingDays.value, healthGoal: healthGoal.value, sendNotification: notify, dishesPerDay: dishesPerDay.value })
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
  }
  catch (error) {
    toastError(error, '收藏更新失败')
  }
}
</script>

<template>
  <view class="plan-page">
    <wd-navbar title="锅仔备餐小本" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <view class="menu-cover">
      <view class="menu-cover__topline">
        <text>GUOZAI'S MENU</text><text>VOL. 01</text>
      </view>
      <view class="menu-cover__content">
        <view class="menu-cover__copy">
          <text class="cover-kicker">
            少想一点今天吃什么
          </text><text class="cover-title">
            这一周<br>吃点认真喜欢的
          </text><text class="cover-sub">
            锅仔把你爱吃的、忌口的和这一阵子的状态，慢慢排成一张晚餐单。
          </text>
        </view>
        <view class="cover-character">
          <view class="cover-character__ring" /><image src="/static/guozai/action_10_thinking.png" mode="aspectFit" aria-label="思考菜单的锅仔" />
        </view>
      </view>
      <view class="menu-cover__footer">
        <text>07 DAYS · DINNER ONLY</text><text>翻开这一册 ›</text>
      </view>
    </view>

    <view v-if="loading" class="loading" aria-live="polite">
      锅仔正在翻找你的备餐本…
    </view>

    <template v-else>
      <view v-if="currentPlan" class="active-menu" role="button" aria-label="查看当前周计划" @click="openPlan()">
        <view class="active-menu__paper">
          <view class="active-menu__paper-head">
            <text>锅仔的本周菜单</text><text>{{ dateLabel(currentPlan.createdAt) }} 起</text>
          </view>
          <view class="active-menu__content">
            <view class="active-menu__copy-wrap">
              <text class="active-menu__eyebrow">
                今晚先吃这组
              </text><text class="active-menu__dish">
                {{ currentPlan.days[0]?.dishName || '这一周的晚餐' }}
              </text><text class="active-menu__copy">
                {{ currentPlan.days.length }} 天、每天 {{ dishesOf(currentPlan.days[0]).length }} 道菜。锅仔已经替你把这一桌想好了。
              </text>
            </view>
            <image class="active-menu__character" src="/static/guozai/action_11_cooking.png" mode="aspectFit" aria-label="端着晚餐的锅仔" />
          </view>
          <view class="active-menu__tape">
            GUOZAI MADE THIS
          </view>
        </view>
        <view class="active-menu__footer">
          <text>翻开本周菜单</text><text>从第一餐开始 ›</text>
        </view>
      </view>

      <view v-else class="first-menu">
        <view class="first-menu__number">
          01
        </view>
        <view class="first-menu__copy">
          <text class="first-menu__title">
            从第一张晚餐单开始
          </text><text>不用一次说清所有偏好。先告诉锅仔几个人吃饭，它会把这一周慢慢安排好。</text>
        </view>
        <view class="start-button" role="button" aria-label="开始安排一周晚餐" @click="showComposer = true">
          <text>开始编排这一周</text><text>›</text>
        </view>
      </view>

      <view v-if="currentPlan" class="new-plan-link" role="button" @click="showComposer = true">
        <text>想换个节奏？</text><text>重新编排一周 ›</text>
      </view>

      <view class="archive">
        <view class="archive__heading">
          <view>
            <text class="archive__label">
              KITCHEN ARCHIVE
            </text><text class="archive__title">
              备餐档案
            </text>
          </view><text>{{ history.length }} 册</text>
        </view>
        <view v-if="history.length === 0" class="archive-empty">
          <image src="/static/guozai/action_08_peek.png" mode="aspectFit" /><view><text>这里会收下你的晚餐单</text><text>常吃的组合，以后不用重新想。</text></view>
        </view>
        <view v-else class="archive-toggle" role="button" :aria-expanded="showHistory" @click="showHistory = !showHistory">
          <text>{{ showHistory ? '收起最近的菜单' : '翻开最近的菜单' }}</text><text class="archive-toggle__arrow" :class="{ 'archive-toggle__arrow--open': showHistory }">
            ⌄
          </text>
        </view>
        <view v-if="showHistory" class="archive-list">
          <view v-for="item in history" :key="item.id" class="archive-card" role="button" :aria-label="`查看${dateLabel(item.createdAt)}的计划`" @click="openPlan(item.id)">
            <view class="archive-card__stamp">
              <text>GUOZAI</text><text>MENU<br>ARCHIVE</text>
            </view>
            <view class="archive-card__main">
              <view class="archive-card__date">
                <text>{{ dateLabel(item.createdAt) }}</text><text>{{ item.days.reduce((total, day) => total + dishesOf(day).length, 0) }} 道菜</text>
              </view>
              <text class="archive-card__dish">
                {{ item.days[0]?.dishName || '一周晚餐计划' }}
              </text><text class="archive-card__copy">
                {{ item.days.slice(1, 3).map(day => day.dishName).join(' · ') }}
              </text>
            </view>
            <view class="archive-card__side">
              <image src="/static/guozai/action_08_peek.png" mode="aspectFit" aria-label="探出头的锅仔" />
              <view class="favorite" role="button" :aria-label="item.favorite ? '取消收藏这份计划' : '收藏这份计划'" @click.stop="toggleFavorite(item.id)">
                {{ item.favorite ? '★' : '☆' }}
              </view>
            </view>
          </view>
        </view>
      </view>

      <view v-if="showComposer" class="composer">
        <view class="composer__heading">
          <view>
            <text class="archive__label">
              NEW MENU
            </text><text class="composer__title">
              这次，想怎么吃？
            </text>
          </view><text class="composer__close" role="button" aria-label="收起菜单设置" @click="showComposer = false">
            ×
          </text>
        </view>
        <view class="composer__rule" />
        <view class="composer-question">
          <text class="composer-question__index">
            01
          </text><view>
            <text class="composer-question__title">
              几个人一起吃晚饭？
            </text><view class="counter">
              <text role="button" aria-label="减少用餐人数" @click="people = Math.max(1, people - 1)">
                −
              </text><text>{{ people }} 人</text><text role="button" aria-label="增加用餐人数" @click="people = Math.min(8, people + 1)">
                ＋
              </text>
            </view>
          </view>
        </view>
        <view class="composer-question">
          <text class="composer-question__index">
            02
          </text><view>
            <text class="composer-question__title">
              每天想吃几道菜？
            </text><view class="dish-count-options">
              <view v-for="option in dishCounts" :key="option.value" :class="{ selected: dishesPerDay === option.value }" role="button" :aria-pressed="dishesPerDay === option.value" @click="dishesPerDay = option.value">
                <text>{{ option.label }}</text>
              </view>
            </view><text class="dish-count-hint">
              {{ dishCounts.find(option => option.value === dishesPerDay)?.copy }}；{{ people >= 3 && dishesPerDay === 1 ? '三人建议选 2 道，吃得更完整。' : '锅仔会按人数搭配主菜和配菜。' }}
            </text>
          </view>
        </view>
        <view class="composer-question">
          <text class="composer-question__index">
            03
          </text><view>
            <text class="composer-question__title">
              这一周哪几天做饭？
            </text><text class="cooking-days-hint">
              默认全选；有事的日子点一下跳过，锅仔就不排这顿。
            </text><view class="cooking-days">
              <view v-for="(day, index) in weekdays" :key="day" :class="{ selected: cookingDays.includes(index) }" role="button" :aria-pressed="cookingDays.includes(index)" :aria-label="`${cookingDays.includes(index) ? '取消' : '选择'}${day}晚餐`" @click="toggleCookingDay(index)">
                {{ day }}
              </view>
            </view>
          </view>
        </view>
        <view class="composer-question">
          <text class="composer-question__index">
            04
          </text><view>
            <text class="composer-question__title">
              这一阵子想怎么吃？
            </text><view class="goals">
              <view v-for="goal in goals" :key="goal.value" :class="{ selected: healthGoal === goal.value }" role="button" :aria-pressed="healthGoal === goal.value" @click="healthGoal = goal.value">
                {{ goal.label }}
              </view>
            </view>
          </view>
        </view>
        <text class="notice">
          已安排 {{ cookingDays.length }} 天晚餐、每天 {{ dishesPerDay }} 道菜。锅仔会带入口味、忌口和健康目标。
        </text>
        <button class="generate" :disabled="generating || !cookingDays.length" @click="generate">
          {{ generating ? '锅仔正在排菜单…' : '生成这一册晚餐单' }}
        </button>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.plan-page { min-height: 100vh; padding: 0 32rpx calc(76rpx + env(safe-area-inset-bottom)); color: var(--mrc-text); background: var(--mrc-bg); box-sizing: border-box; }
.menu-cover, .first-menu, .active-menu, .composer { margin-top: 28rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 30rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.menu-cover { position: relative; overflow: hidden; margin-top: 0; padding: 28rpx 30rpx 24rpx; border-radius: 40rpx 40rpx 28rpx 28rpx; background: linear-gradient(145deg, var(--mrc-surface-sun), var(--mrc-surface-peach)); }
.menu-cover::after { position: absolute; right: -90rpx; bottom: -90rpx; width: 270rpx; height: 270rpx; border: 2rpx dashed var(--mrc-border); border-radius: 50%; content: ''; opacity: .65; }
.menu-cover__topline, .menu-cover__footer, .active-menu__header, .active-menu__footer, .archive__heading, .archive-toggle, .archive-card__date, .composer__heading, .counter { display: flex; align-items: center; justify-content: space-between; }
.menu-cover__topline, .menu-cover__footer, .archive__label, .active-menu__header { color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; letter-spacing: 1.8rpx; }
.menu-cover__content { position: relative; z-index: 1; display: flex; align-items: center; min-height: 270rpx; }
.menu-cover__copy { width: 58%; }.cover-kicker, .active-menu__eyebrow { display: block; color: var(--mrc-accent); font-size: 21rpx; font-weight: 800; }
.cover-title { display: block; margin-top: 12rpx; color: var(--mrc-text-strong); font-size: 48rpx; font-weight: 800; line-height: 1.13; }.cover-sub { display: block; margin-top: 14rpx; color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.55; }
.cover-character { position: absolute; right: -16rpx; bottom: -20rpx; width: 285rpx; height: 285rpx; }.cover-character__ring { position: absolute; inset: 24rpx; border: 2rpx solid var(--mrc-border); border-radius: 50%; }.cover-character image { position: relative; width: 100%; height: 100%; }
.menu-cover__footer { position: relative; z-index: 1; margin-top: 14rpx; padding-top: 18rpx; border-top: 2rpx solid var(--mrc-border-light); }.loading { padding: 84rpx 0; color: var(--mrc-text-sub); font-size: 24rpx; text-align: center; }
.first-menu { position: relative; overflow: hidden; padding: 34rpx 30rpx 30rpx; }.first-menu__number { color: var(--mrc-surface-peach); font-size: 112rpx; font-weight: 800; line-height: .7; }.first-menu__copy { position: relative; margin-top: 24rpx; }
.first-menu__title, .active-menu__dish, .archive__title, .composer__title { display: block; color: var(--mrc-text-strong); font-size: 34rpx; font-weight: 800; }.first-menu__copy text:last-child, .active-menu__copy, .archive-empty text:last-child, .archive-card__copy, .notice { display: block; margin-top: 10rpx; color: var(--mrc-text-sub); font-size: 24rpx; line-height: 1.58; }
.start-button, .generate { width: 100%; min-height: 100rpx; display: flex; align-items: center; justify-content: space-between; margin-top: 30rpx; padding: 0 32rpx; border: 0; border-radius: 22rpx; background: var(--mrc-primary-grad); color: #fff; font-size: 29rpx; font-weight: 800; box-sizing: border-box; }.generate { justify-content: center; border-radius: 50rpx; }.generate::after { border: 0; }
.active-menu { position: relative; overflow: hidden; padding: 14rpx; border-radius: 34rpx; background: var(--mrc-surface-peach); }.active-menu__paper { position: relative; min-height: 286rpx; overflow: hidden; padding: 26rpx 26rpx 24rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 26rpx; background: var(--mrc-surface); }.active-menu__paper::before { position: absolute; top: 80rpx; right: 18rpx; bottom: 18rpx; left: 18rpx; border: 2rpx dashed var(--mrc-border-light); border-radius: 18rpx; content: ''; }.active-menu__paper-head { position: relative; z-index: 1; display: flex; align-items: center; justify-content: space-between; color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; letter-spacing: 1rpx; }.active-menu__content { position: relative; z-index: 1; display: flex; align-items: center; min-height: 190rpx; }.active-menu__copy-wrap { width: 63%; }.active-menu__eyebrow { color: var(--mrc-accent); font-size: 21rpx; font-weight: 800; }.active-menu__dish { margin-top: 8rpx; color: var(--mrc-text-strong); font-size: 38rpx; line-height: 1.2; }.active-menu__copy { margin-top: 12rpx; color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.55; }.active-menu__character { position: absolute; right: -16rpx; bottom: -30rpx; width: 230rpx; height: 230rpx; }.active-menu__tape { position: absolute; z-index: 2; right: 28rpx; bottom: 18rpx; padding: 8rpx 14rpx; transform: rotate(-4deg); background: var(--mrc-surface-sun); color: var(--mrc-text-sub); font-size: 16rpx; font-weight: 800; letter-spacing: 1rpx; }.active-menu__footer { min-height: 84rpx; display: flex; align-items: center; justify-content: space-between; padding: 0 14rpx; color: var(--mrc-text); font-size: 23rpx; font-weight: 800; }
.new-plan-link { min-height: 84rpx; display: flex; align-items: center; justify-content: space-between; padding: 0 8rpx; color: var(--mrc-accent); font-size: 24rpx; font-weight: 700; }.archive { margin-top: 34rpx; }.archive__heading { padding-bottom: 16rpx; border-bottom: 2rpx solid var(--mrc-border-light); }.archive__title { margin-top: 5rpx; }.archive__heading > text { color: var(--mrc-text-sub); font-size: 22rpx; }
.archive-empty { display: flex; align-items: center; gap: 14rpx; padding: 24rpx 6rpx; }.archive-empty image { width: 108rpx; height: 108rpx; }.archive-empty text:first-child { display: block; color: var(--mrc-text); font-size: 25rpx; font-weight: 700; }.archive-toggle { min-height: 92rpx; color: var(--mrc-text); font-size: 25rpx; font-weight: 700; }.archive-toggle__arrow { color: var(--mrc-text-sub); font-size: 34rpx; transition: transform 180ms cubic-bezier(.23, 1, .32, 1); }.archive-toggle__arrow--open { transform: rotate(180deg); }
.archive-list { padding-bottom: 8rpx; }.archive-card { display: flex; align-items: stretch; gap: 18rpx; min-height: 154rpx; margin: 14rpx 0; overflow: hidden; padding: 18rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 24rpx; background: var(--mrc-surface); box-sizing: border-box; }.archive-card__stamp { width: 92rpx; display: flex; flex: 0 0 auto; align-items: center; justify-content: center; flex-direction: column; border: 2rpx dashed var(--mrc-primary); border-radius: 16rpx; color: var(--mrc-accent); font-size: 16rpx; font-weight: 900; line-height: 1.2; letter-spacing: 1rpx; text-align: center; }.archive-card__stamp text + text { margin-top: 8rpx; }.archive-card__main { min-width: 0; flex: 1; }.archive-card__date { display: flex; align-items: center; justify-content: space-between; color: var(--mrc-text-sub); font-size: 20rpx; }.archive-card__dish { display: block; margin-top: 10rpx; overflow: hidden; color: var(--mrc-text-strong); font-size: 29rpx; font-weight: 800; white-space: nowrap; text-overflow: ellipsis; }.archive-card__copy { overflow: hidden; color: var(--mrc-text-sub); font-size: 21rpx; line-height: 1.5; white-space: nowrap; text-overflow: ellipsis; }.archive-card__side { position: relative; width: 78rpx; flex: 0 0 auto; }.archive-card__side image { width: 78rpx; height: 78rpx; }.favorite { position: absolute; right: -2rpx; bottom: -2rpx; width: 56rpx; height: 56rpx; display: flex; align-items: center; justify-content: center; border-radius: 50%; color: var(--mrc-accent); background: var(--mrc-surface-sun); font-size: 29rpx; font-weight: 800; }
.composer { padding: 30rpx; }.composer__close { width: 76rpx; height: 76rpx; color: var(--mrc-text-sub); font-size: 48rpx; line-height: 68rpx; text-align: center; }.composer__rule { height: 2rpx; margin: 26rpx 0 4rpx; background: var(--mrc-border-light); }.composer-question { display: flex; gap: 18rpx; padding: 28rpx 0; border-bottom: 2rpx solid var(--mrc-border-light); }.composer-question__index { color: var(--mrc-primary); font-size: 23rpx; font-weight: 800; }.composer-question__title { display: block; color: var(--mrc-text-strong); font-size: 27rpx; font-weight: 800; }
.counter { width: 420rpx; margin-top: 20rpx; }.counter text { width: 76rpx; height: 76rpx; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: var(--mrc-surface-peach); font-size: 32rpx; font-weight: 800; }.counter text:nth-child(2) { width: auto; background: transparent; color: var(--mrc-accent); font-size: 30rpx; }
.goals { display: flex; gap: 10rpx; margin-top: 20rpx; }.goals view { min-width: 118rpx; min-height: 76rpx; display: flex; align-items: center; justify-content: center; padding: 0 12rpx; border: 2rpx solid var(--mrc-border); border-radius: 18rpx; color: var(--mrc-text-sub); font-size: 22rpx; font-weight: 700; }.goals .selected, .dish-count-options .selected { border-color: var(--mrc-primary); background: var(--mrc-surface-peach); color: var(--mrc-accent); }
.dish-count-options { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16rpx; margin-top: 22rpx; }.dish-count-options view { min-height: 96rpx; display: flex; align-items: center; justify-content: center; border: 2rpx solid var(--mrc-border); border-radius: 20rpx; color: var(--mrc-text); background: var(--mrc-surface); }.dish-count-options view text { font-size: 30rpx; font-weight: 800; }.dish-count-options .selected text { color: var(--mrc-accent); }.dish-count-hint { display: block; margin-top: 16rpx; color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.55; }
.cooking-days-hint { display: block; margin-top: 12rpx; color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.55; }.cooking-days { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12rpx; margin-top: 20rpx; }.cooking-days view { min-height: 76rpx; display: flex; align-items: center; justify-content: center; border: 2rpx solid var(--mrc-border); border-radius: 18rpx; color: var(--mrc-text-sub); background: var(--mrc-surface); font-size: 23rpx; font-weight: 700; }.cooking-days .selected { border-color: var(--mrc-primary); background: var(--mrc-surface-peach); color: var(--mrc-accent); }.notice { margin-top: 22rpx; }.start-button:active, .active-menu:active, .new-plan-link:active, .archive-toggle:active, .archive-card:active, .favorite:active, .counter text:active, .goals view:active, .dish-count-options view:active, .cooking-days view:active { transform: scale(.985); opacity: .84; }.start-button, .active-menu, .new-plan-link, .archive-toggle, .archive-card, .favorite, .counter text, .goals view, .dish-count-options view, .cooking-days view { transition: transform 160ms ease-out, opacity 160ms ease-out; }
@media (prefers-reduced-motion: reduce) { .start-button, .active-menu, .new-plan-link, .archive-toggle, .archive-card, .favorite, .counter text, .goals view, .dish-count-options view, .cooking-days view, .archive-toggle__arrow { transition: none; } }
</style>
