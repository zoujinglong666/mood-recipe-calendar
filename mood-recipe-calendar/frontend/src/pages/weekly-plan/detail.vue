<script setup lang="ts">
import type { PlanDay, WeeklyPlan } from '@/api/weeklyPlans'
import { useImagePreview } from '@wot-ui/ui'
import { computed, nextTick, ref } from 'vue'
import { resolveAssetUrl } from '@/api/request'
import { generatePlanDishCover, getCurrentPlan, getWeeklyPlan, replacePlanDay, toggleShoppingItem } from '@/api/weeklyPlans'
import { navBack } from '@/composables/useNavBar'
import { exportRecipeShare, saveShareImage } from '@/utils/albumShare'
import { STATIC_BASE_URL } from '@/utils/assets'
import { toastError, toastSuccess } from '@/utils/toast'

definePage({ name: 'weekly-plan-detail', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '这一周吃什么' } })

const router = useRouter()
const route = useRoute()
const { previewImage } = useImagePreview()
const plan = ref<WeeklyPlan>()
const loading = ref(true)
const swapping = ref(-1)
const activeDay = ref(0)
const shoppingOpen = ref(false)
const detailsOpen = ref<Record<number, boolean>>({})
const coverLoading = ref<Record<string, boolean>>({})
const activeDish = ref<Record<number, number>>({})
const sharingDay = ref(-1)
let coverQueue = Promise.resolve()
const weekdayNames = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const groups = computed(() => ['肉蛋豆', '蔬菜', '主食', '调料']
  .map(category => ({ category, items: plan.value?.shopping.filter(item => item.category === category) || [] }))
  .filter(group => group.items.length))

function weekday(day: PlanDay | undefined, index: number) {
  return day?.day || weekdayNames[index] || `第${index + 1}天`
}

function dishesOf(day: PlanDay) {
  return day.dishes?.length ? day.dishes : [{ name: day.dishName, ingredients: day.ingredients, steps: day.steps, fallbackImageUrl: day.fallbackImageUrl }]
}

function currentDish(day: PlanDay, dayIndex: number) {
  return dishesOf(day)[activeDish.value[dayIndex] || 0] || dishesOf(day)[0]
}

function coverKey(dayIndex: number, dishIndex: number) {
  return `${dayIndex}-${dishIndex}`
}

function coverOf(day: PlanDay, dishIndex: number) {
  const dish = dishesOf(day)[dishIndex]
  return dish?.imageUrl || (dishIndex === 0 ? day.imageUrl : '') || dish?.fallbackImageUrl
}

function previewDishImages(day: PlanDay, dishIndex: number) {
  const covers = dishesOf(day)
    .map((_, index) => ({ index, url: resolveAssetUrl(coverOf(day, index)) }))
    .filter(item => item.url)
  if (!covers.length)
    return
  previewImage({
    images: covers.map(item => item.url),
    startPosition: Math.max(0, covers.findIndex(item => item.index === dishIndex)),
    closeOnClick: false,
    loop: covers.length > 1,
  })
}

function hasGeneratedCover(day: PlanDay | undefined, dishIndex: number) {
  const dish = day && dishesOf(day)[dishIndex]
  return Boolean(dish?.imageUrl || (dishIndex === 0 && day?.imageUrl))
}

function selectDay(index: number) {
  activeDay.value = index
  queueCovers(index)
}

function onDayChange(event: { detail: { current: number } }) {
  activeDay.value = event.detail.current
  queueCovers(activeDay.value)
}

function selectDish(dayIndex: number, dishIndex: number) {
  activeDish.value = { ...activeDish.value, [dayIndex]: dishIndex }
  queueCovers(dayIndex)
}

function onDishChange(dayIndex: number, event: { detail: { current: number } }) {
  selectDish(dayIndex, event.detail.current)
}

function toggleDetails(index: number) {
  detailsOpen.value = { ...detailsOpen.value, [index]: !detailsOpen.value[index] }
}

async function load() {
  loading.value = true
  try {
    const id = Number(route.query.id)
    plan.value = Number.isFinite(id) && id > 0 ? await getWeeklyPlan(id) : await getCurrentPlan()
    activeDay.value = 0
    queueCovers(0)
  }
  catch (error) {
    toastError(error, '还没有备餐计划')
    router.back()
  }
  finally {
    loading.value = false
  }
}

onShow(load)

function queueCovers(dayIndex: number) {
  coverQueue = coverQueue.then(async () => {
    const day = plan.value?.days[dayIndex]
    if (!day)
      return
    for (let dishIndex = 0; dishIndex < dishesOf(day).length; dishIndex++)
      await ensureDishCover(dayIndex, dishIndex)
  }).catch(() => undefined)
}

async function ensureDishCover(dayIndex: number, dishIndex: number) {
  const key = coverKey(dayIndex, dishIndex)
  if (!plan.value || hasGeneratedCover(plan.value.days[dayIndex], dishIndex) || coverLoading.value[key])
    return
  coverLoading.value = { ...coverLoading.value, [key]: true }
  try {
    plan.value = await generatePlanDishCover(plan.value.id, dayIndex, dishIndex)
  }
  catch {
    // 图片失败不会打断查看菜谱；保留已有菜谱图或占位。
  }
  finally {
    coverLoading.value = { ...coverLoading.value, [key]: false }
  }
}

async function replaceDay(index: number) {
  if (!plan.value || swapping.value >= 0)
    return
  swapping.value = index
  try {
    plan.value = await replacePlanDay(plan.value.id, index)
    activeDish.value = { ...activeDish.value, [index]: 0 }
    queueCovers(index)
  }
  catch (error) {
    toastError(error, '换菜失败，请重试')
  }
  finally {
    swapping.value = -1
  }
}

async function toggle(name: string) {
  if (!plan.value)
    return
  try {
    plan.value = await toggleShoppingItem(plan.value.id, name)
  }
  catch (error) {
    toastError(error, '清单更新失败')
  }
}

function record(dish: string) {
  uni.setStorageSync('mrc_record_draft', { dish, mood: '满足' })
  router.pushTab({ name: 'record' })
}

async function shareDay(day: PlanDay, index: number) {
  if (sharingDay.value >= 0)
    return
  const dishIndex = activeDish.value[index] || 0
  const dish = currentDish(day, index)
  if (!dish)
    return
  sharingDay.value = index
  try {
    await nextTick()
    const path = await exportRecipeShare({
      name: dish.name,
      mood: '满足',
      reason: day.healthTip,
      ingredients: dish.ingredients,
      steps: dish.steps,
      image: resolveAssetUrl(coverOf(day, dishIndex)),
      guozaiPath: `${STATIC_BASE_URL}/static/guozai/action_16_chopsticks.png`,
      style: 'guozai',
    }, 'weeklyRecipeShareCanvas')
    await saveShareImage(path, `${dish.name}-锅仔食谱卡.png`)
    toastSuccess('食谱卡已保存，可以分享给朋友')
  }
  catch (error) {
    toastError(error, '食谱卡生成失败，请重试')
  }
  finally {
    sharingDay.value = -1
  }
}
</script>

<template>
  <view class="detail-page">
    <wd-navbar title="这一周的晚餐" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <view v-if="loading" class="loading" aria-live="polite">
      锅仔正在翻开备餐小本…
    </view>

    <template v-else-if="plan">
      <view class="detail-hero">
        <image :src="`${STATIC_BASE_URL}/static/guozai/action_09_celebrate.png`" mode="aspectFit" aria-label="庆祝的锅仔" />
        <view>
          <text class="eyebrow">
            锅仔的一周备餐本
          </text>
          <text class="hero-title">
            一周安排好啦
          </text>
          <text class="hero-copy">
            一次只想一桌，慢慢把每餐做好。
          </text>
        </view>
      </view>

      <view class="week-switcher" role="tablist" aria-label="选择晚餐日期">
        <view
          v-for="(day, index) in plan.days"
          :key="index"
          class="week-tab"
          :class="{ 'week-tab--active': activeDay === index }"
          role="tab"
          :aria-selected="activeDay === index"
          :aria-label="`查看${weekday(day, index)}晚餐`"
          @click="selectDay(index)"
        >
          <text>{{ weekday(day, index) }}</text>
          <text class="week-tab__index">
            {{ index + 1 }}
          </text>
        </view>
      </view>

      <view class="day-progress" aria-live="polite">
        <text>{{ weekday(plan.days[activeDay], activeDay) }}晚餐</text>
        <text>{{ activeDay + 1 }} / {{ plan.days.length }}</text>
      </view>

      <swiper class="recipe-swiper" :current="activeDay" :duration="220" @change="onDayChange">
        <swiper-item v-for="(day, index) in plan.days" :key="`${day.day}-${index}`">
          <scroll-view class="recipe-scroll" scroll-y>
            <view class="day-card" :class="{ 'day-card--active': activeDay === index }">
              <view class="day-card__topline">
                <text class="day-label">
                  {{ weekday(day, index) }} · 今晚 {{ dishesOf(day).length }} 道菜
                </text>
                <view class="replace-action" role="button" :aria-label="`换掉${day.dishName}`" :aria-disabled="swapping >= 0" @click="replaceDay(index)">
                  {{ swapping === index ? '正在换菜…' : '换一桌' }}
                </view>
              </view>

              <swiper class="dish-cover-carousel" :current="activeDish[index] || 0" :duration="220" @change="onDishChange(index, $event)">
                <swiper-item v-for="(dish, dishIndex) in dishesOf(day)" :key="`${dish.name}-${dishIndex}`">
                  <view class="dish-cover" :class="{ 'dish-cover--loading': coverLoading[coverKey(index, dishIndex)] }">
                    <view v-if="coverOf(day, dishIndex)" class="dish-cover__preview" role="button" :aria-label="`预览${dish.name}菜品大图`" @click.stop="previewDishImages(day, dishIndex)">
                      <image :src="resolveAssetUrl(coverOf(day, dishIndex))" mode="aspectFill" />
                      <text class="dish-cover__preview-hint">
                        查看大图
                      </text>
                    </view>
                    <view v-else class="dish-cover__empty">
                      <text>{{ coverLoading[coverKey(index, dishIndex)] ? '锅仔正在画这道菜…' : '这道菜的照片正在路上' }}</text>
                    </view>
                    <text v-if="coverLoading[coverKey(index, dishIndex)]" class="dish-cover__loading">
                      正在生成菜品图
                    </text>
                    <text v-if="dishesOf(day).length > 1" class="dish-cover__count">
                      {{ dishIndex + 1 }} / {{ dishesOf(day).length }}
                    </text>
                  </view>
                </swiper-item>
              </swiper>

              <view v-if="dishesOf(day).length > 1" class="dish-pager" role="tablist" aria-label="选择今晚的菜品">
                <view v-for="(dish, dishIndex) in dishesOf(day)" :key="`${dish.name}-${dishIndex}`" class="dish-pager__item" :class="{ 'dish-pager__item--active': (activeDish[index] || 0) === dishIndex }" role="tab" :aria-selected="(activeDish[index] || 0) === dishIndex" :aria-label="`查看第${dishIndex + 1}道${dish.name}`" @click="selectDish(index, dishIndex)">
                  <text>{{ dishIndex + 1 }}</text><text>{{ dish.name }}</text>
                </view>
              </view>

              <text class="dish">
                {{ currentDish(day, index).name }}
              </text>
              <text class="health-tip">
                {{ day.healthTip }}
              </text>

              <view class="reuse-note">
                <text class="reuse-note__label">
                  食材安排
                </text>
                <text>{{ day.reuseHint }}</text>
              </view>

              <view class="share-day" :class="{ 'share-day--busy': sharingDay >= 0 }" role="button" :aria-label="`生成${currentDish(day, index).name}的食谱卡`" :aria-disabled="sharingDay >= 0" @click="shareDay(day, index)">
                <image :src="`${STATIC_BASE_URL}/static/guozai/action_16_chopsticks.png`" mode="aspectFit" aria-label="拿着筷子的锅仔" />
                <view>
                  <text class="share-day__title">
                    {{ sharingDay === index ? '锅仔正在排版食谱卡…' : '保存这道菜的食谱卡' }}
                  </text><text>带图片、材料清单和做法，直接分享给朋友</text>
                </view>
                <text class="share-day__arrow">
                  ›
                </text>
              </view>

              <view class="details-toggle" role="button" :aria-expanded="Boolean(detailsOpen[index])" :aria-label="detailsOpen[index] ? '收起食材与做法' : '展开食材与做法'" @click="toggleDetails(index)">
                <text>{{ detailsOpen[index] ? '收起食材与做法' : '看食材和做法' }}</text>
                <text class="details-toggle__arrow" :class="{ 'details-toggle__arrow--open': detailsOpen[index] }">
                  ⌄
                </text>
              </view>

              <view v-if="detailsOpen[index]" class="recipe-details">
                <view v-for="(dish, dishIndex) in dishesOf(day)" :key="`${dish.name}-${dishIndex}`" class="menu-dish">
                  <text class="menu-dish__title">
                    {{ dishIndex + 1 }}. {{ dish.name }}
                  </text>
                  <view class="ingredients">
                    <text v-for="item in dish.ingredients" :key="item">
                      {{ item }}
                    </text>
                  </view>
                  <view class="steps">
                    <view v-for="(step, stepIndex) in dish.steps" :key="step" class="step">
                      <text class="step__number">
                        {{ stepIndex + 1 }}
                      </text>
                      <text>{{ step }}</text>
                    </view>
                  </view>
                </view>
              </view>

              <view class="record" role="button" :aria-label="`把${day.dishName}记进时光机`" @click="record(day.dishName)">
                <view>
                  <text class="record__title">
                    做好这一桌？记进时光机
                  </text>
                  <text class="record__copy">
                    给这一顿留下一个小纪念
                  </text>
                </view>
                <text class="record__arrow">
                  ›
                </text>
              </view>
            </view>
          </scroll-view>
        </swiper-item>
      </swiper>

      <canvas id="weeklyRecipeShareCanvas" type="2d" class="weekly-recipe-share__canvas" />
      <wd-image-preview />

      <view class="shopping-panel">
        <view class="shopping-toggle" role="button" :aria-expanded="shoppingOpen" aria-label="展开或收起本周采购清单" @click="shoppingOpen = !shoppingOpen">
          <view>
            <text class="shopping-title">
              本周采购清单
            </text>
            <text class="shopping-copy">
              {{ plan.shopping.length }} 项食材，买菜时再打开
            </text>
          </view>
          <text class="details-toggle__arrow" :class="{ 'details-toggle__arrow--open': shoppingOpen }">
            ⌄
          </text>
        </view>

        <view v-if="shoppingOpen" class="shopping-list">
          <view v-for="group in groups" :key="group.category" class="shopping-group">
            <text class="group-title">
              {{ group.category }}
            </text>
            <view v-for="item in group.items" :key="item.name" class="shop-item" role="checkbox" :aria-checked="item.purchased" @click="toggle(item.name)">
              <text class="shop-item__check" :class="{ checked: item.purchased }">
                {{ item.purchased ? '✓' : '○' }}
              </text>
              <text class="shop-item__name" :class="{ done: item.purchased }">
                {{ item.name }}
              </text>
              <text class="shop-item__quantity">
                {{ item.quantity }}
              </text>
            </view>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.detail-page { min-height: 100vh; padding: 0 32rpx calc(50rpx + env(safe-area-inset-bottom)); box-sizing: border-box; background: var(--mrc-bg); }
.loading { padding: 180rpx 0; color: var(--mrc-text-sub); text-align: center; }
.detail-hero, .day-card, .shopping-panel { border: 2rpx solid var(--mrc-border-light); border-radius: 30rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.detail-hero { display: flex; align-items: center; gap: 12rpx; margin: 8rpx 0 20rpx; padding: 22rpx 24rpx; background: linear-gradient(135deg, var(--mrc-surface-sun), var(--mrc-surface-peach)); }
.detail-hero image { width: 132rpx; height: 132rpx; flex: 0 0 auto; }
.detail-hero text, .day-card text, .shopping-panel text { display: block; }
.eyebrow, .day-label, .reuse-note__label, .group-title { color: var(--mrc-accent); font-size: 21rpx; font-weight: 800; letter-spacing: 1rpx; }
.hero-title { margin-top: 4rpx; color: var(--mrc-text-strong); font-size: 32rpx; font-weight: 800; }
.hero-copy, .shopping-copy { margin-top: 6rpx; color: var(--mrc-text-sub); font-size: 23rpx; line-height: 1.55; }
.week-switcher { display: flex; gap: 8rpx; margin-bottom: 16rpx; }
.week-tab { display: flex; min-width: 0; min-height: 88rpx; flex: 1; flex-direction: column; align-items: center; justify-content: center; border: 2rpx solid transparent; border-radius: 20rpx; color: var(--mrc-text-sub); font-size: 20rpx; transition: transform 180ms cubic-bezier(.23, 1, .32, 1), background-color 180ms cubic-bezier(.23, 1, .32, 1), color 180ms cubic-bezier(.23, 1, .32, 1); }
.week-tab:active, .replace-action:active, .details-toggle:active, .record:active, .shopping-toggle:active, .shop-item:active { transform: scale(.98); opacity: .82; }
.week-tab--active { border-color: var(--mrc-border-light); background: var(--mrc-surface-peach); color: var(--mrc-text-strong); }
.week-tab__index { margin-top: 2rpx; font-size: 18rpx; opacity: .68; }
.day-progress { display: flex; justify-content: space-between; margin-bottom: 12rpx; color: var(--mrc-text-sub); font-size: 22rpx; }
.day-progress text:first-child { color: var(--mrc-text); font-weight: 700; }
.recipe-swiper { height: 980rpx; }
.recipe-scroll { height: 100%; box-sizing: border-box; }
.day-card { min-height: 720rpx; margin: 0 2rpx; padding: 30rpx; box-sizing: border-box; transition: transform 220ms cubic-bezier(.23, 1, .32, 1), opacity 180ms ease-out; }
.day-card--active { transform: translateY(0); opacity: 1; }
.day-card__topline, .shopping-toggle, .shop-item, .record, .step { display: flex; align-items: center; }
.day-card__topline, .shopping-toggle, .shop-item { justify-content: space-between; }
.replace-action { min-width: 112rpx; min-height: 72rpx; display: flex; align-items: center; justify-content: center; border-radius: 18rpx; background: var(--mrc-surface-peach); color: var(--mrc-accent); font-size: 22rpx; font-weight: 800; transition: transform 160ms ease-out, opacity 160ms ease-out; }
.dish-cover-carousel { height: 300rpx; margin-top: 24rpx; overflow: hidden; border-radius: 24rpx; }
.dish-cover { position: relative; width: 100%; height: 100%; overflow: hidden; border-radius: 24rpx; background: var(--mrc-surface-peach); }
.dish-cover__preview, .dish-cover__preview image, .dish-cover__empty { width: 100%; height: 100%; }
.dish-cover__preview-hint { position: absolute; top: 16rpx; right: 16rpx; min-height: 56rpx; display: flex !important; align-items: center; padding: 0 18rpx; border-radius: 28rpx; background: rgba(0, 0, 0, .55); color: #fff; font-size: 20rpx; font-weight: 700; }
.dish-cover__empty { display: flex; align-items: center; justify-content: center; padding: 32rpx; box-sizing: border-box; color: var(--mrc-text-sub); font-size: 24rpx; text-align: center; }
.dish-cover__loading, .dish-cover__count { position: absolute; bottom: 16rpx; padding: 8rpx 14rpx; border-radius: 14rpx; background: rgba(0, 0, 0, .55); color: #fff; font-size: 20rpx; }
.dish-cover__loading { right: 16rpx; }
.dish-cover__count { left: 16rpx; }
.dish-cover--loading image { opacity: .72; }
.dish-pager { display: flex; gap: 10rpx; margin-top: 16rpx; overflow-x: auto; }
.dish-pager__item { display: flex; min-width: 0; min-height: 64rpx; flex: 1; align-items: center; gap: 8rpx; padding: 0 14rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 16rpx; color: var(--mrc-text-sub); transition: transform 160ms ease-out, opacity 160ms ease-out, background-color 160ms ease-out; box-sizing: border-box; }
.dish-pager__item text:first-child { display: flex; width: 28rpx; height: 28rpx; flex: 0 0 auto; align-items: center; justify-content: center; border-radius: 50%; background: var(--mrc-border-light); color: var(--mrc-text-sub); font-size: 18rpx; font-weight: 800; }
.dish-pager__item text:last-child { overflow: hidden; font-size: 21rpx; font-weight: 700; white-space: nowrap; text-overflow: ellipsis; }
.dish-pager__item--active { border-color: var(--mrc-primary); background: var(--mrc-surface-peach); color: var(--mrc-text-strong); }
.dish-pager__item--active text:first-child { background: var(--mrc-primary); color: var(--mrc-surface); }
.dish-pager__item:active { transform: scale(.98); opacity: .82; }
.dish { margin-top: 34rpx; color: var(--mrc-text-strong); font-size: 48rpx; font-weight: 800; line-height: 1.22; }
.health-tip { margin-top: 18rpx; color: var(--mrc-text); font-size: 26rpx; line-height: 1.65; }
.reuse-note { margin-top: 26rpx; padding: 20rpx 22rpx; border-radius: 20rpx; background: var(--mrc-surface-sun); }
.reuse-note text:last-child { margin-top: 6rpx; color: var(--mrc-text-sub); font-size: 23rpx; line-height: 1.55; }
.share-day {
  display: flex;
  min-height: 112rpx;
  box-sizing: border-box;
  align-items: center;
  gap: 14rpx;
  margin-top: 18rpx;
  padding: 14rpx 18rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 22rpx;
  background: var(--mrc-surface-peach);
  transition: transform 160ms ease-out, opacity 160ms ease-out;
}

.share-day image { width: 76rpx; height: 76rpx; flex: 0 0 auto; }
.share-day view { min-width: 0; flex: 1; }
.share-day text { display: block; color: var(--mrc-text-sub); font-size: 21rpx; line-height: 1.45; }
.share-day__title { color: var(--mrc-text-strong) !important; font-size: 26rpx !important; font-weight: 800; }
.share-day__arrow { flex: 0 0 auto; color: var(--mrc-accent) !important; font-size: 42rpx !important; }
.share-day:active { transform: scale(.98); opacity: .82; }
.share-day--busy { opacity: .56; }
.weekly-recipe-share__canvas { position: fixed; top: -9999px; left: -9999px; width: 750px; height: 1500px; opacity: 0; pointer-events: none; }
.details-toggle { min-height: 96rpx; display: flex; align-items: center; justify-content: space-between; margin-top: 18rpx; padding: 0 4rpx; border-top: 2rpx solid var(--mrc-border-light); color: var(--mrc-text); font-size: 25rpx; font-weight: 700; transition: transform 160ms ease-out, opacity 160ms ease-out; }
.details-toggle__arrow { color: var(--mrc-text-sub); font-size: 32rpx; transition: transform 180ms cubic-bezier(.23, 1, .32, 1); }
.details-toggle__arrow--open { transform: rotate(180deg); }
.recipe-details { padding-bottom: 4rpx; }.menu-dish + .menu-dish { margin-top: 28rpx; padding-top: 28rpx; border-top: 2rpx dashed var(--mrc-border); }.menu-dish__title { margin-bottom: 16rpx; color: var(--mrc-text-strong); font-size: 28rpx; font-weight: 800; }
.ingredients { display: flex; flex-wrap: wrap; gap: 12rpx; }
.ingredients text { padding: 10rpx 16rpx; border-radius: 16rpx; background: var(--mrc-surface-peach); color: var(--mrc-text); font-size: 22rpx; }
.steps { margin-top: 24rpx; }
.step { align-items: flex-start; gap: 14rpx; margin-top: 14rpx; color: var(--mrc-text); font-size: 24rpx; line-height: 1.6; }
.step__number { width: 34rpx; height: 34rpx; flex: 0 0 auto; border-radius: 50%; background: var(--mrc-primary); color: var(--mrc-surface); font-size: 20rpx; font-weight: 800; line-height: 34rpx; text-align: center; }
.record { justify-content: space-between; gap: 20rpx; min-height: 108rpx; margin-top: 34rpx; padding: 18rpx 22rpx; border-radius: 22rpx; background: var(--mrc-primary); transition: transform 160ms ease-out, opacity 160ms ease-out; }
.record__title { color: var(--mrc-surface); font-size: 27rpx; font-weight: 800; }
.record__copy { margin-top: 4rpx; color: var(--mrc-surface); font-size: 21rpx; opacity: .78; }
.record__arrow { color: var(--mrc-surface); font-size: 48rpx; font-weight: 300; }
.shopping-panel { margin: 12rpx 2rpx 20rpx; overflow: hidden; }
.shopping-toggle { min-height: 112rpx; padding: 16rpx 26rpx; box-sizing: border-box; transition: transform 160ms ease-out, opacity 160ms ease-out; }
.shopping-title { color: var(--mrc-text-strong); font-size: 27rpx; font-weight: 800; }
.shopping-list { padding: 0 26rpx 26rpx; border-top: 2rpx solid var(--mrc-border-light); }
.shopping-group + .shopping-group { margin-top: 18rpx; }
.group-title { margin: 22rpx 0 6rpx; }
.shop-item { min-height: 84rpx; gap: 16rpx; border-bottom: 2rpx solid var(--mrc-border-light); transition: transform 160ms ease-out, opacity 160ms ease-out; }
.shop-item__check { width: 38rpx; color: var(--mrc-primary); font-size: 30rpx; text-align: center; }
.shop-item__name { flex: 1; color: var(--mrc-text); font-size: 25rpx; }
.shop-item__quantity { color: var(--mrc-text-sub); font-size: 22rpx; }
.done { color: var(--mrc-text-light) !important; text-decoration: line-through; }
.checked { font-weight: 800; }
@media (prefers-reduced-motion: reduce) { .week-tab, .day-card, .replace-action, .dish-pager__item, .share-day, .details-toggle, .details-toggle__arrow, .record, .shopping-toggle, .shop-item { transition: none; } }
</style>
