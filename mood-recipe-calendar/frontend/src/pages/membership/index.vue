<script setup lang="ts">
import type { VirtualProduct } from '../../api/virtualCommerce'
import { computed, ref } from 'vue'
import { navBack, useNavBar } from '@/composables/useNavBar'
import { STATIC_BASE_URL } from '@/utils/assets'
import {
  createVirtualOrder,
  fetchVirtualOrder,
  fetchVirtualProducts,
  getVirtualPaymentParams,
  MEMBER_PRODUCT_SKU,
  requestWechatVirtualPayment,
} from '../../api/virtualCommerce'
import { useUserStore } from '../../stores/user'
import { ensureLogin, refreshUserInfo } from '../../utils/login'
import { toast, toastError, toastSuccess } from '../../utils/toast'

definePage({ name: 'membership', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '锅仔会员' } })

const router = useRouter()
const nav = useNavBar()
const userStore = useUserStore()
const loading = ref(true)
const paying = ref(false)
const product = ref<VirtualProduct | null>(null)
const error = ref('')
let paymentSupported = false
// #ifdef MP-WEIXIN
paymentSupported = typeof (uni as any).requestVirtualPayment === 'function'
// #endif

const benefits = [
  { icon: '✦', title: '锅仔管饭智能体', value: '把一家人的吃饭问题交给锅仔', detail: '结合人数、预算、忌口和不做饭日期，安排周菜单、购物清单并随时重排。' },
  { icon: '∞', title: 'AI 私人菜单', value: '30 天内不限次数定制', detail: '按家中现有食材、时间、口味和健康目标，生成更贴合当下的一餐。' },
  { icon: '▣', title: '月度画册收藏版', value: '高清导出不限次数', detail: '把当月真实记录、心情和锅仔寄语整理成无水印收藏图，随时保存分享。' },
  { icon: '⌁', title: '一张会员通行证', value: '不用再逐项购买权益', detail: '30 天内统一覆盖智能规划、私人菜单和高清画册，家庭需求变化时随时回来调整。' },
]

const active = computed(() => userStore.userInfo?.isMember === 1
  && Boolean(userStore.userInfo?.memberExpire)
  && new Date(userStore.userInfo!.memberExpire!).getTime() > Date.now())
const expireText = computed(() => userStore.userInfo?.memberExpire?.slice(0, 10) || '')
const price = computed(() => ((product.value?.priceFen || 990) / 100).toFixed(2))
const purchasable = computed(() => paymentSupported && Boolean(product.value?.platformItemId))
const actionText = computed(() => {
  if (active.value)
    return `会员有效至 ${expireText.value}`
  if (!userStore.isLoggedIn)
    return '登录后开通会员'
  if (!paymentSupported)
    return '请在微信小程序内开通'
  if (!purchasable.value)
    return '会员道具配置中'
  return `¥${price.value} · 开通 30 天`
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    await ensureLogin()
    const [, products] = await Promise.all([refreshUserInfo(true), fetchVirtualProducts()])
    product.value = products.find(item => item.sku === MEMBER_PRODUCT_SKU) || null
  }
  catch (e: any) {
    error.value = e?.message === 'NOT_LOGGED_IN' ? '' : (e?.message || '权益加载失败')
  }
  finally {
    loading.value = false
  }
}

onShow(load)

async function purchase() {
  if (active.value || paying.value)
    return
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login' })
    return
  }
  if (!product.value || !purchasable.value) {
    toast(paymentSupported ? '会员道具 ID 配置后即可开通' : '请在微信小程序内开通')
    return
  }
  paying.value = true
  let checking = false
  try {
    const openid = await ensureLogin()
    const order = await createVirtualOrder(openid, product.value.sku)
    const params = await getVirtualPaymentParams(openid, order.orderNo)
    await requestWechatVirtualPayment(params)
    checking = true
    uni.showLoading({ title: '正在确认会员…', mask: true })
    const delivered = await waitForDelivery(order.orderNo)
    if (!delivered) {
      toast('支付已完成，会员正在到账，请稍后刷新')
      return
    }
    await refreshUserInfo(true)
    toastSuccess('锅仔会员已开通')
  }
  catch (e: any) {
    toastError(e, '暂时无法开通会员')
  }
  finally {
    if (checking)
      uni.hideLoading()
    paying.value = false
  }
}

async function waitForDelivery(orderNo: string) {
  for (let attempt = 0; attempt < 4; attempt += 1) {
    await new Promise(resolve => setTimeout(resolve, attempt === 0 ? 900 : 1600))
    if ((await fetchVirtualOrder(orderNo)).status === 'DELIVERED')
      return true
  }
  return false
}
</script>

<template>
  <view class="member-page">
    <view class="member-nav" :style="{ paddingTop: `${nav.statusBarHeight}px`, minHeight: `${nav.navBarHeight}px` }">
      <view class="member-nav__back" role="button" aria-label="返回" @click="navBack">
        <text>‹</text>
      </view>
      <text class="member-nav__title">
        锅仔会员
      </text>
      <view class="member-nav__space" />
    </view>

    <view class="member-hero">
      <view class="member-hero__halo" />
      <view class="member-hero__copy">
        <text class="member-hero__eyebrow">
          GUOZAI CLUB · 30 DAYS
        </text>
        <text class="member-hero__title">
          不是多几个功能，<br>是少操心每一餐。
        </text>
        <text class="member-hero__sub">
          锅仔记住你的家，也替你把菜单、买菜和回忆认真安排好。
        </text>
        <view class="member-hero__status" :class="{ 'is-active': active }">
          <text>{{ active ? `会员中 · ${expireText} 到期` : '尚未开通' }}</text>
        </view>
      </view>
      <image class="member-hero__guozai" :src="`${STATIC_BASE_URL}/static/guozai/action_06_glasses.png`" mode="aspectFit" />
    </view>

    <view class="member-summary">
      <view><text>4</text><text>项核心权益</text></view>
      <view><text>30</text><text>天持续陪伴</text></view>
      <view><text>∞</text><text>会员不限次数</text></view>
    </view>

    <view class="member-section-head">
      <text class="member-section-head__eyebrow">
        MEMBER BENEFITS
      </text>
      <text class="member-section-head__title">
        每项权益，解决一个真实麻烦
      </text>
    </view>

    <view class="member-benefits">
      <view v-for="(item, index) in benefits" :key="item.title" class="member-benefit">
        <view class="member-benefit__icon">
          <text>{{ item.icon }}</text>
        </view>
        <view class="member-benefit__body">
          <view class="member-benefit__head">
            <text class="member-benefit__index">
              0{{ index + 1 }}
            </text>
            <text class="member-benefit__title">
              {{ item.title }}
            </text>
          </view>
          <text class="member-benefit__value">
            {{ item.value }}
          </text>
          <text class="member-benefit__detail">
            {{ item.detail }}
          </text>
        </view>
      </view>
    </view>

    <view v-if="error" class="member-error" role="button" @click="load">
      {{ error }} · 点击重试
    </view>
    <view class="member-note">
      基础推荐和饮食记录继续免费；会员购买的是更深度、更省心的持续服务。
      <text class="member-note__legal">
        ¥{{ price }} / 30 天，非自动续费。虚拟权益一经使用不支持无理由退款；未使用或到账异常请通过「我的-联系客服」申请处理。
      </text>
    </view>
    <view class="member-safe-space" />

    <view class="member-footer">
      <view class="member-footer__price">
        <text>{{ active ? '已包含全部权益' : `¥${price}` }}</text>
        <text>{{ active ? '续费后有效期顺延' : '30 天权益包 · 非自动续费' }}</text>
      </view>
      <view class="member-footer__button" :class="{ 'is-disabled': active || loading || paying || !purchasable }" role="button" :aria-label="actionText" @click="purchase">
        {{ paying ? '确认中…' : actionText }}
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.member-page { min-height: 100vh; box-sizing: border-box; padding: 0 24rpx; color: var(--mrc-text-strong); background: var(--mrc-page); }
.member-nav { display: flex; align-items: center; justify-content: space-between; }
.member-nav__back, .member-nav__space { display: flex; width: 68rpx; height: 68rpx; align-items: center; justify-content: center; }
.member-nav__back text { color: var(--mrc-text-strong); font-size: 54rpx; line-height: 1; }
.member-nav__title { font-size: 34rpx; font-weight: 850; }
.member-hero { position: relative; min-height: 390rpx; margin-top: 14rpx; overflow: hidden; border-radius: 42rpx; background: linear-gradient(145deg, #2d1a13 0%, #4b291d 58%, #703827 100%); box-shadow: 0 22rpx 50rpx rgba(74, 39, 25, .24); }
.member-hero__halo { position: absolute; right: -80rpx; top: -100rpx; width: 350rpx; height: 350rpx; border: 2rpx solid rgba(255, 218, 154, .22); border-radius: 50%; box-shadow: 0 0 0 54rpx rgba(255, 218, 154, .04); }
.member-hero__copy { position: relative; z-index: 2; width: 68%; padding: 42rpx 0 36rpx 38rpx; }
.member-hero__eyebrow { color: #f3c47e; font-size: 18rpx; font-weight: 850; letter-spacing: 3rpx; }
.member-hero__title { display: block; margin-top: 22rpx; color: #fffaf2; font-size: 44rpx; font-weight: 900; line-height: 1.25; }
.member-hero__sub { display: block; margin-top: 20rpx; color: rgba(255, 246, 232, .72); font-size: 22rpx; line-height: 1.55; }
.member-hero__status { display: inline-flex; margin-top: 24rpx; padding: 10rpx 18rpx; border: 2rpx solid rgba(255, 224, 177, .3); border-radius: 24rpx; color: #f6d6a4; font-size: 19rpx; font-weight: 750; }
.member-hero__status.is-active { border-color: rgba(92, 224, 172, .45); color: #8de7c3; background: rgba(22, 117, 83, .18); }
.member-hero__guozai { position: absolute; z-index: 1; right: -10rpx; bottom: 6rpx; width: 245rpx; height: 245rpx; }
.member-summary { display: grid; grid-template-columns: repeat(3, 1fr); margin: -22rpx 20rpx 0; padding: 26rpx 10rpx; position: relative; z-index: 3; border: 2rpx solid var(--mrc-border); border-radius: 30rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-lift); }
.member-summary view { display: flex; flex-direction: column; align-items: center; gap: 5rpx; border-right: 2rpx solid var(--mrc-border-light); }
.member-summary view:last-child { border-right: 0; }
.member-summary text:first-child { color: var(--mrc-accent); font-size: 34rpx; font-weight: 900; }
.member-summary text:last-child { color: var(--mrc-text-sub); font-size: 18rpx; }
.member-section-head { margin: 48rpx 8rpx 20rpx; }
.member-section-head__eyebrow, .member-section-head__title { display: block; }
.member-section-head__eyebrow { color: var(--mrc-accent); font-size: 18rpx; font-weight: 850; letter-spacing: 3rpx; }
.member-section-head__title { margin-top: 7rpx; font-size: 32rpx; font-weight: 900; }
.member-benefits { display: flex; flex-direction: column; gap: 16rpx; }
.member-benefit { display: flex; gap: 22rpx; padding: 26rpx; border: 2rpx solid var(--mrc-border); border-radius: 32rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.member-benefit__icon { display: flex; width: 74rpx; height: 74rpx; flex: 0 0 auto; align-items: center; justify-content: center; border-radius: 24rpx; background: linear-gradient(145deg, var(--mrc-surface-sun), var(--mrc-surface-peach)); color: var(--mrc-accent); font-size: 30rpx; font-weight: 900; }
.member-benefit__body { min-width: 0; flex: 1; }
.member-benefit__head { display: flex; align-items: center; gap: 12rpx; }
.member-benefit__index { color: var(--mrc-accent); font-size: 17rpx; font-weight: 900; letter-spacing: 1rpx; }
.member-benefit__title { font-size: 27rpx; font-weight: 850; }
.member-benefit__value { display: block; margin-top: 9rpx; color: var(--mrc-accent); font-size: 22rpx; font-weight: 750; }
.member-benefit__detail { display: block; margin-top: 8rpx; color: var(--mrc-text-sub); font-size: 21rpx; line-height: 1.55; }
.member-note, .member-error { margin: 24rpx 8rpx 0; color: var(--mrc-text-sub); font-size: 20rpx; line-height: 1.55; text-align: center; }
.member-note__legal { display: block; margin-top: 10rpx; font-size: 18rpx; }
.member-error { color: var(--mrc-accent); }
.member-safe-space { height: 190rpx; }
.member-footer { position: fixed; z-index: 20; right: 0; bottom: 0; left: 0; display: flex; align-items: center; gap: 20rpx; padding: 22rpx 24rpx calc(22rpx + env(safe-area-inset-bottom)); border-top: 2rpx solid var(--mrc-border-light); background: rgba(255, 248, 237, .96); box-shadow: 0 -12rpx 34rpx rgba(93, 54, 30, .08); }
.member-footer__price { display: flex; min-width: 190rpx; flex-direction: column; }
.member-footer__price text:first-child { color: var(--mrc-text-deep); font-size: 28rpx; font-weight: 900; }
.member-footer__price text:last-child { margin-top: 4rpx; color: var(--mrc-text-sub); font-size: 17rpx; }
.member-footer__button { display: flex; min-height: 86rpx; flex: 1; align-items: center; justify-content: center; padding: 0 24rpx; border-radius: 28rpx; background: linear-gradient(135deg, #f0b45f, #e96b4a); color: #fff; font-size: 25rpx; font-weight: 850; box-shadow: 0 12rpx 24rpx rgba(219, 92, 61, .22); text-align: center; }
.member-footer__button.is-disabled { opacity: .62; box-shadow: none; }
@media (prefers-reduced-motion: reduce) { .member-footer__button { transition: none; } }
</style>
