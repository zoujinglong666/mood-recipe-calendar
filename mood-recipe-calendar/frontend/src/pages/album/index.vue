<script setup lang="ts">
import { ref, computed } from 'vue'
import AppNav from '../../components/common/AppNav.vue'
import LoadingState from '../../components/guozai/LoadingState.vue'
import ErrorState from '../../components/guozai/ErrorState.vue'
import { ensureLogin } from '../../utils/login'
import { fetchMonthAlbum, type AlbumItem } from '../../api/albums'

definePage({
  name: 'album',
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '月度画册',
  },
})

const router = useRouter()
const currentPage = ref(0)
const totalPages = 5
const isTransitioning = ref(false)

const loading = ref(true)
const error = ref('')
const album = ref<AlbumItem | null>(null)

const now = new Date()
const monthStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
const monthNum = now.getMonth() + 1

const stats = computed(() => {
  if (!album.value?.stats) return { totalDays: 0, moodDistribution: {}, topDishes: [], longestStreak: 0 }
  try { return JSON.parse(album.value.stats) } catch { return { totalDays: 0, moodDistribution: {}, topDishes: [], longestStreak: 0 } }
})

const moodList = computed(() => {
  const dist = stats.value.moodDistribution || {}
  return Object.entries(dist).map(([mood, count]) => ({ mood, count }))
})

async function loadAlbum() {
  loading.value = true
  error.value = ''
  try {
    const openid = await ensureLogin()
    album.value = await fetchMonthAlbum(openid, monthStr)
  } catch (e: any) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onShow(() => {
  currentPage.value = 0
  loadAlbum()
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
</script>

<template>
  <view class="album-page">
    <AppNav title="月度画册" right-icon="share" />

    <!-- Loading -->
    <LoadingState v-if="loading" text="锅仔正在装订画册..." />

    <!-- Error -->
    <ErrorState v-else-if="error" :text="error" @retry="loadAlbum" />

    <!-- 内容 -->
    <template v-else>
    <!-- 封面 -->
    <view v-show="currentPage === 0" class="album-cover">
      <view class="album-cover__bg">
        <view class="album-cover__blob album-cover__blob--1" />
        <view class="album-cover__blob album-cover__blob--2" />
        <view class="album-cover__blob album-cover__blob--3" />
        <view class="album-cover__blob album-cover__blob--4" />
      </view>

      <view class="album-cover__content">
        <text class="album-cover__brand">心情菜谱日历</text>
        <text class="album-cover__title">小圆同学的</text>
        <text class="album-cover__title">{{ monthNum }}月干饭日记</text>
        <text class="album-cover__subtitle">{{ now.getFullYear() }}年{{ monthNum }}月 · 共记录{{ stats.totalDays }}天</text>

        <view class="album-cover__guozai-wrap">
          <image class="album-cover__guozai" src="/static/guozai/action_05_album.png" mode="aspectFit" />
          <text class="album-cover__deco album-cover__deco--star1">⭐</text>
          <text class="album-cover__deco album-cover__deco--star2">⭐</text>
          <text class="album-cover__deco album-cover__deco--heart1">❤️</text>
          <text class="album-cover__deco album-cover__deco--heart2">🧡</text>
          <text class="album-cover__deco album-cover__deco--cloud">☁️</text>
        </view>

        <view class="album-cover__bubble">
          <text>帮你把回忆装订成册啦！</text>
        </view>
      </view>
    </view>

    <!-- 第2页：月度盘点 -->
    <view v-show="currentPage === 1" class="album-stats">
      <view class="album-stats__header">
        <text class="album-stats__title">9月盘点</text>
        <image class="album-stats__guozai-icon" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
      </view>

      <view class="album-stats__big">
        <text class="album-stats__number">23</text>
        <text class="album-stats__label">本月记录天数</text>
      </view>

      <!-- 心情分布 -->
      <view class="album-card">
        <text class="album-card__title">心情分布</text>
        <view class="album-moods">
          <view class="album-mood album-mood--happy">
            <text class="album-mood__name">开心</text>
            <text class="album-mood__count">8天</text>
          </view>
          <view class="album-mood album-mood--warm">
            <text class="album-mood__name">暖阳</text>
            <text class="album-mood__count">6天</text>
          </view>
          <view class="album-mood album-mood--calm">
            <text class="album-mood__name">平静</text>
            <text class="album-mood__count">6天</text>
          </view>
          <view class="album-mood album-mood--tired">
            <text class="album-mood__name">疲惫</text>
            <text class="album-mood__count">5天</text>
          </view>
          <view class="album-mood album-mood--sad">
            <text class="album-mood__name">难过</text>
            <text class="album-mood__count">4天</text>
          </view>
          <view class="album-mood album-mood--anxious">
            <text class="album-mood__name">焦虑</text>
            <text class="album-mood__count">4天</text>
          </view>
        </view>
      </view>

      <!-- 最常做的菜 TOP3 -->
      <view class="album-card">
        <text class="album-card__title">最常做的菜 TOP3</text>
        <view class="album-dishes">
          <view class="album-dish">
            <text class="album-dish__medal album-dish__medal--gold">🥇</text>
            <text class="album-dish__name">红烧肉</text>
            <text class="album-dish__count">4次</text>
          </view>
          <view class="album-dish">
            <text class="album-dish__medal album-dish__medal--silver">🥈</text>
            <text class="album-dish__name">番茄炒蛋</text>
            <text class="album-dish__count">3次</text>
          </view>
          <view class="album-dish">
            <text class="album-dish__medal album-dish__medal--bronze">🥉</text>
            <text class="album-dish__name">青椒肉丝</text>
            <text class="album-dish__count">2次</text>
          </view>
        </view>
      </view>

      <!-- 最长连续记录 -->
      <view class="album-streak">
        <text class="album-streak__text">最长连续记录</text>
        <text class="album-streak__num">7</text>
        <text class="album-streak__fire">🔥</text>
        <text class="album-streak__text">天</text>
      </view>

      <!-- 右下角锅仔 -->
      <image class="album-stats__guozai" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
    </view>

    <!-- 第3页：每日记录 -->
    <view v-show="currentPage === 2" class="album-daily">
      <text class="album-daily__title">每日记录</text>

      <view class="album-daily__list">
        <view class="album-daily__card">
          <image class="album-daily__img" src="/static/dish_noodle.png" mode="aspectFill" />
          <view class="album-daily__info">
            <text class="album-daily__date">9月1日</text>
            <text class="album-daily__dish">番茄牛腩面</text>
            <text class="album-daily__mood">😊 今天加班到9点</text>
            <text class="album-daily__note">今天加班到9点，回家做了碗面，吃完活了</text>
          </view>
          <text class="album-daily__heart">❤️</text>
        </view>

        <view class="album-daily__card">
          <image class="album-daily__img" src="/static/dish_potato.png" mode="aspectFill" />
          <view class="album-daily__info">
            <text class="album-daily__date">9月2日</text>
            <text class="album-daily__dish">酸辣土豆丝</text>
            <text class="album-daily__mood">😊 今天尝试新菜谱</text>
            <text class="album-daily__note">今天尝试新菜谱，成功！</text>
          </view>
          <text class="album-daily__heart">❤️</text>
        </view>

        <view class="album-daily__card">
          <image class="album-daily__img" src="/static/dish_soup.png" mode="aspectFill" />
          <view class="album-daily__info">
            <text class="album-daily__date">9月3日</text>
            <text class="album-daily__dish">香菇鸡汤</text>
            <text class="album-daily__mood">😌 天气转凉</text>
            <text class="album-daily__note">天气转凉，喝口汤暖乎乎</text>
          </view>
          <text class="album-daily__heart">❤️</text>
        </view>
      </view>

      <!-- 底部锅仔 -->
      <view class="album-daily__guozai-wrap">
        <image class="album-daily__guozai" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
        <text class="album-daily__deco album-daily__deco--star1">⭐</text>
        <text class="album-daily__deco album-daily__deco--star2">⭐</text>
        <text class="album-daily__deco album-daily__deco--heart">🧡</text>
        <text class="album-daily__deco album-daily__deco--cloud1">☁️</text>
        <text class="album-daily__deco album-daily__deco--cloud2">☁️</text>
      </view>
    </view>

    <!-- 第4页：AI寄语 -->
    <view v-show="currentPage === 3" class="album-message">
      <view class="album-message__guozai-wrap">
        <image class="album-message__guozai" src="/static/guozai/action_06_glasses.png" mode="aspectFit" />
        <text class="album-message__deco album-message__deco--star1">⭐</text>
        <text class="album-message__deco album-message__deco--star2">⭐</text>
        <text class="album-message__deco album-message__deco--heart1">❤️</text>
        <text class="album-message__deco album-message__deco--heart2">🧡</text>
        <text class="album-message__deco album-message__deco--cloud1">☁️</text>
        <text class="album-message__deco album-message__deco--cloud2">☁️</text>
      </view>

      <view class="album-message__text">
        <text>9月，你有12天选择了治愈系食物。</text>
        <text>看来工作压力不小。</text>
        <text>但你没饿着自己，</text>
        <text>你煮了面、炖了汤、炒了饭。</text>
        <text>10月，请继续对自己好一点。</text>
      </view>

      <text class="album-message__sign">—— 锅仔</text>
    </view>

    <!-- 第5页：分享页 -->
    <view v-show="currentPage === 4" class="album-share">
      <view class="album-share__header">
        <text class="album-share__brand">心情菜谱日历</text>
        <image class="album-share__guozai-sm" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
      </view>

      <view class="album-share__card">
        <text class="album-share__label">记录了</text>
        <view class="album-share__big">
          <text class="album-share__num">23</text>
          <text class="album-share__unit">天</text>
        </view>
        <text class="album-share__info">最常做：红烧肉</text>
        <text class="album-share__info">心情：开心最多</text>
      </view>

      <view class="album-share__guozai-wrap">
        <view class="album-share__bubble">
          <text>用一道菜，治愈今天的你。</text>
        </view>
        <image class="album-share__guozai" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
      </view>

      <view class="album-share__btn">
        <text class="album-share__btn-text">分享我的9月干饭日记</text>
        <view class="album-share__qrcode">
          <view class="album-share__qr-grid" />
        </view>
      </view>
    </view>

    <!-- 翻页控制 -->
    <view class="album-nav">
      <view v-if="currentPage > 0" class="album-nav__btn" @click="prev">上一页</view>
      <view class="album-nav__dots">
        <view
          v-for="i in totalPages"
          :key="i"
          class="album-nav__dot"
          :class="{ 'album-nav__dot--active': currentPage === i - 1 }"
        />
      </view>
      <view v-if="currentPage < totalPages - 1" class="album-nav__btn album-nav__btn--primary" @click="next">下一页</view>
    </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.album-page {
  min-height: 100vh;
  background: var(--mrc-bg-alt);
  position: relative;
  padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* 封面 */
.album-cover {
  position: relative;
  min-height: calc(100vh - 100rpx);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40rpx 48rpx;
  box-sizing: border-box;
  overflow: hidden;
}
.album-cover__bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
}
.album-cover__blob {
  position: absolute;
  border-radius: 50%;
  opacity: 0.15;
  filter: blur(30rpx);
}
.album-cover__blob--1 {
  width: 300rpx;
  height: 300rpx;
  background: var(--mrc-primary);
  top: 100rpx;
  left: -80rpx;
}
.album-cover__blob--2 {
  width: 250rpx;
  height: 250rpx;
  background: var(--mrc-yellow);
  top: 200rpx;
  right: -60rpx;
}
.album-cover__blob--3 {
  width: 280rpx;
  height: 280rpx;
  background: var(--mrc-green);
  bottom: 300rpx;
  left: -40rpx;
  opacity: 0.1;
}
.album-cover__blob--4 {
  width: 220rpx;
  height: 220rpx;
  background: var(--mrc-blue);
  bottom: 150rpx;
  right: -30rpx;
  opacity: 0.12;
}
.album-cover__content {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}
.album-cover__brand {
  font-size: 32rpx;
  color: var(--mrc-text-sub);
  letter-spacing: 8rpx;
  margin-bottom: 40rpx;
}
.album-cover__title {
  font-size: 88rpx;
  font-weight: 900;
  color: var(--mrc-text-deep);
  line-height: 1.2;
  text-align: center;
  letter-spacing: 4rpx;
}
.album-cover__subtitle {
  font-size: 32rpx;
  color: var(--mrc-text-mid);
  margin-top: 28rpx;
  margin-bottom: 40rpx;
  letter-spacing: 2rpx;
}
.album-cover__guozai-wrap {
  position: relative;
  width: 100%;
  display: flex;
  justify-content: center;
  margin-bottom: 40rpx;
}
.album-cover__guozai {
  width: 480rpx;
  height: 480rpx;
}
.album-cover__deco {
  position: absolute;
  font-size: 48rpx;
}
.album-cover__deco--star1 {
  top: 20rpx;
  left: 80rpx;
  transform: rotate(-15deg);
}
.album-cover__deco--star2 {
  top: 60rpx;
  right: 100rpx;
  transform: rotate(20deg);
  font-size: 40rpx;
}
.album-cover__deco--heart1 {
  top: 120rpx;
  right: 60rpx;
  font-size: 36rpx;
}
.album-cover__deco--heart2 {
  bottom: 100rpx;
  left: 60rpx;
  font-size: 32rpx;
}
.album-cover__deco--cloud {
  bottom: 60rpx;
  right: 80rpx;
  font-size: 44rpx;
}
.album-cover__bubble {
  background: #fff;
  border-radius: 48rpx;
  padding: 28rpx 48rpx;
  box-shadow: 0 8rpx 24rpx rgba(121, 73, 53, 0.1);
  position: relative;
}
.album-cover__bubble text {
  font-size: 34rpx;
  color: var(--mrc-text-deep);
  font-weight: 600;
}
.album-cover__bubble::after {
  content: '';
  position: absolute;
  bottom: -16rpx;
  left: 50%;
  transform: translateX(-50%);
  width: 0;
  height: 0;
  border-left: 16rpx solid transparent;
  border-right: 16rpx solid transparent;
  border-top: 16rpx solid #fff;
}

/* 第2页：月度盘点 */
.album-stats {
  position: relative;
  min-height: calc(100vh - 100rpx);
  padding: 32rpx 36rpx 200rpx;
  box-sizing: border-box;
}
.album-stats__header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  margin-bottom: 24rpx;
}
.album-stats__title {
  font-size: 64rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
  letter-spacing: 4rpx;
}
.album-stats__guozai-icon {
  width: 72rpx;
  height: 72rpx;
}
.album-stats__big {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 36rpx;
}
.album-stats__number {
  font-size: 180rpx;
  font-weight: 700;
  color: var(--mrc-accent);
  line-height: 1;
  letter-spacing: -8rpx;
}
.album-stats__label {
  font-size: 32rpx;
  color: var(--mrc-text-sub);
  margin-top: 12rpx;
  letter-spacing: 4rpx;
}
.album-card {
  background: var(--mrc-bg-soft);
  border-radius: 32rpx;
  padding: 28rpx 28rpx;
  margin-bottom: 24rpx;
}
.album-card__title {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--mrc-text-strong);
  margin-bottom: 28rpx;
  display: block;
}
.album-moods {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16rpx;
}
.album-mood {
  border-radius: 20rpx;
  padding: 20rpx 8rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}
.album-mood__name {
  font-size: 30rpx;
  color: #fff;
  font-weight: 600;
}
.album-mood__count {
  font-size: 28rpx;
  color: rgba(255,255,255,0.9);
}
.album-mood--happy { background: var(--mrc-yellow); }
.album-mood--warm { background: var(--mrc-mood-anxious); }
.album-mood--calm { background: var(--mrc-green); }
.album-mood--tired { background: var(--mrc-blue); }
.album-mood--sad { background: var(--mrc-mood-sad); }
.album-mood--anxious { background: var(--mrc-accent); }
.album-dishes {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.album-dish {
  display: flex;
  align-items: center;
  padding: 20rpx 8rpx;
  border-bottom: 2rpx solid rgba(232, 212, 192, 0.5);
}
.album-dish:last-child {
  border-bottom: none;
}
.album-dish__medal {
  font-size: 44rpx;
  width: 64rpx;
  text-align: center;
}
.album-dish__name {
  flex: 1;
  font-size: 36rpx;
  color: var(--mrc-text-strong);
  font-weight: 600;
  margin-left: 16rpx;
}
.album-dish__count {
  font-size: 36rpx;
  color: var(--mrc-accent);
  font-weight: 700;
}
.album-streak {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  margin-top: 16rpx;
  padding: 24rpx;
}
.album-streak__text {
  font-size: 34rpx;
  color: var(--mrc-text-deep);
}
.album-streak__num {
  font-size: 48rpx;
  font-weight: 700;
  color: var(--mrc-accent);
}
.album-streak__fire {
  font-size: 40rpx;
}
.album-stats__guozai {
  position: absolute;
  bottom: 140rpx;
  right: 20rpx;
  width: 200rpx;
  height: 200rpx;
  opacity: 0.9;
}

/* 第3页：每日记录 */
.album-daily {
  position: relative;
  min-height: calc(100vh - 100rpx);
  padding: 40rpx 36rpx 200rpx;
  box-sizing: border-box;
}
.album-daily__title {
  font-size: 72rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
  text-align: center;
  display: block;
  margin-bottom: 40rpx;
  letter-spacing: 6rpx;
}
.album-daily__list {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
}
.album-daily__card {
  background: var(--mrc-bg-soft);
  border-radius: 32rpx;
  padding: 24rpx;
  display: flex;
  align-items: flex-start;
  gap: 24rpx;
  position: relative;
}
.album-daily__img {
  width: 180rpx;
  height: 180rpx;
  border-radius: 20rpx;
  flex-shrink: 0;
}
.album-daily__info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
}
.album-daily__date {
  font-size: 30rpx;
  color: var(--mrc-text-sub);
}
.album-daily__dish {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--mrc-text-strong);
}
.album-daily__mood {
  font-size: 28rpx;
  color: var(--mrc-text-mid);
}
.album-daily__note {
  font-size: 28rpx;
  color: var(--mrc-text-deep);
  line-height: 1.5;
  margin-top: 4rpx;
}
.album-daily__heart {
  position: absolute;
  top: 28rpx;
  right: 28rpx;
  font-size: 36rpx;
}
.album-daily__guozai-wrap {
  position: relative;
  display: flex;
  justify-content: center;
  margin-top: 40rpx;
}
.album-daily__guozai {
  width: 360rpx;
  height: 360rpx;
}
.album-daily__deco {
  position: absolute;
  font-size: 40rpx;
}
.album-daily__deco--star1 {
  top: 20rpx;
  left: 120rpx;
  transform: rotate(-15deg);
}
.album-daily__deco--star2 {
  top: 60rpx;
  right: 140rpx;
  transform: rotate(20deg);
  font-size: 32rpx;
}
.album-daily__deco--heart {
  top: 100rpx;
  right: 100rpx;
  font-size: 32rpx;
}
.album-daily__deco--cloud1 {
  bottom: 40rpx;
  left: 60rpx;
  font-size: 36rpx;
}
.album-daily__deco--cloud2 {
  bottom: 80rpx;
  right: 80rpx;
  font-size: 32rpx;
}

/* 第4页：AI寄语 */
.album-message {
  position: relative;
  min-height: calc(100vh - 100rpx);
  padding: 60rpx 48rpx 200rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.album-message__guozai-wrap {
  position: relative;
  width: 100%;
  display: flex;
  justify-content: center;
  margin-bottom: 60rpx;
}
.album-message__guozai {
  width: 400rpx;
  height: 400rpx;
}
.album-message__deco {
  position: absolute;
  font-size: 44rpx;
}
.album-message__deco--star1 {
  top: 20rpx;
  left: 100rpx;
  transform: rotate(-15deg);
}
.album-message__deco--star2 {
  top: 10rpx;
  right: 120rpx;
  transform: rotate(20deg);
  font-size: 52rpx;
}
.album-message__deco--heart1 {
  top: 80rpx;
  right: 80rpx;
  font-size: 36rpx;
}
.album-message__deco--heart2 {
  top: 160rpx;
  left: 60rpx;
  font-size: 32rpx;
}
.album-message__deco--cloud1 {
  bottom: 20rpx;
  left: 40rpx;
  font-size: 40rpx;
}
.album-message__deco--cloud2 {
  top: 180rpx;
  right: 40rpx;
  font-size: 44rpx;
}
.album-message__text {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  width: 100%;
}
.album-message__text text {
  font-size: 44rpx;
  color: var(--mrc-text-strong);
  line-height: 1.6;
  font-weight: 600;
}
.album-message__sign {
  align-self: flex-end;
  font-size: 40rpx;
  color: var(--mrc-brown);
  margin-top: 48rpx;
  font-weight: 600;
}

/* 第5页：分享页 */
.album-share {
  position: relative;
  min-height: calc(100vh - 100rpx);
  padding: 40rpx 36rpx 200rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.album-share__header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  margin-bottom: 48rpx;
}
.album-share__brand {
  font-size: 48rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
  letter-spacing: 4rpx;
}
.album-share__guozai-sm {
  width: 72rpx;
  height: 72rpx;
}
.album-share__card {
  width: 100%;
  background: linear-gradient(135deg, var(--mrc-bg-soft), #FDE6D4);
  border-radius: 40rpx;
  padding: 48rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 32rpx;
}
.album-share__label {
  font-size: 40rpx;
  color: var(--mrc-text-mid);
  margin-bottom: 16rpx;
}
.album-share__big {
  display: flex;
  align-items: baseline;
  margin-bottom: 32rpx;
}
.album-share__num {
  font-size: 160rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
  line-height: 1;
}
.album-share__unit {
  font-size: 48rpx;
  color: var(--mrc-text-deep);
  font-weight: 600;
  margin-left: 8rpx;
}
.album-share__info {
  font-size: 36rpx;
  color: var(--mrc-text-strong);
  margin-bottom: 12rpx;
}
.album-share__guozai-wrap {
  position: relative;
  width: 100%;
  display: flex;
  justify-content: center;
  margin-bottom: 40rpx;
}
.album-share__bubble {
  position: absolute;
  top: 0;
  left: 40rpx;
  background: #fff;
  border-radius: 32rpx;
  padding: 20rpx 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(121, 73, 53, 0.1);
  z-index: 2;
}
.album-share__bubble text {
  font-size: 30rpx;
  color: var(--mrc-text-deep);
  font-weight: 600;
}
.album-share__bubble::after {
  content: '';
  position: absolute;
  bottom: -12rpx;
  left: 40rpx;
  width: 0;
  height: 0;
  border-left: 12rpx solid transparent;
  border-right: 12rpx solid transparent;
  border-top: 12rpx solid #fff;
}
.album-share__guozai {
  width: 360rpx;
  height: 360rpx;
  margin-top: 40rpx;
}
.album-share__btn {
  width: 100%;
  background: linear-gradient(135deg, var(--mrc-mood-anxious), var(--mrc-primary-deep));
  border-radius: 48rpx;
  padding: 32rpx 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(253, 145, 132, 0.3);
}
.album-share__btn-text {
  font-size: 38rpx;
  color: #fff;
  font-weight: 700;
}
.album-share__qrcode {
  width: 80rpx;
  height: 80rpx;
  background: #fff;
  border-radius: 8rpx;
  padding: 6rpx;
  box-sizing: border-box;
}
.album-share__qr-grid {
  width: 100%;
  height: 100%;
  background:
    linear-gradient(90deg, #000 50%, transparent 50%),
    linear-gradient(#000 50%, transparent 50%);
  background-size: 12rpx 12rpx;
  background-position: 0 0, 6rpx 6rpx;
}

/* 占位页 */
.album-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 16rpx;
}
.album-placeholder__text {
  font-size: 36rpx;
  color: var(--mrc-text-deep);
  font-weight: 600;
}
.album-placeholder__sub {
  font-size: 28rpx;
  color: var(--mrc-text-sub);
}

/* 翻页控制 */
.album-nav {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 32rpx calc(20rpx + env(safe-area-inset-bottom));
  background: rgba(253, 246, 236, 0.95);
  backdrop-filter: blur(10px);
  z-index: 30;
}
.album-nav__btn {
  font-size: 28rpx;
  color: var(--mrc-text-deep);
  padding: 16rpx 32rpx;
  background: var(--mrc-bg-soft);
  border-radius: 32rpx;
  min-width: 120rpx;
  text-align: center;
}
.album-nav__btn--primary {
  background: linear-gradient(135deg, var(--mrc-primary), var(--mrc-primary-deep));
  color: #fff;
}
.album-nav__dots {
  display: flex;
  gap: 12rpx;
}
.album-nav__dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: var(--mrc-border-light);
}
.album-nav__dot--active {
  background: var(--mrc-primary-deep);
  width: 32rpx;
  border-radius: 8rpx;
}
</style>
