<script setup lang="ts">
import type { DailyMealPlan } from '@/api/dailyMealPlan'
import { ref } from 'vue'
import { fetchDailyMealPlan, replaceDailyMealPlanMeal } from '@/api/dailyMealPlan'
import { STATIC_BASE_URL } from '@/utils/assets'
import { toastError } from '@/utils/toast'
import ErrorState from '@/components/guozai/ErrorState.vue'
import LoadingState from '@/components/guozai/LoadingState.vue'

definePage({
  name: 'daily-meal-plan',
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '今日三餐',
  },
})

import { useShare } from '@/composables/useShare'

// 让微信胶囊「···」可转发 / 分享到朋友圈
useShare()

const router = useRouter()
const loading = ref(true)
const plan = ref<DailyMealPlan>()
const empty = ref(false)
const expanded = ref(-1)
const replacing = ref(-1)

async function load() {
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
  }
}

function open(id?: number) {
  if (id)
    router.push({ name: 'recipe', params: { recipeId: String(id) } })
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

    <LoadingState v-if="loading" text="锅仔正在搭配今日三餐…" />

    <ErrorState
      v-else-if="empty"
      text="这顿还没配出来"
      subtext="锅仔宁可多想一会儿，也不会把没通过检查的菜单端上来。再试一次吧"
      action-text="重新生成一日三餐"
      @retry="load"
    />

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

.content {
  padding: 20rpx 30rpx calc(70rpx + env(safe-area-inset-bottom));
}

.eyebrow {
  display: block;
  color: var(--mrc-accent);
  font-size: 21rpx;
  font-weight: 800;
  letter-spacing: 2rpx;
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
.content {
  padding: 12rpx 28rpx calc(72rpx + env(safe-area-inset-bottom));
}

.eyebrow { font-size: 19rpx; letter-spacing: 3rpx; }

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
</style>
