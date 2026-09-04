<script setup lang="ts">
import { navBack } from '@/composables/useNavBar'
import { computed, ref } from 'vue'
import Icon from '../../components/common/Icon.vue'
import LoadingState from '../../components/guozai/LoadingState.vue'
import ErrorState from '../../components/guozai/ErrorState.vue'
import { ensureLogin } from '../../utils/login'
import { fetchRecordsByMonth, fetchStats, type RecordItem } from '../../api/records'

definePage({
  name: 'calendar',
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '心情日历',
  },
})

const router = useRouter()

const weekCN = ['日', '一', '二', '三', '四', '五', '六']
const now = new Date()
const year = now.getFullYear()
const month = now.getMonth() + 1
const today = now.getDate()
const monthStr = `${year}-${String(month).padStart(2, '0')}`

const loading = ref(true)
const error = ref('')
const records = ref<RecordItem[]>([])
const stats = ref({ totalDays: 0, currentStreak: 0 })

// 记录日期映射
const recordMap = computed(() => {
  const map = new Map<number, RecordItem>()
  records.value.forEach(r => {
    const day = parseInt(r.recordDate?.split('-')[2] || '0', 10)
    if (day > 0) map.set(day, r)
  })
  return map
})

const firstDay = new Date(year, month - 1, 1).getDay()
const daysInMonth = new Date(year, month, 0).getDate()

const calCells = computed(() => {
  const cells: ({ day: number; hasRecord: boolean; isToday: boolean; dishImg: string; mood: string } | null)[] = []
  for (let i = 0; i < firstDay; i++) cells.push(null)
  for (let d = 1; d <= daysInMonth; d++) {
    const rec = recordMap.value.get(d)
    cells.push({
      day: d,
      hasRecord: !!rec,
      isToday: d === today,
      dishImg: rec?.imageUrl || '',
      mood: rec?.moodTag || '',
    })
  }
  while (cells.length % 7 !== 0) cells.push(null)
  return cells
})

// 空状态弹窗
const showEmpty = ref(false)
const emptyDay = ref(0)
// 记录详情弹窗
const showDetail = ref(false)
const detailRecord = ref<RecordItem | null>(null)

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const openid = await ensureLogin()
    const [monthRecords, statsData] = await Promise.all([
      fetchRecordsByMonth(openid, monthStr),
      fetchStats(openid),
    ])
    records.value = monthRecords
    stats.value = { totalDays: statsData.totalDays, currentStreak: statsData.currentStreak }
  } catch (e: any) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onShow(() => {
  loadData()
})

function goAlbum() {
  router.push({ name: 'album' })
}
function handleCellClick(c: { day: number; hasRecord: boolean }) {
  if (c.hasRecord) {
    const rec = recordMap.value.get(c.day)
    if (rec) {
      detailRecord.value = rec
      showDetail.value = true
    }
  } else {
    emptyDay.value = c.day
    showEmpty.value = true
  }
}
function goRecordFromEmpty() {
  showEmpty.value = false
  router.push({ name: 'record' })
}
</script>

<template>
  <view class="cal-page">
    <wd-navbar :title="`${year}年${month}月`" left-arrow safe-area-inset-top @click-left="navBack" />

    <!-- Loading -->
    <LoadingState v-if="loading" text="锅仔正在翻日历..." />

    <!-- Error -->
    <ErrorState v-else-if="error" :text="error" @retry="loadData" />

    <!-- 内容 -->
    <template v-else>
      <!-- 统计条 -->
      <view class="cal-stat">
        <view><text class="cal-stat__eyebrow">本月食光</text><text class="cal-stat__text">已记录 {{ stats.totalDays }} 天 · 连续 {{ stats.currentStreak }} 天</text></view>
        <view class="cal-stat__icon"><Icon name="flame" :size="38" color="var(--mrc-accent)" /></view>
      </view>

      <!-- 日历 -->
      <view class="cal-board">
        <view class="cal-week">
          <text v-for="w in weekCN" :key="w" class="cal-week__item">{{ w }}</text>
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
            @click="c && handleCellClick(c)"
          >
            <template v-if="c">
              <image v-if="c.hasRecord && c.dishImg" class="cal-cell__img" :src="c.dishImg" mode="aspectFill" />
              <view v-else-if="c.hasRecord" class="cal-cell__mood">{{ c.mood?.charAt(0) }}</view>
              <template v-else>
                <text class="cal-cell__day">{{ c.day }}</text>
                <view class="cal-cell__dot" />
              </template>
            </template>
          </view>
        </view>
        <image class="cal-board__guozai guozai-breathe" src="/static/guozai/action_04_calendar.png" mode="aspectFit" />
      </view>

      <!-- 生成月度画册按钮 -->
      <view class="cal-album" @click="goAlbum">
        <image class="cal-album__guozai" src="/static/guozai/action_05_album.png" mode="aspectFit" />
        <text class="cal-album__text">生成月度画册</text>
      </view>
    </template>

    <!-- 空状态弹窗 -->
    <view v-if="showEmpty" class="cal-empty-mask" @click="showEmpty = false">
      <view class="cal-empty-sheet pop-in" @click.stop>
        <image class="cal-empty-sheet__guozai guozai-breathe" src="/static/guozai/state_01_empty.png" mode="aspectFit" />
        <text class="cal-empty-sheet__title">{{ month }}月{{ emptyDay }}日还没记录哦</text>
        <text class="cal-empty-sheet__sub">去吃点好吃的吧～</text>
        <view class="cal-empty-sheet__btn" @click="goRecordFromEmpty">去记录</view>
      </view>
    </view>

    <!-- 记录详情弹窗 -->
    <view v-if="showDetail && detailRecord" class="cal-empty-mask" @click="showDetail = false">
      <view class="cal-empty-sheet pop-in" @click.stop>
        <image v-if="detailRecord.imageUrl" class="cal-detail__img" :src="detailRecord.imageUrl" mode="aspectFill" />
        <text class="cal-empty-sheet__title">{{ detailRecord.dishName }}</text>
        <text class="cal-detail__mood">心情：{{ detailRecord.moodTag }}</text>
        <text v-if="detailRecord.note" class="cal-detail__note">{{ detailRecord.note }}</text>
        <view class="cal-empty-sheet__btn" @click="showDetail = false">知道了</view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.cal-page {
  min-height: 100vh;
  background: var(--mrc-bg);
  padding: 0 32rpx;
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* 统计条 */
.cal-stat {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 28rpx;
  padding: 24rpx 28rpx;
  box-shadow: var(--mrc-shadow-soft);
  margin-bottom: 28rpx;
}
.cal-stat__text {
  display: block;
  font-size: 30rpx;
  color: var(--mrc-text-deep);
  font-weight: 600;
}
.cal-stat__eyebrow {
  display: block;
  margin-bottom: 4rpx;
  font-size: 21rpx;
  color: var(--mrc-accent);
  font-weight: 700;
  letter-spacing: 2rpx;
}
.cal-stat__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 68rpx;
  height: 68rpx;
  border-radius: 22rpx;
  background: var(--mrc-surface-peach);
}

/* 日历板 */
.cal-board {
  position: relative;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 32rpx;
  padding: 24rpx 20rpx 152rpx;
  box-shadow: var(--mrc-shadow);
  margin-bottom: 32rpx;
  overflow: hidden;
}
.cal-week {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  margin-bottom: 12rpx;
}
.cal-week__item {
  text-align: center;
  font-size: 28rpx;
  color: var(--mrc-text-sub);
  font-weight: 600;
  padding: 12rpx 0;
}
.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8rpx;
}
.cal-cell {
  aspect-ratio: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 16rpx;
  position: relative;
  background: var(--mrc-surface-sun);
}
.cal-cell--today {
  border: 3rpx solid var(--mrc-accent);
  background: var(--mrc-warm-light);
}
.cal-cell--record {
  background: transparent;
  padding: 4rpx;
}
.cal-cell__img {
  width: 100%;
  height: 100%;
  border-radius: 12rpx;
}
.cal-cell__day {
  font-size: 30rpx;
  color: var(--mrc-text-deep);
  font-weight: 600;
}
.cal-cell--today .cal-cell__day {
  color: var(--mrc-accent);
  font-weight: 700;
}
.cal-cell__dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: var(--mrc-text-light);
  margin-top: 4rpx;
}
.cal-board__guozai {
  position: absolute;
  bottom: -14rpx;
  right: 24rpx;
  left: auto;
  transform: none;
  width: 180rpx;
  height: 180rpx;
  z-index: 3;
}

/* 生成画册按钮 */
.cal-album {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 120rpx;
  background: var(--mrc-primary-grad);
  border-radius: 32rpx;
  box-shadow: var(--mrc-shadow-coral);
}
.cal-album__guozai {
  position: absolute;
  top: -50rpx;
  left: 40rpx;
  width: 120rpx;
  height: 120rpx;
  z-index: 2;
}
.cal-album__text {
  font-size: 38rpx;
  color: #fff;
  font-weight: 700;
  letter-spacing: 4rpx;
}
.cal-album:active {
  transform: scale(0.98);
}

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
  background: var(--mrc-white);
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
.cal-cell__mood {
  font-size: 24rpx;
  color: var(--mrc-accent);
  font-weight: 700;
}
</style>
