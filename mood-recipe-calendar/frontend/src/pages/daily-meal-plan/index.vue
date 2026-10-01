<script setup lang="ts">
import type { DailyMealPlan } from '@/api/dailyMealPlan'
import { ref } from 'vue'
import { fetchDailyMealPlan, replaceDailyMealPlanMeal } from '@/api/dailyMealPlan'
import { STATIC_BASE_URL } from '@/utils/assets'
import { toastError } from '@/utils/toast'

definePage({
  name: 'daily-meal-plan',
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '今日三餐',
  },
})

const router = useRouter()
const loading = ref(false)
const retrying = ref(false)
const plan = ref<DailyMealPlan>()
const empty = ref(false)
const expanded = ref(-1)
const replacing = ref(-1)

async function load() {
  if (loading.value || retrying.value)
    return

  const isRetry = empty.value
  if (isRetry)
    retrying.value = true
  else
    loading.value = true

  try {
    plan.value = await fetchDailyMealPlan()
    empty.value = false
  }
  catch (error: any) {
    plan.value = undefined
    empty.value = true
    if (!String(error?.message || '').includes('暂时无法'))
      toastError(error, '加载三餐计划失败')
  }
  finally {
    loading.value = false
    retrying.value = false
  }
}

function open(id?: number) {
  if (id)
    router.push({ name: 'recipe', query: { recipeId: String(id) } })
}

function list(value: string) {
  try {
    return JSON.parse(value || '[]') as string[]
  }
  catch {
    return value ? [value] : []
  }
}

function toggle(index: number) {
  expanded.value = expanded.value === index ? -1 : index
}

async function replace(index: number) {
  if (!plan.value || replacing.value >= 0)
    return

  replacing.value = index
  try {
    plan.value = await replaceDailyMealPlanMeal(index, plan.value.date)
    expanded.value = index
  }
  catch (error) {
    toastError(error, '暂时没有合适的替换菜')
  }
  finally {
    replacing.value = -1
  }
}

onShow(load)
</script>

<template>
  <view class="page">
    <wd-navbar
      title="锅仔的一日三餐"
      left-arrow
      safe-area-inset-top
      custom-style="background-color: transparent !important;"
      @click-left="router.back()"
    />

    <view v-if="loading" class="content">
      <view class="skeleton hero-skeleton" />
      <view v-for="i in 3" :key="i" class="skeleton card-skeleton" />
    </view>

    <view v-else-if="empty" class="empty-state">
      <view class="empty-hero">
        <view class="empty-hero__orb empty-hero__orb--large" />
        <view class="empty-hero__orb empty-hero__orb--small" />
        <view class="empty-hero__copy">
          <text class="empty-hero__eyebrow">GUOZAI · DAILY MENU</text>
          <text class="empty-hero__title">这次还没配出<br>放心的一日三餐</text>
          <text class="empty-hero__description">锅仔宁可多想一会儿，也不会把没通过检查的菜单端上来。</text>
        </view>
        <image
          class="empty-hero__image"
          :src="`${STATIC_BASE_URL}/static/guozai/action_10_thinking.png`"
          mode="aspectFit"
        />
      </view>

      <view class="empty-checks">
        <view class="empty-checks__heading">
          <text class="empty-checks__number">01</text>
          <view>
            <text class="empty-checks__title">再试一次，锅仔会重新核对</text>
            <text class="empty-checks__subtitle">只展示完整且通过检查的三餐计划</text>
          </view>
        </view>
        <view class="empty-checks__items">
          <view class="empty-check">
            <text class="empty-check__mark">✓</text>
            <text>忌口安全</text>
          </view>
          <view class="empty-check">
            <text class="empty-check__mark">✓</text>
            <text>当季候选</text>
          </view>
          <view class="empty-check">
            <text class="empty-check__mark">✓</text>
            <text>三餐不重复</text>
          </view>
        </view>
      </view>

      <view
        class="empty-action mrc-btn-primary pressable"
        :class="{ 'empty-action--disabled': retrying }"
        role="button"
        :aria-label="retrying ? '正在重新搭配一日三餐' : '重新生成一日三餐'"
        @click="load"
      >
        <text>{{ retrying ? '正在重新搭配…' : '重新生成一日三餐' }}</text>
        <text class="empty-action__arrow">{{ retrying ? '•••' : '→' }}</text>
      </view>
      <text class="empty-footnote">每次生成都会重新经过安全与完整性检查</text>
    </view>

    <view v-else-if="plan" class="content">
      <view class="cover">
        <view>
          <text class="eyebrow">GUOZAI · DAILY MENU</text>
          <text class="title">今天这样吃<br>更省一点心</text>
          <text class="cover-copy">{{ plan.date }} · 三餐已经按忌口和当季候选检查过。</text>
        </view>
        <image :src="`${STATIC_BASE_URL}/static/guozai/action_01_bowl.png`" mode="aspectFit" />
      </view>

      <view
        v-for="(meal, i) in plan.meals"
        :key="meal.id || meal.name"
        class="card"
        :class="{ 'card--open': expanded === i }"
      >
        <view class="head pressable" @click="toggle(i)">
          <view>
            <text class="meal">0{{ i + 1 }} · {{ ['早餐', '午餐', '晚餐'][i] }}</text>
            <text class="name">{{ meal.name }}</text>
            <text class="meta">{{ meal.cookingTime || 30 }} 分钟 · {{ meal.difficulty || '简单' }}</text>
          </view>
          <text class="arrow">{{ expanded === i ? '收起' : '展开' }}</text>
        </view>
        <view class="ops">
          <text class="pressable" @click="replace(i)">{{ replacing === i ? '锅仔在换菜…' : '换一道' }}</text>
        </view>
        <view v-if="expanded === i" class="detail">
          <text class="section">准备食材</text>
          <text v-for="item in list(meal.ingredients)" :key="item" class="item">· {{ item }}</text>
          <text class="section">跟着做</text>
          <text v-for="(step, stepIndex) in list(meal.steps)" :key="step" class="item">{{ stepIndex + 1 }}. {{ step }}</text>
          <view class="record pressable" @click="open(meal.id)">
            查看完整菜谱并记录这餐 ›
          </view>
        </view>
      </view>

      <view class="why">
        <text class="section">锅仔为什么这样搭配</text>
        <text>三餐已避开你的忌口，尽量错开核心食材，并优先使用当季候选。</text>
      </view>
      <view class="notice">
        依据规则版本 {{ plan.knowledgePackVersion }}；日常饮食建议，不替代医疗建议。
      </view>
    </view>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  min-height: 100dvh;
  color: var(--mrc-text);
  background: radial-gradient(88% 30% at 8% 4%, var(--mrc-surface-sun) 0%, transparent 72%), var(--mrc-bg);
}

.content,
.empty-state {
  padding: 20rpx 30rpx calc(70rpx + env(safe-area-inset-bottom));
}

.empty-hero {
  position: relative;
  min-height: 420rpx;
  overflow: hidden;
  box-sizing: border-box;
  padding: 40rpx 34rpx;
  border: 2rpx solid var(--mrc-border);
  border-radius: 40rpx;
  background: linear-gradient(145deg, var(--mrc-surface) 0%, var(--mrc-surface-peach) 100%);
  box-shadow: var(--mrc-shadow-lift), var(--mrc-gloss);
}

.empty-hero__copy {
  position: relative;
  z-index: 2;
  width: 64%;
}

.empty-hero__eyebrow,
.eyebrow {
  display: block;
  color: var(--mrc-accent);
  font-size: 21rpx;
  font-weight: 800;
  letter-spacing: 2rpx;
}

.empty-hero__title {
  display: block;
  margin-top: 20rpx;
  color: var(--mrc-text-deep);
  font-size: 45rpx;
  font-weight: 800;
  letter-spacing: -1rpx;
  line-height: 1.28;
}

.empty-hero__description {
  display: block;
  margin-top: 24rpx;
  color: var(--mrc-text-sub);
  font-size: 25rpx;
  line-height: 1.65;
}

.empty-hero__image {
  position: absolute;
  z-index: 2;
  right: -8rpx;
  bottom: -2rpx;
  width: 265rpx;
  height: 265rpx;
}

.empty-hero__orb {
  position: absolute;
  border-radius: 50%;
  background: var(--mrc-surface-sun);
}

.empty-hero__orb--large {
  right: -70rpx;
  bottom: -90rpx;
  width: 300rpx;
  height: 300rpx;
}

.empty-hero__orb--small {
  top: 34rpx;
  right: 42rpx;
  width: 54rpx;
  height: 54rpx;
  opacity: .8;
}

.empty-checks {
  position: relative;
  z-index: 3;
  margin: -28rpx 18rpx 0;
  padding: 30rpx 28rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 30rpx;
  background: var(--mrc-surface);
  box-shadow: var(--mrc-shadow), var(--mrc-gloss);
}

.empty-checks__heading {
  display: flex;
  align-items: flex-start;
  gap: 18rpx;
}

.empty-checks__number {
  color: var(--mrc-accent);
  font-size: 21rpx;
  font-weight: 900;
  letter-spacing: 1rpx;
  line-height: 1.55;
}

.empty-checks__title,
.empty-checks__subtitle {
  display: block;
}

.empty-checks__title {
  color: var(--mrc-text-deep);
  font-size: 29rpx;
  font-weight: 800;
  line-height: 1.45;
}

.empty-checks__subtitle {
  margin-top: 6rpx;
  color: var(--mrc-text-sub);
  font-size: 23rpx;
  line-height: 1.5;
}

.empty-checks__items {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12rpx;
  margin-top: 28rpx;
}

.empty-check {
  display: flex;
  min-width: 0;
  min-height: 86rpx;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  box-sizing: border-box;
  padding: 0 10rpx;
  border-radius: 22rpx;
  color: var(--mrc-text-deep);
  background: var(--mrc-surface-2);
  font-size: 22rpx;
  font-weight: 700;
}

.empty-check__mark {
  display: flex;
  width: 30rpx;
  height: 30rpx;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  background: var(--mrc-accent);
  font-size: 18rpx;
  font-weight: 900;
}

.empty-action {
  display: flex;
  min-height: 104rpx;
  align-items: center;
  justify-content: space-between;
  margin-top: 32rpx;
  padding: 0 34rpx;
  box-sizing: border-box;
  font-size: 29rpx;
  font-weight: 800;
}

.empty-action--disabled {
  opacity: .68;
}

.empty-action__arrow {
  font-size: 34rpx;
  line-height: 1;
}

.empty-footnote {
  display: block;
  margin-top: 18rpx;
  color: var(--mrc-text-muted);
  font-size: 22rpx;
  line-height: 1.5;
  text-align: center;
}

.cover {
  position: relative;
  display: flex;
  min-height: 270rpx;
  overflow: hidden;
  padding: 34rpx 30rpx 22rpx;
  border-radius: 40rpx;
  background: var(--mrc-surface-peach);
  box-shadow: var(--mrc-shadow-lift);
}

.cover::after {
  position: absolute;
  top: -40rpx;
  right: -38rpx;
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
  background: var(--mrc-surface-sun);
  content: '';
}

.cover > view {
  position: relative;
  z-index: 1;
}

.cover image {
  position: absolute;
  z-index: 1;
  right: 10rpx;
  bottom: 0;
  width: 250rpx;
  height: 250rpx;
}

.title {
  display: block;
  margin: 12rpx 0;
  font-size: 46rpx;
  font-weight: 700;
  letter-spacing: -1rpx;
  line-height: 1.18;
}

.cover-copy {
  position: relative;
  z-index: 2;
  display: block;
  max-width: 400rpx;
  color: var(--mrc-text-sub);
  font-size: 24rpx;
  line-height: 1.6;
}

.card,
.why {
  margin: 20rpx 0;
  padding: 28rpx;
  border-radius: 30rpx;
  background: var(--mrc-surface);
  box-shadow: var(--mrc-shadow-soft);
}

.card--open {
  box-shadow: var(--mrc-shadow-lift);
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.meal {
  color: var(--mrc-accent);
  font-size: 23rpx;
  font-weight: 700;
  letter-spacing: 1rpx;
}

.name {
  display: block;
  margin: 10rpx 0;
  font-size: 35rpx;
  font-weight: 700;
}

.meta,
.notice {
  display: block;
  color: var(--mrc-text-sub);
  font-size: 24rpx;
}

.arrow {
  color: var(--mrc-text-sub);
  font-size: 23rpx;
}

.ops {
  margin-top: 18rpx;
  color: var(--mrc-accent);
  font-size: 26rpx;
}

.detail {
  margin-top: 22rpx;
  padding-top: 18rpx;
  border-top: 1rpx solid var(--mrc-border-light);
  animation: detail-in .24s ease-out;
}

.section {
  display: block;
  margin: 14rpx 0;
  font-weight: 700;
}

.item {
  display: block;
  color: var(--mrc-text-sub);
  line-height: 1.9;
}

.record {
  margin-top: 24rpx;
  color: var(--mrc-accent);
  font-weight: 700;
}

.why text:last-child {
  color: var(--mrc-text-sub);
  line-height: 1.7;
}

.notice {
  margin-top: 30rpx;
  line-height: 1.7;
}

.pressable {
  transition: transform .2s ease, opacity .2s ease;
}

.pressable:active {
  opacity: .78;
  transform: scale(.98);
}

.skeleton {
  background: linear-gradient(100deg, var(--mrc-surface-2) 20%, var(--mrc-surface) 40%, var(--mrc-surface-2) 60%);
  background-size: 200% 100%;
  animation: shimmer 1.2s infinite;
}

.hero-skeleton {
  height: 420rpx;
  border-radius: 40rpx;
}

.card-skeleton {
  height: 150rpx;
  margin-top: 20rpx;
  border-radius: 30rpx;
}

@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}

@keyframes detail-in {
  from {
    opacity: 0;
    transform: translateY(-10rpx);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 页面统一覆盖：与首页、菜谱详情共用同一套暖色卡片和行动层级。 */
.content,
.empty-state {
  padding: 12rpx 28rpx calc(72rpx + env(safe-area-inset-bottom));
}

.empty-hero {
  min-height: 360rpx;
  padding: 34rpx 30rpx 30rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 32rpx;
  background: radial-gradient(circle at 84% 16%, rgba(255, 197, 61, .18), transparent 28%), var(--mrc-surface);
  box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss);
}

.empty-hero__copy { width: 70%; }
.empty-hero__eyebrow, .eyebrow { font-size: 19rpx; letter-spacing: 3rpx; }
.empty-hero__title { margin-top: 18rpx; color: var(--mrc-text-strong); font-size: 39rpx; letter-spacing: -1.2rpx; line-height: 1.32; }
.empty-hero__description { max-width: 430rpx; margin-top: 18rpx; font-size: 23rpx; line-height: 1.55; }
.empty-hero__image { right: -4rpx; bottom: -8rpx; width: 236rpx; height: 236rpx; }
.empty-hero__orb--large { right: -86rpx; bottom: -112rpx; width: 310rpx; height: 310rpx; }

.empty-checks {
  z-index: auto;
  margin: 16rpx 0 0;
  padding: 24rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 28rpx;
  box-shadow: var(--mrc-shadow-soft);
}
.empty-checks__title { font-size: 27rpx; line-height: 1.4; }
.empty-checks__items { margin-top: 22rpx; }
.empty-check { min-height: 82rpx; border-radius: 20rpx; font-size: 21rpx; }

.empty-action {
  min-height: 96rpx;
  margin-top: 18rpx;
  padding: 0 28rpx;
  border-radius: 30rpx;
  box-shadow: var(--mrc-shadow-coral), var(--mrc-gloss);
  font-size: 27rpx;
}
.empty-footnote { margin-top: 14rpx; }

.cover {
  min-height: 238rpx;
  padding: 30rpx 28rpx 24rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 32rpx;
  box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss);
}
.cover image { width: 220rpx; height: 220rpx; }
.title { margin: 12rpx 0 10rpx; color: var(--mrc-text-strong); font-size: 38rpx; font-weight: 800; }
.card, .why { margin: 16rpx 0; padding: 24rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; }
.name { margin: 8rpx 0; color: var(--mrc-text-strong); font-size: 32rpx; font-weight: 800; }
.arrow { min-width: 88rpx; padding: 10rpx 14rpx; border-radius: var(--mrc-radius-pill); color: var(--mrc-accent); background: var(--mrc-accent-soft); font-size: 21rpx; text-align: center; }
.ops { margin-top: 14rpx; font-weight: 700; }
.detail { margin-top: 18rpx; padding-top: 16rpx; }
.record { display: flex; align-items: center; justify-content: center; min-height: 82rpx; margin-top: 22rpx; border-radius: 22rpx; color: var(--mrc-accent); background: var(--mrc-surface-peach); text-align: center; }
.notice { margin-top: 22rpx; padding: 0 8rpx; text-align: center; }
.hero-skeleton { height: 360rpx; border-radius: 32rpx; }
.card-skeleton { height: 142rpx; margin-top: 16rpx; border-radius: 28rpx; }
</style>
