<script setup lang="ts">
import type { RecordItem } from '../../api/records'
import { useImagePreview } from '@wot-ui/ui'
import { computed, ref, onUnmounted } from 'vue'
import { navBack } from '@/composables/useNavBar'
import { STATIC_BASE_URL } from '@/utils/assets'
import { resolveAssetUrl } from '@/api/request'
import { fetchRecordsByMonth, fetchStats } from '../../api/records'
import ErrorState from '../../components/guozai/ErrorState.vue'
import LoadingState from '../../components/guozai/LoadingState.vue'
import GuozaiImage from '../../components/guozai/GuozaiImage.vue'
import { ensureLogin } from '../../utils/login'
import { bus, MRC_EVENTS } from '@/utils/bus'

/** record 页 onShow 时读取此键，将补记落到指定历史日期。 */
const RECORD_DATE_KEY = 'mrc_record_date'

definePage({
  name: 'calendar',
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '心情日历',
  },
})

import { onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'

// 直接本页写生命周期，比 useShare 组合式更可靠
onShareAppMessage(() => {
  const pages = getCurrentPages()
  const route = (pages[pages.length - 1] as any)?.route || ''
  return { title: '锅仔 · 按心情帮你决定今天吃什么', path: `/${route}` }
})
onShareTimeline(() => ({ title: '锅仔 · 按心情帮你决定今天吃什么' }))

const router = useRouter()
const route = useRoute()

const { previewImage } = useImagePreview()

const weekCN = ['日', '一', '二', '三', '四', '五', '六']
const now = new Date()
const year = ref(now.getFullYear())
const month = ref(now.getMonth() + 1)
const today = now.getDate()
const monthStr = computed(() => `${year.value}-${String(month.value).padStart(2, '0')}`)
const monthNumber = computed(() => String(month.value).padStart(2, '0'))

const loading = ref(true)
const error = ref('')
const records = ref<RecordItem[]>([])
const stats = ref({ totalDays: 0, currentStreak: 0 })
const statsError = ref(false)

// 记录日期映射：同一天可能有多条记录，存为数组而非单条（避免互相覆盖）
const recordMap = computed(() => {
  const map = new Map<number, RecordItem[]>()
  records.value.forEach((r) => {
    const day = Number.parseInt(r.recordDate?.split('-')[2] || '0', 10)
    if (day > 0) {
      const list = map.get(day)
      if (list) list.push(r)
      else map.set(day, [r])
    }
  })
  return map
})

const firstDay = computed(() => new Date(year.value, month.value - 1, 1).getDay())
const daysInMonth = computed(() => new Date(year.value, month.value, 0).getDate())

const calCells = computed(() => {
  const cells: ({ day: number, hasRecord: boolean, isToday: boolean, dishImg: string, mood: string, count: number } | null)[] = []
  for (let i = 0; i < firstDay.value; i++) cells.push(null)
  for (let d = 1; d <= daysInMonth.value; d++) {
    const recs = recordMap.value.get(d)
    cells.push({
      day: d,
      hasRecord: !!recs?.length,
      isToday: d === today && month.value === now.getMonth() + 1 && year.value === now.getFullYear(),
      dishImg: recs?.[0]?.imageUrl ? resolveAssetUrl(recs[0].imageUrl) : '',
      mood: recs?.[0]?.moodTag || '',
      count: recs?.length || 0,
    })
  }
  while (cells.length % 7 !== 0) cells.push(null)
  return cells
})

// 空状态弹窗
const showEmpty = ref(false)
const emptyDay = ref(0)
// 记录详情弹窗（支持一天多条，左右翻看）
const showDetail = ref(false)
const detailRecords = ref<RecordItem[]>([])
const detailIndex = ref(0)
const detailRecord = computed<RecordItem | null>(() => detailRecords.value[detailIndex.value] ?? null)
let initialDayHandled = false

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    await ensureLogin()
    // 日历记录是核心，独立加载；统计（连续天数等）失败不应清空整月日历
    records.value = await fetchRecordsByMonth(monthStr.value)
    fetchStats()
      .then(s => { stats.value = { totalDays: s.totalDays, currentStreak: s.currentStreak }; statsError.value = false })
      .catch(() => { statsError.value = true })
    if (!initialDayHandled) {
      initialDayHandled = true
      const selectedDay = Number(route.query.day)
      const selRecs = recordMap.value.get(selectedDay)
      if (selRecs?.length) {
        detailRecords.value = selRecs
        detailIndex.value = 0
        showDetail.value = true
      }
    }
  }
  catch (e: any) {
    error.value = e.message || '加载失败'
  }
  finally {
    loading.value = false
  }
}

onShow(() => {
  loadData()
})

// ---------- 月份切换（回看历史 / 预看未来不得超出当前月） ----------
const canGoNext = computed(() => {
  const max = new Date()
  return !(year.value > max.getFullYear() || (year.value === max.getFullYear() && month.value >= max.getMonth() + 1))
})
function reloadMonth() {
  loadData()
}
function prevMonth() {
  if (month.value === 1) {
    month.value = 12
    year.value--
  }
  else {
    month.value--
  }
  reloadMonth()
}
function nextMonth() {
  if (!canGoNext.value) return
  if (month.value === 12) {
    month.value = 1
    year.value++
  }
  else {
    month.value++
  }
  reloadMonth()
}
function retryStats() {
  fetchStats()
    .then(s => { stats.value = { totalDays: s.totalDays, currentStreak: s.currentStreak }; statsError.value = false })
    .catch(() => { statsError.value = true })
}
bus.on(MRC_EVENTS.RECORDS_CHANGED, reloadMonth)
onUnmounted(() => bus.off(MRC_EVENTS.RECORDS_CHANGED, reloadMonth))

function goAlbum() {
  router.push({ name: 'album' })
}
function handleCellClick(c: { day: number, hasRecord: boolean, count?: number }) {
  if (c.hasRecord) {
    const recs = recordMap.value.get(c.day)
    if (recs?.length) {
      detailRecords.value = recs
      detailIndex.value = 0
      showDetail.value = true
    }
  }
  else {
    emptyDay.value = c.day
    showEmpty.value = true
  }
}
function prevDetail() {
  if (detailRecords.value.length > 1)
    detailIndex.value = (detailIndex.value - 1 + detailRecords.value.length) % detailRecords.value.length
}
function nextDetail() {
  if (detailRecords.value.length > 1)
    detailIndex.value = (detailIndex.value + 1) % detailRecords.value.length
}
function onDetailSwipe(e: any) {
  if (typeof e?.detail?.current === 'number')
    detailIndex.value = e.detail.current
}
function goRecordFromEmpty() {
  showEmpty.value = false
  // 把补记落到所点的历史日期：record 页 onShow 读取该键作为默认记录日期
  uni.setStorageSync(RECORD_DATE_KEY, `${year.value}-${String(month.value).padStart(2, '0')}-${String(emptyDay.value).padStart(2, '0')}`)
  router.pushTab({ name: 'record' })
}

// ---------- 图片预览 ----------
/** 当月带照片的记录，用于左右滑动翻看整月食光 */
const photoRecords = computed(() => records.value.filter(item => Boolean(item.imageUrl)))

function previewRecordPhoto(record: RecordItem | null) {
  if (!record)
    return
  const photos = photoRecords.value
  if (!photos.length)
    return
  previewImage({
    images: photos.map(item => resolveAssetUrl(item.imageUrl)),
    startPosition: Math.max(0, photos.findIndex(item => item.id === record.id)),
    closeOnClick: true,
    loop: photos.length > 1,
  })
}
</script>

<template>
  <view class="cal-page">
    <wd-navbar title="食光日历" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <!-- Loading -->
    <LoadingState v-if="loading" text="锅仔正在翻日历..." />

    <!-- Error -->
    <ErrorState v-else-if="error" @retry="loadData" />

    <!-- 内容 -->
    <template v-else>
      <view class="cal-intro">
        <view class="cal-intro__topline">
          <text>GUOZAI FOOD DIARY</text><text>{{ year }} / {{ monthNumber }}</text>
        </view>
        <view class="cal-intro__content">
          <view class="cal-intro__copy">
            <text class="cal-intro__eyebrow">
              锅仔替你收好的本月食光
            </text>
            <text class="cal-intro__title">
              这个月，认真吃过
            </text>
            <view class="cal-intro__stats">
              <view>
                <text>本月记录</text><text>
                  {{ recordMap.size }}<text class="cal-intro__unit">
                    天
                  </text>
                </text>
              </view>
              <view role="button" aria-label="连续打卡天数，点击重试" @click="retryStats">
                <text>连续打卡</text><text>
                  {{ statsError ? '—' : stats.currentStreak }}<text class="cal-intro__unit">
                    天
                  </text>
                </text>
              </view>
            </view>
          </view>
          <image :src="`${STATIC_BASE_URL}/static/guozai/action_04_calendar.png`" mode="aspectFit" aria-label="抱着日历的锅仔" />
        </view>
      </view>

      <view class="cal-board">
        <view class="cal-board__heading">
          <view class="cal-board__nav">
            <text class="cal-board__nav-btn" role="button" aria-label="上个月" @click="prevMonth">‹</text>
            <view class="cal-board__nav-title">
              <text class="cal-board__kicker">
                MONTHLY TABLE
              </text><text class="cal-board__title">
                {{ month }}月食光簿
              </text>
            </view>
            <text class="cal-board__nav-btn" :class="{ 'cal-board__nav-btn--disabled': !canGoNext }" role="button" aria-label="下个月" @click="nextMonth">›</text>
          </view>
          <view class="cal-board__legend">
            <view /><text>有记录</text>
          </view>
        </view>
        <view class="cal-week">
          <text v-for="w in weekCN" :key="w" class="cal-week__item">
            {{ w }}
          </text>
        </view>
        <view class="cal-grid">
          <view
            v-for="(c, i) in calCells"
            :key="i"
            class="cal-cell"
            :class="{
              'cal-cell--today': c && c.isToday,
              'cal-cell--record': c && c.hasRecord,
            }"
            :role="c ? 'button' : undefined"
            :aria-label="c ? `${month}月${c.day}日，${c.hasRecord ? '查看饮食记录' : '还没有记录，点击去记录'}` : undefined"
            @click="c && handleCellClick(c)"
          >
            <template v-if="c">
              <template v-if="c.hasRecord">
                <image v-if="c.dishImg" class="cal-cell__img" :src="c.dishImg" mode="aspectFill" />
                <view v-else class="cal-cell__mood">
                  {{ c.mood?.charAt(0) }}
                </view>
                <text class="cal-cell__record-day">
                  {{ c.day }}
                </text>
                <text v-if="c.count > 1" class="cal-cell__count">
                  {{ c.count }}
                </text>
              </template>
              <template v-else>
                <text class="cal-cell__day">
                  {{ c.day }}
                </text>
              </template>
              <text v-if="c.isToday" class="cal-cell__today-tag">
                今
              </text>
            </template>
          </view>
        </view>
        <view class="cal-board__note">
          <image class="guozai-breathe" :src="`${STATIC_BASE_URL}/static/guozai/action_08_peek.png`" mode="aspectFit" aria-label="提醒查看食光的锅仔" />
          <view><text>锅仔的小提示</text><text>有照片的日子可以点开，回看那天吃了什么。</text></view>
        </view>
      </view>

      <view class="cal-album" role="button" aria-label="生成本月食光画册" @click="goAlbum">
        <image class="cal-album__guozai" :src="`${STATIC_BASE_URL}/static/guozai/action_05_album.png`" mode="aspectFit" aria-label="翻看画册的锅仔" />
        <view class="cal-album__copy">
          <text class="cal-album__eyebrow">
            MONTHLY ALBUM
          </text>
          <text class="cal-album__title">
            把{{ month }}月装订成册
          </text>
          <text class="cal-album__sub">
            锅仔会把本月的菜与心情排成一本画册
          </text>
        </view>
        <text class="cal-album__arrow">
          ›
        </text>
      </view>
    </template>

    <!-- 空状态弹窗 -->
    <view v-if="showEmpty" class="cal-empty-mask" @click="showEmpty = false">
      <view class="cal-empty-sheet pop-in" @click.stop>
        <image class="cal-empty-sheet__guozai guozai-breathe" :src="`${STATIC_BASE_URL}/static/guozai/state_01_empty.png`" mode="aspectFit" />
        <text class="cal-empty-sheet__title">
          {{ month }}月{{ emptyDay }}日还没记录哦
        </text>
        <text class="cal-empty-sheet__sub">
          去吃点好吃的吧～
        </text>
        <view class="cal-empty-sheet__btn" @click="goRecordFromEmpty">
          去记录
        </view>
      </view>
    </view>

    <!-- 记录详情弹窗 -->
    <view v-if="showDetail && detailRecord" class="cal-empty-mask" @click="showDetail = false">
      <view class="cal-empty-sheet pop-in" @click.stop>
        <swiper
          v-if="detailRecords.length"
          class="cal-detail__swiper"
          :current="detailIndex"
          :duration="280"
          @change="onDetailSwipe"
        >
          <swiper-item v-for="(rec, idx) in detailRecords" :key="rec.id ?? idx">
            <view class="cal-detail__img">
              <GuozaiImage
                :src="resolveAssetUrl(rec.imageUrl)"
                placeholder-text="📷"
                aria-label="查看这张照片的大图"
                @click="previewRecordPhoto(rec)"
              />
            </view>
          </swiper-item>
        </swiper>
        <text class="cal-empty-sheet__title">
          {{ detailRecord.dishName }}
        </text>
        <view v-if="detailRecords.length > 1" class="cal-detail__pager">
          <text class="cal-detail__pager-btn" @click="prevDetail">‹</text>
          <text class="cal-detail__pager-ind">{{ detailIndex + 1 }} / {{ detailRecords.length }}</text>
          <text class="cal-detail__pager-btn" @click="nextDetail">›</text>
        </view>
        <text class="cal-detail__mood">
          心情：{{ detailRecord.moodTag }}
        </text>
        <text v-if="detailRecord.note" class="cal-detail__note">
          {{ detailRecord.note }}
        </text>
        <view class="cal-empty-sheet__btn" @click="showDetail = false">
          知道了
        </view>
      </view>
    </view>

    <wd-image-preview />
  </view>
</template>

<style lang="scss" scoped>
.cal-page {
  min-height: 100vh;
  padding: 0 32rpx;
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
  background: var(--mrc-bg);
  box-sizing: border-box;
}

.cal-intro {
  position: relative;
  overflow: hidden;
  margin-bottom: 24rpx;
  padding: 24rpx 26rpx 20rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 36rpx 36rpx 24rpx 24rpx;
  background: linear-gradient(145deg, var(--mrc-surface-sun), var(--mrc-surface-peach));
  box-shadow: var(--mrc-shadow-soft);
}
.cal-intro::after {
  position: absolute;
  right: -86rpx;
  bottom: -100rpx;
  width: 290rpx;
  height: 290rpx;
  border: 2rpx dashed var(--mrc-border);
  border-radius: 50%;
  content: '';
}
.cal-intro__topline,
.cal-intro__stats,
.cal-board__heading,
.cal-board__legend,
.cal-board__note,
.cal-album {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.cal-intro__topline {
  position: relative;
  z-index: 1;
  color: var(--mrc-accent);
  font-size: 18rpx;
  font-weight: 850;
  letter-spacing: 2rpx;
}
.cal-intro__content {
  position: relative;
  z-index: 1;
  display: block;
  min-height: 230rpx;
  padding-top: 24rpx;
}
.cal-intro__copy { position: relative; z-index: 2; width: 68%; }
.cal-intro__eyebrow {
  display: block;
  color: var(--mrc-accent);
  font-size: 21rpx;
  font-weight: 800;
}
.cal-intro__title {
  display: block;
  margin-top: 8rpx;
  color: var(--mrc-text-strong);
  font-size: 38rpx;
  font-weight: 850;
  line-height: 1.2;
}
.cal-intro__stats {
  justify-content: flex-start;
  gap: 14rpx;
  margin-top: 26rpx;
}
.cal-intro__stats > view {
  min-width: 132rpx;
  padding: 12rpx 16rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 18rpx;
  background: var(--mrc-surface);
}
.cal-intro__stats > view > text:first-child {
  display: block;
  color: var(--mrc-text-sub);
  font-size: 18rpx;
}
.cal-intro__stats > view > text:last-child {
  display: block;
  margin-top: 2rpx;
  color: var(--mrc-text-strong);
  font-size: 30rpx;
  font-weight: 850;
}
.cal-intro__unit { display: inline !important; font-size: 18rpx !important; font-weight: 650 !important; }
.cal-intro__content > image {
  position: absolute;
  right: -12rpx;
  bottom: -24rpx;
  width: 250rpx;
  height: 250rpx;
}

.cal-board {
  overflow: hidden;
  margin-bottom: 24rpx;
  padding: 26rpx 20rpx 20rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 32rpx;
  background: var(--mrc-surface);
  box-shadow: var(--mrc-shadow-soft);
}
.cal-board__heading { padding: 0 6rpx 18rpx; border-bottom: 2rpx solid var(--mrc-border-light); }
.cal-board__kicker {
  display: block;
  color: var(--mrc-accent);
  font-size: 18rpx;
  font-weight: 850;
  letter-spacing: 2rpx;
}
.cal-board__title {
  display: block;
  margin-top: 4rpx;
  color: var(--mrc-text-strong);
  font-size: 30rpx;
  font-weight: 850;
}
.cal-board__nav {
  display: flex;
  align-items: center;
  gap: 18rpx;
}
.cal-board__nav-title {
  display: flex;
  flex-direction: column;
}
.cal-board__nav-btn {
  width: 56rpx;
  height: 56rpx;
  line-height: 50rpx;
  text-align: center;
  font-size: 40rpx;
  font-weight: 700;
  color: var(--mrc-primary);
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 50%;
  box-shadow: var(--mrc-shadow-soft);
}
.cal-board__nav-btn--disabled {
  color: var(--mrc-text-disabled, #c9c4bd);
  background: var(--mrc-surface-muted, #f1ece5);
  box-shadow: none;
  pointer-events: none;
}
.cal-board__nav-btn:active {
  transform: scale(0.94);
}
.cal-board__legend { gap: 8rpx; color: var(--mrc-text-sub); font-size: 19rpx; }
.cal-board__legend view { width: 14rpx; height: 14rpx; border-radius: 50%; background: var(--mrc-primary); }
.cal-week {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  margin: 18rpx 0 10rpx;
}
.cal-week__item {
  padding: 8rpx 0;
  color: var(--mrc-text-sub);
  font-size: 22rpx;
  font-weight: 750;
  text-align: center;
}
.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8rpx;
}
.cal-cell {
  position: relative;
  display: flex;
  aspect-ratio: 1;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 2rpx solid transparent;
  border-radius: 16rpx;
  background: var(--mrc-surface-sun);
  transition: transform 160ms ease-out, opacity 160ms ease-out;
}
.cal-cell:active { transform: scale(.94); opacity: .82; }
.cal-cell--today { border-color: var(--mrc-primary); background: var(--mrc-surface-peach); }
.cal-cell--record { border-color: var(--mrc-border); background: var(--mrc-surface-peach); }
.cal-cell__img { width: 100%; height: 100%; }
.cal-cell__day { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 750; }
.cal-cell__record-day {
  position: absolute;
  bottom: 4rpx;
  left: 5rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 28rpx;
  height: 28rpx;
  padding: 0 4rpx;
  border-radius: 9rpx;
  background: rgba(0, 0, 0, .62);
  color: #fff;
  font-size: 16rpx;
  font-weight: 800;
}
.cal-cell__today-tag {
  position: absolute;
  top: 4rpx;
  right: 4rpx;
  padding: 2rpx 5rpx;
  border-radius: 8rpx;
  background: var(--mrc-primary);
  color: #fff;
  font-size: 14rpx;
  font-weight: 800;
}
.cal-cell__mood { color: var(--mrc-accent); font-size: 22rpx; font-weight: 800; }
.cal-board__note { gap: 14rpx; justify-content: flex-start; min-height: 112rpx; margin-top: 18rpx; padding: 10rpx 16rpx 4rpx; border-top: 2rpx dashed var(--mrc-border); }
.cal-board__note image { width: 96rpx; height: 96rpx; flex: 0 0 auto; }
.cal-board__note text:first-child { display: block; color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; }
.cal-board__note text:last-child { display: block; margin-top: 4rpx; color: var(--mrc-text-sub); font-size: 21rpx; line-height: 1.45; }

.cal-album {
  position: relative;
  min-height: 154rpx;
  justify-content: flex-start;
  gap: 14rpx;
  overflow: hidden;
  padding: 18rpx 24rpx 18rpx 18rpx;
  border-radius: 30rpx;
  background: var(--mrc-primary-grad);
  box-shadow: var(--mrc-shadow-coral);
  transition: transform 160ms ease-out, opacity 160ms ease-out;
  box-sizing: border-box;
}
.cal-album::after { position: absolute; right: -40rpx; width: 170rpx; height: 170rpx; border: 2rpx dashed rgba(255, 255, 255, .42); border-radius: 50%; content: ''; }
.cal-album__guozai { position: relative; z-index: 1; width: 118rpx; height: 118rpx; flex: 0 0 auto; }
.cal-album__copy { position: relative; z-index: 1; min-width: 0; flex: 1; }
.cal-album__eyebrow { display: block; color: rgba(255, 255, 255, .78); font-size: 17rpx; font-weight: 850; letter-spacing: 2rpx; }
.cal-album__title { display: block; margin-top: 5rpx; color: #fff; font-size: 30rpx; font-weight: 850; }
.cal-album__sub { display: block; margin-top: 5rpx; color: rgba(255, 255, 255, .82); font-size: 19rpx; line-height: 1.4; }
.cal-album__arrow { position: relative; z-index: 1; flex: 0 0 auto; color: #fff; font-size: 48rpx; }
.cal-album:active { transform: scale(.985); opacity: .84; }

/* 空状态弹窗 */
.cal-empty-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(90, 62, 43, 0.4);
  display: flex;
  align-items: flex-end;
  z-index: 100;
}
.cal-empty-sheet {
  width: 100%;
  background: var(--mrc-surface);
  border-radius: 40rpx 40rpx 0 0;
  padding: 48rpx 40rpx calc(48rpx + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  align-items: center;
}
.cal-empty-sheet__guozai {
  width: 280rpx;
  height: 280rpx;
  margin-bottom: 8rpx;
}
.cal-empty-sheet__title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
  margin-bottom: 8rpx;
}
.cal-empty-sheet__sub {
  font-size: 26rpx;
  color: var(--mrc-text-sub);
  margin-bottom: 32rpx;
}
.cal-empty-sheet__btn {
  width: 100%;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--mrc-primary), var(--mrc-primary-deep));
  color: var(--mrc-white);
  font-size: 30rpx;
  font-weight: 600;
  border-radius: 44rpx;
  box-shadow: 0 8rpx 20rpx rgba(253, 145, 132, 0.35);
}
.cal-empty-sheet__btn:active {
  transform: scale(0.97);
}

/* 详情弹窗 */
.cal-detail__swiper {
  width: 100%;
  height: 300rpx;
  border-radius: 20rpx;
  margin-bottom: 20rpx;
  overflow: hidden;
}
.cal-detail__swiper .cal-detail__img {
  margin-bottom: 0;
}
.cal-detail__img--placeholder {
  width: 100%;
  height: 300rpx;
  border-radius: 20rpx;
  background: var(--mrc-surface, #F4ECE2);
}
.cal-detail__img {
  width: 100%;
  height: 300rpx;
  border-radius: 20rpx;
  margin-bottom: 20rpx;
}
.cal-detail__mood {
  font-size: 26rpx;
  color: var(--mrc-accent);
  margin-bottom: 12rpx;
  font-weight: 600;
}
.cal-detail__note {
  font-size: 28rpx;
  color: var(--mrc-text);
  margin-bottom: 24rpx;
  line-height: 1.6;
}
.cal-cell__count {
  position: absolute;
  bottom: 4rpx;
  right: 5rpx;
  min-width: 26rpx;
  height: 26rpx;
  padding: 0 5rpx;
  border-radius: 9rpx;
  background: var(--mrc-primary);
  color: #fff;
  font-size: 15rpx;
  font-weight: 800;
  text-align: center;
  line-height: 26rpx;
}
.cal-detail__pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20rpx;
  margin-top: 2rpx;
  margin-bottom: 14rpx;
}
.cal-detail__pager-btn {
  width: 58rpx;
  height: 58rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--mrc-surface-sun);
  color: var(--mrc-accent);
  font-size: 42rpx;
  font-weight: 800;
}
.cal-detail__pager-ind {
  color: var(--mrc-text-sub);
  font-size: 24rpx;
  font-weight: 700;
}
@media (prefers-reduced-motion: reduce) {
  .cal-cell,
  .cal-album {
    transition: none;
  }
}
</style>
