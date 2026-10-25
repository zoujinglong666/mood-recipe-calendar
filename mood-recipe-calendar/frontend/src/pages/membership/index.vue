<script setup lang="ts">
import type { MemberBenefitGroup, VirtualProduct } from '../../api/virtualCommerce'
import { computed, onMounted, ref } from 'vue'
import { navBack, useNavBar } from '@/composables/useNavBar'
import { STATIC_BASE_URL } from '@/utils/assets'
import {
  createVirtualOrder,
  fetchMemberBenefits,
  fetchVirtualOrder,
  fetchVirtualProducts,
  getVirtualPaymentParams,
  MEMBER_PRODUCT_SKU,
  MEMBER_YEAR_SKU,
  requestWechatVirtualPayment,
} from '../../api/virtualCommerce'
import { createFamily, fetchFamily, joinFamily, leaveFamily, type FamilyInfo } from '../../api/family'
import { useUserStore } from '../../stores/user'
import { ensureLogin, navigateToLogin, refreshUserInfo, refreshWechatLoginForPayment } from '../../utils/login'
import { toast, toastError, toastSuccess } from '../../utils/toast'

definePage({ name: 'membership', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '锅仔会员' } })

import { onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'

// 直接本页写生命周期，比 useShare 组合式更可靠
onShareAppMessage(() => {
  const pages = getCurrentPages()
  const route = (pages[pages.length - 1] as any)?.route || ''
  return { title: '锅仔 · 按心情帮你决定今天吃什么', path: `/${route}` }
})
onShareTimeline(() => ({ title: '锅仔 · 按心情帮你决定今天吃什么' }))

const router = useRouter()
const nav = useNavBar()
const userStore = useUserStore()
const loading = ref(true)
const paying = ref('')

/** 「开通会员成功通知」模板 ID（与后端 wechat.subscription.member-template-id 一致）。 */
const MEMBER_NOTICE_TEMPLATE_ID = '03DBh5APFeME7iZl7akNURNuNvDCxW2e1GMMQiifNiY'
/** 支付前静默请求订阅授权：拒绝/失败均不影响支付流程；返回用户是否勾选了允许。 */
function requestMemberNoticeAuthorization(): Promise<boolean> {
  return new Promise((resolve) => {
    // #ifdef MP-WEIXIN
    const requestSubscribeMessage = (uni as any).requestSubscribeMessage
    if (typeof requestSubscribeMessage !== 'function') {
      resolve(false)
      return
    }
    requestSubscribeMessage({
      tmplIds: [MEMBER_NOTICE_TEMPLATE_ID],
      success: (res: Record<string, string>) => resolve(['accept', 'acceptWithAudio'].includes(res[MEMBER_NOTICE_TEMPLATE_ID])),
      fail: () => resolve(false),
    })
    // #endif
    // #ifndef MP-WEIXIN
    resolve(false)
    // #endif
  })
}

/** 续费入口旁的授权入口：订阅消息一次授权只能发一条，续费/到期场景由用户在此重新授权。 */
async function enableRenewalNotice() {
  const accepted = await requestMemberNoticeAuthorization()
  if (accepted)
    toastSuccess('已开启，会员到期与续费成功后会通知你')
  else
    toast('未开启授权，不影响会员使用')
}
const product = ref<VirtualProduct | null>(null)
const yearProduct = ref<VirtualProduct | null>(null)
const error = ref('')
// 家庭共享
const family = ref<FamilyInfo | null>(null)
const familyLoaded = ref(false)
const joinCode = ref('')
const familyBusy = ref(false)
let paymentSupported = false
// #ifdef MP-WEIXIN
paymentSupported = typeof (uni as any).requestVirtualPayment === 'function'
// #endif

/**
 * 会员权益：优先由后台接口驱动（/virtual-commerce/member-benefits），
 * 后台未配置时回退到下方内置文案，保证会员页永远有内容、不白屏。
 */
interface BenefitItem { icon: string, title: string, value: string, detail: string }

/** 兜底权益文案：与后台真实会员闸门一一对应（首页额度、锅仔对话、周计划、冰箱、画册）。 */
const FALLBACK_GROUPS: { key: string, eyebrow: string, title: string, items: BenefitItem[] }[] = [
  {
    key: 'MEAL',
    eyebrow: 'MEAL',
    title: '每天吃什么，锅仔替你想',
    items: [
      { icon: '✦', title: '锅仔智能体不限次对话', value: '免费用户仅签到送 3 轮，会员不限次', detail: '想换菜、想调整、想问怎么做，随时开口；结合人数、预算、忌口和不做饭的日期。' },
      { icon: '▤', title: '周菜单不限次生成与重排', value: '免费用户每周 1 次，会员不限次', detail: '安排整周晚餐、一键生成购物清单；哪天不合适，直接让锅仔换一道。' },
      { icon: '600', title: '首页推荐每天 20 次', value: '免费用户每天 3 次', detail: '按你的心情、口味和忌口推荐，量足到可以任性挑；额度每天刷新。' },
      { icon: '◇', title: 'AI 私人菜单深度定制', value: '一次给到贴合你的整份菜单', detail: '输入心情和现有食材，生成一份完整可用、带做法的私人菜单，不用逐项凑。' },
    ],
  },
  {
    key: 'FRIDGE',
    eyebrow: 'FRIDGE',
    title: '冰箱里有什么，拍一张就知道',
    items: [
      { icon: '◉', title: '冰箱拍照识别食材', value: '拍一张，自动认食材、算保质期', detail: '对着冰箱拍一张，锅仔自动认出食材并估算保质期，确认后一键入库，不用一个个手输。' },
      { icon: '⏱', title: '食材临期提醒', value: '快过期时提醒你，别浪费', detail: '临期或过期的食材会主动提醒，并告诉你哪些其实不该放冰箱、该怎么存。' },
    ],
  },
  {
    key: 'MEMORY',
    eyebrow: 'MEMORY',
    title: '认真吃过的每一餐，都替你收好',
    items: [
      { icon: '▣', title: '高清无水印月度画册', value: '把这一个月认真收藏下来', detail: '将当月真实记录、心情和锅仔寄语整理成无水印收藏图，随时保存、分享。' },
      { icon: '↗', title: '锅仔联网搜索', value: '问到库外知识也能答', detail: '时令食材、食材挑选、某道菜的多样做法，锅仔可以联网帮你查，并注明来源。' },
    ],
  },
]

const remoteBenefits = ref<MemberBenefitGroup[]>([])

/** 合并为页面使用的分组结构；接口为空或字段缺失时回退内置文案。 */
const benefitGroups = computed(() => {
  const remote = remoteBenefits.value
    .filter(group => group.items?.length)
    .map(group => ({
      key: group.key,
      eyebrow: (group.key || '').toUpperCase(),
      title: group.title || '',
      items: group.items.map(item => ({
        icon: item.icon || '·',
        title: item.title || '',
        value: item.value || '',
        detail: item.detail || '',
      })),
    }))
  return remote.length ? remote : FALLBACK_GROUPS
})

const benefitCount = computed(() => benefitGroups.value.reduce((total, group) => total + group.items.length, 0))

onMounted(async () => {
  try {
    const groups = await fetchMemberBenefits()
    if (Array.isArray(groups) && groups.length)
      remoteBenefits.value = groups
  }
  catch {
    // 接口失败静默回退内置文案，会员页不因接口异常而残缺
  }
})

const active = computed(() => userStore.isActiveMember)
const expireText = computed(() => userStore.userInfo?.memberExpire?.slice(0, 10) || '')
const price = computed(() => ((product.value?.priceFen || 990) / 100).toFixed(2))
const yearPrice = computed(() => ((yearProduct.value?.priceFen || 3900) / 100).toFixed(2))
const yearDailyPrice = computed(() => ((yearProduct.value?.priceFen || 3900) / 100 / 365).toFixed(2))
const purchasable = computed(() => paymentSupported && Boolean(product.value?.paymentAvailable))
const yearPurchasable = computed(() => paymentSupported && Boolean(yearProduct.value?.paymentAvailable))
// 会员生效中也可随时续费：时长由后端在现有到期日上顺延（grantEntitlement 已支持累加）

async function load() {
  loading.value = true
  error.value = ''
  try {
    await ensureLogin()
    // 会员商品是页面核心；刷新用户信息失败不应阻断商品展示
    const products = await fetchVirtualProducts()
    product.value = products.find(item => item.sku === MEMBER_PRODUCT_SKU) || null
    yearProduct.value = products.find(item => item.sku === MEMBER_YEAR_SKU) || null
    refreshUserInfo(true).catch(() => {})
    loadFamily()
  }
  catch (e: any) {
    error.value = e?.message === 'NOT_LOGGED_IN' ? '' : (e?.message || '权益加载失败')
  }
  finally {
    loading.value = false
  }
}

onShow(() => {
  // 会员页曝光：付费漏斗入口（We 分析里配合 member_pay 算曝光→支付转化）
  funnel('member_view')
  load()
})

/** 家庭共享：拉取我的家庭（未登录/无家庭静默）。 */
async function loadFamily() {
  try {
    family.value = await fetchFamily()
  }
  catch {
    family.value = null
  }
  finally {
    familyLoaded.value = true
  }
}

async function onCreateFamily() {
  if (familyBusy.value)
    return
  familyBusy.value = true
  try {
    family.value = await createFamily()
    toastSuccess('家庭已创建，把邀请码发给家人吧')
  }
  catch (e: any) {
    toastError(e, '创建家庭失败')
  }
  finally {
    familyBusy.value = false
  }
}

async function onJoinFamily() {
  const code = joinCode.value.trim()
  if (!code) {
    toast('先输入家人的邀请码')
    return
  }
  if (familyBusy.value)
    return
  familyBusy.value = true
  try {
    family.value = await joinFamily(code)
    joinCode.value = ''
    toastSuccess('已加入家庭')
  }
  catch (e: any) {
    toastError(e, '加入家庭失败')
  }
  finally {
    familyBusy.value = false
  }
}

async function onLeaveFamily() {
  if (familyBusy.value)
    return
  familyBusy.value = true
  try {
    await leaveFamily()
    family.value = null
    toast('已退出家庭')
  }
  catch (e: any) {
    toastError(e, '退出家庭失败')
  }
  finally {
    familyBusy.value = false
  }
}

function copyInviteCode() {
  if (!family.value)
    return
  uni.setClipboardData({
    data: family.value.inviteCode,
    success: () => toast('邀请码已复制，发给家人'),
  })
}

/** 购买指定 SKU 的会员（月卡/年卡）；已是会员时即续费，时长由后端顺延。 */
async function purchase(sku: string) {
  const target = sku === MEMBER_YEAR_SKU ? yearProduct.value : product.value
  if (paying.value)
    return
  const isRenewal = active.value
  if (!userStore.isLoggedIn) {
    navigateToLogin()
    return
  }
  const ok = sku === MEMBER_YEAR_SKU ? yearPurchasable.value : purchasable.value
  if (!target || !ok) {
    toast(paymentSupported
      ? (target?.paymentUnavailableMsg || '会员暂不可开通')
      : '请在微信小程序内开通')
    return
  }
  paying.value = sku
  let checking = false
  try {
    const openid = await refreshWechatLoginForPayment()
    // 支付前请求「开通会员成功通知」授权：拒绝不影响支付，只是收不到订阅消息
    requestMemberNoticeAuthorization()
    const order = await createVirtualOrder(openid, target.sku)
    const params = await getVirtualPaymentParams(openid, order.orderNo)
    await requestWechatVirtualPayment(params)
    checking = true
    uni.showLoading({ title: '正在确认会员…', mask: true })
    const delivered = await waitForDelivery(order.orderNo)
    if (!delivered) {
      // 后端有定时查单对账+发货重试兜底；给出明确预期，避免"付了钱没反应"的焦虑
      toast('支付已完成，会员确认中——到账后自动生效，稍后重新进入本页即可')
      return
    }
    await refreshUserInfo(true)
    loadFamily()
    toastSuccess(isRenewal
      ? '续费成功，有效期已顺延'
      : (sku === MEMBER_YEAR_SKU ? '年卡已开通，全家都能用了' : '锅仔会员已开通'))
  }
  catch (e: any) {
    toastError(e, isRenewal ? '暂时无法续费' : '暂时无法开通会员')
  }
  finally {
    if (checking)
      uni.hideLoading()
    paying.value = ''
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
      <view><text>{{ benefitCount }}</text><text>项会员权益</text></view>
      <view><text>30</text><text>天持续陪伴</text></view>
      <view><text>∞</text><text>对话不限次数</text></view>
    </view>

    <view class="member-section-head">
      <text class="member-section-head__eyebrow">
        MEMBER BENEFITS
      </text>
      <text class="member-section-head__title">
        每项权益，解决一个真实麻烦
      </text>
    </view>

    <view v-for="group in benefitGroups" :key="group.key" class="member-group">
      <view class="member-group__head">
        <text class="member-group__eyebrow">
          {{ group.eyebrow }}
        </text>
        <text class="member-group__title">
          {{ group.title }}
        </text>
      </view>
      <view class="member-benefits">
        <view v-for="(item, index) in group.items" :key="item.title" class="member-benefit">
          <view class="member-benefit__icon">
            <text>{{ item.icon }}</text>
          </view>
          <view class="member-benefit__body">
            <view class="member-benefit__head">
              <text class="member-benefit__index">
                {{ String(index + 1).padStart(2, '0') }}
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
    </view>

    <view v-if="error" class="member-error" role="button" @click="load">
      {{ error }} · 点击重试
    </view>
    <!-- 家庭共享：一份会员，全家人用 -->
    <view v-if="familyLoaded" class="member-family">
      <view class="member-family__head">
        <text class="member-family__eyebrow">FAMILY</text>
        <text class="member-family__title">一家人，共用一份会员</text>
      </view>

      <template v-if="!family">
        <text class="member-family__copy">
          创建家庭，把邀请码发给家人。家人的会员权益全部通用，一份钱全家用。
        </text>
        <view class="member-family__actions">
          <view class="member-family__btn member-family__btn--primary" :class="{ 'is-disabled': familyBusy }" role="button" aria-label="创建家庭" @click="onCreateFamily">
            {{ familyBusy ? '创建中…' : '创建家庭' }}
          </view>
          <view class="member-family__join">
            <input v-model="joinCode" class="member-family__input" placeholder="输入家人邀请码" placeholder-class="member-family__ph" :maxlength="16" />
            <view class="member-family__btn" :class="{ 'is-disabled': familyBusy }" role="button" aria-label="加入家庭" @click="onJoinFamily">
              加入
            </view>
          </view>
        </view>
      </template>

      <template v-else>
        <view class="member-family__code" role="button" aria-label="复制邀请码" @click="copyInviteCode">
          <text class="member-family__code-label">邀请码</text>
          <text class="member-family__code-value">{{ family.inviteCode }}</text>
          <text class="member-family__code-copy">复制</text>
        </view>
        <view class="member-family__status" :class="{ 'is-on': family.myAccessActive }">
          {{ family.myAccessActive ? (family.myRole === 'OWNER' ? '你的会员权益生效中' : '你正在使用家人的会员权益') : '家庭创建者暂未开通会员，权益未生效' }}
        </view>
        <view class="member-family__members">
          <view v-for="m in family.members" :key="m.openid" class="member-family__member">
            <image class="member-family__avatar" :src="m.avatarUrl || `${STATIC_BASE_URL}/static/guozai/mood_01_happy.png`" mode="aspectFill" />
            <text class="member-family__name">{{ m.nickname }}</text>
            <text v-if="m.role === 'OWNER'" class="member-family__role">创建者</text>
            <text v-else-if="m.openid === userStore.openid" class="member-family__role member-family__role--me">我</text>
          </view>
        </view>
        <view class="member-family__leave" role="button" aria-label="退出家庭" @click="onLeaveFamily">
          {{ family.myRole === 'OWNER' ? '解散家庭' : '退出家庭' }}
        </view>
      </template>
    </view>

    <view class="member-note">
      基础推荐和饮食记录继续免费；会员购买的是更深度、更省心的持续服务。
      <text class="member-note__legal">
        ¥{{ price }} / 30 天，非自动续费；虚拟权益已使用不支持无理由退款，到账异常请通过「反馈建议」联系我们。
      </text>
    </view>
    <view class="member-safe-space" />

    <view class="member-footer">
      <view v-if="active" class="member-footer__top">
        <view class="member-footer__price">
          <text class="member-footer__expire">会员有效至 {{ expireText }}</text>
          <text class="member-footer__hint">随时续费，时长自动顺延</text>
        </view>
        <view class="member-footer__notice" role="button" aria-label="开启到账提醒" @click="enableRenewalNotice">
           到账提醒
        </view>
      </view>
      <view class="member-footer__plans">
        <view
          class="member-plan member-plan--year"
          :class="{ 'is-disabled': !yearPurchasable }"
          role="button"
          :aria-label="active ? '续费会员年卡' : '开通会员年卡'"
          @click="purchase(MEMBER_YEAR_SKU)"
        >
          <view class="member-plan__tag">全家共享 · 最划算</view>
          <view class="member-plan__price">
            <text class="member-plan__currency">¥</text>
            <text class="member-plan__amount">{{ yearPrice }}</text>
            <text class="member-plan__unit">/ 365 天</text>
          </view>
          <view class="member-plan__note">低至 ¥{{ yearDailyPrice }}/天 · 含家庭共享</view>
        </view>
        <view
          class="member-plan member-plan--month"
          :class="{ 'is-disabled': !purchasable }"
          role="button"
          :aria-label="active ? '续费会员月卡' : '开通会员月卡'"
          @click="purchase(MEMBER_PRODUCT_SKU)"
        >
          <view class="member-plan__price">
            <text class="member-plan__currency">¥</text>
            <text class="member-plan__amount">{{ price }}</text>
            <text class="member-plan__unit">/ 30 天</text>
          </view>
          <view class="member-plan__note">{{ active ? '续费 · 在现有效期上顺延 30 天' : '先试试' }}</view>
        </view>
      </view>
      <view v-if="paying" class="member-footer__paying">确认中…</view>
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
.member-group { margin-bottom: 36rpx; }
.member-group:last-of-type { margin-bottom: 0; }
.member-group__head { margin: 0 8rpx 16rpx; }
.member-group__eyebrow, .member-group__title { display: block; }
.member-group__eyebrow { color: var(--mrc-accent); font-size: 17rpx; font-weight: 850; letter-spacing: 3rpx; opacity: .82; }
.member-group__title { margin-top: 5rpx; color: var(--mrc-text-strong); font-size: 26rpx; font-weight: 850; }
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
.member-safe-space { height: 280rpx; }
.member-footer { position: fixed; z-index: 20; right: 0; bottom: 0; left: 0; display: flex; flex-direction: column; gap: 16rpx; padding: 20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom)); border-top: 2rpx solid var(--mrc-border-light); background: rgba(255, 248, 237, .96); box-shadow: 0 -12rpx 34rpx rgba(93, 54, 30, .08); }
.member-footer__top { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
.member-footer__price { display: flex; min-width: 0; align-items: baseline; gap: 14rpx; }
.member-footer__expire { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 900; }
.member-footer__hint { color: var(--mrc-text-sub); font-size: 19rpx; }
.member-footer__button { display: flex; min-height: 86rpx; flex: 1; align-items: center; justify-content: center; padding: 0 24rpx; border-radius: 28rpx; background: linear-gradient(135deg, #f0b45f, #e96b4a); color: #fff; font-size: 25rpx; font-weight: 850; box-shadow: 0 12rpx 24rpx rgba(219, 92, 61, .22); text-align: center; }
.member-footer__button.is-disabled { opacity: .62; box-shadow: none; }
@media (prefers-reduced-motion: reduce) { .member-footer__button { transition: none; } }
.member-footer__notice {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  padding: 10rpx 22rpx;
  border: 2rpx solid var(--mrc-border);
  border-radius: 999rpx;
  background: var(--mrc-surface);
  color: var(--mrc-accent);
  font-size: 20rpx;
  font-weight: 700;
}
.member-footer__notice:active { opacity: .7; }

/* 家庭共享 */
.member-family { margin: 24rpx 0 8rpx; padding: 26rpx 28rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 32rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }
.member-family__head { display: flex; align-items: baseline; gap: 12rpx; margin-bottom: 14rpx; }
.member-family__eyebrow { color: var(--mrc-accent); font-size: 18rpx; font-weight: 800; letter-spacing: 2rpx; }
.member-family__title { color: var(--mrc-text-strong); font-size: 30rpx; font-weight: 800; }
.member-family__copy { color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.55; }
.member-family__actions { display: flex; flex-direction: column; gap: 16rpx; margin-top: 18rpx; }
.member-family__btn { display: flex; min-height: 80rpx; align-items: center; justify-content: center; padding: 0 28rpx; border-radius: 22rpx; background: var(--mrc-surface-2); color: var(--mrc-text-deep); font-size: 26rpx; font-weight: 700; }
.member-family__btn--primary { background: var(--mrc-primary-grad); color: #fff; box-shadow: var(--mrc-shadow-coral); }
.member-family__btn.is-disabled { opacity: .6; pointer-events: none; }
.member-family__join { display: flex; align-items: center; gap: 14rpx; }
.member-family__input { flex: 1; height: 80rpx; padding: 0 22rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 22rpx; background: var(--mrc-surface-2); color: var(--mrc-text-deep); font-size: 26rpx; letter-spacing: 4rpx; text-transform: uppercase; }
.member-family__ph { color: var(--mrc-text-light); letter-spacing: 0; }
.member-family__code { display: flex; align-items: center; gap: 16rpx; margin-top: 6rpx; padding: 18rpx 24rpx; border: 2rpx dashed var(--mrc-border-strong); border-radius: 22rpx; background: var(--mrc-surface-sun); }
.member-family__code-label { color: var(--mrc-text-sub); font-size: 22rpx; }
.member-family__code-value { flex: 1; color: var(--mrc-text-strong); font-size: 36rpx; font-weight: 800; letter-spacing: 6rpx; }
.member-family__code-copy { color: var(--mrc-accent); font-size: 22rpx; font-weight: 700; }
.member-family__status { margin-top: 14rpx; padding: 12rpx 20rpx; border-radius: 16rpx; background: var(--mrc-surface-2); color: var(--mrc-text-sub); font-size: 21rpx; }
.member-family__status.is-on { background: var(--mrc-accent-soft); color: var(--mrc-accent); font-weight: 700; }
.member-family__members { display: flex; flex-wrap: wrap; gap: 20rpx; margin-top: 18rpx; }
.member-family__member { display: flex; flex-direction: column; align-items: center; gap: 6rpx; width: 108rpx; }
.member-family__avatar { width: 84rpx; height: 84rpx; border-radius: 50%; border: 3rpx solid var(--mrc-surface-peach); background: var(--mrc-surface-2); }
.member-family__name { max-width: 104rpx; overflow: hidden; color: var(--mrc-text-deep); font-size: 20rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.member-family__role { padding: 2rpx 12rpx; border-radius: 999rpx; background: var(--mrc-surface-sun); color: var(--mrc-accent); font-size: 16rpx; font-weight: 700; }
.member-family__role--me { background: var(--mrc-surface-2); color: var(--mrc-text-sub); }
.member-family__leave { margin-top: 16rpx; color: var(--mrc-text-light); font-size: 21rpx; text-align: center; }

/* 底部双 SKU：占满一行，价格不换行，触控高度 ≥ 88rpx */
.member-footer__plans { display: flex; align-items: stretch; gap: 16rpx; }
.member-plan { position: relative; display: flex; min-height: 118rpx; flex: 1; flex-direction: column; justify-content: center; padding: 16rpx 22rpx; border-radius: 24rpx; background: var(--mrc-surface-2); }
.member-plan--year { flex: 1.35; background: linear-gradient(135deg, #f0b45f, #e96b4a); box-shadow: 0 12rpx 24rpx rgba(219, 92, 61, .22); }
.member-plan--month { flex: 1; }
.member-plan__tag { align-self: flex-start; margin-bottom: 6rpx; padding: 3rpx 12rpx; border-radius: 999rpx; background: rgba(255, 255, 255, .9); color: #c2532f; font-size: 16rpx; font-weight: 800; }
.member-plan__price { display: flex; align-items: baseline; gap: 4rpx; white-space: nowrap; }
.member-plan__currency { color: #fff; font-size: 24rpx; font-weight: 700; }
.member-plan__amount { color: #fff; font-size: 46rpx; font-weight: 900; line-height: 1; }
.member-plan__unit { color: rgba(255, 255, 255, .85); font-size: 20rpx; }
.member-plan--month .member-plan__currency { color: var(--mrc-text-deep); }
.member-plan--month .member-plan__amount { color: var(--mrc-text-deep); }
.member-plan--month .member-plan__unit { color: var(--mrc-text-sub); }
.member-plan__note { margin-top: 6rpx; overflow: hidden; color: rgba(255, 255, 255, .9); font-size: 18rpx; line-height: 1.3; text-overflow: ellipsis; white-space: nowrap; }
.member-plan--month .member-plan__note { color: var(--mrc-text-sub); }
.member-plan.is-disabled { opacity: .6; pointer-events: none; }
.member-footer__paying { text-align: center; color: var(--mrc-text-sub); font-size: 24rpx; }
</style>
