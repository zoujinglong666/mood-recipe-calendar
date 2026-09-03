<script setup lang="ts">
import { computed, ref } from 'vue'
import AppNav from '../../components/common/AppNav.vue'
import Icon from '../../components/common/Icon.vue'
import LoadingState from '../../components/guozai/LoadingState.vue'
import { ensureLogin } from '../../utils/login'
import { fetchStats, fetchRecordsByMonth, type RecordItem } from '../../api/records'

definePage({
  name: 'home',
  layout: 'tabbar',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '首页',
  },
})

const router = useRouter()

// 锅仔点击弹跳
const isBouncing = ref(false)
function bounceGuozai() {
  isBouncing.value = true
  setTimeout(() => {
    isBouncing.value = false
    router.push({ name: 'mood' })
  }, 400)
}

// ---- 日期数据 ----
const now = new Date()
const weekCN = ['日', '一', '二', '三', '四', '五', '六']
const dateTitle = `${now.getMonth() + 1}月${now.getDate()}日 周${weekCN[now.getDay()]}`
const currentMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`

// 本月月历
const calCells = computed(() => {
  const year = now.getFullYear()
  const month = now.getMonth()
  const firstDay = new Date(year, month, 1).getDay()
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const cells: ({ d: number; isToday: boolean; hasRecord: boolean } | null)[] = []
  for (let i = 0; i < firstDay; i++) cells.push(null)
  for (let d = 1; d <= daysInMonth; d++) {
    cells.push({
      d,
      isToday: d === now.getDate(),
      hasRecord: recordDays.value.has(d),
    })
  }
  while (cells.length % 7 !== 0) cells.push(null)
  return cells
})

// ---- 动态数据 ----
const loading = ref(true)
const stats = ref({ totalRecords: 0, totalDays: 0, currentStreak: 0, longestStreak: 0, moodDistribution: {}, topDishes: [] })
const recordDays = ref<Set<number>>(new Set())

async function loadData() {
  loading.value = true
  try {
    const openid = await ensureLogin()
    const [statsData, monthRecords] = await Promise.all([
      fetchStats(openid),
      fetchRecordsByMonth(openid, currentMonth),
    ])
    stats.value = statsData
    recordDays.value = new Set(
      monthRecords.map((r: RecordItem) => {
        const day = r.recordDate?.split('-')[2]
        return day ? parseInt(day, 10) : 0
      }).filter(d => d > 0)
    )
  } catch (e: any) {
    // 首页登录/加载失败不展示缺省图，仅 toast 轻提示
    uni.showToast({ title: e.message || '加载失败，请稍后重试', icon: 'none' })
  } finally {
    loading.value = false
  }
}

onShow(() => {
  loadData()
})

// 今日幸运菜：随机选一个心情
const MOODS = ['开心', '平静', '疲惫', '焦虑', '难过', '嘴馋', '低落', '想家']
function gotoLucky() {
  const mood = MOODS[Math.floor(Math.random() * MOODS.length)]
  router.push({ name: 'recipe', query: { mood, random: '1' } })
}

function goto(name: string, q?: Record<string, string>) {
  router.push({ name, query: q || {} })
}
</script>

<template>
  <view class="home">
    <AppNav title="心情菜谱日历" @nav-right="goto('profile')" />

    <!-- Loading -->
    <LoadingState v-if="loading" text="锅仔正在准备..." />

    <!-- 内容 -->
    <template v-else>
      <!-- 锅仔 hero -->
      <view class="home-hero" @click="bounceGuozai">
        <view class="home-hero__bubble">今天想吃什么呀？</view>
        <image
          class="home-hero__img"
          :class="{ 'guozai-breathe': !isBouncing, 'guozai-bounce': isBouncing }"
          src="/static/guozai/action_01_bowl.png"
          mode="aspectFit"
        />
        <text class="home-hero__hint">点我 · 选一个心情</text>
      </view>

      <!-- 今日幸运菜 -->
      <view class="home-lucky" @click="gotoLucky">
        <view class="home-lucky__row">
          <Icon name="dice" :size="48" color="#fff" />
          <text class="home-lucky__main">今日幸运菜</text>
        </view>
        <text class="home-lucky__sub">随机一道治愈菜谱</text>
      </view>

      <!-- 功能条 -->
      <view class="home-actions">
        <view class="home-actions__item" @click="gotoLucky">
          <Icon name="camera" :size="40" color="#C9A87C" />
          <text>随机一道治愈菜谱</text>
        </view>
        <view class="home-actions__divider" />
        <view class="home-actions__item" @click="goto('record')">
          <text>记录今日伙食</text>
        </view>
      </view>

      <!-- 日历卡片 -->
      <view class="home-cal" @click="goto('calendar')">
        <view class="home-cal__header">
          <text class="home-cal__date">{{ dateTitle }}</text>
          <Icon name="heart" :size="32" color="#E07A5F" />
        </view>
        <view class="home-cal__body">
          <!-- 迷你台历 -->
          <view class="home-cal__mini">
            <view class="home-cal__mini-ring" />
            <view class="home-cal__mini-grid">
              <text v-for="w in weekCN" :key="w" class="home-cal__mini-w">{{ w }}</text>
              <block v-for="(c, i) in calCells" :key="i">
                <text v-if="c" class="home-cal__mini-d" :class="{ 'home-cal__mini-d--today': c.isToday }">{{ c.d }}</text>
                <view v-else class="home-cal__mini-d" />
              </block>
            </view>
          </view>
          <!-- 主月历 -->
          <view class="home-cal__main">
            <view class="home-cal__main-grid">
              <text v-for="w in weekCN" :key="w" class="home-cal__main-w">{{ w }}</text>
              <block v-for="(c, i) in calCells" :key="i">
                <view v-if="c" class="home-cal__main-cell" :class="{
                  'home-cal__main-cell--today': c.isToday,
                  'home-cal__main-cell--warm': c.hasRecord,
                }">
                  <text>{{ c.d }}</text>
                </view>
                <view v-else class="home-cal__main-cell" />
              </block>
            </view>
          </view>
        </view>
        <view class="home-cal__footer">
          <text>本月已记录 {{ stats.totalDays }} 天</text>
        </view>
      </view>

      <!-- Slogan -->
      <view class="home-slogan">
        <text class="home-slogan__text">用一道菜，治愈今天的你</text>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.home {
  min-height: 100vh;
  background: var(--mrc-bg);
  padding: 0 32rpx;
  padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* 锅仔 hero */
.home-hero {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 20rpx;
}
.home-hero__bubble {
  position: relative;
  background: var(--mrc-white);
  padding: 16rpx 28rpx;
  border-radius: 24rpx;
  font-size: 30rpx;
  color: var(--mrc-text-deep);
  font-weight: 600;
  margin-bottom: 8rpx;
  box-shadow: 0 4rpx 12rpx rgba(90, 62, 43, 0.08);
}
.home-hero__bubble::after {
  content: '';
  position: absolute;
  bottom: -12rpx;
  left: 50%;
  transform: translateX(-50%);
  border: 12rpx solid transparent;
  border-top-color: var(--mrc-white);
}
.home-hero__img {
  width: 360rpx;
  height: 360rpx;
}
.home-hero__hint {
  font-size: 24rpx;
  color: var(--mrc-text-sub);
  margin-top: -20rpx;
}

/* 今日幸运菜 */
.home-lucky {
  background: linear-gradient(135deg, var(--mrc-primary), var(--mrc-primary-deep));
  border-radius: 32rpx;
  padding: 36rpx 40rpx;
  margin: 24rpx 0;
  box-shadow: 0 8rpx 24rpx rgba(253, 145, 132, 0.35);
}
.home-lucky__row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 8rpx;
}
.home-lucky__main {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--mrc-white);
}
.home-lucky__sub {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.85);
  margin-left: 64rpx;
}

/* 功能条 */
.home-actions {
  display: flex;
  align-items: center;
  background: var(--mrc-bg-card);
  border-radius: 24rpx;
  padding: 28rpx 0;
  margin-bottom: 24rpx;
}
.home-actions__item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  font-size: 28rpx;
  color: var(--mrc-text);
  font-weight: 600;
}
.home-actions__divider {
  width: 2rpx;
  height: 40rpx;
  background: var(--mrc-border);
}

/* 日历卡片 */
.home-cal {
  background: var(--mrc-bg-card);
  border-radius: 24rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
}
.home-cal__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}
.home-cal__date {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
}
.home-cal__body {
  display: flex;
  gap: 16rpx;
}
/* 迷你台历 */
.home-cal__mini {
  position: relative;
  width: 220rpx;
  background: var(--mrc-white);
  border: 3rpx solid var(--mrc-accent);
  border-radius: 8rpx;
  padding: 24rpx 12rpx 12rpx;
}
.home-cal__mini-ring {
  position: absolute;
  top: -10rpx;
  left: 50%;
  transform: translateX(-50%);
  width: 40rpx;
  height: 20rpx;
  background: var(--mrc-accent);
  border-radius: 0 0 20rpx 20rpx;
}
.home-cal__mini-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4rpx;
  text-align: center;
}
.home-cal__mini-w {
  font-size: 16rpx;
  color: var(--mrc-text-sub);
  font-weight: 600;
}
.home-cal__mini-d {
  font-size: 18rpx;
  color: var(--mrc-text);
  padding: 4rpx 0;
}
.home-cal__mini-d--today {
  color: var(--mrc-accent);
  font-weight: 700;
}
/* 主月历 */
.home-cal__main {
  flex: 1;
  background: var(--mrc-white);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 12rpx;
  padding: 12rpx;
}
.home-cal__main-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4rpx;
  text-align: center;
}
.home-cal__main-w {
  font-size: 20rpx;
  color: var(--mrc-text-sub);
  font-weight: 600;
  padding: 6rpx 0;
}
.home-cal__main-cell {
  font-size: 22rpx;
  color: var(--mrc-text);
  padding: 8rpx 0;
  border-radius: 8rpx;
}
.home-cal__main-cell--today {
  border: 3rpx solid var(--mrc-accent);
  font-weight: 700;
  color: var(--mrc-accent);
}
.home-cal__main-cell--warm {
  background: var(--mrc-bg-soft);
}
.home-cal__footer {
  text-align: center;
  margin-top: 16rpx;
  font-size: 24rpx;
  color: var(--mrc-text-sub);
}

/* Slogan */
.home-slogan {
  text-align: center;
  padding: 20rpx 0 40rpx;
}
.home-slogan__text {
  font-size: 26rpx;
  color: var(--mrc-text-light);
  font-style: italic;
}
</style>
