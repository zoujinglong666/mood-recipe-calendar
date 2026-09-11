<script setup lang="ts">
import type { WeeklyPlan } from '@/api/weeklyPlans'
import { computed, ref } from 'vue'
import { getCurrentPlan, replacePlanDay, toggleShoppingItem } from '@/api/weeklyPlans'
import { navBack } from '@/composables/useNavBar'
import { toastError } from '@/utils/toast'

definePage({ name: 'weekly-plan-detail', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '这一周吃什么' } })
const router = useRouter()
const plan = ref<WeeklyPlan>()
const loading = ref(true)
const swapping = ref(-1)
const groups = computed(() => ['肉蛋豆', '蔬菜', '主食', '调料'].map(category => ({ category, items: plan.value?.shopping.filter(item => item.category === category) || [] })).filter(group => group.items.length))
async function load() {
  loading.value = true
  try {
    plan.value = await getCurrentPlan()
  }
  catch (error) {
    toastError(error, '还没有备餐计划')
    router.back()
  }
  finally { loading.value = false }
}
onShow(load)
async function replaceDay(index: number) {
  if (!plan.value || swapping.value >= 0)
    return
  swapping.value = index
  try {
    plan.value = await replacePlanDay(plan.value.id, index)
  }
  catch (error) { toastError(error, '换菜失败，请重试') }
  finally { swapping.value = -1 }
}
async function toggle(name: string) {
  if (!plan.value)
    return
  try {
    plan.value = await toggleShoppingItem(plan.value.id, name)
  }
  catch (error) { toastError(error, '清单更新失败') }
}
function record(dish: string) {
  uni.setStorageSync('mrc_record_draft', { dish, mood: '满足' })
  router.pushTab({ name: 'record' })
}
</script>

<template>
  <view class="detail-page">
    <wd-navbar title="这一周的晚餐" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />
    <view v-if="loading" class="loading">
      锅仔正在翻开备餐小本…
    </view><template v-else-if="plan">
      <view class="detail-hero">
        <image src="/static/guozai/action_09_celebrate.png" mode="aspectFit" /><view><text>一周安排好啦</text><text>买一次菜，慢慢把每一顿做好。</text></view>
      </view><view v-for="(day, index) in plan.days" :key="day.day" class="day-card">
        <view class="day-head">
          <text>{{ day.day }}</text><text role="button" @click="replaceDay(index)">
            {{ swapping === index ? '换菜中…' : '换一道' }}
          </text>
        </view><text class="dish">
          {{ day.dishName }}
        </text><text class="tip">
          {{ day.healthTip }}
        </text><text class="reuse">
          {{ day.reuseHint }}
        </text><view class="ingredients">
          <text v-for="item in day.ingredients" :key="item">
            {{ item }}
          </text>
        </view><view class="steps">
          <text v-for="(step, i) in day.steps" :key="step">
            {{ i + 1 }}. {{ step }}
          </text>
        </view><view class="record" role="button" @click="record(day.dishName)">
          做完这一餐，记进时光机 ›
        </view>
      </view><view class="shopping">
        <text class="shopping-title">
          一次买齐的清单
        </text><view v-for="group in groups" :key="group.category">
          <text class="group-title">
            {{ group.category }}
          </text><view v-for="item in group.items" :key="item.name" class="shop-item" @click="toggle(item.name)">
            <text :class="{ checked: item.purchased }">
              {{ item.purchased ? '✓' : '○' }}
            </text><text :class="{ done: item.purchased }">
              {{ item.name }}
            </text><text>{{ item.quantity }}</text>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.detail-page{min-height:100vh;padding:0 32rpx calc(50rpx + env(safe-area-inset-bottom));background:var(--mrc-bg);box-sizing:border-box}.loading{padding:180rpx 0;text-align:center;color:var(--mrc-text-sub)}.detail-hero,.day-card,.shopping{margin-bottom:20rpx;padding:26rpx;border:2rpx solid var(--mrc-border-light);border-radius:28rpx;background:var(--mrc-surface);box-shadow:var(--mrc-shadow-soft)}.detail-hero{display:flex;align-items:center;background:linear-gradient(135deg,var(--mrc-surface-sun),var(--mrc-surface-peach))}.detail-hero image{width:130rpx;height:130rpx}.detail-hero text{display:block}.detail-hero text:first-child,.dish,.shopping-title{color:var(--mrc-text-strong);font-size:30rpx;font-weight:800}.detail-hero text:last-child,.tip,.reuse{margin-top:8rpx;color:var(--mrc-text-sub);font-size:22rpx;line-height:1.5}.day-head,.shop-item{display:flex;justify-content:space-between}.day-head text:first-child{color:var(--mrc-accent);font-weight:800}.day-head text:last-child{color:var(--mrc-text-sub);font-size:23rpx}.dish,.tip,.reuse,.steps text{display:block}.dish{margin-top:18rpx}.reuse{color:var(--mrc-accent)}.ingredients{display:flex;flex-wrap:wrap;gap:10rpx;margin-top:18rpx}.ingredients text{padding:8rpx 14rpx;border-radius:16rpx;background:var(--mrc-surface-peach);color:var(--mrc-text-sub);font-size:21rpx}.steps{margin-top:18rpx;padding-top:14rpx;border-top:2rpx solid var(--mrc-border-light)}.steps text{margin-top:8rpx;color:var(--mrc-text);font-size:23rpx;line-height:1.5}.record{margin-top:20rpx;color:var(--mrc-accent);font-size:24rpx;font-weight:800}.group-title{display:block;margin:24rpx 0 8rpx;color:var(--mrc-text-sub);font-size:22rpx;font-weight:700}.shop-item{min-height:72rpx;align-items:center;border-bottom:2rpx solid var(--mrc-border-light);color:var(--mrc-text)}.shop-item text:first-child{color:var(--mrc-primary);font-size:30rpx}.shop-item text:last-child{color:var(--mrc-text-sub);font-size:22rpx}.done{text-decoration:line-through;color:var(--mrc-text-light)!important}.checked{font-weight:800}
</style>
