<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from '../../components/common/Icon.vue'
import { ensureLogin } from '../../utils/login'
import { toastError } from '../../utils/toast'
import { fetchCompanionMessage, fetchStats, fetchRecordsByMonth, type CompanionMessage, type RecordItem, type StatsResult } from '../../api/records'

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
/** 全部锅仔形象池（点击切换时遍历全部，不受时间段限制） */
const ALL_HERO_GUOZAI: HeroGuozai[] = [
  { img: '/static/guozai/action_01_bowl.png', name: '端碗锅仔' },
  { img: '/static/guozai/action_02_soup.png', name: '喝汤锅仔' },
  { img: '/static/guozai/action_06_glasses.png', name: '学者锅仔' },
  { img: '/static/guozai/action_07_empty.png', name: '空空锅仔' },
  { img: '/static/guozai/action_08_peek.png', name: '探头锅仔' },
  { img: '/static/guozai/action_09_celebrate.png', name: '庆祝锅仔' },
  { img: '/static/guozai/action_10_thinking.png', name: '思考锅仔' },
  { img: '/static/guozai/action_11_cooking.png', name: '厨师锅仔' },
  { img: '/static/guozai/action_12_heart.png', name: '比心锅仔' },
  { img: '/static/guozai/action_13_wave.png', name: '挥手锅仔' },
  { img: '/static/guozai/action_14_clap.png', name: '鼓掌锅仔' },
  { img: '/static/guozai/action_15_sleepy.png', name: '困困锅仔' },
  { img: '/static/guozai/action_16_chopsticks.png', name: '干饭锅仔' },
  { img: '/static/guozai/action_17_full.png', name: '饱饱锅仔' },
  { img: '/static/guozai/action_18_cheer.png', name: '加油锅仔' },
  { img: '/static/guozai/action_19_kungfu.png', name: '功夫锅仔' },
  { img: '/static/guozai/action_20_panda.png', name: '熊猫锅仔' },
]
/** 时间段 → 初始锅仔（与寄语呼应，仅用于首次展示） */
const HERO_GUOZAI_BY_PERIOD: Record<string, HeroGuozai[]> = {
  morning: [
    { img: '/static/guozai/action_01_bowl.png', name: '端碗锅仔' },
    { img: '/static/guozai/action_13_wave.png', name: '挥手锅仔' },
    { img: '/static/guozai/action_11_cooking.png', name: '厨师锅仔' },
  ],
  noon: [
    { img: '/static/guozai/action_02_soup.png', name: '喝汤锅仔' },
    { img: '/static/guozai/action_16_chopsticks.png', name: '干饭锅仔' },
    { img: '/static/guozai/action_17_full.png', name: '饱饱锅仔' },
  ],
  afternoon: [
    { img: '/static/guozai/action_10_thinking.png', name: '思考锅仔' },
    { img: '/static/guozai/action_14_clap.png', name: '鼓掌锅仔' },
    { img: '/static/guozai/action_12_heart.png', name: '比心锅仔' },
    { img: '/static/guozai/action_20_panda.png', name: '熊猫锅仔' },
  ],
  evening: [
    { img: '/static/guozai/action_09_celebrate.png', name: '庆祝锅仔' },
    { img: '/static/guozai/action_18_cheer.png', name: '加油锅仔' },
    { img: '/static/guozai/action_06_glasses.png', name: '学者锅仔' },
    { img: '/static/guozai/action_19_kungfu.png', name: '功夫锅仔' },
  ],
  late: [
    { img: '/static/guozai/action_08_peek.png', name: '探头锅仔' },
    { img: '/static/guozai/action_15_sleepy.png', name: '困困锅仔' },
    { img: '/static/guozai/action_07_empty.png', name: '空空锅仔' },
  ],
}
function getPeriod(hour: number) {
  return hour < 5 ? 'late' : hour < 11 ? 'morning' : hour < 15 ? 'noon' : hour < 18 ? 'afternoon' : hour < 22 ? 'evening' : 'late'
}
/** 初始按时间段选一个锅仔，找到它在全局池中的索引 */
const initialPeriodPool = HERO_GUOZAI_BY_PERIOD[getPeriod(now.getHours())] || HERO_GUOZAI_BY_PERIOD.morning
const initialGuozai = initialPeriodPool[now.getDate() % initialPeriodPool.length]
const initialGlobalIndex = Math.max(0, ALL_HERO_GUOZAI.findIndex(g => g.img === initialGuozai.img))
const heroGuozaiIndex = ref(initialGlobalIndex)
const heroGuozai = computed(() => ALL_HERO_GUOZAI[heroGuozaiIndex.value % ALL_HERO_GUOZAI.length])
const isHeroCycling = ref(false)
function cycleHeroGuozai() {
  if (isHeroCycling.value) return
  isHeroCycling.value = true
  heroGuozaiIndex.value = (heroGuozaiIndex.value + 1) % ALL_HERO_GUOZAI.length
  setTimeout(() => { isHeroCycling.value = false }, 400)
}
const loading = ref(true)
const stats = ref<StatsResult>({ totalRecords: 0, totalDays: 0, currentStreak: 0, longestStreak: 0, moodDistribution: {}, topDishes: [] })
const recordDays = ref<Set<number>>(new Set())
const companion = ref<CompanionMessage>(localCompanion(now.getHours()))
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
    const [statsData, monthRecords] = await Promise.all([fetchStats(openid), fetchRecordsByMonth(openid, currentMonth), loadCompanion()])
    stats.value = statsData
    recordDays.value = new Set(monthRecords.map((r: RecordItem) => {
      const day = r.recordDate?.split('-')[2]
      return day ? parseInt(day, 10) : 0
    }).filter(d => d > 0))
  } catch (e: any) {
    toastError(e, '加载失败，请稍后重试')
  } finally { loading.value = false }
}

function localCompanion(hour: number): CompanionMessage {
  if (hour >= 5 && hour < 11) return { greeting: '早上好，先把自己照顾好', message: '早饭不用复杂，热乎顺口就很好。', insight: '锅仔会慢慢记住你的口味', actionText: '告诉我现在的心情' }
  if (hour < 15) return { greeting: '到饭点了，别让肚子等太久', message: '忙归忙，午饭还是要认真吃。', insight: '每次选择，都让我更懂你', actionText: '告诉我现在的心情' }
  if (hour < 18) return { greeting: '下午好，想想今晚吃什么', message: '提前选好，饿的时候就不用匆忙决定。', insight: '锅仔正在学习你的饭点', actionText: '看看今天吃什么' }
  if (hour < 22) return { greeting: '晚上好，今天辛苦啦', message: '先用一顿合胃口的饭，把自己稳稳接住。', insight: '你的口味和忌口，我都会记得', actionText: '告诉我现在的心情' }
  return { greeting: '夜深了，吃点轻松温暖的', message: '选点清淡省事的，吃完也早点休息。', insight: '晚睡的时候，锅仔也在', actionText: '看看适合夜晚的菜' }
}

async function loadCompanion() {
  const current = new Date()
  const hour = current.getHours()
  const period = hour < 5 ? 'late' : hour < 11 ? 'morning' : hour < 15 ? 'noon' : hour < 18 ? 'afternoon' : hour < 22 ? 'evening' : 'late'
  const cacheKey = `${current.getFullYear()}-${current.getMonth() + 1}-${current.getDate()}-${period}`
  try {
    const cached = uni.getStorageSync('mrc_companion_message')
    if (cached?.key === cacheKey) {
      companion.value = cached.value
      return
    }
    const value = await fetchCompanionMessage(hour)
    companion.value = value
    uni.setStorageSync('mrc_companion_message', { key: cacheKey, value })
  } catch {
    companion.value = localCompanion(hour)
  }
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
    <view class="home-hero" role="button" aria-label="告诉锅仔我现在的心情" @click="bounceGuozai">
        <view class="home-hero__intro">
          <text class="home-hero__eyebrow">{{ dateTitle }}</text>
          <text class="home-hero__title">{{ companion.greeting }}</text>
          <text class="home-hero__copy">{{ companion.message }}</text>
          <view class="home-hero__memory">
            <view class="home-hero__memory-dot" />
            <view>
              <text class="home-hero__memory-title">{{ heroGuozai.name }}</text>
              <text class="home-hero__memory-copy">{{ companion.insight }}</text>
            </view>
          </view>
        </view>
        <image class="home-hero__img" :class="{ 'guozai-breathe': !isBouncing && !isHeroCycling, 'guozai-bounce': isBouncing, 'guozai-cycle': isHeroCycling }" :src="heroGuozai.img" :key="heroGuozai.img" mode="aspectFit" role="button" aria-label="点击切换锅仔形象" @click.stop="cycleHeroGuozai" />
        <view class="home-hero__profile" role="button" aria-label="打开我的页面" @click.stop="goto('profile')">
          <image src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
        </view>
        <view class="home-hero__bubble"><text>{{ companion.actionText }}</text><text class="home-hero__bubble-arrow">›</text></view>
        <view class="home-hero__spark home-hero__spark--one" /><view class="home-hero__spark home-hero__spark--two" />
      </view>

      <view class="home-lucky" role="button" aria-label="让锅仔随机推荐一道菜" @click="gotoLucky">
        <view class="home-lucky__content">
          <image class="home-lucky__guozai" src="/static/guozai/action_10_thinking.png" mode="aspectFit" />
          <view><text class="home-lucky__eyebrow">没想法的时候</text><text class="home-lucky__main">让锅仔替你决定</text><text class="home-lucky__sub">抽一道今日治愈菜</text></view>
        </view>
        <view class="home-lucky__arrow">›</view>
      </view>

      <view class="home-actions">
        <view class="home-actions__item home-actions__item--recipe" role="button" aria-label="再推荐一道菜" @click="gotoLucky">
          <view class="home-actions__text"><text class="home-actions__label">今日菜单</text><text class="home-actions__name">再来一道</text></view>
          <image class="home-actions__guozai" src="/static/guozai/action_02_soup.png" mode="aspectFit" />
        </view>
        <view class="home-actions__item home-actions__item--record" role="button" aria-label="记录一餐" @click="router.pushTab({ name: 'record' })">
          <view class="home-actions__text"><text class="home-actions__label">吃过什么</text><text class="home-actions__name">记录一餐</text></view>
          <image class="home-actions__guozai" src="/static/guozai/action_03_camera.png" mode="aspectFit" />
        </view>
      </view>

      <view class="home-cal" role="button" aria-label="打开本月心情日历" @click="goto('calendar')">
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
.home-hero { position: relative; min-height: 500rpx; margin: 8rpx -8rpx 28rpx; overflow: hidden; border: 2rpx solid var(--mrc-border); border-radius: 40rpx; background: radial-gradient(circle at 78% 18%, rgba(255, 197, 61, 0.22) 0, rgba(255, 197, 61, 0) 28%), linear-gradient(145deg, var(--mrc-surface) 0%, var(--mrc-surface-peach) 100%); box-shadow: var(--mrc-shadow-lift), var(--mrc-gloss); }
.home-hero__intro { position: relative; z-index: 2; display: flex; flex-direction: column; width: 72%; padding: 40rpx 0 0 36rpx; }
.home-hero__eyebrow, .home-cal__kicker { color: var(--mrc-accent); font-size: 22rpx; font-weight: 700; letter-spacing: 2rpx; }
.home-hero__title { margin-top: 12rpx; color: var(--mrc-text-strong); font-size: 40rpx; font-weight: 700; line-height: 1.3; }
.home-hero__copy { margin-top: 12rpx; color: var(--mrc-text-sub); font-size: 24rpx; }
.home-hero__memory { display: flex; align-items: center; align-self: flex-start; gap: 14rpx; max-width: 330rpx; margin-top: 22rpx; padding: 16rpx 20rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 22rpx; background: var(--mrc-surface-sun); box-shadow: var(--mrc-shadow-sm); }
.home-hero__memory-dot { width: 16rpx; height: 16rpx; flex-shrink: 0; border: 5rpx solid var(--mrc-accent-soft); border-radius: 50%; background: var(--mrc-accent); }
.home-hero__memory-title, .home-hero__memory-copy { display: block; }
.home-hero__memory-title { color: var(--mrc-text-deep); font-size: 23rpx; font-weight: 800; }
.home-hero__memory-copy { margin-top: 4rpx; color: var(--mrc-text-sub); font-size: 19rpx; line-height: 1.35; }
.home-hero__img { position: absolute; z-index: 1; right: -18rpx; bottom: -12rpx; width: 438rpx; height: 438rpx; animation: guozai-hero-in 0.45s ease; }
@keyframes guozai-hero-in { from { opacity: 0; transform: scale(0.88) translateY(12rpx); } to { opacity: 1; transform: scale(1) translateY(0); } }
.guozai-cycle { animation: guozai-cycle-pop 0.4s ease !important; }
@keyframes guozai-cycle-pop { 0% { transform: scale(1) rotate(0); } 40% { transform: scale(1.12) rotate(-6deg); } 70% { transform: scale(0.95) rotate(3deg); } 100% { transform: scale(1) rotate(0); } }
.home-hero__bubble { position: absolute; z-index: 3; left: 34rpx; bottom: 40rpx; display: flex; align-items: center; gap: 16rpx; max-width: 460rpx; min-height: 88rpx; box-sizing: border-box; padding: 16rpx 22rpx; border: 2rpx solid var(--mrc-border); border-radius: 28rpx 28rpx 28rpx 8rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-sm); color: var(--mrc-text-deep); font-size: 25rpx; font-weight: 700; }
.home-hero__bubble-arrow { color: var(--mrc-accent); font-size: 42rpx; line-height: 24rpx; }
.home-hero__spark { position: absolute; z-index: 0; width: 18rpx; height: 18rpx; border-radius: 50%; background: var(--mrc-pop); }
.home-hero__spark--one { top: 178rpx; right: 72rpx; }.home-hero__spark--two { right: 310rpx; bottom: 98rpx; width: 12rpx; height: 12rpx; background: var(--mrc-primary); }
/* 我的入口：hero 右上角圆形锅仔头像（小程序端 navbar 右侧被胶囊遮挡，故移入内容区） */
.home-hero__profile { position: absolute; z-index: 4; top: 24rpx; right: 24rpx; width: 88rpx; height: 88rpx; border: 2rpx solid var(--mrc-border); border-radius: 50%; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-sm); display: flex; align-items: center; justify-content: center; overflow: hidden; }
.home-hero__profile image { width: 58rpx; height: 58rpx; }

/* 唯一强 CTA，减少一页内互相抢眼的高饱和元素。 */
.home-lucky { display: flex; align-items: center; justify-content: space-between; min-height: 156rpx; padding: 24rpx 32rpx; border-radius: 32rpx; background: var(--mrc-primary-grad); box-shadow: var(--mrc-shadow-coral), var(--mrc-gloss); box-sizing: border-box; }
.home-lucky__content { display: flex; align-items: center; gap: 20rpx; }.home-lucky__guozai { width: 88rpx; height: 88rpx; flex-shrink: 0; filter: drop-shadow(0 4rpx 8rpx rgba(0,0,0,0.12)); }
.home-lucky__eyebrow, .home-lucky__main, .home-lucky__sub { display: block; }.home-lucky__eyebrow { margin-bottom: 4rpx; color: rgba(255, 255, 255, 0.82); font-size: 20rpx; }.home-lucky__main { color: #fff; font-size: 32rpx; font-weight: 700; }.home-lucky__sub { margin-top: 4rpx; color: rgba(255, 255, 255, 0.9); font-size: 24rpx; }.home-lucky__arrow { margin-left: 12rpx; color: rgba(255, 255, 255, 0.95); font-size: 56rpx; font-weight: 300; }

.home-actions { display: flex; gap: 16rpx; margin: 24rpx 0 32rpx; }.home-actions__item { display: flex; flex: 1; align-items: center; justify-content: space-between; min-width: 0; min-height: 128rpx; padding: 0 20rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); box-sizing: border-box; }.home-actions__item--recipe { background: var(--mrc-surface-peach); }.home-actions__item--record { overflow: hidden; background: var(--mrc-surface-mint); }.home-actions__text { z-index: 1; display: flex; flex-direction: column; gap: 8rpx; }.home-actions__label { color: var(--mrc-text-sub); font-size: 21rpx; }.home-actions__name { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 700; white-space: nowrap; }.home-actions__guozai { width: 126rpx; height: 126rpx; margin-right: -16rpx; }

.home-cal { padding: 28rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 32rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }.home-cal__header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24rpx; }.home-cal__date { display: block; margin-top: 4rpx; color: var(--mrc-text-deep); font-size: 32rpx; font-weight: 700; }.home-cal__more { display: flex; align-items: center; min-height: 88rpx; gap: 8rpx; color: var(--mrc-accent); font-size: 24rpx; font-weight: 700; }.home-cal__main { background: var(--mrc-surface-2); border: 2rpx solid var(--mrc-border-light); border-radius: 20rpx; padding: 16rpx; }.home-cal__main-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 8rpx; text-align: center; }.home-cal__main-w { padding: 8rpx 0; color: var(--mrc-text-sub); font-size: 20rpx; font-weight: 600; }.home-cal__main-cell { padding: 8rpx 0; border-radius: 12rpx; color: var(--mrc-text); font-size: 22rpx; }.home-cal__main-cell--today { background: var(--mrc-accent-soft); outline: 2rpx solid var(--mrc-accent); color: var(--mrc-accent); font-weight: 700; }.home-cal__main-cell--warm { background: var(--mrc-surface-peach); color: var(--mrc-text-deep); font-weight: 600; }.home-cal__footer { display: flex; align-items: center; gap: 20rpx; margin-top: 20rpx; color: var(--mrc-text-sub); font-size: 24rpx; }.home-cal__legend { display: flex; align-items: center; gap: 8rpx; font-size: 21rpx; }.home-cal__dot { width: 14rpx; height: 14rpx; border-radius: 50%; }.home-cal__dot--recorded { border: 2rpx solid var(--mrc-primary); background: var(--mrc-surface-peach); }.home-cal__dot--today { background: var(--mrc-accent); }.home-cal__footer-note { margin-left: auto; font-size: 21rpx; }
.home-slogan { display: flex; align-items: center; gap: 16rpx; padding: 36rpx 12rpx 44rpx; }.home-slogan__text { flex: 0 0 auto; color: var(--mrc-text-light); font-size: 24rpx; letter-spacing: 2rpx; }.home-slogan__line { flex: 1; height: 2rpx; background: var(--mrc-border); }.home-lucky:active, .home-actions__item:active, .home-cal:active, .home-hero:active { transform: scale(0.985); }
</style>
