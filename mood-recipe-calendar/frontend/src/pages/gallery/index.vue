<script setup lang="ts">
import { navBack } from '@/composables/useNavBar'
import { ref, computed } from 'vue'
import Icon from '../../components/common/Icon.vue'
import LoadingState from '../../components/guozai/LoadingState.vue'
import ErrorState from '../../components/guozai/ErrorState.vue'
import { ensureLogin } from '../../utils/login'
import {
  fetchProducts,
  fetchCheckinStatus,
  doCheckin,
  fetchOrders,
  type Product,
  type CheckinStatus,
  type ShopOrder,
} from '../../api/gallery'

definePage({
  name: 'gallery',
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '锅仔形象馆',
  },
})

const router = useRouter()

// ---------- 数据状态 ----------
const loading = ref(true)
const error = ref('')
const products = ref<Product[]>([])
const checkin = ref<CheckinStatus>({ checkedIn: false, streak: 0, exchangeReady: false, daysToExchange: 30, totalDays: 0 })
const orders = ref<ShopOrder[]>([])
const activeTab = ref<'assets' | 'shop' | 'orders'>('assets')

// 购买弹窗
const showBuy = ref(false)
const selectedProduct = ref<Product | null>(null)
const buyType = ref<'normal' | 'exchange'>('normal')

// ---------- 表情包（内容资产区：锅仔透明 PNG 资源库） ----------
const STICKER_GROUPS = [
  {
    title: '心情表情',
    items: [
      { name: '开心', src: '/static/guozai/mood_01_happy.png' },
      { name: '平静', src: '/static/guozai/mood_02_calm.png' },
      { name: '疲惫', src: '/static/guozai/mood_03_tired.png' },
      { name: '焦虑', src: '/static/guozai/mood_04_anxious.png' },
      { name: '难过', src: '/static/guozai/mood_05_sad.png' },
      { name: '嘴馋', src: '/static/guozai/mood_06_hungry.png' },
      { name: '低落', src: '/static/guozai/mood_07_low.png' },
      { name: '想家', src: '/static/guozai/mood_08_homesick.png' },
    ],
  },
  {
    title: '动作表情',
    items: [
      { name: '端碗', src: '/static/guozai/action_01_bowl.png' },
      { name: '端汤', src: '/static/guozai/action_02_soup.png' },
      { name: '拍照', src: '/static/guozai/action_03_camera.png' },
      { name: '日历', src: '/static/guozai/action_04_calendar.png' },
      { name: '画册', src: '/static/guozai/action_05_album.png' },
      { name: '眼镜', src: '/static/guozai/action_06_glasses.png' },
      { name: '空碗', src: '/static/guozai/action_07_empty.png' },
      { name: '探头', src: '/static/guozai/action_08_peek.png' },
      { name: '庆祝', src: '/static/guozai/action_09_celebrate.png' },
      { name: '思考', src: '/static/guozai/action_10_thinking.png' },
    ],
  },
  {
    title: '状态表情',
    items: [
      { name: '空状态', src: '/static/guozai/state_01_empty.png' },
      { name: '加载中', src: '/static/guozai/state_02_loading.png' },
      { name: '网络错误', src: '/static/guozai/state_03_error.png' },
    ],
  },
]

const progressPct = computed(() => Math.min(100, Math.round((checkin.value.streak / 30) * 100)))

// ---------- 数据加载 ----------
async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const openid = await ensureLogin()
    const [p, c, o] = await Promise.all([
      fetchProducts(),
      fetchCheckinStatus(openid),
      fetchOrders(openid),
    ])
    products.value = p
    checkin.value = c
    orders.value = o
  } catch (e: any) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onShow(() => {
  loadData()
})

// ---------- 签到 ----------
async function onCheckin() {
  if (checkin.value.checkedIn) return
  try {
    const openid = await ensureLogin()
    checkin.value = await doCheckin(openid)
    uni.showToast({ title: '签到成功，锅仔陪你吃饭！', icon: 'none' })
  } catch (e: any) {
    uni.showToast({ title: e.message || '签到失败', icon: 'none' })
  }
}

// ---------- 表情包下载（小程序保存到相册） ----------
function onDownloadSticker(sticker: { name: string; src: string }) {
  // #ifndef MP-WEIXIN
  uni.showToast({ title: '请在小程序中体验下载表情包', icon: 'none' })
  // #endif
  // #ifdef MP-WEIXIN
  uni.authorize({
    scope: 'scope.writePhotosAlbum',
    success: () => {
      uni.saveImageToPhotosAlbum({
        filePath: sticker.src,
        success: () => uni.showToast({ title: `已保存「${sticker.name}」到相册`, icon: 'success' }),
        fail: () => {
          // 兜底：先取图片信息再保存
          uni.getImageInfo({
            src: sticker.src,
            success: (info) => {
              uni.saveImageToPhotosAlbum({
                filePath: info.path,
                success: () => uni.showToast({ title: `已保存「${sticker.name}」`, icon: 'success' }),
                fail: () => uni.showToast({ title: '保存失败，请检查相册权限', icon: 'none' }),
              })
            },
            fail: () => uni.showToast({ title: '保存失败', icon: 'none' }),
          })
        },
      })
    },
    fail: () => uni.showToast({ title: '需要相册权限才能下载', icon: 'none' }),
  })
  // #endif
}

// ---------- 购买 ----------
function openBuy(product: Product) {
  selectedProduct.value = product
  // 已解锁 1 元兑换则默认选兑换，否则默认原价
  buyType.value = checkin.value.exchangeReady ? 'exchange' : 'normal'
  showBuy.value = true
}

async function onBuy() {
  if (!selectedProduct.value) return
  uni.showToast({ title: '锅仔周边正在筹备发售，先解锁 AI 菜单试试看吧', icon: 'none' })
  showBuy.value = false
}

const orderStatusText: Record<string, string> = {
  pending: '待支付',
  paid: '已支付',
  cancelled: '已取消',
}
const orderTypeText: Record<string, string> = {
  normal: '原价购买',
  exchange: '1元兑换',
}
</script>

<template>
  <view class="gallery-page">
    <!-- 顶部导航（通用组件：状态栏 + 胶囊避让） -->
    <wd-navbar title="锅仔形象馆" left-arrow safe-area-inset-top @click-left="navBack" /> 
    <!-- Loading / Error -->
    <LoadingState v-if="loading" text="锅仔正在布置形象馆..." />
    <ErrorState v-else-if="error" :text="error" @retry="loadData" />

    <template v-else>
      <!-- 签到福利卡片 -->
      <view class="checkin-card">
        <view class="checkin-card__left">
          <view class="checkin-card__head">
            <text class="checkin-card__label">签到福利</text>
            <text v-if="checkin.exchangeReady" class="checkin-card__ready">🎉 1元兑换已解锁</text>
          </view>
          <view class="checkin-card__progress">
            <text class="checkin-card__streak">
              连续签到 <text class="checkin-card__num">{{ checkin.streak }}</text> / 30 天
            </text>
            <text class="checkin-card__hint">满 30 天可 1 元兑换任意周边</text>
          </view>
          <view class="checkin-bar">
            <view class="checkin-bar__inner" :style="{ width: progressPct + '%' }" />
          </view>
        </view>
        <view class="checkin-card__right">
          <image
            class="checkin-card__guozai"
            :src="checkin.exchangeReady ? '/static/guozai/action_09_celebrate.png' : (checkin.checkedIn ? '/static/guozai/mood_01_happy.png' : '/static/guozai/action_08_peek.png')"
            mode="aspectFit"
          />
          <view
            class="checkin-btn"
            :class="{ 'checkin-btn--done': checkin.checkedIn, 'checkin-btn--ready': checkin.exchangeReady }"
            @click="onCheckin"
          >
            {{ checkin.checkedIn ? '今日已签到' : '签到' }}
          </view>
        </view>
      </view>

      <!-- Tab 切换 -->
      <view class="gallery-tabs">
        <view class="gallery-tab" :class="{ 'gallery-tab--active': activeTab === 'assets' }" @click="activeTab = 'assets'">
          内容资产
        </view>
        <view class="gallery-tab" :class="{ 'gallery-tab--active': activeTab === 'shop' }" @click="activeTab = 'shop'">
          周边商城
        </view>
        <view class="gallery-tab" :class="{ 'gallery-tab--active': activeTab === 'orders' }" @click="activeTab = 'orders'">
          我的订单
        </view>
      </view>

      <!-- ============ 内容资产区：锅仔表情包 ============ -->
      <view v-if="activeTab === 'assets'">
        <view class="asset-intro">
          <image class="asset-intro__guozai" src="/static/guozai/mood_06_hungry.png" mode="aspectFit" />
          <view class="asset-intro__text">
            <text class="asset-intro__title">锅仔专属表情包</text>
            <text class="asset-intro__sub">点击即可保存到相册，让锅仔住进你的聊天里</text>
          </view>
        </view>
        <view v-for="group in STICKER_GROUPS" :key="group.title" class="asset-group">
          <text class="asset-group__title">{{ group.title }}</text>
          <view class="asset-grid">
            <view
              v-for="s in group.items"
              :key="s.name"
              class="asset-item"
              @click="onDownloadSticker(s)"
            >
              <image class="asset-item__img" :src="s.src" mode="aspectFit" />
              <text class="asset-item__name">{{ s.name }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- ============ 周边商城 ============ -->
      <view v-if="activeTab === 'shop'">
        <view v-if="products.length === 0" class="gallery-empty">
          <image class="gallery-empty__img" src="/static/guozai/action_07_empty.png" mode="aspectFit" />
          <text class="gallery-empty__text">周边正在赶工中，锅仔先去喝口水～</text>
        </view>
        <view class="shop-grid">
          <view v-for="p in products" :key="p.id" class="shop-card" @click="openBuy(p)">
            <image class="shop-card__img" :src="p.image" mode="aspectFill" />
            <view class="shop-card__body">
              <text class="shop-card__name">{{ p.name }}</text>
              <text class="shop-card__desc">{{ p.description }}</text>
              <view class="shop-card__bottom">
                <view class="shop-card__price">
                  <text class="shop-card__price--now">¥{{ p.price }}</text>
                  <text class="shop-card__price--exchange">签到1元兑</text>
                </view>
                <view class="shop-card__buy" @click.stop="openBuy(p)">预约</view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- ============ 我的订单 ============ -->
      <view v-if="activeTab === 'orders'">
        <view v-if="orders.length === 0" class="gallery-empty">
          <image class="gallery-empty__img" src="/static/guozai/action_07_empty.png" mode="aspectFit" />
          <text class="gallery-empty__text">还没有订单，去带一只锅仔回家吧～</text>
        </view>
        <view v-for="o in orders" :key="o.id" class="order-card">
          <image class="order-card__img" :src="o.productImage || '/static/guozai/mood_01_happy.png'" mode="aspectFill" />
          <view class="order-card__main">
            <text class="order-card__name">{{ o.productName }}</text>
            <text class="order-card__type">{{ orderTypeText[o.payType] || o.payType }}</text>
            <text class="order-card__no">单号 {{ o.orderNo }}</text>
          </view>
          <view class="order-card__right">
            <text class="order-card__amount">¥{{ o.amount }}</text>
            <view class="order-card__status" :class="'order-card__status--' + o.status">
              {{ orderStatusText[o.status] || o.status }}
            </view>
          </view>
        </view>
      </view>

      <view class="gallery-footer">
        <image class="gallery-footer__guozai" src="/static/guozai/mood_02_calm.png" mode="aspectFit" />
        <text class="gallery-footer__text">「用一道菜，治愈今天的你。」</text>
      </view>
    </template>

    <!-- 购买弹窗 -->
    <view v-if="showBuy && selectedProduct" class="buy-mask" @click.self="showBuy = false">
      <view class="buy-pop pop-in">
        <image class="buy-pop__img" :src="selectedProduct.image" mode="aspectFill" />
        <text class="buy-pop__name">{{ selectedProduct.name }}</text>
        <text class="buy-pop__desc">{{ selectedProduct.description }}</text>

        <view class="buy-pop__options">
          <view
            class="buy-option"
            :class="{
              'buy-option--active': buyType === 'normal',
              'buy-option--disabled': !checkin.exchangeReady && buyType === 'normal',
            }"
            @click="buyType = 'normal'"
          >
            <view class="buy-option__top">
              <text class="buy-option__name">原价购买</text>
              <text class="buy-option__price">¥{{ selectedProduct.price }}</text>
            </view>
            <text class="buy-option__sub">立即带走锅仔周边</text>
          </view>
          <view
            class="buy-option"
            :class="{ 'buy-option--active': buyType === 'exchange' }"
            @click="buyType = 'exchange'"
          >
            <view class="buy-option__top">
              <text class="buy-option__name">1元兑换</text>
              <text class="buy-option__price">¥{{ selectedProduct.exchangePrice }}</text>
            </view>
            <text v-if="checkin.exchangeReady" class="buy-option__sub buy-option__sub--ok">🎉 连续签到已达标，可兑换！</text>
            <text v-else class="buy-option__sub">连续签到满 30 天解锁（当前 {{ checkin.streak }}/30）</text>
          </view>
        </view>

        <view
          class="buy-pop__btn"
          :class="{ 'buy-pop__btn--disabled': buyType === 'exchange' && !checkin.exchangeReady }"
          @click="onBuy"
        >
          {{ buyType === 'exchange' && !checkin.exchangeReady ? '签到满30天可兑换' : '周边筹备中，先预约关注' }}
        </view>
        <view class="buy-pop__close" @click="showBuy = false">
          <Icon name="back" :size="32" color="var(--mrc-text-sub)" />
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.gallery-page {
  min-height: 100vh;
  background: var(--mrc-bg);
  padding: 0 32rpx;
  padding-bottom: calc(60rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* 签到卡片 */
.checkin-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, var(--mrc-surface-peach), var(--mrc-surface-sun));
  border: 2rpx solid var(--mrc-border);
  border-radius: 32rpx;
  padding: 32rpx;
  margin: 12rpx 0 24rpx;
  box-shadow: var(--mrc-shadow);
}
.checkin-card__left {
  flex: 1;
  padding-right: 20rpx;
}
.checkin-card__head {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 16rpx;
}
.checkin-card__label {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
}
.checkin-card__ready {
  font-size: 24rpx;
  color: var(--mrc-accent);
  background: var(--mrc-accent-soft);
  padding: 4rpx 16rpx;
  border-radius: 24rpx;
  font-weight: 600;
}
.checkin-card__streak {
  font-size: 28rpx;
  color: var(--mrc-text);
}
.checkin-card__num {
  font-size: 40rpx;
  font-weight: 900;
  color: var(--mrc-accent);
  margin: 0 4rpx;
}
.checkin-card__hint {
  display: block;
  font-size: 24rpx;
  color: var(--mrc-text-sub);
  margin-top: 8rpx;
}
.checkin-bar {
  height: 16rpx;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 8rpx;
  margin-top: 20rpx;
  overflow: hidden;
}
.checkin-bar__inner {
  height: 100%;
  background: linear-gradient(90deg, var(--mrc-primary), var(--mrc-accent));
  border-radius: 8rpx;
  transition: width 0.5s ease;
}
.checkin-card__right {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}
.checkin-card__guozai {
  width: 120rpx;
  height: 120rpx;
}
.checkin-btn {
  padding: 12rpx 36rpx;
  border-radius: 40rpx;
  background: var(--mrc-primary-grad);
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
  box-shadow: 0 6rpx 16rpx rgba(255, 139, 106, 0.4);
}
.checkin-btn--done {
  background: var(--mrc-green);
  box-shadow: none;
}
.checkin-btn--ready {
  background: linear-gradient(135deg, var(--mrc-yellow), #ffb347);
}
.checkin-btn:active {
  transform: scale(0.96);
}

/* Tab */
.gallery-tabs {
  display: flex;
  background: var(--mrc-surface-2);
  border-radius: 50rpx;
  padding: 8rpx;
  margin-bottom: 32rpx;
}
.gallery-tab {
  flex: 1;
  text-align: center;
  padding: 20rpx 0;
  border-radius: 40rpx;
  font-size: 30rpx;
  color: var(--mrc-text-sub);
  font-weight: 500;
  transition: all 0.2s ease;
}
.gallery-tab--active {
  background: var(--mrc-surface);
  color: var(--mrc-accent);
  font-weight: 700;
  box-shadow: 0 4rpx 12rpx rgba(90, 62, 43, 0.08);
}

/* 内容资产 */
.asset-intro {
  display: flex;
  align-items: center;
  gap: 20rpx;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 28rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
  box-shadow: var(--mrc-shadow-soft);
}
.asset-intro__guozai {
  width: 100rpx;
  height: 100rpx;
}
.asset-intro__text {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.asset-intro__title {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
}
.asset-intro__sub {
  font-size: 24rpx;
  color: var(--mrc-text-sub);
}
.asset-group {
  margin-bottom: 32rpx;
}
.asset-group__title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--mrc-text);
  display: block;
  margin-bottom: 16rpx;
  padding-left: 16rpx;
  border-left: 8rpx solid var(--mrc-primary);
}
.asset-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20rpx;
}
.asset-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 24rpx;
  padding: 20rpx 8rpx;
  transition: all 0.15s ease;
}
.asset-item:active {
  transform: scale(0.94);
  border-color: var(--mrc-primary);
}
.asset-item__img {
  width: 110rpx;
  height: 110rpx;
}
.asset-item__name {
  font-size: 22rpx;
  color: var(--mrc-text-sub);
}

/* 周边商城 */
.shop-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24rpx;
}
.shop-card {
  background: var(--mrc-white);
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 16rpx rgba(121, 73, 53, 0.06);
  border: 2rpx solid var(--mrc-border-light);
}
.shop-card:active {
  transform: scale(0.97);
}
.shop-card__img {
  width: 100%;
  height: 280rpx;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  box-shadow: var(--mrc-shadow-soft);
}
.shop-card__body {
  padding: 20rpx;
}
.shop-card__name {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
}
.shop-card__desc {
  display: block;
  font-size: 22rpx;
  color: var(--mrc-text-sub);
  margin-top: 8rpx;
  height: 56rpx;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.shop-card__bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
}
.shop-card__price {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
}
.shop-card__price--now {
  font-size: 32rpx;
  font-weight: 800;
  color: var(--mrc-accent);
}
.shop-card__price--exchange {
  font-size: 20rpx;
  color: var(--mrc-text-light);
}
.shop-card__buy {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--mrc-primary), var(--mrc-primary-deep));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
  box-shadow: 0 4rpx 12rpx rgba(255, 139, 106, 0.35);
}

/* 我的订单 */
.order-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  background: var(--mrc-white);
  border-radius: 24rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  border: 2rpx solid var(--mrc-border-light);
}
.order-card__img {
  width: 120rpx;
  height: 120rpx;
  border-radius: 16rpx;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  box-shadow: var(--mrc-shadow-soft);
  flex-shrink: 0;
}
.order-card__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  overflow: hidden;
}
.order-card__name {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
}
.order-card__type {
  font-size: 22rpx;
  color: var(--mrc-accent);
  background: rgba(232, 131, 107, 0.12);
  align-self: flex-start;
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
}
.order-card__no {
  font-size: 20rpx;
  color: var(--mrc-text-light);
}
.order-card__right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12rpx;
}
.order-card__amount {
  font-size: 32rpx;
  font-weight: 800;
  color: var(--mrc-text-deep);
}
.order-card__status {
  font-size: 22rpx;
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
  background: var(--mrc-bg-card);
  color: var(--mrc-text-sub);
}
.order-card__status--paid {
  background: rgba(107, 203, 119, 0.15);
  color: var(--mrc-green);
}
.order-card__status--pending {
  background: rgba(255, 217, 61, 0.2);
  color: #c99700;
}

/* 空状态 */
.gallery-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
  padding: 80rpx 0;
}
.gallery-empty__img {
  width: 220rpx;
  height: 220rpx;
}
.gallery-empty__text {
  font-size: 26rpx;
  color: var(--mrc-text-sub);
}

/* 底部 */
.gallery-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  padding: 40rpx 0 20rpx;
}
.gallery-footer__guozai {
  width: 60rpx;
  height: 60rpx;
}
.gallery-footer__text {
  font-size: 24rpx;
  color: var(--mrc-text-light);
}

/* 购买弹窗 */
.buy-mask {
  position: fixed;
  inset: 0;
  background: rgba(90, 62, 43, 0.45);
  z-index: 100;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.buy-pop {
  width: 100%;
  background: var(--mrc-bg);
  border-radius: 40rpx 40rpx 0 0;
  padding: 40rpx 40rpx calc(40rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.buy-pop__img {
  width: 200rpx;
  height: 200rpx;
  border-radius: 24rpx;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  margin-bottom: 16rpx;
}
.buy-pop__name {
  font-size: 36rpx;
  font-weight: 800;
  color: var(--mrc-text-deep);
}
.buy-pop__desc {
  font-size: 24rpx;
  color: var(--mrc-text-sub);
  margin-top: 8rpx;
  margin-bottom: 24rpx;
}
.buy-pop__options {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}
.buy-option {
  border: 3rpx solid var(--mrc-border);
  border-radius: 24rpx;
  padding: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  transition: all 0.15s ease;
}
.buy-option--active {
  border-color: var(--mrc-accent);
  background: rgba(232, 131, 107, 0.06);
}
.buy-option__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.buy-option__name {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
}
.buy-option__price {
  font-size: 36rpx;
  font-weight: 900;
  color: var(--mrc-accent);
}
.buy-option__sub {
  font-size: 22rpx;
  color: var(--mrc-text-sub);
}
.buy-option__sub--ok {
  color: var(--mrc-green);
}
.buy-pop__btn {
  width: 100%;
  margin-top: 32rpx;
  height: 96rpx;
  border-radius: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--mrc-primary), var(--mrc-primary-deep));
  color: #fff;
  font-size: 32rpx;
  font-weight: 700;
  box-shadow: 0 8rpx 20rpx rgba(255, 139, 106, 0.35);
}
.buy-pop__btn--disabled {
  background: var(--mrc-border);
  color: var(--mrc-text-light);
  box-shadow: none;
}
.buy-pop__btn:active {
  transform: scale(0.98);
}
.buy-pop__close {
  position: absolute;
  top: 32rpx;
  right: 32rpx;
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: rotate(90deg);
}
</style>
