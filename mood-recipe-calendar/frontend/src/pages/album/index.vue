<script setup lang="ts">
import type { AlbumItem } from '../../api/albums'
import type { RecordItem } from '../../api/records'
import type { LayoutBox } from '../../utils/albumLayout'
import { computed, ref } from 'vue'
import { navBack, useNavBar } from '@/composables/useNavBar'
import { STATIC_BASE_URL } from '@/utils/assets'
import { fetchMonthAlbum } from '../../api/albums'
import { fetchRecordsByMonth } from '../../api/records'
import {
  ALBUM_ENTITLEMENT_CODE,
  ALBUM_PRODUCT_SKU,
  consumeEntitlement,
  createVirtualOrder,
  fetchEntitlements,
  fetchVirtualOrder,
  getVirtualPaymentParams,
  requestWechatVirtualPayment,
} from '../../api/virtualCommerce'
import Icon from '../../components/common/Icon.vue'
import ErrorState from '../../components/guozai/ErrorState.vue'
import LoadingState from '../../components/guozai/LoadingState.vue'
import { useUserStore } from '../../stores/user'
import { autoLayout, chunkPages, MOOD_COLOR, MOOD_EMOJI } from '../../utils/albumLayout'
import { exportAlbumShare } from '../../utils/albumShare'
import { ensureLogin, refreshUserInfo } from '../../utils/login'
import { toast, toastError, toastSuccess } from '../../utils/toast'

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
const userStore = useUserStore()

const now = new Date()
const monthStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
const monthNum = now.getMonth() + 1
const yearNum = now.getFullYear()
interface AlbumStats {
  totalDays: number
  moodDistribution: Record<string, number>
  topDishes: { name: string, count: number }[]
  longestStreak: number
}
const EMPTY_STATS: AlbumStats = { totalDays: 0, moodDistribution: {}, topDishes: [], longestStreak: 0 }

// ---------- 数据解析 ----------
const stats = computed<AlbumStats>(() => {
  if (!album.value?.stats)
    return EMPTY_STATS
  try {
    const parsed = JSON.parse(album.value.stats) as Partial<AlbumStats>
    return {
      totalDays: Number(parsed.totalDays) || 0,
      moodDistribution: parsed.moodDistribution || {},
      topDishes: Array.isArray(parsed.topDishes) ? parsed.topDishes : [],
      longestStreak: Number(parsed.longestStreak) || 0,
    }
  }
  catch { return EMPTY_STATS }
})

const moodList = computed(() => {
  const dist = stats.value.moodDistribution || {}
  return Object.entries(dist)
    .map(([mood, count]) => ({ mood, count }))
    .sort((a, b) => b.count - a.count)
})

const topDishes = computed(() => (stats.value.topDishes || []).slice(0, 3))
const ownerName = computed(() => userStore.userInfo?.nickname?.trim() || '我的')
const recordCount = computed(() => records.value.length)
const monthlyInsight = computed(() => {
  if (!recordCount.value)
    return '第一顿记录，会成为锅仔认识你的开始。'
  const mood = moodList.value[0]?.mood
  const dish = topDishes.value[0]?.name
  if (mood && dish)
    return `你常在「${mood}」时留下饭香，也最常做「${dish}」。`
  return `你留下了 ${recordCount.value} 顿真实的饭，锅仔都收好了。`
})
const personalFacts = computed(() => [
  moodList.value[0] ? `最常记录 · ${moodList.value[0].mood} ${moodList.value[0].count}次` : '',
  topDishes.value[0] ? `熟悉味道 · ${topDishes.value[0].name}` : '',
].filter(Boolean))

// ---------- 每日记录：智能排版分页 ----------
const recordPages = computed(() => chunkPages(records.value))

// 每日记录内容区（rpx）：x/y/w/h
const DAILY_AREA = { x: 0, y: 0, w: 678, h: 690 }
const dailyBoxes = computed<LayoutBox[][]>(() =>
  recordPages.value.map(pg => autoLayout(pg.length, DAILY_AREA, 22)),
)

// ---------- 画册页数 ----------
const totalPages = computed(() => 3 + recordPages.value.length) // 封面/盘点 + 记录页 + 寄语/分享

async function loadAlbum() {
  loading.value = true
  error.value = ''
  try {
    const openid = await ensureLogin()
    const [albumData, recordData] = await Promise.all([
      fetchMonthAlbum(openid, monthStr),
      fetchRecordsByMonth(openid, monthStr),
      refreshUserInfo(true),
    ])
    album.value = albumData
    records.value = recordData
    await refreshEntitlements(openid)
  }
  catch (e: any) {
    error.value = e.message || '加载失败'
  }
  finally {
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
  if (currentPage.value < totalPages.value - 1)
    currentPage.value++
}
function prev() {
  if (currentPage.value > 0)
    currentPage.value--
}

// ---------- 分享导出（需虚拟支付权益） ----------
const sharing = ref(false)
const purchasing = ref(false)
// 状态栏 + 自定义导航栏高度，用于把浮动分享按钮定位到导航栏下方，避免被微信胶囊遮挡。
const nav = useNavBar()
const shareTop = computed(() => `${nav.statusBarHeight + nav.navBarHeight + 8}px`)
/** 已到账且可用的画册权益次数；null 表示尚未查询或无权益。 */
const entitlementRemaining = ref<number | null>(null)
const isMember = computed(() => userStore.userInfo?.isMember === 1
  && Boolean(userStore.userInfo?.memberExpire)
  && new Date(userStore.userInfo!.memberExpire!).getTime() > Date.now())

const hasAlbumEntitlement = computed(() => isMember.value || (entitlementRemaining.value !== null && entitlementRemaining.value > 0))

/** 分享按钮文案：有权益则直接保存，无权益则提示先解锁。 */
const shareButtonText = computed(() =>
  hasAlbumEntitlement.value
    ? `保存我的${monthNum}月干饭分享图`
    : '解锁并保存高清分享图')

async function refreshEntitlements(openid: string) {
  const list = await fetchEntitlements(openid)
  const item = list.find(e => e.code === ALBUM_ENTITLEMENT_CODE)
  entitlementRemaining.value = item?.remainingUses ?? null
}

async function onShare() {
  if (sharing.value || purchasing.value)
    return

  let openid = ''
  try {
    openid = await ensureLogin()
  }
  catch (e: any) {
    toastError(e, '请先登录')
    return
  }

  // 没有可用权益时先走购买流程；未成功获得权益则不消耗本次导出。
  if (!hasAlbumEntitlement.value) {
    const purchased = await purchaseAlbum(openid)
    if (!purchased)
      return
  }

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
      guozaiPath: `${STATIC_BASE_URL}/static/guozai/mood_01_happy.png`,
      footer: '「锅仔」· 你的情绪味蕾搭子',
      aiAssisted: true,
    })
    // 高清图已成功保存，才扣减一次权益，避免支付成功却导出失败白白消耗。
    if (!isMember.value && hasAlbumEntitlement.value) {
      try {
        const result = await consumeEntitlement(openid, ALBUM_ENTITLEMENT_CODE)
        entitlementRemaining.value = result.remainingUses
      }
      catch {
        toast('高清图已保存，但权益扣减异常，请稍后刷新')
      }
    }
    toastSuccess('已保存到相册')
  }
  catch (e: any) {
    toastError(e, '导出失败')
  }
  finally {
    uni.hideLoading()
    sharing.value = false
  }
}

/** 画册收藏版购买流程：下单 → 服务端签名 → 微信虚拟支付 → 等待异步发货到账。 */
async function purchaseAlbum(openid: string): Promise<boolean> {
  if (purchasing.value)
    return false
  purchasing.value = true
  let checkingDelivery = false
  try {
    const order = await createVirtualOrder(openid, ALBUM_PRODUCT_SKU)
    const params = await getVirtualPaymentParams(openid, order.orderNo)
    await requestWechatVirtualPayment(params)
    checkingDelivery = true
    uni.showLoading({ title: '锅仔正在确认权益…', mask: true })
    const delivered = await waitForDelivery(order.orderNo)
    if (!delivered) {
      toast('支付已完成，权益确认中，稍后刷新即可保存')
      return false
    }
    await refreshEntitlements(openid)
    return hasAlbumEntitlement.value
  }
  catch (e: any) {
    toastError(e, '暂时无法发起支付')
    return false
  }
  finally {
    if (checkingDelivery)
      uni.hideLoading()
    purchasing.value = false
  }
}

/** 轮询订单发货状态，确认权益到账；最长约 5 秒。 */
function waitForDelivery(orderNo: string) {
  return (async () => {
    for (let attempt = 0; attempt < 4; attempt += 1) {
      await new Promise(resolve => setTimeout(resolve, attempt === 0 ? 900 : 1600))
      if ((await fetchVirtualOrder(orderNo)).status === 'DELIVERED')
        return true
    }
    return false
  })()
}

// 心情分布色块（宽度占比）
function moodSegStyle(mood: string, count: number) {
  const total = moodList.value.reduce((s, m) => s + m.count, 0) || 1
  return { width: `${Math.max((count / total) * 100, 8)}%`, background: MOOD_COLOR[mood] || '#E8836B' }
}

function dailyCellStyle(box: LayoutBox) {
  return { left: `${box.x}rpx`, top: `${box.y}rpx`, width: `${box.w}rpx`, height: `${box.h}rpx` }
}

function dailyTagSize(name: string) {
  return name && name.length > 5 ? '20rpx' : '24rpx'
}

function recordDateLabel(date: string) {
  return date?.slice(5).replace('-', '.') || '本月'
}

const aiLines = computed(() => {
  const t = album.value?.aiSummary
  if (!t)
    return ['这个月，你好好吃饭了。', '下个月，请继续对自己好一点。']
  return t.split('\n').filter(Boolean)
})
</script>

<template>
  <view class="album-page">
    <wd-navbar title="月度画册" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <view class="album-page__share" :style="{ top: shareTop }" role="button" aria-label="保存月度画册分享图" @click="onShare">
      <Icon name="share" :size="36" color="#6A4A37" />
      <view v-if="!hasAlbumEntitlement" class="album-page__share-badge">
        解锁
      </view>
    </view>

    <LoadingState v-if="loading" text="锅仔正在装订画册..." />
    <ErrorState v-else-if="error" @retry="loadAlbum" />

    <template v-else>
      <swiper
        class="album-swiper"
        :current="currentPage"
        :duration="320"
        @change="onSwiperChange"
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
              <text class="album-cover__brand">
                心情菜谱日历
              </text>
              <text class="album-cover__title">
                {{ ownerName }}的
              </text>
              <text class="album-cover__title">
                {{ monthNum }}月干饭日记
              </text>
              <text class="album-cover__subtitle">
                {{ yearNum }}年{{ monthNum }}月 · 共记录{{ stats.totalDays }}天
              </text>
              <view class="album-cover__guozai-wrap">
                <image class="album-cover__guozai" :src="`${STATIC_BASE_URL}/static/guozai/action_05_album.png`" mode="aspectFit" />
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
              <view>
                <text class="album-kicker">
                  MONTHLY TASTE NOTES
                </text>
                <text class="album-stats__title">
                  {{ monthNum }}月，锅仔读懂了这些
                </text>
              </view>
              <text class="album-folio">
                {{ yearNum }} / {{ String(monthNum).padStart(2, '0') }}
              </text>
            </view>

            <view class="album-stats__overview">
              <view class="album-stats__big">
                <text class="album-stats__number">
                  {{ stats.totalDays || 0 }}
                </text>
                <text class="album-stats__label">
                  天有认真吃饭
                </text>
              </view>
              <view class="album-stats__minis">
                <view><text>{{ recordCount }}</text><text>顿被记住</text></view>
                <view><text>{{ stats.longestStreak || 0 }}</text><text>天最长连续</text></view>
              </view>
              <image class="album-stats__hero-guozai" :src="`${STATIC_BASE_URL}/static/guozai/mood_10_content.png`" mode="aspectFit" />
            </view>

            <view class="album-card">
              <view class="album-card__head">
                <text class="album-card__index">
                  01
                </text><text class="album-card__title">
                  这一月的心情味道
                </text>
              </view>
              <view v-if="moodList.length" class="album-mood-list">
                <view
                  v-for="m in moodList.slice(0, 3)"
                  :key="m.mood"
                  class="album-mood-row"
                >
                  <view class="album-mood-row__label">
                    <text>{{ m.mood }}</text><text>{{ m.count }}次</text>
                  </view>
                  <view class="album-mood-row__track">
                    <view :style="moodSegStyle(m.mood, m.count)" />
                  </view>
                </view>
              </view>
              <view v-else class="album-card__empty">
                <text>这个月还没有心情记录</text>
              </view>
            </view>

            <view class="album-card">
              <view class="album-card__head">
                <text class="album-card__index">
                  02
                </text><text class="album-card__title">
                  反复想念的味道
                </text>
              </view>
              <view v-if="topDishes.length" class="album-dishes">
                <view v-for="(dish, i) in topDishes" :key="dish.name" class="album-dish">
                  <text class="album-dish__medal">
                    0{{ i + 1 }}
                  </text>
                  <text class="album-dish__name">
                    {{ dish.name }}
                  </text>
                  <text class="album-dish__count">
                    {{ dish.count }}次
                  </text>
                </view>
              </view>
              <view v-else class="album-card__empty">
                <text>这个月还没有做菜记录</text>
              </view>
            </view>

            <view class="album-stats__note">
              <view class="album-stats__note-mark" />
              <text>{{ monthlyInsight }}</text>
            </view>
          </view>
        </swiper-item>

        <!-- ======== 每日记录（智能排版，可多页） ======== -->
        <swiper-item v-for="(pg, pi) in recordPages" :key="`daily-${pi}`">
          <view class="album-daily">
            <view class="album-daily__header">
              <view>
                <text class="album-kicker">
                  MY FOOD MOMENTS
                </text><text class="album-daily__title">
                  每日记录
                </text>
              </view>
              <text class="album-folio">
                {{ String(pi + 1).padStart(2, '0') }} / {{ String(recordPages.length).padStart(2, '0') }}
              </text>
            </view>
            <view class="album-daily__paper">
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
                    <text class="album-daily__cell-emoji">
                      {{ MOOD_EMOJI[rec.moodTag] || '🍽️' }}
                    </text>
                  </view>
                  <view class="album-daily__cell-tag">
                    <text class="album-daily__cell-tag-meta">
                      {{ recordDateLabel(rec.recordDate) }} · {{ rec.moodTag || '一顿饭' }}
                    </text>
                    <text class="album-daily__cell-tag-txt" :style="{ fontSize: dailyTagSize(rec.dishName) }">
                      {{ rec.dishName }}
                    </text>
                  </view>
                </view>
              </view>
            </view>
            <view class="album-daily__caption">
              <image class="album-daily__guozai" :src="`${STATIC_BASE_URL}/static/guozai/mood_01_happy.png`" mode="aspectFit" />
              <view><text>锅仔的食光批注</text><text>不是打卡，是把认真生活的证据留了下来。</text></view>
            </view>
          </view>
        </swiper-item>
        <swiper-item v-if="!recordPages.length">
          <view class="album-daily album-daily--empty">
            <text class="album-daily__title">
              每日记录
            </text>
            <view class="album-daily__empty-tip">
              <image class="album-daily__empty-guozai" :src="`${STATIC_BASE_URL}/static/guozai/action_07_empty.png`" mode="aspectFit" />
              <text class="album-daily__empty-text">
                这个月还没有记录\n去吃点好吃的吧！
              </text>
            </view>
          </view>
        </swiper-item>

        <!-- ======== AI 寄语 ======== -->
        <swiper-item>
          <view class="album-message">
            <view class="album-message__header">
              <view>
                <text class="album-kicker">
                  A LETTER FROM GUOZAI
                </text><text class="album-message__title">
                  写给{{ ownerName }}的{{ monthNum }}月回信
                </text>
              </view>
              <text class="album-folio">
                AI 生成寄语
              </text>
            </view>
            <view class="album-message__letter">
              <view class="album-message__stamp">
                锅仔<br>食光邮局
              </view>
              <view class="album-message__facts">
                <text v-for="fact in personalFacts" :key="fact">
                  {{ fact }}
                </text>
              </view>
              <view class="album-message__text">
                <text v-for="(line, i) in aiLines" :key="i">
                  {{ line }}
                </text>
              </view>
              <view class="album-message__sign">
                <view><text>一直记得你每顿饭的</text><text>锅仔</text></view>
                <image class="album-message__guozai" :src="`${STATIC_BASE_URL}/static/guozai/action_06_glasses.png`" mode="aspectFit" />
              </view>
            </view>
            <text class="album-message__privacy">
              只分析菜名、心情与记录频率，不读取你的日记正文
            </text>
          </view>
        </swiper-item>

        <!-- ======== 分享页 ======== -->
        <swiper-item>
          <view class="album-share">
            <view class="album-share__header">
              <text class="album-share__brand">
                心情菜谱日历
              </text>
              <image class="album-share__guozai-sm" :src="`${STATIC_BASE_URL}/static/guozai/mood_01_happy.png`" mode="aspectFit" />
            </view>
            <view class="album-share__card">
              <text class="album-share__label">
                记录了
              </text>
              <view class="album-share__big">
                <text class="album-share__num">
                  {{ stats.totalDays || 0 }}
                </text>
                <text class="album-share__unit">
                  天
                </text>
              </view>
              <text class="album-share__info">
                最常做：{{ topDishes[0]?.name || '—' }}
              </text>
              <text class="album-share__info">
                心情：{{ moodList[0] ? `${moodList[0].mood}最多` : '—' }}
              </text>
            </view>
            <view class="album-share__guozai-wrap">
              <view class="album-share__bubble">
                <text>用一道菜，治愈今天的你。</text>
              </view>
              <image class="album-share__guozai" :src="`${STATIC_BASE_URL}/static/guozai/action_09_celebrate.png`" mode="aspectFit" />
            </view>
            <view class="album-share__btn" @click="onShare">
              <text class="album-share__btn-text">
                {{ shareButtonText }}
              </text>
              <view class="album-share__qrcode">
                <view class="album-share__qr-grid" />
              </view>
            </view>
            <view v-if="hasAlbumEntitlement" class="album-share__hint">
              本次保存将消耗 1 次收藏版权益（剩余 {{ entitlementRemaining }} 次）
            </view>
            <view v-else class="album-share__hint">
              收藏版为虚拟付费内容，解锁后可导出高清无水印分享图
            </view>
          </view>
        </swiper-item>
      </swiper>

      <!-- 翻页控制 -->
      <view class="album-nav">
        <view v-if="currentPage > 0" class="album-nav__btn" @click="prev">
          上一页
        </view>
        <view class="album-nav__dots">
          <view
            v-for="i in totalPages"
            :key="i"
            class="album-nav__dot"
            :class="{ 'album-nav__dot--active': currentPage === i - 1 }"
          />
        </view>
        <view v-if="currentPage < totalPages - 1" class="album-nav__btn album-nav__btn--primary" @click="next">
          下一页
        </view>
        <view v-else class="album-nav__btn album-nav__btn--primary" @click="onShare">
          保存分享图
        </view>
      </view>

      <!-- 隐藏 Canvas 节点（导出分享长图用） -->
      <canvas id="shareCanvas" type="2d" class="album-share__canvas" />
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
  padding: 26rpx 32rpx 110rpx;
  box-sizing: border-box;
  overflow-y: auto;
}
.album-kicker { display: block; color: var(--mrc-accent); font-size: 18rpx; font-weight: 800; letter-spacing: 3rpx; }
.album-folio { color: var(--mrc-text-sub); font-size: 19rpx; font-weight: 700; letter-spacing: 1rpx; }
.album-stats__header, .album-daily__header, .album-message__header { display: flex; align-items: flex-end; justify-content: space-between; gap: 20rpx; margin-bottom: 22rpx; padding-right: 82rpx; }
.album-stats__title, .album-daily__title, .album-message__title { display: block; margin-top: 8rpx; color: var(--mrc-text-deep); font-size: 39rpx; font-weight: var(--mrc-fw-heavy); line-height: 1.25; }
.album-stats__overview { position: relative; display: grid; grid-template-columns: 1.15fr 1fr; min-height: 232rpx; margin-bottom: 18rpx; overflow: hidden; border: 2rpx solid var(--mrc-border); border-radius: 34rpx; background: linear-gradient(135deg, var(--mrc-surface-peach), var(--mrc-surface-sun)); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }
.album-stats__overview::after { position: absolute; right: -72rpx; bottom: -86rpx; width: 230rpx; height: 230rpx; border: 2rpx dashed var(--mrc-border-strong); border-radius: 50%; content: ''; opacity: .5; }
.album-stats__big { display: flex; flex-direction: column; justify-content: center; padding-left: 30rpx; }
.album-stats__number { color: var(--mrc-accent); font-size: 100rpx; font-weight: 900; font-variant-numeric: tabular-nums; letter-spacing: -6rpx; line-height: .9; }
.album-stats__label { margin-top: 12rpx; color: var(--mrc-text-deep); font-size: 24rpx; font-weight: 750; }
.album-stats__minis { display: grid; align-content: center; gap: 13rpx; padding-right: 92rpx; }
.album-stats__minis view { display: flex; align-items: baseline; gap: 8rpx; }
.album-stats__minis text:first-child { color: var(--mrc-text-strong); font-size: 34rpx; font-weight: 850; font-variant-numeric: tabular-nums; }
.album-stats__minis text:last-child { color: var(--mrc-text-sub); font-size: 19rpx; }
.album-stats__hero-guozai { position: absolute; z-index: 1; right: -6rpx; bottom: -4rpx; width: 126rpx; height: 126rpx; }
.album-card {
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 28rpx;
  padding: 22rpx 24rpx;
  margin-bottom: 16rpx;
  box-shadow: var(--mrc-shadow-soft);
}
.album-card__head { display: flex; align-items: center; gap: 14rpx; margin-bottom: 16rpx; }
.album-card__index { color: var(--mrc-accent); font-size: 18rpx; font-weight: 850; letter-spacing: 1rpx; }
.album-card__title { color: var(--mrc-text-strong); font-size: 28rpx; font-weight: 800; }
.album-card__empty { padding: 16rpx 0; color: var(--mrc-text-sub); font-size: 24rpx; text-align: center; }
.album-mood-list { display: grid; gap: 13rpx; }
.album-mood-row__label { display: flex; justify-content: space-between; margin-bottom: 7rpx; color: var(--mrc-text-sub); font-size: 20rpx; }
.album-mood-row__label text:first-child { color: var(--mrc-text-deep); font-weight: 700; }
.album-mood-row__track { height: 12rpx; overflow: hidden; border-radius: 10rpx; background: var(--mrc-surface-2); }
.album-mood-row__track view { height: 100%; border-radius: inherit; }
.album-dishes { display: flex; flex-direction: column; }
.album-dish { display: flex; min-height: 58rpx; align-items: center; border-bottom: 2rpx solid var(--mrc-border-light); }
.album-dish:last-child { border-bottom: none; }
.album-dish__medal { display: flex; width: 42rpx; height: 42rpx; align-items: center; justify-content: center; border-radius: 50%; background: var(--mrc-accent-soft); color: var(--mrc-accent); font-size: 17rpx; font-weight: 850; }
.album-dish__name { flex: 1; margin-left: 14rpx; color: var(--mrc-text-strong); font-size: 25rpx; font-weight: 700; }
.album-dish__count { color: var(--mrc-accent); font-size: 22rpx; font-weight: 750; }
.album-stats__note { position: relative; display: flex; min-height: 70rpx; align-items: center; gap: 14rpx; padding: 10rpx 16rpx; color: var(--mrc-text-sub); font-size: 21rpx; line-height: 1.45; }
.album-stats__note-mark { width: 8rpx; height: 38rpx; flex: 0 0 auto; border-radius: 8rpx; background: var(--mrc-primary-grad); }

/* 每日记录：智能排版 */
.album-daily {
  position: relative;
  height: 100%;
  padding: 26rpx 36rpx 110rpx;
  box-sizing: border-box;
  overflow-y: auto;
}
.album-daily__paper { padding: 14rpx; border: 2rpx solid var(--mrc-border); border-radius: 34rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-lift), var(--mrc-gloss); }
.album-daily__layout { position: relative; width: 100%; height: 690rpx; }
.album-daily__cell { position: absolute; overflow: hidden; border-radius: 24rpx; background: var(--mrc-surface-2); box-shadow: inset 0 0 0 2rpx var(--mrc-border-light); }
.album-daily__cell-img { width: 100%; height: 100%; display: block; }
.album-daily__cell-empty {
  width: 100%; height: 100%;
  background: var(--mrc-surface-2);
  border: 2rpx dashed var(--mrc-border-light);
  display: flex; align-items: center; justify-content: center;
}
.album-daily__cell-emoji { font-size: 72rpx; }
.album-daily__cell-tag {
  position: absolute; left: 0; right: 0; bottom: 0;
  display: flex; min-height: 84rpx; flex-direction: column; justify-content: center; padding: 10rpx 18rpx;
  background: linear-gradient(180deg, transparent, rgba(38, 20, 13, .88));
}
.album-daily__cell-tag-meta { color: rgba(255, 255, 255, .72); font-size: 17rpx; font-weight: 600; }
.album-daily__cell-tag-txt { max-width: 96%; overflow: hidden; color: #fff; font-size: 24rpx; font-weight: 750; text-overflow: ellipsis; white-space: nowrap; }
.album-daily__caption { display: flex; min-height: 104rpx; align-items: center; gap: 14rpx; margin-top: 18rpx; padding: 10rpx 18rpx; border-radius: 26rpx; background: var(--mrc-surface-peach); }
.album-daily__caption view { min-width: 0; }
.album-daily__caption text { display: block; color: var(--mrc-text-sub); font-size: 20rpx; line-height: 1.45; }
.album-daily__caption text:first-child { margin-bottom: 4rpx; color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; letter-spacing: 1rpx; }
.album-daily__guozai { width: 86rpx; height: 86rpx; flex: 0 0 auto; }

/* 每日记录空状态 */
.album-daily--empty { display: flex; flex-direction: column; align-items: center; }
.album-daily__empty-tip { display: flex; flex-direction: column; align-items: center; gap: 24rpx; margin-top: 120rpx; }
.album-daily__empty-guozai { width: 320rpx; height: 320rpx; }
.album-daily__empty-text { font-size: 34rpx; color: var(--mrc-text-sub); text-align: center; line-height: 1.8; white-space: pre-line; }

/* AI 寄语 */
.album-message {
  position: relative;
  height: 100%;
  padding: 26rpx 36rpx 110rpx;
  box-sizing: border-box;
  overflow-y: auto;
}
.album-message__letter { position: relative; min-height: 710rpx; padding: 50rpx 34rpx 28rpx; overflow: hidden; border: 2rpx solid var(--mrc-border); border-radius: 34rpx; background: linear-gradient(var(--mrc-surface), var(--mrc-surface)) padding-box, repeating-linear-gradient(135deg, var(--mrc-primary) 0 12rpx, var(--mrc-surface) 12rpx 24rpx, var(--mrc-blue) 24rpx 36rpx, var(--mrc-surface) 36rpx 48rpx) border-box; box-shadow: var(--mrc-shadow-lift); }
.album-message__letter::before { position: absolute; top: 19rpx; left: 36rpx; width: 130rpx; height: 22rpx; background: var(--mrc-surface-sun); content: ''; opacity: .9; transform: rotate(-3deg); }
.album-message__stamp { position: absolute; top: 34rpx; right: 28rpx; display: flex; width: 92rpx; height: 92rpx; align-items: center; justify-content: center; border: 2rpx solid var(--mrc-primary); border-radius: 10rpx; color: var(--mrc-accent); font-size: 15rpx; font-weight: 800; letter-spacing: 1rpx; line-height: 1.35; text-align: center; transform: rotate(4deg); }
.album-message__facts { display: flex; flex-wrap: wrap; gap: 10rpx; padding-right: 94rpx; }
.album-message__facts text { padding: 9rpx 15rpx; border-radius: 20rpx; background: var(--mrc-accent-soft); color: var(--mrc-accent); font-size: 18rpx; font-weight: 700; }
.album-message__text { display: flex; flex-direction: column; gap: 12rpx; margin-top: 42rpx; padding: 32rpx 2rpx; border-top: 2rpx solid var(--mrc-border-light); border-bottom: 2rpx solid var(--mrc-border-light); }
.album-message__text::before { color: var(--mrc-accent); content: '锅仔想对你说'; font-size: 20rpx; font-weight: 800; letter-spacing: 2rpx; }
.album-message__text text { color: var(--mrc-text-strong); font-size: 30rpx; font-weight: 650; line-height: 1.75; }
.album-message__sign { display: flex; align-items: flex-end; justify-content: flex-end; margin-top: 26rpx; }
.album-message__sign view { padding-bottom: 12rpx; text-align: right; }
.album-message__sign text { display: block; color: var(--mrc-text-sub); font-size: 18rpx; }
.album-message__sign text:last-child { margin-top: 4rpx; color: var(--mrc-text-deep); font-size: 28rpx; font-weight: 850; }
.album-message__guozai { width: 150rpx; height: 150rpx; }
.album-message__privacy { display: block; margin-top: 18rpx; color: var(--mrc-text-light); font-size: 18rpx; line-height: 1.45; text-align: center; }

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
  background: var(--mrc-surface);
  border-top: 2rpx solid var(--mrc-border-light);
  z-index: 30;
}
.album-nav__btn { display: flex; min-width: 120rpx; min-height: 88rpx; align-items: center; justify-content: center; padding: 0 28rpx; border-radius: 44rpx; background: var(--mrc-surface-2); color: var(--mrc-text-deep); font-size: 26rpx; font-weight: 700; text-align: center; }
.album-nav__btn--primary { background: var(--mrc-primary-grad); color: #fff; }
.album-nav__dots { display: flex; gap: 12rpx; }
.album-nav__dot { width: 14rpx; height: 14rpx; border-radius: 50%; background: var(--mrc-border-light); }
.album-nav__dot--active { background: var(--mrc-primary-deep); width: 32rpx; border-radius: 8rpx; }
/* 分享按钮：右上角被微信胶囊遮挡，故浮到导航栏下方（top 由 shareTop 按真实状态栏+导航栏高度注入） */
.album-page__share { position: absolute; right: 24rpx; z-index: 50; display: flex; width: 88rpx; height: 88rpx; align-items: center; justify-content: center; border: 2rpx solid var(--mrc-border-light); border-radius: 50%; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-sm); }
.album-page__share:active, .album-nav__btn:active { opacity: .76; transform: scale(.97); }
.album-page__share-badge {
  position: absolute;
  top: -6rpx;
  right: -6rpx;
  padding: 2rpx 12rpx;
  border-radius: 20rpx;
  background: var(--mrc-primary-deep);
  color: #fff;
  font-size: 18rpx;
  font-weight: 700;
  line-height: 1.4;
  box-shadow: 0 4rpx 12rpx rgba(253, 145, 132, 0.35);
}

.album-share__hint {
  margin-top: -8rpx;
  padding: 0 8rpx;
  color: var(--mrc-text-sub);
  font-size: 22rpx;
  line-height: 1.5;
  text-align: center;
}

@media (prefers-reduced-motion: reduce) {
  .album-page__share, .album-nav__btn { transition: none; }
}
</style>
