<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from '../../components/common/Icon.vue'
import { ensureLogin } from '../../utils/login'
import { fetchStats, fetchRecordsByMonth, type RecordItem, type StatsResult } from '../../api/records'

definePage({ name: 'home', layout: 'tabbar', style: { navigationStyle: 'custom', navigationBarTitleText: '首页' } })
const router = useRouter()
const isBouncing = ref(false)
function bounceGuozai() {
  isBouncing.value = true
  setTimeout(() => { isBouncing.value = false; router.push({ name: 'mood' }) }, 400)
}

const now = new Date()
const weekCN = ['日', '一', '二', '三', '四', '五', '六']
const dateTitle = `${now.getMonth() + 1}月${now.getDate()}日 周${weekCN[now.getDay()]}`
const currentMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
const loading = ref(true)
const stats = ref<StatsResult>({ totalRecords: 0, totalDays: 0, currentStreak: 0, longestStreak: 0, moodDistribution: {}, topDishes: [] })
const recordDays = ref<Set<number>>(new Set())
const calCells = computed(() => {
  const year = now.getFullYear()
  const month = now.getMonth()
  const firstDay = new Date(year, month, 1).getDay()
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const cells: ({ d: number; isToday: boolean; hasRecord: boolean } | null)[] = []
  for (let i = 0; i < firstDay; i++) cells.push(null)
  for (let d = 1; d <= daysInMonth; d++) cells.push({ d, isToday: d === now.getDate(), hasRecord: recordDays.value.has(d) })
  while (cells.length % 7 !== 0) cells.push(null)
  return cells
})
async function loadData() {
  loading.value = true
  try {
    const openid = await ensureLogin()
    const [statsData, monthRecords] = await Promise.all([fetchStats(openid), fetchRecordsByMonth(openid, currentMonth)])
    stats.value = statsData
    recordDays.value = new Set(monthRecords.map((r: RecordItem) => {
      const day = r.recordDate?.split('-')[2]
      return day ? parseInt(day, 10) : 0
    }).filter(d => d > 0))
  } catch (e: any) {
    uni.showToast({ title: e.message || '加载失败，请稍后重试', icon: 'none' })
  } finally { loading.value = false }
}
onShow(loadData)
const MOODS = ['开心', '平静', '疲惫', '焦虑', '难过', '嘴馋', '低落', '想家', '期待', '满足', '得意', '害羞']
function gotoLucky() { router.push({ name: 'recipe', query: { mood: MOODS[Math.floor(Math.random() * MOODS.length)], random: '1' } }) }
function goto(name: string, q?: Record<string, string>) { router.push({ name, query: q || {} }) }
</script>

<template>
  <view class="home mrc-hero">
    <wd-navbar title="心情菜谱日历" safe-area-inset-top  custom-style="background-color: transparent !important;" />
    <!-- 首页不展示整屏缺省图：直接渲染真实内容，数据就绪后响应式更新 -->
    <view class="home-hero" @click="bounceGuozai">
        <view class="home-hero__intro">
          <text class="home-hero__eyebrow">{{ dateTitle }}</text>
          <text class="home-hero__title">今天，想怎么照顾自己？</text>
          <text class="home-hero__copy">选个心情，锅仔来配一道刚刚好的菜</text>
        </view>
        <image class="home-hero__img" :class="{ 'guozai-breathe': !isBouncing, 'guozai-bounce': isBouncing }" src="/static/guozai/action_01_bowl.png" mode="aspectFit" />
        <view class="home-hero__profile" @click.stop="goto('profile')">
          <image src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
        </view>
        <view class="home-hero__bubble"><text>点我，告诉我现在的心情</text><text class="home-hero__bubble-arrow">›</text></view>
        <view class="home-hero__spark home-hero__spark--one" /><view class="home-hero__spark home-hero__spark--two" />
      </view>

      <view class="home-lucky" @click="gotoLucky">
        <view class="home-lucky__content">
          <view class="home-lucky__icon"><Icon name="dice" :size="42" color="var(--mrc-white)" /></view>
          <view><text class="home-lucky__eyebrow">没想法的时候</text><text class="home-lucky__main">让锅仔替你决定</text><text class="home-lucky__sub">抽一道今日治愈菜</text></view>
        </view>
        <view class="home-lucky__arrow">›</view>
      </view>

      <view class="home-actions">
        <view class="home-actions__item home-actions__item--recipe" @click="gotoLucky">
          <view class="home-actions__text"><text class="home-actions__label">今日菜单</text><text class="home-actions__name">再来一道</text></view>
          <Icon name="cart" :size="44" color="var(--mrc-accent)" />
        </view>
        <view class="home-actions__item home-actions__item--record" @click="goto('record')">
          <view class="home-actions__text"><text class="home-actions__label">吃过什么</text><text class="home-actions__name">记录一餐</text></view>
          <image class="home-actions__guozai" src="/static/guozai/action_03_camera.png" mode="aspectFit" />
        </view>
      </view>

      <view class="home-cal" @click="goto('calendar')">
        <view class="home-cal__header">
          <view><text class="home-cal__kicker">本月食光</text><text class="home-cal__date">已经好好吃饭 {{ stats.totalDays }} 天</text></view>
          <view class="home-cal__more"><text>月历</text><text>›</text></view>
        </view>
        <view class="home-cal__main"><view class="home-cal__main-grid">
          <text v-for="w in weekCN" :key="w" class="home-cal__main-w">{{ w }}</text>
          <block v-for="(c, i) in calCells" :key="i">
            <view v-if="c" class="home-cal__main-cell" :class="{ 'home-cal__main-cell--today': c.isToday, 'home-cal__main-cell--warm': c.hasRecord }"><text>{{ c.d }}</text></view>
            <view v-else class="home-cal__main-cell" />
          </block>
        </view></view>
        <view class="home-cal__footer">
          <view class="home-cal__legend"><view class="home-cal__dot home-cal__dot--recorded" /><text>已记录</text></view>
          <view class="home-cal__legend"><view class="home-cal__dot home-cal__dot--today" /><text>今天</text></view>
          <text class="home-cal__footer-note">去看看你的食光</text>
        </view>
      </view>
      <view class="home-slogan"><view class="home-slogan__line" /><text class="home-slogan__text">好好吃饭，也好好生活</text><view class="home-slogan__line" /></view>
  </view>
</template>

<style lang="scss" scoped>
.home { min-height: 100vh; padding: 0 32rpx; padding-bottom: calc(120rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
/* 锅仔是首页主舞台、心情入口与角色化引导，而不是独立装饰图。 */
.home-hero { position: relative; min-height: 500rpx; margin: 8rpx -8rpx 28rpx; overflow: hidden; border-radius: 40rpx; background: radial-gradient(circle at 76% 24%, rgba(255, 197, 61, 0.30) 0, rgba(255, 197, 61, 0) 22%), linear-gradient(145deg, #fffaf2 0%, #ffe7d5 100%); box-shadow: var(--mrc-shadow-soft); }
.home-hero__intro { position: relative; z-index: 2; display: flex; flex-direction: column; width: 72%; padding: 40rpx 0 0 36rpx; }
.home-hero__eyebrow, .home-cal__kicker { color: var(--mrc-accent); font-size: 22rpx; font-weight: 700; letter-spacing: 2rpx; }
.home-hero__title { margin-top: 12rpx; color: var(--mrc-text-strong); font-size: 40rpx; font-weight: 700; line-height: 1.3; }
.home-hero__copy { margin-top: 12rpx; color: var(--mrc-text-sub); font-size: 24rpx; }
.home-hero__img { position: absolute; z-index: 1; right: -26rpx; bottom: -18rpx; width: 470rpx; height: 470rpx; }
.home-hero__bubble { position: absolute; z-index: 3; left: 34rpx; bottom: 50rpx; display: flex; align-items: center; gap: 16rpx; max-width: 460rpx; padding: 20rpx 24rpx; border: 2rpx solid rgba(255, 255, 255, 0.75); border-radius: 28rpx 28rpx 28rpx 8rpx; background: rgba(255, 252, 247, 0.94); box-shadow: var(--mrc-shadow-sm); color: var(--mrc-text-deep); font-size: 25rpx; font-weight: 600; }
.home-hero__bubble-arrow { color: var(--mrc-accent); font-size: 42rpx; line-height: 24rpx; }
.home-hero__spark { position: absolute; z-index: 0; width: 18rpx; height: 18rpx; border-radius: 50%; background: var(--mrc-pop); }
.home-hero__spark--one { top: 178rpx; right: 72rpx; }.home-hero__spark--two { right: 310rpx; bottom: 98rpx; width: 12rpx; height: 12rpx; background: var(--mrc-primary); }
/* 我的入口：hero 右上角圆形锅仔头像（小程序端 navbar 右侧被胶囊遮挡，故移入内容区） */
.home-hero__profile { position: absolute; z-index: 4; top: 24rpx; right: 24rpx; width: 76rpx; height: 76rpx; border-radius: 50%; background: rgba(255, 255, 255, 0.88); box-shadow: var(--mrc-shadow-sm); display: flex; align-items: center; justify-content: center; overflow: hidden; }
.home-hero__profile image { width: 58rpx; height: 58rpx; }

/* 唯一强 CTA，减少一页内互相抢眼的高饱和元素。 */
.home-lucky { display: flex; align-items: center; justify-content: space-between; min-height: 156rpx; padding: 24rpx 32rpx; border-radius: 32rpx; background: var(--mrc-primary-grad); box-shadow: var(--mrc-shadow-coral), var(--mrc-gloss); box-sizing: border-box; }
.home-lucky__content { display: flex; align-items: center; gap: 20rpx; }.home-lucky__icon { display: flex; align-items: center; justify-content: center; width: 76rpx; height: 76rpx; border: 2rpx solid rgba(255, 255, 255, 0.38); border-radius: 24rpx; background: rgba(255, 255, 255, 0.18); }
.home-lucky__eyebrow, .home-lucky__main, .home-lucky__sub { display: block; }.home-lucky__eyebrow { margin-bottom: 4rpx; color: rgba(255, 255, 255, 0.78); font-size: 20rpx; }.home-lucky__main { color: var(--mrc-white); font-size: 32rpx; font-weight: 700; }.home-lucky__sub { margin-top: 4rpx; color: rgba(255, 255, 255, 0.85); font-size: 24rpx; }.home-lucky__arrow { margin-left: 12rpx; color: rgba(255, 255, 255, 0.92); font-size: 56rpx; font-weight: 300; }

.home-actions { display: flex; gap: 16rpx; margin: 24rpx 0 32rpx; }.home-actions__item { display: flex; flex: 1; align-items: center; justify-content: space-between; min-width: 0; height: 128rpx; padding: 0 20rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; box-shadow: var(--mrc-shadow-soft); box-sizing: border-box; }.home-actions__item--recipe { background: #fff5ea; }.home-actions__item--record { overflow: hidden; background: #eff9f3; }.home-actions__text { z-index: 1; display: flex; flex-direction: column; gap: 8rpx; }.home-actions__label { color: var(--mrc-text-sub); font-size: 21rpx; }.home-actions__name { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 700; white-space: nowrap; }.home-actions__guozai { width: 126rpx; height: 126rpx; margin-right: -16rpx; }

.home-cal { padding: 28rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 32rpx; background: var(--mrc-bg-card); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }.home-cal__header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24rpx; }.home-cal__date { display: block; margin-top: 4rpx; color: var(--mrc-text-deep); font-size: 32rpx; font-weight: 700; }.home-cal__more { display: flex; gap: 8rpx; color: var(--mrc-accent); font-size: 24rpx; font-weight: 600; }.home-cal__main { background: #fffdf9; border: 2rpx solid var(--mrc-border-light); border-radius: 20rpx; padding: 16rpx; }.home-cal__main-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 8rpx; text-align: center; }.home-cal__main-w { padding: 8rpx 0; color: var(--mrc-text-sub); font-size: 20rpx; font-weight: 600; }.home-cal__main-cell { padding: 8rpx 0; border-radius: 12rpx; color: var(--mrc-text); font-size: 22rpx; }.home-cal__main-cell--today { background: var(--mrc-accent-soft); outline: 2rpx solid var(--mrc-accent); color: var(--mrc-accent); font-weight: 700; }.home-cal__main-cell--warm { background: #ffe7d7; }.home-cal__footer { display: flex; align-items: center; gap: 20rpx; margin-top: 20rpx; color: var(--mrc-text-sub); font-size: 24rpx; }.home-cal__legend { display: flex; align-items: center; gap: 8rpx; font-size: 21rpx; }.home-cal__dot { width: 14rpx; height: 14rpx; border-radius: 50%; }.home-cal__dot--recorded { border: 2rpx solid var(--mrc-primary); background: #ffe7d7; }.home-cal__dot--today { background: var(--mrc-accent); }.home-cal__footer-note { margin-left: auto; font-size: 21rpx; }
.home-slogan { display: flex; align-items: center; gap: 16rpx; padding: 36rpx 12rpx 44rpx; }.home-slogan__text { flex: 0 0 auto; color: var(--mrc-text-light); font-size: 24rpx; letter-spacing: 2rpx; }.home-slogan__line { flex: 1; height: 2rpx; background: var(--mrc-border); }.home-lucky:active, .home-actions__item:active, .home-cal:active, .home-hero:active { transform: scale(0.985); }
</style>
