<script setup lang="ts">
import { navBack } from '@/composables/useNavBar'
import Icon from '../../components/common/Icon.vue'
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import LoadingState from '../../components/guozai/LoadingState.vue'
import ErrorState from '../../components/guozai/ErrorState.vue'
import { ensureLogin } from '../../utils/login'
import { fetchYearStats, type YearStatsResult } from '../../api/records'

definePage({
  name: 'report',
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '年度报告',
  },
})

const toast = useToast()
const currentPage = ref(0)
const totalPages = 8

const loading = ref(true)
const error = ref('')
const yearStats = ref<YearStatsResult | null>(null)

const currentYear = new Date().getFullYear()

const moodList = computed(() => {
  const dist = yearStats.value?.moodDistribution || {}
  return Object.entries(dist).map(([mood, count]) => ({ mood, count }))
})

const monthlyData = computed(() => {
  const heat = yearStats.value?.monthlyHeatmap || {}
  return Array.from({ length: 12 }, (_, i) => ({
    month: i + 1,
    count: heat[`${currentYear}-${String(i + 1).padStart(2, '0')}`] || 0,
  }))
})

const maxMonthly = computed(() => Math.max(...monthlyData.value.map(m => m.count), 1))

/** 平均频率：总菜品数 / 总记录天数 */
const avgFreq = computed(() => {
  const days = yearStats.value?.totalDays || 0
  const recs = yearStats.value?.totalRecords || 0
  if (!days) return '0.0'
  return (recs / days).toFixed(1)
})

/** 最常做的菜 TOP3 */
const top3 = computed(() => (yearStats.value?.topDishes || []).slice(0, 3))

/** 最勤快的月份 */
const peakMonth = computed(() => {
  let idx = -1
  let cnt = 0
  monthlyData.value.forEach((m, i) => {
    if (m.count > cnt) {
      cnt = m.count
      idx = i
    }
  })
  return { month: idx + 1, count: cnt }
})

/** 开心天数 */
const happyDays = computed(() => yearStats.value?.moodDistribution?.['开心'] || 0)

/** AI 寄语（基于真实数据动态生成） */
const messageLines = computed(() => {
  const recs = yearStats.value?.totalRecords || 0
  const days = yearStats.value?.totalDays || 0
  const topName = top3.value[0]?.name || '美食'
  const top2Name = top3.value[1]?.name || '家常菜'
  return [
    `这一年你做了${recs}道菜，`,
    `记录了${days}天。开心的时候你`,
    `奖励自己大餐，疲惫的时候你`,
    `用热汤取暖。${topName}是你的`,
    `本命，${top2Name}是你的安慰。`,
    `${currentYear}辛苦了，${currentYear + 1}也要继续`,
    `好好吃饭呀。`,
  ]
})

/** 菜名 -> 静态图映射 */
const DISH_IMG: Record<string, string> = {
  红烧肉: '/static/dish_hongshaorou.png',
  番茄炒蛋: '/static/dish_fanqiechaodan.png',
  番茄牛腩: '/static/dish_tomato_beef.png',
  番茄牛腩面: '/static/dish_noodle.png',
  青椒肉丝: '/static/dish_qingjiaorousi.png',
  土豆丝: '/static/dish_potato.png',
  牛肉面: '/static/dish_noodle.png',
  汤面: '/static/dish_noodle.png',
  汤: '/static/dish_soup.png',
  鸡汤: '/static/dish_soup.png',
}
const FALLBACK_DISH_IMGS = ['/static/dish1.png', '/static/dish2.png', '/static/dish3.png', '/static/dish4.png']
function dishImg(name: string, idx = 0): string {
  return DISH_IMG[name] || FALLBACK_DISH_IMGS[idx % FALLBACK_DISH_IMGS.length]
}

/** TOP3 俏皮点评 */
const TOP3_AI = ['你的本命菜，怎么做都不腻', '简单却永远吃不腻的国民菜', '下饭神器，你一定很爱米饭']
function top3Ai(idx: number): string {
  return TOP3_AI[idx] || '这道菜陪伴了你很多个日子'
}

/** 月度热力图柱色（随月份渐变） */
const BAR_PALETTE = ['#FDE0C8', '#FCD4B0', '#FBC898', '#FABC80', '#F9B068', '#F8A450', '#F59848', '#F08C40', '#EB8038', '#E67430', '#E16828', '#DC5C20']
function barColor(i: number): string {
  return BAR_PALETTE[i % BAR_PALETTE.length]
}

async function loadStats() {
  loading.value = true
  error.value = ''
  try {
    const openid = await ensureLogin()
    yearStats.value = await fetchYearStats(openid, currentYear)
  } catch (e: any) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onShow(() => {
  currentPage.value = 0
  loadStats()
})

/** 上滑 / 下滑翻页（swiper 一页一屏） */
function onSwiperChange(e: any) {
  currentPage.value = e.detail.current
}
function share() {
  toast.show('分享长图即将上线')
}
</script>

<template>
  <view class="report">
    <wd-navbar title="年度报告" left-arrow safe-area-inset-top @click-left="navBack"  custom-style="background-color: transparent !important;" />

    <view class="report__share" @click="share">
      <Icon name="share" :size="36" color="var(--mrc-text)" />
    </view>

    <!-- Loading -->
    <LoadingState v-if="loading" text="锅仔正在整理年度报告..." />

    <!-- Error -->
    <ErrorState v-else-if="error" :text="error" @retry="loadStats" />

    <swiper v-else class="report__swiper" vertical :current="currentPage" @change="onSwiperChange" :duration="320">
      <!-- 第1页：封面 -->
      <swiper-item>
      <view class="rpt-cover rpt-page">
        <view class="rpt-cover__bg" />
        <text class="rpt-cover__brand">心情菜谱日历</text>
        <view class="rpt-cover__title">
          <text class="rpt-cover__title-line">你的</text>
          <text class="rpt-cover__title-line">{{ currentYear }}干饭报告</text>
        </view>
        <image class="rpt-cover__guozai" src="/static/guozai/action_06_glasses.png" mode="aspectFit" />
        <view class="rpt-cover__footer">
          <text class="rpt-cover__slogan">⭐ {{ currentYear }}年度 · 用一道菜治愈每一天 ❤️</text>
          <view class="rpt-cover__divider">
            <view class="rpt-cover__line" />
            <text class="rpt-cover__stars">⭐⭐⭐</text>
            <view class="rpt-cover__line" />
          </view>
        </view>
      </view>
      </swiper-item>

      <!-- 第2页：年度总览 -->
      <swiper-item>
      <view class="rpt-overview rpt-page">
        <text class="rpt-title">年度总览</text>
        <view class="rpt-overview__cards">
          <view class="rpt-overview__card">
            <text class="rpt-overview__num">{{ yearStats?.totalDays || 0 }}</text>
            <text class="rpt-overview__unit">天</text>
            <text class="rpt-overview__label">总记录天数</text>
          </view>
          <view class="rpt-overview__card">
            <text class="rpt-overview__num">{{ yearStats?.totalRecords || 0 }}</text>
            <text class="rpt-overview__unit">道</text>
            <text class="rpt-overview__label">总菜品数</text>
          </view>
          <view class="rpt-overview__card">
            <text class="rpt-overview__num">{{ avgFreq }}</text>
            <text class="rpt-overview__unit">天/次</text>
            <text class="rpt-overview__label">平均频率</text>
          </view>
        </view>
        <image class="rpt-guozai" src="/static/guozai/mood_10_content.png" mode="aspectFit" />
        <view class="rpt-overview__footer">
          <view class="rpt-overview__line" />
          <text class="rpt-overview__text">你在{{ currentYear }}年认真对待了每一餐</text>
          <view class="rpt-overview__line" />
        </view>
      </view>
      </swiper-item>

      <!-- 第3页：最常做菜TOP3 -->
      <swiper-item>
      <view class="rpt-top3 rpt-page">
        <text class="rpt-title">最常做的菜</text>
        <text class="rpt-title rpt-title--sub">TOP3</text>
        <view class="rpt-top3__list">
          <view v-if="!top3.length" class="rpt-top3__empty">记录几道拿手菜，这里就有你的 TOP3</view>
          <view v-for="(d, i) in top3" :key="d.name" class="rpt-top3__item">
            <view class="rpt-top3__medal" :class="i === 0 ? 'rpt-top3__medal--gold' : i === 1 ? 'rpt-top3__medal--silver' : 'rpt-top3__medal--bronze'">{{ ['🥇', '🥈', '🥉'][i] }}</view>
            <image class="rpt-top3__img" :src="dishImg(d.name, i)" mode="aspectFill" />
            <view class="rpt-top3__info">
              <text class="rpt-top3__name">{{ d.name }} <text class="rpt-top3__count">{{ d.count }}次</text></text>
              <text class="rpt-top3__ai">AI: {{ top3Ai(i) }}</text>
            </view>
          </view>
        </view>
        <image class="rpt-guozai rpt-guozai--sm" src="/static/guozai/mood_11_proud.png" mode="aspectFit" />
      </view>
      </swiper-item>

      <!-- 第4页：心情分布 -->
      <swiper-item>
      <view class="rpt-mood rpt-page">
        <text class="rpt-title">这一年的心情</text>
        <view class="rpt-mood__chart-wrap">
          <view class="rpt-mood__donut" />
          <view class="rpt-mood__legend">
            <view v-if="!moodList.length" class="rpt-top3__empty">记录心情，让锅仔更懂你</view>
            <view v-for="(m, i) in moodList" :key="m.mood" class="rpt-mood__legend-item">
              <view class="rpt-mood__dot" :class="'rpt-mood__dot--' + (i % 9 + 1)" />
              <text>{{ m.mood }}{{ m.count }}天</text>
            </view>
          </view>
        </view>
        <text class="rpt-mood__text">这一年你有{{ happyDays }}天是开心的，疲惫的日子也不少，但你总能用美食治愈自己。</text>
        <image class="rpt-guozai rpt-guozai--sm" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
      </view>
      </swiper-item>

      <!-- 第5页：月度热力图 -->
      <swiper-item>
      <view class="rpt-heatmap rpt-page">
        <text class="rpt-title">每月干饭热力图</text>
        <view class="rpt-heatmap__highlight">
          <text class="rpt-heatmap__month">{{ peakMonth.count > 0 ? peakMonth.month + '月 ' + peakMonth.count + '天' : '记录中…' }}</text>
          <text class="rpt-heatmap__fire">🔥</text>
        </view>
        <view class="rpt-heatmap__chart">
          <view class="rpt-heatmap__bars">
            <view
              v-for="m in monthlyData"
              :key="m.month"
              class="rpt-heatmap__bar"
              :class="{ 'rpt-heatmap__bar--peak': m.count > 0 && m.count === peakMonth.count }"
              :style="{ height: Math.max(m.count / maxMonthly * 100, m.count > 0 ? 8 : 2) + '%', background: barColor(m.month - 1) }"
            />
          </view>
          <view class="rpt-heatmap__axis">
            <text v-for="m in monthlyData" :key="m.month">{{ m.month }}月</text>
          </view>
        </view>
        <text class="rpt-heatmap__text">{{ peakMonth.count > 0 ? peakMonth.month + '月是你最勤快的一个月' : '本月还没开始记录，去好好吃饭吧' }}</text>
        <view class="rpt-heatmap__guozai-wrap">
          <image class="rpt-guozai rpt-guozai--sm" src="/static/guozai/mood_11_proud.png" mode="aspectFit" />
          <text class="rpt-heatmap__deco rpt-heatmap__deco--s1">⭐</text>
          <text class="rpt-heatmap__deco rpt-heatmap__deco--s2">⭐</text>
          <text class="rpt-heatmap__deco rpt-heatmap__deco--h1">❤️</text>
          <text class="rpt-heatmap__deco rpt-heatmap__deco--h2">🧡</text>
          <text class="rpt-heatmap__deco rpt-heatmap__deco--c1">☁️</text>
          <text class="rpt-heatmap__deco rpt-heatmap__deco--c2">☁️</text>
        </view>
      </view>
      </swiper-item>

      <!-- 第6页：年度之最 -->
      <swiper-item>
      <view class="rpt-best rpt-page">
        <text class="rpt-title">{{ currentYear }}年度之最</text>
        <view class="rpt-best__list">
          <view class="rpt-best__item">
            <text class="rpt-best__icon">🔥</text>
            <view class="rpt-best__info">
              <text class="rpt-best__name">最勤快的月份</text>
              <text class="rpt-best__detail">{{ peakMonth.count > 0 ? peakMonth.month + '月 · 记录了' + peakMonth.count + '天' : '记录中…' }}</text>
            </view>
          </view>
          <view class="rpt-best__item">
            <text class="rpt-best__icon">📅</text>
            <view class="rpt-best__info">
              <text class="rpt-best__name">最长连续记录</text>
              <text class="rpt-best__detail">{{ (yearStats?.longestStreak || 0) + '天 · 坚持就是胜利' }}</text>
            </view>
          </view>
          <view class="rpt-best__item">
            <text class="rpt-best__icon">🏆</text>
            <view class="rpt-best__info">
              <text class="rpt-best__name">最常做的菜</text>
              <text class="rpt-best__detail">{{ top3.length ? top3[0].name + ' · ' + top3[0].count + '次' : '记录中…' }}</text>
            </view>
          </view>
        </view>
        <image class="rpt-guozai rpt-guozai--sm" src="/static/guozai/mood_10_content.png" mode="aspectFit" />
      </view>
      </swiper-item>

      <!-- 第7页：AI寄语 -->
      <swiper-item>
      <view class="rpt-message rpt-page">
        <view class="rpt-message__guozai-wrap">
          <image class="rpt-message__guozai" src="/static/guozai/action_06_glasses.png" mode="aspectFit" />
          <text class="rpt-message__deco rpt-message__deco--s1">⭐</text>
          <text class="rpt-message__deco rpt-message__deco--s2">⭐</text>
          <text class="rpt-message__deco rpt-message__deco--h1">❤️</text>
          <text class="rpt-message__deco rpt-message__deco--h2">🧡</text>
          <text class="rpt-message__deco rpt-message__deco--c1">☁️</text>
          <text class="rpt-message__deco rpt-message__deco--c2">☁️</text>
        </view>
        <view class="rpt-message__text">
          <text v-for="(line, i) in messageLines" :key="i">{{ line }}</text>
        </view>
        <text class="rpt-message__sign">—— 爱你的锅仔</text>
      </view>
      </swiper-item>

      <!-- 第8页：分享页 -->
      <swiper-item>
      <view class="rpt-share rpt-page">
        <view class="rpt-share__header">
          <text class="rpt-share__brand">心情菜谱日历</text>
          <image class="rpt-share__guozai-sm" src="/static/guozai/action_09_celebrate.png" mode="aspectFit" />
        </view>
        <view class="rpt-share__grid">
          <view class="rpt-share__card">
            <text class="rpt-share__card-title">{{ currentYear }}干饭报告</text>
            <text class="rpt-share__card-sub">年度总结</text>
          </view>
          <view class="rpt-share__card">
            <text class="rpt-share__card-title">记录{{ yearStats?.totalDays || 0 }}天</text>
            <text class="rpt-share__card-sub">做了{{ yearStats?.totalRecords || 0 }}道菜</text>
          </view>
          <view class="rpt-share__card">
            <text class="rpt-share__card-title">做了{{ yearStats?.totalRecords || 0 }}道菜</text>
            <text class="rpt-share__card-sub">平均{{ avgFreq }}天/次</text>
          </view>
          <view class="rpt-share__card">
            <text class="rpt-share__card-title">最常做{{ top3.length ? top3[0].name : '—' }}</text>
            <text class="rpt-share__card-sub">{{ top3.length ? top3[0].count + '次本命菜' : '记录中…' }}</text>
          </view>
        </view>
        <image class="rpt-share__guozai" src="/static/guozai/action_09_celebrate.png" mode="aspectFit" />
        <view class="rpt-share__btn" @click="share">
          <text class="rpt-share__btn-text">分享我的{{ currentYear }}干饭报告</text>
          <view class="rpt-share__qrcode"><view class="rpt-share__qr-grid" /></view>
        </view>
        <view class="rpt-share__custom">
          <view class="rpt-share__line" />
          <text class="rpt-share__custom-text">定制我的{{ currentYear }}干饭纪念册</text>
          <view class="rpt-share__line" />
        </view>
      </view>
      </swiper-item>
    </swiper>

    <!-- 上滑指引：提示下方还有内容（末页隐藏） -->
    <view v-if="!loading && !error && currentPage < totalPages - 1" class="rpt-swipe-hint">
      <text>上滑继续</text>
      <text class="rpt-swipe-hint__arrow">⌄</text>
    </view>

    <!-- 底部进度指示 -->
    <view v-if="!loading && !error" class="rpt-nav">
      <view
        v-for="i in totalPages"
        :key="i"
        class="rpt-nav__dot"
        :class="{ 'rpt-nav__dot--active': currentPage === i - 1 }"
      />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.report {
  min-height: 100vh;
  background: var(--mrc-bg);
  position: relative;
}
/* 一页一屏：竖向 swiper 占满导航栏以下的可用高度 */
.report__swiper {
  height: calc(100vh - env(safe-area-inset-top) - 92rpx - 64rpx);
  width: 100%;
}
/* 每页统一：占满 swiper-item 高度，内容纵向分布，超出裁剪 */
.rpt-page {
  position: relative;
  height: 100%;
  box-sizing: border-box;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-bottom: 24rpx;
}

/* 上滑指引 */
.rpt-swipe-hint {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  bottom: 84rpx;
  z-index: 30;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2rpx;
  color: var(--mrc-text-sub);
  font-size: 22rpx;
  opacity: 0.85;
  animation: rpt-hint-bounce 1.6s ease-in-out infinite;
}
.rpt-swipe-hint__arrow {
  font-size: 30rpx;
  line-height: 1;
  color: var(--mrc-accent);
}
@keyframes rpt-hint-bounce {
  0%, 100% { transform: translateX(-50%) translateY(0); }
  50% { transform: translateX(-50%) translateY(8rpx); }
}

/* 通用标题 */
.rpt-title {
  display: block;
  text-align: center;
  font-size: 72rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
  margin-top: 32rpx;
  margin-bottom: 16rpx;
}
.rpt-title--sub {
  font-size: 64rpx;
  margin-top: 0;
  margin-bottom: 48rpx;
}

/* 通用锅仔 */
.rpt-guozai {
  display: block;
  width: 320rpx;
  height: 320rpx;
  margin: 24rpx auto 0;
}
.rpt-guozai--sm {
  width: 280rpx;
  height: 280rpx;
}

/* 第1页：封面 */
.rpt-cover {
  position: relative;
  min-height: calc(100vh - 200rpx);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48rpx 40rpx 160rpx;
  box-sizing: border-box;
  overflow: hidden;
}
.rpt-cover__bg {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background:
    radial-gradient(circle at 20% 30%, rgba(255,200,150,0.3) 0%, transparent 40%),
    radial-gradient(circle at 80% 70%, rgba(255,180,140,0.25) 0%, transparent 40%),
    linear-gradient(180deg, var(--mrc-bg-alt) 0%, #FDEED8 100%);
  z-index: 0;
}
.rpt-cover__brand {
  position: relative;
  z-index: 1;
  font-size: 40rpx;
  color: var(--mrc-text-mid);
  margin-bottom: 60rpx;
  letter-spacing: 4rpx;
}
.rpt-cover__title {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 40rpx;
}
.rpt-cover__title-line {
  font-size: 72rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
  line-height: 1.3;
}
.rpt-cover__guozai {
  position: relative;
  z-index: 1;
  width: 400rpx;
  height: 400rpx;
  margin-bottom: 40rpx;
}
.rpt-cover__footer {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: auto;
}
.rpt-cover__slogan {
  font-size: 34rpx;
  color: var(--mrc-text-mid);
  margin-bottom: 24rpx;
}
.rpt-cover__divider {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.rpt-cover__line {
  width: 120rpx;
  height: 2rpx;
  background: var(--mrc-border);
}
.rpt-cover__stars {
  font-size: 28rpx;
}

/* 第2页：年度总览 */
.rpt-overview {
  padding: 40rpx 36rpx 60rpx;
  box-sizing: border-box;
  min-height: calc(100vh - 200rpx);
  display: flex;
  flex-direction: column;
}
.rpt-overview__cards {
  display: flex;
  gap: 20rpx;
  margin: 48rpx 0;
}
.rpt-overview__card {
  flex: 1;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 28rpx;
  padding: 40rpx 16rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: var(--mrc-shadow-soft);
}
.rpt-overview__num {
  font-size: 88rpx;
  font-weight: 700;
  color: var(--mrc-accent);
  line-height: 1;
}
.rpt-overview__unit {
  font-size: 36rpx;
  color: var(--mrc-text-deep);
  font-weight: 600;
  margin-top: 8rpx;
}
.rpt-overview__label {
  font-size: 28rpx;
  color: var(--mrc-text-mid);
  margin-top: 20rpx;
}
.rpt-overview__footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20rpx;
  margin-top: auto;
  padding-top: 40rpx;
}
.rpt-overview__line {
  width: 100rpx;
  height: 2rpx;
  background: var(--mrc-border);
}
.rpt-overview__text {
  font-size: 30rpx;
  color: var(--mrc-text-mid);
}

/* 第3页：TOP3 */
.rpt-top3 {
  padding: 40rpx 36rpx 60rpx;
  box-sizing: border-box;
  min-height: calc(100vh - 200rpx);
}
.rpt-top3__list {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
  margin-bottom: 32rpx;
}
.rpt-top3__item {
  position: relative;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 32rpx;
  padding: 28rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
  box-shadow: var(--mrc-shadow-soft);
}
.rpt-top3__medal {
  position: absolute;
  top: -8rpx;
  left: 16rpx;
  font-size: 56rpx;
  z-index: 2;
}
.rpt-top3__img {
  width: 160rpx;
  height: 160rpx;
  border-radius: 20rpx;
  flex-shrink: 0;
  margin-left: 32rpx;
}
.rpt-top3__info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.rpt-top3__name {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--mrc-text-strong);
}
.rpt-top3__count {
  font-size: 36rpx;
  color: var(--mrc-accent);
  font-weight: 600;
}
.rpt-top3__ai {
  font-size: 28rpx;
  color: var(--mrc-text-mid);
  line-height: 1.5;
}

/* 第4页：心情分布 */
.rpt-mood {
  padding: 40rpx 36rpx 60rpx;
  box-sizing: border-box;
  min-height: calc(100vh - 200rpx);
}
.rpt-mood__chart-wrap {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin: 48rpx 0;
}
.rpt-mood__donut {
  width: 320rpx;
  height: 320rpx;
  border-radius: 50%;
  flex-shrink: 0;
  background: conic-gradient(
    var(--mrc-yellow) 0deg 87deg,
    #FFE066 87deg 157deg,
    var(--mrc-green) 157deg 237deg,
    #8FD99A 237deg 265deg,
    var(--mrc-blue) 265deg 293deg,
    #C8E0ED 293deg 315deg,
    var(--mrc-accent) 315deg 352deg,
    #F0A088 352deg 367deg,
    var(--mrc-mood-anxious) 367deg 382deg,
    #FFC9A0 382deg 400deg
  );
  position: relative;
}
.rpt-mood__donut::after {
  content: '';
  position: absolute;
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  width: 160rpx;
  height: 160rpx;
  background: var(--mrc-bg);
  border-radius: 50%;
}
.rpt-mood__legend {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  background: #FEF8E8;
  border-radius: 20rpx;
  padding: 20rpx;
}
.rpt-mood__legend-item {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.rpt-mood__dot {
  width: 28rpx;
  height: 28rpx;
  border-radius: 6rpx;
  flex-shrink: 0;
}
.rpt-mood__dot--1 { background: var(--mrc-yellow); }
.rpt-mood__dot--2 { background: #FFE066; }
.rpt-mood__dot--3 { background: var(--mrc-green); }
.rpt-mood__dot--4 { background: #8FD99A; }
.rpt-mood__dot--5 { background: var(--mrc-blue); }
.rpt-mood__dot--6 { background: #C8E0ED; }
.rpt-mood__dot--7 { background: var(--mrc-accent); }
.rpt-mood__dot--8 { background: var(--mrc-mood-anxious); }
.rpt-mood__dot--9 { background: #FFC9A0; }
.rpt-mood__legend-item text {
  font-size: 26rpx;
  color: var(--mrc-text-strong);
}
.rpt-mood__text {
  display: block;
  font-size: 34rpx;
  color: var(--mrc-text-strong);
  line-height: 1.7;
  margin: 32rpx 0;
  padding: 0 16rpx;
}

/* 第5页：月度热力图 */
.rpt-heatmap {
  padding: 40rpx 36rpx 60rpx;
  box-sizing: border-box;
  min-height: calc(100vh - 200rpx);
}
.rpt-heatmap__highlight {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  margin: 32rpx 0;
}
.rpt-heatmap__month {
  font-size: 52rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
}
.rpt-heatmap__fire {
  font-size: 48rpx;
}
.rpt-heatmap__chart {
  margin: 24rpx 0;
}
.rpt-heatmap__bars {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 360rpx;
  padding: 0 8rpx;
  border-bottom: 3rpx solid var(--mrc-border);
}
.rpt-heatmap__bar {
  width: 7%;
  border-radius: 8rpx 8rpx 0 0;
  min-height: 20rpx;
}
.rpt-heatmap__bar--peak {
  box-shadow: 0 0 16rpx rgba(248, 164, 80, 0.5);
}
.rpt-heatmap__axis {
  display: flex;
  justify-content: space-between;
  padding: 12rpx 8rpx 0;
}
.rpt-heatmap__axis text {
  font-size: 22rpx;
  color: var(--mrc-text-mid);
  width: 7%;
  text-align: center;
}
.rpt-heatmap__text {
  display: block;
  text-align: center;
  font-size: 36rpx;
  color: var(--mrc-text-strong);
  font-weight: 600;
  margin: 32rpx 0;
}
.rpt-heatmap__guozai-wrap {
  position: relative;
  display: flex;
  justify-content: center;
}
.rpt-heatmap__deco {
  position: absolute;
  font-size: 36rpx;
}
.rpt-heatmap__deco--s1 { top: 10rpx; left: 80rpx; }
.rpt-heatmap__deco--s2 { top: 0; right: 100rpx; font-size: 44rpx; }
.rpt-heatmap__deco--h1 { top: 40rpx; right: 60rpx; }
.rpt-heatmap__deco--h2 { top: 100rpx; left: 50rpx; font-size: 28rpx; }
.rpt-heatmap__deco--c1 { bottom: 20rpx; left: 30rpx; }
.rpt-heatmap__deco--c2 { top: 120rpx; right: 30rpx; font-size: 40rpx; }

/* 第6页：年度之最 */
.rpt-best {
  padding: 40rpx 36rpx 60rpx;
  box-sizing: border-box;
  min-height: calc(100vh - 200rpx);
}
.rpt-best__list {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
  margin: 48rpx 0;
}
.rpt-best__item {
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 32rpx;
  padding: 36rpx 32rpx;
  display: flex;
  align-items: center;
  gap: 28rpx;
  box-shadow: var(--mrc-shadow-soft);
}
.rpt-best__icon {
  font-size: 72rpx;
  flex-shrink: 0;
}
.rpt-best__info {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.rpt-best__name {
  font-size: 42rpx;
  font-weight: 700;
  color: var(--mrc-text-strong);
}
.rpt-best__detail {
  font-size: 32rpx;
  color: var(--mrc-text-mid);
}

/* 第7页：AI寄语 */
.rpt-message {
  padding: 40rpx 48rpx 60rpx;
  box-sizing: border-box;
  min-height: calc(100vh - 200rpx);
  display: flex;
  flex-direction: column;
  align-items: center;
}
.rpt-message__guozai-wrap {
  position: relative;
  width: 100%;
  display: flex;
  justify-content: center;
  margin-bottom: 48rpx;
}
.rpt-message__guozai {
  width: 360rpx;
  height: 360rpx;
}
.rpt-message__deco {
  position: absolute;
  font-size: 40rpx;
}
.rpt-message__deco--s1 { top: 10rpx; left: 80rpx; transform: rotate(-15deg); }
.rpt-message__deco--s2 { top: 0; right: 100rpx; transform: rotate(20deg); font-size: 48rpx; }
.rpt-message__deco--h1 { top: 60rpx; right: 60rpx; font-size: 32rpx; }
.rpt-message__deco--h2 { top: 140rpx; left: 50rpx; font-size: 28rpx; }
.rpt-message__deco--c1 { bottom: 10rpx; left: 30rpx; font-size: 36rpx; }
.rpt-message__deco--c2 { top: 160rpx; right: 30rpx; font-size: 40rpx; }
.rpt-message__text {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  width: 100%;
}
.rpt-message__text text {
  font-size: 40rpx;
  color: var(--mrc-text-strong);
  line-height: 1.6;
  font-weight: 600;
}
.rpt-message__sign {
  align-self: flex-end;
  font-size: 40rpx;
  color: var(--mrc-brown);
  margin-top: 40rpx;
  font-weight: 600;
}

/* 第8页：分享页 */
.rpt-share {
  padding: 40rpx 36rpx 60rpx;
  box-sizing: border-box;
  min-height: calc(100vh - 200rpx);
  display: flex;
  flex-direction: column;
  align-items: center;
}
.rpt-share__header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 40rpx;
}
.rpt-share__brand {
  font-size: 48rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
  letter-spacing: 4rpx;
}
.rpt-share__guozai-sm {
  width: 72rpx;
  height: 72rpx;
}
.rpt-share__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx;
  width: 100%;
  margin-bottom: 32rpx;
}
.rpt-share__card {
  background: linear-gradient(135deg, #FEF8E8, #FDEED8);
  border-radius: 28rpx;
  padding: 32rpx 24rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  box-shadow: 0 4rpx 12rpx rgba(121, 73, 53, 0.06);
}
.rpt-share__card-title {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--mrc-text-strong);
  text-align: center;
}
.rpt-share__card-sub {
  font-size: 26rpx;
  color: var(--mrc-text-mid);
}
.rpt-share__guozai {
  width: 320rpx;
  height: 320rpx;
  margin: 16rpx 0;
}
.rpt-share__btn {
  width: 100%;
  background: linear-gradient(135deg, var(--mrc-mood-anxious), var(--mrc-primary-deep));
  border-radius: 48rpx;
  padding: 32rpx 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(253, 145, 132, 0.3);
  margin-bottom: 32rpx;
}
.rpt-share__btn-text {
  font-size: 38rpx;
  color: #fff;
  font-weight: 700;
}
.rpt-share__qrcode {
  width: 80rpx;
  height: 80rpx;
  background: #fff;
  border-radius: 8rpx;
  padding: 8rpx;
  box-sizing: border-box;
}
.rpt-share__qr-grid {
  width: 100%;
  height: 100%;
  background:
    linear-gradient(90deg, #000 50%, transparent 50%),
    linear-gradient(#000 50%, transparent 50%);
  background-size: 12rpx 12rpx;
  background-position: 0 0, 6rpx 6rpx;
}
.rpt-share__custom {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-top: auto;
}
.rpt-share__line {
  width: 100rpx;
  height: 2rpx;
  background: var(--mrc-border);
}
.rpt-share__custom-text {
  font-size: 30rpx;
  color: var(--mrc-text-mid);
}

/* 底部翻页导航 */
.rpt-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  box-sizing: border-box;
  z-index: 100;
}
.rpt-nav__dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: var(--mrc-border);
  transition: all 0.3s;
}
.rpt-nav__dot--active {
  width: 32rpx;
  border-radius: 8rpx;
  background: var(--mrc-primary-deep);
}
/* 分享按钮：navbar 右侧被小程序胶囊遮挡，移到内容区右上角浮动 */
.report__share { position: absolute; top: calc(env(safe-area-inset-top) + 92rpx); right: 24rpx; z-index: 50; width: 72rpx; height: 72rpx; border-radius: 50%; background: rgba(255, 255, 255, 0.9); box-shadow: var(--mrc-shadow-sm); display: flex; align-items: center; justify-content: center; }
</style>
