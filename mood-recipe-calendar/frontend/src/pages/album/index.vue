<script setup lang="ts">
import { navBack } from '@/composables/useNavBar'
import Icon from '../../components/common/Icon.vue'
import { ref, computed } from 'vue'
import LoadingState from '../../components/guozai/LoadingState.vue'
import ErrorState from '../../components/guozai/ErrorState.vue'
import { ensureLogin } from '../../utils/login'
import { toast, toastError, toastSuccess } from '../../utils/toast'
import { fetchMonthAlbum, type AlbumItem } from '../../api/albums'
import { fetchRecordsByMonth, type RecordItem } from '../../api/records'
import { autoLayout, chunkPages, MOOD_EMOJI, MOOD_COLOR, type LayoutBox } from '../../utils/albumLayout'
import { exportAlbumShare } from '../../utils/albumShare'

definePage({
  name: 'album',
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '月度画册',
  },
})

const currentPage = ref(0)
const loading = ref(true)
const error = ref('')
const album = ref<AlbumItem | null>(null)
const records = ref<RecordItem[]>([])

const now = new Date()
const monthStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
const monthNum = now.getMonth() + 1
const yearNum = now.getFullYear()
type AlbumStats = {
  totalDays: number
  moodDistribution: Record<string, number>
  topDishes: { name: string; count: number }[]
  longestStreak: number
}
const EMPTY_STATS: AlbumStats = { totalDays: 0, moodDistribution: {}, topDishes: [], longestStreak: 0 }

// ---------- 数据解析 ----------
const stats = computed<AlbumStats>(() => {
  if (!album.value?.stats) return EMPTY_STATS
  try {
    const parsed = JSON.parse(album.value.stats) as Partial<AlbumStats>
    return {
      totalDays: Number(parsed.totalDays) || 0,
      moodDistribution: parsed.moodDistribution || {},
      topDishes: Array.isArray(parsed.topDishes) ? parsed.topDishes : [],
      longestStreak: Number(parsed.longestStreak) || 0,
    }
  } catch { return EMPTY_STATS }
})

const moodList = computed(() => {
  const dist = stats.value.moodDistribution || {}
  return Object.entries(dist)
    .map(([mood, count]) => ({ mood, count }))
    .sort((a, b) => b.count - a.count)
})

const topDishes = computed(() => (stats.value.topDishes || []).slice(0, 3))

// ---------- 每日记录：智能排版分页 ----------
const recordPages = computed(() => chunkPages(records.value))

// 每日记录内容区（rpx）：x/y/w/h
const DAILY_AREA = { x: 40, y: 200, w: 670, h: 960 }
const dailyBoxes = computed<LayoutBox[][]>(() =>
  recordPages.value.map((pg) => autoLayout(pg.length, DAILY_AREA, 22)),
)

// ---------- 画册页数 ----------
const totalPages = computed(() => 3 + recordPages.value.length) // 封面/盘点 + 记录页 + 寄语/分享

async function loadAlbum() {
  loading.value = true
  error.value = ''
  try {
    const openid = await ensureLogin()
    album.value = await fetchMonthAlbum(openid, monthStr)
    records.value = await fetchRecordsByMonth(openid, monthStr)
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

// ---------- 翻页 ----------
function onSwiperChange(e: any) {
  currentPage.value = e.detail.current
}
function next() {
  if (currentPage.value < totalPages.value - 1) currentPage.value++
}
function prev() {
  if (currentPage.value > 0) currentPage.value--
}

// ---------- 分享导出 ----------
const sharing = ref(false)
async function onShare() {
  if (sharing.value) return
  sharing.value = true
  uni.showLoading({ title: '正在生成分享图...' })
  try {
    const topMood = moodList.value[0]
    await exportAlbumShare({
      brand: '心情菜谱日历',
      title: `${yearNum}年${monthNum}月干饭日记`,
      totalDays: stats.value.totalDays || 0,
      topDish: topDishes.value[0]?.name || '—',
      topMood: topMood ? `${topMood.mood} ${topMood.count}天` : '—',
      slogan: '用一道菜，治愈今天的你',
      guozaiPath: '/static/guozai/mood_01_happy.png',
      footer: '「锅仔」· 你的情绪味蕾搭子',
    })
    toastSuccess('已保存到相册')
  } catch (e: any) {
    toastError(e, '导出失败')
  } finally {
    uni.hideLoading()
    sharing.value = false
  }
}

// 心情分布色块（宽度占比）
function moodSegStyle(mood: string, count: number) {
  const total = moodList.value.reduce((s, m) => s + m.count, 0) || 1
  const w = (count / total) * 670
  return { width: `${Math.max(w, 60)}rpx`, background: MOOD_COLOR[mood] || '#E8836B' }
}

function dailyCellStyle(box: LayoutBox) {
  return { left: `${box.x}rpx`, top: `${box.y}rpx`, width: `${box.w}rpx`, height: `${box.h}rpx` }
}

function dailyTagSize(name: string) {
  return name && name.length > 5 ? '20rpx' : '24rpx'
}

const aiLines = computed(() => {
  const t = album.value?.aiSummary
  if (!t) return ['这个月，你好好吃饭了。', '下个月，请继续对自己好一点。']
  return t.split('\n').filter(Boolean)
})
</script>

<template>
  <view class="album-page">
    <wd-navbar title="月度画册" left-arrow safe-area-inset-top @click-left="navBack"  custom-style="background-color: transparent !important;" />

    <view class="album-page__share" @click="onShare">
      <Icon name="share" :size="36" color="var(--mrc-text)" />
    </view>

    <LoadingState v-if="loading" text="锅仔正在装订画册..." />
    <ErrorState v-else-if="error" @retry="loadAlbum" />

    <template v-else>
      <swiper
        class="album-swiper"
        :current="currentPage"
        @change="onSwiperChange"
        :duration="320"
      >
        <!-- ======== 第1页：封面 ======== -->
        <swiper-item>
          <view class="album-cover">
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
              <text class="album-cover__subtitle">{{ yearNum }}年{{ monthNum }}月 · 共记录{{ stats.totalDays }}天</text>
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
        </swiper-item>

        <!-- ======== 第2页：月度盘点 ======== -->
        <swiper-item>
          <view class="album-stats">
            <view class="album-stats__header">
              <text class="album-stats__title">{{ monthNum }}月盘点</text>
              <image class="album-stats__guozai-icon" src="/static/guozai/mood_10_content.png" mode="aspectFit" />
            </view>

            <view class="album-stats__big">
              <text class="album-stats__number">{{ stats.totalDays || 0 }}</text>
              <text class="album-stats__label">本月记录天数</text>
            </view>

            <!-- 心情分布 -->
            <view class="album-card">
              <text class="album-card__title">心情分布</text>
              <view v-if="moodList.length" class="album-mood-bar">
                <view
                  v-for="m in moodList"
                  :key="m.mood"
                  class="album-mood-seg"
                  :style="moodSegStyle(m.mood, m.count)"
                >
                  <text class="album-mood-seg__txt">{{ m.mood }} {{ m.count }}</text>
                </view>
              </view>
              <view v-else class="album-card__empty">
                <text>这个月还没有心情记录</text>
              </view>
            </view>

            <!-- 最常做的菜 TOP3 -->
            <view class="album-card">
              <text class="album-card__title">最常做的菜 TOP3</text>
              <view v-if="topDishes.length" class="album-dishes">
                <view v-for="(dish, i) in topDishes" :key="dish.name" class="album-dish">
                  <text class="album-dish__medal">{{ ['🥇', '🥈', '🥉'][i] }}</text>
                  <text class="album-dish__name">{{ dish.name }}</text>
                  <text class="album-dish__count">{{ dish.count }}次</text>
                </view>
              </view>
              <view v-else class="album-card__empty">
                <text>这个月还没有做菜记录</text>
              </view>
            </view>

            <!-- 最长连续记录 -->
            <view class="album-streak">
              <text class="album-streak__text">最长连续记录</text>
              <text class="album-streak__num">{{ stats.longestStreak || 0 }}</text>
              <text class="album-streak__fire">🔥</text>
              <text class="album-streak__text">天</text>
            </view>

            <image class="album-stats__guozai" src="/static/guozai/mood_10_content.png" mode="aspectFit" />
          </view>
        </swiper-item>

        <!-- ======== 每日记录（智能排版，可多页） ======== -->
        <swiper-item v-for="(pg, pi) in recordPages" :key="'daily-' + pi">
          <view class="album-daily">
            <text class="album-daily__title">每日记录</text>
            <view class="album-daily__layout">
              <view
                v-for="(rec, idx) in pg"
                :key="rec.id"
                class="album-daily__cell"
                :style="dailyCellStyle(dailyBoxes[pi][idx])"
              >
                <image
                  v-if="rec.imageUrl"
                  class="album-daily__cell-img"
                  :src="rec.imageUrl"
                  mode="aspectFill"
                />
                <view v-else class="album-daily__cell-empty">
                  <text class="album-daily__cell-emoji">{{ MOOD_EMOJI[rec.moodTag] || '🍽️' }}</text>
                </view>
                <view class="album-daily__cell-tag">
                  <text class="album-daily__cell-tag-txt" :style="{ fontSize: dailyTagSize(rec.dishName) }">{{ rec.dishName }}</text>
                </view>
              </view>
            </view>
            <view class="album-daily__guozai-wrap">
              <image class="album-daily__guozai" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
              <text class="album-daily__deco album-daily__deco--star1">⭐</text>
              <text class="album-daily__deco album-daily__deco--star2">⭐</text>
            </view>
          </view>
        </swiper-item>
        <swiper-item v-if="!recordPages.length">
          <view class="album-daily album-daily--empty">
            <text class="album-daily__title">每日记录</text>
            <view class="album-daily__empty-tip">
              <image class="album-daily__empty-guozai" src="/static/guozai/action_07_empty.png" mode="aspectFit" />
              <text class="album-daily__empty-text">这个月还没有记录\n去吃点好吃的吧！</text>
            </view>
          </view>
        </swiper-item>

        <!-- ======== AI 寄语 ======== -->
        <swiper-item>
          <view class="album-message">
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
              <text v-for="(line, i) in aiLines" :key="i">{{ line }}</text>
            </view>
            <text class="album-message__sign">—— 锅仔</text>
          </view>
        </swiper-item>

        <!-- ======== 分享页 ======== -->
        <swiper-item>
          <view class="album-share">
            <view class="album-share__header">
              <text class="album-share__brand">心情菜谱日历</text>
              <image class="album-share__guozai-sm" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
            </view>
            <view class="album-share__card">
              <text class="album-share__label">记录了</text>
              <view class="album-share__big">
                <text class="album-share__num">{{ stats.totalDays || 0 }}</text>
                <text class="album-share__unit">天</text>
              </view>
              <text class="album-share__info">最常做：{{ topDishes[0]?.name || '—' }}</text>
              <text class="album-share__info">心情：{{ moodList[0] ? moodList[0].mood + '最多' : '—' }}</text>
            </view>
            <view class="album-share__guozai-wrap">
              <view class="album-share__bubble">
                <text>用一道菜，治愈今天的你。</text>
              </view>
              <image class="album-share__guozai" src="/static/guozai/action_09_celebrate.png" mode="aspectFit" />
            </view>
            <view class="album-share__btn" @click="onShare">
              <text class="album-share__btn-text">保存我的{{ monthNum }}月干饭分享图</text>
              <view class="album-share__qrcode">
                <view class="album-share__qr-grid" />
              </view>
            </view>
          </view>
        </swiper-item>
      </swiper>

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
        <view v-else class="album-nav__btn album-nav__btn--primary" @click="onShare">保存分享图</view>
      </view>

      <!-- 隐藏 Canvas 节点（导出分享长图用） -->
      <canvas type="2d" id="shareCanvas" class="album-share__canvas" />
    </template>
  </view>
</template>

<style lang="scss" scoped>
.album-page {
  height: 100vh;
  background: var(--mrc-bg);
  position: relative;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  overflow: hidden;
}
.album-swiper {
  flex: 1;
  min-height: 0;
}
.album-swiper :deep(.uni-swiper-dots),
.album-swiper :deep(.uni-swiper-dot) {
  display: none;
}

/* 封面 */
.album-cover {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40rpx 48rpx 120rpx;
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
.album-cover__blob--1 { width: 300rpx; height: 300rpx; background: var(--mrc-primary); top: 100rpx; left: -80rpx; }
.album-cover__blob--2 { width: 250rpx; height: 250rpx; background: var(--mrc-yellow); top: 200rpx; right: -60rpx; }
.album-cover__blob--3 { width: 280rpx; height: 280rpx; background: var(--mrc-green); bottom: 300rpx; left: -40rpx; opacity: 0.1; }
.album-cover__blob--4 { width: 220rpx; height: 220rpx; background: var(--mrc-blue); bottom: 150rpx; right: -30rpx; opacity: 0.12; }
.album-cover__content {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  margin-top: 40rpx;
}
.album-cover__brand { font-size: 32rpx; color: var(--mrc-text-sub); letter-spacing: 8rpx; margin-bottom: 40rpx; }
.album-cover__title { font-size: 76rpx; font-weight: var(--mrc-fw-heavy); color: var(--mrc-text-deep); line-height: 1.2; text-align: center; letter-spacing: 4rpx; }
.album-cover__subtitle { font-size: 32rpx; color: var(--mrc-text-mid); margin-top: 28rpx; margin-bottom: 40rpx; letter-spacing: 2rpx; }
.album-cover__guozai-wrap { position: relative; width: 100%; display: flex; justify-content: center; margin-bottom: 40rpx; }
.album-cover__guozai { width: 460rpx; height: 460rpx; }
.album-cover__deco { position: absolute; font-size: 48rpx; }
.album-cover__deco--star1 { top: 20rpx; left: 80rpx; transform: rotate(-15deg); }
.album-cover__deco--star2 { top: 60rpx; right: 100rpx; transform: rotate(20deg); font-size: 40rpx; }
.album-cover__deco--heart1 { top: 120rpx; right: 60rpx; font-size: 36rpx; }
.album-cover__deco--heart2 { bottom: 100rpx; left: 60rpx; font-size: 32rpx; }
.album-cover__deco--cloud { bottom: 60rpx; right: 80rpx; font-size: 44rpx; }
.album-cover__bubble {
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 48rpx;
  padding: 28rpx 48rpx;
  box-shadow: var(--mrc-shadow-soft);
  position: relative;
}
.album-cover__bubble text { font-size: 34rpx; color: var(--mrc-text-deep); font-weight: 600; }
.album-cover__bubble::after {
  content: '';
  position: absolute;
  bottom: -16rpx;
  left: 50%;
  transform: translateX(-50%);
  width: 0; height: 0;
  border-left: 16rpx solid transparent;
  border-right: 16rpx solid transparent;
  border-top: 16rpx solid #fff;
}

/* 月度盘点 */
.album-stats {
  position: relative;
  height: 100%;
  padding: 24rpx 36rpx 120rpx;
  box-sizing: border-box;
  overflow-y: auto;
}
.album-stats__header { display: flex; align-items: center; justify-content: center; gap: 16rpx; margin-bottom: 16rpx; }
.album-stats__title { font-size: 60rpx; font-weight: 700; color: var(--mrc-text-deep); letter-spacing: 4rpx; }
.album-stats__guozai-icon { width: 72rpx; height: 72rpx; }
.album-stats__big { display: flex; flex-direction: column; align-items: center; margin-bottom: 28rpx; }
.album-stats__number { font-size: 160rpx; font-weight: 700; color: var(--mrc-accent); line-height: 1; letter-spacing: -8rpx; }
.album-stats__label { font-size: 30rpx; color: var(--mrc-text-sub); margin-top: 12rpx; letter-spacing: 4rpx; }
.album-card {
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 32rpx;
  padding: 28rpx 28rpx;
  margin-bottom: 24rpx;
  box-shadow: var(--mrc-shadow-soft);
}
.album-card__title { font-size: 38rpx; font-weight: 700; color: var(--mrc-text-strong); margin-bottom: 24rpx; display: block; }
.album-card__empty { text-align: center; font-size: 28rpx; color: var(--mrc-text-sub); padding: 20rpx 0; }

/* 心情分布色块条 */
.album-mood-bar { display: flex; height: 56rpx; border-radius: 28rpx; overflow: hidden; }
.album-mood-seg { display: flex; align-items: center; justify-content: center; }
.album-mood-seg__txt { font-size: 22rpx; color: #fff; font-weight: 700; white-space: nowrap; }

.album-dishes { display: flex; flex-direction: column; gap: 8rpx; }
.album-dish { display: flex; align-items: center; padding: 20rpx 8rpx; border-bottom: 2rpx solid rgba(232, 212, 192, 0.5); }
.album-dish:last-child { border-bottom: none; }
.album-dish__medal { font-size: 44rpx; width: 64rpx; text-align: center; }
.album-dish__name { flex: 1; font-size: 36rpx; color: var(--mrc-text-strong); font-weight: 600; margin-left: 16rpx; }
.album-dish__count { font-size: 36rpx; color: var(--mrc-accent); font-weight: 700; }
.album-streak { display: flex; align-items: center; justify-content: center; gap: 12rpx; margin-top: 8rpx; padding: 20rpx; }
.album-streak__text { font-size: 32rpx; color: var(--mrc-text-deep); }
.album-streak__num { font-size: 46rpx; font-weight: 700; color: var(--mrc-accent); }
.album-streak__fire { font-size: 38rpx; }
.album-stats__guozai { position: absolute; bottom: 100rpx; right: 20rpx; width: 180rpx; height: 180rpx; opacity: 0.9; }

/* 每日记录：智能排版 */
.album-daily {
  position: relative;
  height: 100%;
  padding: 24rpx 36rpx 120rpx;
  box-sizing: border-box;
}
.album-daily__title { font-size: 64rpx; font-weight: 700; color: var(--mrc-text-deep); text-align: center; display: block; margin-bottom: 24rpx; letter-spacing: 6rpx; }
.album-daily__layout { position: relative; width: 100%; height: 960rpx; }
.album-daily__cell { position: absolute; box-sizing: border-box; }
.album-daily__cell-img { width: 100%; height: 100%; border-radius: 24rpx; display: block; }
.album-daily__cell-empty {
  width: 100%; height: 100%; border-radius: 24rpx;
  background: var(--mrc-surface-2);
  border: 2rpx dashed var(--mrc-border-light);
  display: flex; align-items: center; justify-content: center;
}
.album-daily__cell-emoji { font-size: 72rpx; }
.album-daily__cell-tag {
  position: absolute; left: 0; right: 0; bottom: 0;
  height: 52rpx; border-radius: 0 0 24rpx 24rpx;
  background: rgba(90, 62, 43, 0.78);
  display: flex; align-items: center; justify-content: center;
}
.album-daily__cell-tag-txt { color: #fff; font-weight: 600; font-size: 24rpx; max-width: 90%; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.album-daily__guozai-wrap { position: relative; display: flex; justify-content: center; margin-top: 8rpx; }
.album-daily__guozai { width: 240rpx; height: 240rpx; }
.album-daily__deco { position: absolute; font-size: 36rpx; }
.album-daily__deco--star1 { top: 10rpx; left: 60rpx; transform: rotate(-15deg); }
.album-daily__deco--star2 { top: 40rpx; right: 80rpx; transform: rotate(20deg); }

/* 每日记录空状态 */
.album-daily--empty { display: flex; flex-direction: column; align-items: center; }
.album-daily__empty-tip { display: flex; flex-direction: column; align-items: center; gap: 24rpx; margin-top: 120rpx; }
.album-daily__empty-guozai { width: 320rpx; height: 320rpx; }
.album-daily__empty-text { font-size: 34rpx; color: var(--mrc-text-sub); text-align: center; line-height: 1.8; white-space: pre-line; }

/* AI 寄语 */
.album-message {
  position: relative;
  height: 100%;
  padding: 60rpx 48rpx 140rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  overflow-y: auto;
}
.album-message__guozai-wrap { position: relative; width: 100%; display: flex; justify-content: center; margin-bottom: 48rpx; }
.album-message__guozai { width: 360rpx; height: 360rpx; }
.album-message__deco { position: absolute; font-size: 44rpx; }
.album-message__deco--star1 { top: 20rpx; left: 100rpx; transform: rotate(-15deg); }
.album-message__deco--star2 { top: 10rpx; right: 120rpx; transform: rotate(20deg); font-size: 52rpx; }
.album-message__deco--heart1 { top: 80rpx; right: 80rpx; font-size: 36rpx; }
.album-message__deco--heart2 { top: 160rpx; left: 60rpx; font-size: 32rpx; }
.album-message__deco--cloud1 { bottom: 20rpx; left: 40rpx; font-size: 40rpx; }
.album-message__deco--cloud2 { top: 180rpx; right: 40rpx; font-size: 44rpx; }
.album-message__text { display: flex; flex-direction: column; gap: 16rpx; width: 100%; }
.album-message__text text { font-size: 42rpx; color: var(--mrc-text-strong); line-height: 1.6; font-weight: 600; }
.album-message__sign { align-self: flex-end; font-size: 38rpx; color: var(--mrc-brown); margin-top: 40rpx; font-weight: 600; }

/* 分享页 */
.album-share {
  position: relative;
  height: 100%;
  padding: 32rpx 36rpx 140rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  overflow-y: auto;
}
.album-share__header { display: flex; align-items: center; justify-content: center; gap: 16rpx; margin-bottom: 40rpx; }
.album-share__brand { font-size: 46rpx; font-weight: 700; color: var(--mrc-text-deep); letter-spacing: 4rpx; }
.album-share__guozai-sm { width: 68rpx; height: 68rpx; }
.album-share__card {
  width: 100%;
  background: linear-gradient(135deg, var(--mrc-bg-soft), #FDE6D4);
  border-radius: 40rpx;
  padding: 40rpx 36rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 28rpx;
}
.album-share__label { font-size: 38rpx; color: var(--mrc-text-mid); margin-bottom: 12rpx; }
.album-share__big { display: flex; align-items: baseline; margin-bottom: 24rpx; }
.album-share__num { font-size: 140rpx; font-weight: 700; color: var(--mrc-text-deep); line-height: 1; }
.album-share__unit { font-size: 44rpx; color: var(--mrc-text-deep); font-weight: 600; margin-left: 8rpx; }
.album-share__info { font-size: 34rpx; color: var(--mrc-text-strong); margin-bottom: 12rpx; }
.album-share__guozai-wrap { position: relative; width: 100%; display: flex; justify-content: center; margin-bottom: 28rpx; }
.album-share__bubble {
  position: absolute; top: 0; left: 40rpx;
  background: #fff; border-radius: 32rpx; padding: 20rpx 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(121, 73, 53, 0.1); z-index: 2;
}
.album-share__bubble text { font-size: 30rpx; color: var(--mrc-text-deep); font-weight: 600; }
.album-share__bubble::after {
  content: ''; position: absolute; bottom: -12rpx; left: 40rpx;
  width: 0; height: 0;
  border-left: 12rpx solid transparent; border-right: 12rpx solid transparent;
  border-top: 12rpx solid #fff;
}
.album-share__guozai { width: 320rpx; height: 320rpx; margin-top: 36rpx; }
.album-share__btn {
  width: 100%;
  background: linear-gradient(135deg, var(--mrc-mood-anxious), var(--mrc-primary-deep));
  border-radius: 48rpx;
  padding: 32rpx 36rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(253, 145, 132, 0.3);
}
.album-share__btn-text { font-size: 34rpx; color: #fff; font-weight: 700; }
.album-share__qrcode { width: 80rpx; height: 80rpx; background: #fff; border-radius: 8rpx; padding: 8rpx; box-sizing: border-box; }
.album-share__qr-grid {
  width: 100%; height: 100%;
  background:
    linear-gradient(90deg, #000 50%, transparent 50%),
    linear-gradient(#000 50%, transparent 50%);
  background-size: 12rpx 12rpx;
  background-position: 0 0, 6rpx 6rpx;
}

/* 隐藏 Canvas（分享图导出） */
.album-share__canvas {
  position: fixed;
  left: -9999px;
  top: 0;
  width: 375px;
  height: 667px;
}

/* 翻页控制 */
.album-nav {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 32rpx calc(16rpx + env(safe-area-inset-bottom));
  background: rgba(255, 252, 247, 0.96);
  border-top: 2rpx solid var(--mrc-border-light);
  z-index: 30;
}
.album-nav__btn { font-size: 28rpx; color: var(--mrc-text-deep); padding: 16rpx 28rpx; background: var(--mrc-surface-2); border-radius: 32rpx; min-width: 120rpx; text-align: center; }
.album-nav__btn--primary { background: var(--mrc-primary-grad); color: #fff; }
.album-nav__dots { display: flex; gap: 12rpx; }
.album-nav__dot { width: 14rpx; height: 14rpx; border-radius: 50%; background: var(--mrc-border-light); }
.album-nav__dot--active { background: var(--mrc-primary-deep); width: 32rpx; border-radius: 8rpx; }
/* 分享按钮：navbar 右侧被小程序胶囊遮挡，移到内容区右上角浮动 */
.album-page__share { position: absolute; top: calc(env(safe-area-inset-top) + 92rpx); right: 24rpx; z-index: 50; width: 72rpx; height: 72rpx; border-radius: 50%; background: rgba(255, 255, 255, 0.9); box-shadow: var(--mrc-shadow-sm); display: flex; align-items: center; justify-content: center; }
</style>
