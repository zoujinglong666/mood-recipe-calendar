<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppNav from '../../components/common/AppNav.vue'
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
const isTransitioning = ref(false)

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

function next() {
  if (isTransitioning.value) return
  if (currentPage.value < totalPages - 1) {
    isTransitioning.value = true
    currentPage.value++
    setTimeout(() => { isTransitioning.value = false }, 300)
  }
}
function prev() {
  if (isTransitioning.value) return
  if (currentPage.value > 0) {
    isTransitioning.value = true
    currentPage.value--
    setTimeout(() => { isTransitioning.value = false }, 300)
  }
}
function goToPage(i: number) {
  if (isTransitioning.value) return
  isTransitioning.value = true
  currentPage.value = i
  setTimeout(() => { isTransitioning.value = false }, 300)
}
function share() {
  toast.show('分享长图即将上线')
}
</script>

<template>
  <view class="report">
    <AppNav title="年度报告" show-back right-icon="share" />

    <!-- Loading -->
    <LoadingState v-if="loading" text="锅仔正在整理年度报告..." />

    <!-- Error -->
    <ErrorState v-else-if="error" :text="error" @retry="loadStats" />

    <scroll-view v-else scroll-y class="report__scroll">
      <!-- 第1页：封面 -->
      <view v-show="currentPage === 0" class="rpt-cover">
        <view class="rpt-cover__bg" />
        <text class="rpt-cover__brand">心情菜谱日历</text>
        <view class="rpt-cover__title">
          <text class="rpt-cover__title-line">小圆同学的</text>
          <text class="rpt-cover__title-line">{{ currentYear }}干饭报告</text>
        </view>
        <image class="rpt-cover__guozai" src="/static/guozai/action_06_glasses.png" mode="aspectFit" />
        <view class="rpt-cover__footer">
          <text class="rpt-cover__slogan">⭐ 2026年度 · 用一道菜治愈每一天 ❤️</text>
          <view class="rpt-cover__divider">
            <view class="rpt-cover__line" />
            <text class="rpt-cover__stars">⭐⭐⭐</text>
            <view class="rpt-cover__line" />
          </view>
        </view>
      </view>

      <!-- 第2页：年度总览 -->
      <view v-show="currentPage === 1" class="rpt-overview">
        <text class="rpt-title">年度总览</text>
        <view class="rpt-overview__cards">
          <view class="rpt-overview__card">
            <text class="rpt-overview__num">187</text>
            <text class="rpt-overview__unit">天</text>
            <text class="rpt-overview__label">总记录天数</text>
          </view>
          <view class="rpt-overview__card">
            <text class="rpt-overview__num">246</text>
            <text class="rpt-overview__unit">道</text>
            <text class="rpt-overview__label">总菜品数</text>
          </view>
          <view class="rpt-overview__card">
            <text class="rpt-overview__num">2.3</text>
            <text class="rpt-overview__unit">天/次</text>
            <text class="rpt-overview__label">平均频率</text>
          </view>
        </view>
        <image class="rpt-guozai" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
        <view class="rpt-overview__footer">
          <view class="rpt-overview__line" />
          <text class="rpt-overview__text">你在2026年认真对待了每一餐</text>
          <view class="rpt-overview__line" />
        </view>
      </view>

      <!-- 第3页：最常做菜TOP3 -->
      <view v-show="currentPage === 2" class="rpt-top3">
        <text class="rpt-title">最常做的菜</text>
        <text class="rpt-title rpt-title--sub">TOP3</text>
        <view class="rpt-top3__list">
          <view class="rpt-top3__item">
            <view class="rpt-top3__medal rpt-top3__medal--gold">🥇</view>
            <image class="rpt-top3__img" src="/static/dish_hongshaorou.png" mode="aspectFill" />
            <view class="rpt-top3__info">
              <text class="rpt-top3__name">红烧肉 <text class="rpt-top3__count">23次</text></text>
              <text class="rpt-top3__ai">AI: 红烧肉是你的本命菜，肥而不腻说的就是你</text>
            </view>
          </view>
          <view class="rpt-top3__item">
            <view class="rpt-top3__medal rpt-top3__medal--silver">🥈</view>
            <image class="rpt-top3__img" src="/static/dish_fanqiechaodan.png" mode="aspectFill" />
            <view class="rpt-top3__info">
              <text class="rpt-top3__name">番茄炒蛋 <text class="rpt-top3__count">18次</text></text>
              <text class="rpt-top3__ai">AI: 简单却永远吃不腻的国民菜</text>
            </view>
          </view>
          <view class="rpt-top3__item">
            <view class="rpt-top3__medal rpt-top3__medal--bronze">🥉</view>
            <image class="rpt-top3__img" src="/static/dish_qingjiaorousi.png" mode="aspectFill" />
            <view class="rpt-top3__info">
              <text class="rpt-top3__name">青椒肉丝 <text class="rpt-top3__count">12次</text></text>
              <text class="rpt-top3__ai">下饭神器，你一定很爱米饭</text>
            </view>
          </view>
        </view>
        <image class="rpt-guozai rpt-guozai--sm" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
      </view>

      <!-- 第4页：心情分布 -->
      <view v-show="currentPage === 3" class="rpt-mood">
        <text class="rpt-title">这一年的心情</text>
        <view class="rpt-mood__chart-wrap">
          <view class="rpt-mood__donut" />
          <view class="rpt-mood__legend">
            <view class="rpt-mood__legend-item"><view class="rpt-mood__dot rpt-mood__dot--1" /><text>开心47天</text></view>
            <view class="rpt-mood__legend-item"><view class="rpt-mood__dot rpt-mood__dot--2" /><text>平静38天</text></view>
            <view class="rpt-mood__legend-item"><view class="rpt-mood__dot rpt-mood__dot--3" /><text>疲惫43天</text></view>
            <view class="rpt-mood__legend-item"><view class="rpt-mood__dot rpt-mood__dot--4" /><text>焦虑15天</text></view>
            <view class="rpt-mood__legend-item"><view class="rpt-mood__dot rpt-mood__dot--5" /><text>难过15天</text></view>
            <view class="rpt-mood__legend-item"><view class="rpt-mood__dot rpt-mood__dot--6" /><text>难过12天</text></view>
            <view class="rpt-mood__legend-item"><view class="rpt-mood__dot rpt-mood__dot--7" /><text>嘴馋20天</text></view>
            <view class="rpt-mood__legend-item"><view class="rpt-mood__dot rpt-mood__dot--8" /><text>低落8天</text></view>
            <view class="rpt-mood__legend-item"><view class="rpt-mood__dot rpt-mood__dot--9" /><text>想家10天</text></view>
          </view>
        </view>
        <text class="rpt-mood__text">这一年你有47天是开心的，疲惫的日子也不少，但你总能用美食治愈自己。</text>
        <image class="rpt-guozai rpt-guozai--sm" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
      </view>

      <!-- 第5页：月度热力图 -->
      <view v-show="currentPage === 4" class="rpt-heatmap">
        <text class="rpt-title">每月干饭热力图</text>
        <view class="rpt-heatmap__highlight">
          <text class="rpt-heatmap__month">8月 23天</text>
          <text class="rpt-heatmap__fire">🔥</text>
        </view>
        <view class="rpt-heatmap__chart">
          <view class="rpt-heatmap__bars">
            <view class="rpt-heatmap__bar" style="height: 40%; background: #FDE0C8;" />
            <view class="rpt-heatmap__bar" style="height: 50%; background: #FCD4B0;" />
            <view class="rpt-heatmap__bar" style="height: 60%; background: #FBC898;" />
            <view class="rpt-heatmap__bar" style="height: 72%; background: #FABC80;" />
            <view class="rpt-heatmap__bar" style="height: 85%; background: #F9B068;" />
            <view class="rpt-heatmap__bar rpt-heatmap__bar--peak" style="height: 100%; background: #F8A450;" />
            <view class="rpt-heatmap__bar" style="height: 78%; background: #F59848;" />
            <view class="rpt-heatmap__bar" style="height: 65%; background: #F08C40;" />
            <view class="rpt-heatmap__bar" style="height: 55%; background: #EB8038;" />
            <view class="rpt-heatmap__bar" style="height: 48%; background: #E67430;" />
            <view class="rpt-heatmap__bar" style="height: 42%; background: #E16828;" />
            <view class="rpt-heatmap__bar" style="height: 38%; background: #DC5C20;" />
          </view>
          <view class="rpt-heatmap__axis">
            <text>1月</text><text>2月</text><text>3月</text><text>4月</text><text>5月</text><text>6月</text>
            <text>7月</text><text>9月</text><text>10月</text><text>11月</text><text>12月</text>
          </view>
        </view>
        <text class="rpt-heatmap__text">8月是你最勤快的一个月</text>
        <view class="rpt-heatmap__guozai-wrap">
          <image class="rpt-guozai rpt-guozai--sm" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
          <text class="rpt-heatmap__deco rpt-heatmap__deco--s1">⭐</text>
          <text class="rpt-heatmap__deco rpt-heatmap__deco--s2">⭐</text>
          <text class="rpt-heatmap__deco rpt-heatmap__deco--h1">❤️</text>
          <text class="rpt-heatmap__deco rpt-heatmap__deco--h2">🧡</text>
          <text class="rpt-heatmap__deco rpt-heatmap__deco--c1">☁️</text>
          <text class="rpt-heatmap__deco rpt-heatmap__deco--c2">☁️</text>
        </view>
      </view>

      <!-- 第6页：年度之最 -->
      <view v-show="currentPage === 5" class="rpt-best">
        <text class="rpt-title">2026年度之最</text>
        <view class="rpt-best__list">
          <view class="rpt-best__item">
            <text class="rpt-best__icon">🌶️</text>
            <view class="rpt-best__info">
              <text class="rpt-best__name">最能吃辣的一天</text>
              <text class="rpt-best__detail">6月15日 麻辣香锅</text>
            </view>
          </view>
          <view class="rpt-best__item">
            <text class="rpt-best__icon">🌙</text>
            <view class="rpt-best__info">
              <text class="rpt-best__name">最晚的一顿饭</text>
              <text class="rpt-best__detail">11月3日 凌晨1点 泡面</text>
            </view>
          </view>
          <view class="rpt-best__item">
            <text class="rpt-best__icon">🏆</text>
            <view class="rpt-best__info">
              <text class="rpt-best__name">最复杂的一道菜</text>
              <text class="rpt-best__detail">3月20日 红烧肉 耗时3小时</text>
            </view>
          </view>
        </view>
        <image class="rpt-guozai rpt-guozai--sm" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
      </view>

      <!-- 第7页：AI寄语 -->
      <view v-show="currentPage === 6" class="rpt-message">
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
          <text>小圆，这一年你做了246道菜，</text>
          <text>记录了187天。开心的时候你</text>
          <text>奖励自己大餐，疲惫的时候你</text>
          <text>用热汤取暖。红烧肉是你的</text>
          <text>本命，番茄炒蛋是你的安慰。</text>
          <text>2026辛苦了，2027也要继续</text>
          <text>好好吃饭呀。</text>
        </view>
        <text class="rpt-message__sign">—— 爱你的锅仔</text>
      </view>

      <!-- 第8页：分享页 -->
      <view v-show="currentPage === 7" class="rpt-share">
        <view class="rpt-share__header">
          <text class="rpt-share__brand">心情菜谱日历</text>
          <image class="rpt-share__guozai-sm" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
        </view>
        <view class="rpt-share__grid">
          <view class="rpt-share__card">
            <text class="rpt-share__card-title">2026干饭报告</text>
            <text class="rpt-share__card-sub">年度总结</text>
          </view>
          <view class="rpt-share__card">
            <text class="rpt-share__card-title">记录187天</text>
            <text class="rpt-share__card-sub">做了246道菜</text>
          </view>
          <view class="rpt-share__card">
            <text class="rpt-share__card-title">做了246道菜</text>
            <text class="rpt-share__card-sub">平均2.3天/次</text>
          </view>
          <view class="rpt-share__card">
            <text class="rpt-share__card-title">最常做红烧肉</text>
            <text class="rpt-share__card-sub">23次本命菜</text>
          </view>
        </view>
        <image class="rpt-share__guozai" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
        <view class="rpt-share__btn" @click="share">
          <text class="rpt-share__btn-text">分享我的2026干饭报告</text>
          <view class="rpt-share__qrcode"><view class="rpt-share__qr-grid" /></view>
        </view>
        <view class="rpt-share__custom">
          <view class="rpt-share__line" />
          <text class="rpt-share__custom-text">定制我的2026干饭纪念册</text>
          <view class="rpt-share__line" />
        </view>
      </view>
    </scroll-view>

    <!-- 底部翻页导航 -->
    <view v-if="!loading && !error" class="rpt-nav">
      <view
        class="rpt-nav__btn"
        :class="{ 'rpt-nav__btn--disabled': currentPage === 0 }"
        @click="prev"
      >上一页</view>
      <view class="rpt-nav__dots">
        <view
          v-for="i in totalPages"
          :key="i"
          class="rpt-nav__dot"
          :class="{ 'rpt-nav__dot--active': currentPage === i - 1 }"
          @click="goToPage(i - 1)"
        />
      </view>
      <view
        class="rpt-nav__btn rpt-nav__btn--primary"
        :class="{ 'rpt-nav__btn--disabled': currentPage === totalPages - 1 }"
        @click="next"
      >下一页</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.report {
  min-height: 100vh;
  background: var(--mrc-bg);
  position: relative;
}
.report__scroll {
  height: calc(100vh - 100rpx);
  padding-bottom: 120rpx;
  box-sizing: border-box;
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
  gap: 10rpx;
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
  gap: 10rpx;
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
  padding: 6rpx;
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
  height: 100rpx;
  background: rgba(255, 252, 247, 0.96);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32rpx;
  box-sizing: border-box;
  border-top: 1rpx solid var(--mrc-border-light);
  z-index: 100;
}
.rpt-nav__btn {
  font-size: 30rpx;
  color: var(--mrc-text-mid);
  padding: 16rpx 32rpx;
  border-radius: 40rpx;
  background: var(--mrc-surface-2);
}
.rpt-nav__btn--primary {
  background: var(--mrc-primary-grad);
  color: #fff;
  font-weight: 600;
}
.rpt-nav__btn--disabled {
  opacity: 0.4;
}
.rpt-nav__dots {
  display: flex;
  gap: 12rpx;
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
</style>
