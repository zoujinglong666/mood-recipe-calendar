<script setup lang="ts">
import { ref } from 'vue'
import { generateWeeklyPlan, getCurrentPlan } from '@/api/weeklyPlans'
import { navBack } from '@/composables/useNavBar'
import { toastError } from '@/utils/toast'

definePage({ name: 'weekly-plan', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '锅仔备餐小本' } })
const router = useRouter()
const people = ref(3)
const healthGoal = ref('BALANCED')
const generating = ref(false)
const goals = [{ value: 'BALANCED', label: '吃得均衡' }, { value: 'FITNESS', label: '健身增肌' }, { value: 'LEAN', label: '轻盈减脂' }]

onShow(async () => {
  try {
    await getCurrentPlan()
    router.replace({ name: 'weekly-plan-detail' })
  }
  catch {}
})
async function generate() {
  if (generating.value)
    return
  generating.value = true
  try {
    await generateWeeklyPlan({ people: people.value, days: 7, healthGoal: healthGoal.value })
    router.replace({ name: 'weekly-plan-detail' })
  }
  catch (error) { toastError(error, '锅仔暂时没排好这一周，请重试') }
  finally { generating.value = false }
}
</script>

<template>
  <view class="plan-page">
    <wd-navbar title="锅仔备餐小本" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />
    <view class="plan-hero">
      <image src="/static/guozai/action_10_thinking.png" mode="aspectFit" /><view><text>这一周，交给锅仔安排</text><text>少想一点吃什么，多留一点时间给生活。</text></view>
    </view>
    <view class="plan-card">
      <text class="title">
        几个人吃晚饭？
      </text><view class="counter">
        <text role="button" @click="people = Math.max(1, people - 1)">
          −
        </text><text>{{ people }} 人</text><text role="button" @click="people = Math.min(8, people + 1)">
          ＋
        </text>
      </view>
    </view>
    <view class="plan-card">
      <text class="title">
        这阵子想怎么吃？
      </text><view class="goals">
        <view v-for="goal in goals" :key="goal.value" :class="{ selected: healthGoal === goal.value }" @click="healthGoal = goal.value">
          {{ goal.label }}
        </view>
      </view>
    </view>
    <view class="notice">
      默认安排 7 天晚餐；锅仔会带入你的口味、忌口和健康目标。菜价会随地区与季节变化。
    </view>
    <button class="generate" :disabled="generating" @click="generate">
      {{ generating ? '锅仔正在安排菜单…' : '生成这一周的晚餐' }}
    </button>
  </view>
</template>

<style lang="scss" scoped>
.plan-page { min-height: 100vh; padding: 0 32rpx calc(56rpx + env(safe-area-inset-bottom)); color: var(--mrc-text); background: var(--mrc-bg); box-sizing: border-box; }
.plan-hero { display:flex; align-items:center; gap:18rpx; padding:28rpx; border-radius:32rpx; background:linear-gradient(135deg,var(--mrc-surface-sun),var(--mrc-surface-peach)); box-shadow:var(--mrc-shadow-soft); }.plan-hero image { width:164rpx;height:164rpx; }.plan-hero text { display:block; }.plan-hero text:first-child,.title { color:var(--mrc-text-strong);font-size:31rpx;font-weight:800; }.plan-hero text:last-child,.notice { margin-top:10rpx;color:var(--mrc-text-sub);font-size:23rpx;line-height:1.55; }
.plan-card { margin-top:22rpx;padding:28rpx;border:2rpx solid var(--mrc-border-light);border-radius:28rpx;background:var(--mrc-surface);box-shadow:var(--mrc-shadow-soft); }.counter { display:flex;justify-content:space-between;align-items:center;margin-top:22rpx; }.counter text { display:flex;width:82rpx;height:82rpx;align-items:center;justify-content:center;border-radius:50%;background:var(--mrc-surface-peach);font-size:34rpx;font-weight:800; }.counter text:nth-child(2){width:auto;background:transparent;color:var(--mrc-accent);font-size:32rpx;}
.goals { display:flex;gap:12rpx;margin-top:22rpx; }.goals view { flex:1;padding:20rpx 8rpx;border:2rpx solid var(--mrc-border);border-radius:20rpx;text-align:center;font-size:23rpx;font-weight:700; }.goals .selected { border-color:var(--mrc-primary);color:var(--mrc-accent);background:var(--mrc-surface-peach); }.notice { padding:22rpx 12rpx; }.generate { width:100%;min-height:96rpx;border:0;border-radius:48rpx;color:#fff;background:var(--mrc-primary-grad);font-size:29rpx;font-weight:800; }.generate::after{border:0;}
</style>
