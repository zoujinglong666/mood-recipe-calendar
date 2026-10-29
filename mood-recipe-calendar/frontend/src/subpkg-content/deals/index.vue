<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { navBack } from '@/composables/useNavBar'
import { fetchDeals, fetchDealLink, type DealActivity, type DealGroup } from '../../api/deals'
import { toast, toastError, toastSuccess } from '../../utils/toast'
import LoadingState from '../../components/guozai/LoadingState.vue'
import EmptyState from '../../components/guozai/EmptyState.vue'

definePage({ name: 'deals', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '天天优惠' } })

const loading = ref(true)
const unavailable = ref(false)
const groups = ref<DealGroup[]>([])
const opening = ref(-1)

// ==== 官方图标：来自聚推客文档页（img.jutuike.com CDN，https） ====
const ICON_CDN = 'https://img.jutuike.com/taokeout/icon/'

/** 已知活动 → 官方图标（act_id 来自文档页 /doc/48?act_id=N 链接） */
const ACT_ICONS: Record<number, string> = {
  1: 'meituan.png', // 美团外卖红包
  9: 'chwl.png', // 美团到店吃喝玩乐
  28: 'hotal.png', // 美团酒店
  3: 'elmkb_icon.png', // 饿了么天天领红包
  80: 'elmkb_icon.png', // 饿了么赚现金
  133: 'shengxian.png', // 饿了么21城消费日
  42: 'didi_dc_icon.png', // 滴滴打车
  49: 'jtk_hxz_icon.png', // 花小猪
  87: 'tcyl_icon.png', // 同程打车
  53: 'didi_daijia_jtk.png', // 滴滴代驾
  91: 'feizhu_train_jtk.png', // 飞猪火车票
  38: 'kfc.png', // 肯德基
  31: 'pagoda.png', // 百果园
  32: 'nayuki.png', // 奈雪
  33: 'luckin.png', // 瑞幸
  34: 'spk.png', // 星巴克
  37: 'heytea.png', // 喜茶
  46: 'burgerking.png', // 汉堡王
  64: 'pizzahut_jtk_icon.png', // 必胜客
  57: 'jhdc_icon.png', // 聚合在线点餐
  111: 'dpcq.png', // 大牌餐券
  76: 'movie.png', // 电影票
  52: 'kuailon_icon.png', // 特价快递
}

/** 分类级兜底图标：API 没带 icon 且不在 ACT_ICONS 时用 */
const GROUP_ICONS: Record<string, string> = {
  '美团': 'meituan.png',
  '饿了么': 'elmkb_icon.png',
  '连锁餐饮': 'jhdc_icon.png',
  '打车出行': 'didi_dc_icon.png',
  '电影票': 'movie.png',
  '特惠酒店': 'hotal.png',
  '快递优惠': 'kuailon_icon.png',
}

/**
 * 图标候选链：官方 act_id 图标 → API 返回 icon → 分类图标。
 * @error 按链递进，全部失败才显示分类首字占位（无 emoji）。
 */
function iconCandidates(item: DealActivity, category: string): string[] {
  const list = [
    ACT_ICONS[item.actId] ? ICON_CDN + ACT_ICONS[item.actId] : '',
    item.icon,
    GROUP_ICONS[category] ? ICON_CDN + GROUP_ICONS[category] : '',
  ]
  return [...new Set(list.filter(Boolean))]
}

/** 图标加载进度：actId → 当前候选下标 */
const iconStage = ref<Record<number, number>>({})

function currentIcon(item: DealActivity, category: string): string {
  return iconCandidates(item, category)[iconStage.value[item.actId] || 0] || ''
}

function onIconError(item: DealActivity, category: string) {
  const next = (iconStage.value[item.actId] || 0) + 1
  if (next < iconCandidates(item, category).length)
    iconStage.value = { ...iconStage.value, [item.actId]: next }
  else
    iconStage.value = { ...iconStage.value, [item.actId]: 999 } // 全部失败 → 占位字
}

onMounted(async () => {
  try {
    groups.value = await fetchDeals()
  }
  catch (e: any) {
    // 503 = 后端未配置聚推客；其它错误按加载失败处理
    unavailable.value = Number(e?.code) === 503 || String(e?.message || '').includes('暂未开启')
    if (!unavailable.value)
      toastError(e, '优惠加载失败，请稍后再试')
  }
  finally {
    loading.value = false
  }
})

/**
 * 领取：后端转链 → 优先直跳第三方小程序（wx.navigateToMiniProgram，用户点击触发符合平台要求，
 * 跳转前有系统确认弹窗）→ 目标小程序拒跳/用户取消/无小程序信息时降级复制 H5 链接引导粘贴打开。
 * [2026-10-28 更正] 个人主体可以跳第三方小程序，之前的「不能跳」是误区。
 */
async function openDeal(item: DealActivity) {
  if (opening.value >= 0)
    return
  opening.value = item.actId
  try {
    const link = await fetchDealLink(item.actId)
    // 优先直跳：转链返回的 we_app_info（美团/饿了么等官方小程序路径，含跟单参数）
    if (link.miniAppId && link.miniPath) {
      try {
        await new Promise<void>((resolve, reject) => {
          uni.navigateToMiniProgram({
            appId: link.miniAppId,
            path: link.miniPath,
            success: () => resolve(),
            fail: (err) => reject(new Error(err?.errMsg || '跳转失败')),
          })
        })
        return
      }
      catch (jumpErr: any) {
        // 用户在系统确认弹窗点了取消：尊重意愿，不降级复制
        if (String(jumpErr?.message || '').includes('cancel'))
          return
        // 其余失败（目标小程序拒跳/环境不支持）→ 落到复制 H5 兜底
      }
    }
    const url = link.h5 || link.longH5
    if (!url) {
      toast('该活动暂不支持领取')
      return
    }
    await new Promise<void>((resolve, reject) => {
      uni.setClipboardData({ data: url, showToast: false, success: () => resolve(), fail: () => reject(new Error('复制失败')) })
    })
    toastSuccess('领取链接已复制')
    uni.showModal({
      title: link.actName || item.name,
      content: '链接已复制。打开微信任意聊天，粘贴发送后点击即可领券下单。',
      confirmText: '知道了',
      showCancel: false,
    })
  }
  catch (e: any) {
    toastError(e, '领取失败，请稍后再试')
  }
  finally {
    opening.value = -1
  }
}
</script>

<template>
  <view class="deals-page">
    <wd-navbar title="天天优惠" left-arrow safe-area-inset-top @click-left="navBack" custom-style="background-color: transparent !important;" />

    <LoadingState v-if="loading" text="锅仔正在翻找今天的优惠…" />
    <EmptyState
      v-else-if="unavailable"
      text="优惠休息中"
      subtext="这项服务还没开张，过段时间再来看看。"
    />
    <EmptyState
      v-else-if="!groups.length"
      text="今天暂无优惠"
      subtext="锅仔盯着呢，有红包马上告诉你。"
    />
    <scroll-view v-else scroll-y enhanced :show-scrollbar="false" class="deals-scroll">
      <view class="deals-hero">
        <text class="deals-hero__title">领券再下单</text>
        <text class="deals-hero__sub">外卖红包、打车券，省一点是一点</text>
      </view>
      <view v-for="group in groups" :key="group.category" class="deals-group">
        <view class="deals-group__head">
          <text class="deals-group__name">{{ group.category }}</text>
          <text class="deals-group__count">{{ group.activities.length }} 个优惠</text>
        </view>
        <view
          v-for="item in group.activities"
          :key="item.actId"
        class="deals-row pressable"
        role="button"
        :aria-label="`领取${item.name}`"
        @click="openDeal(item)"
      >
        <image
          v-if="currentIcon(item, group.category)"
          :src="currentIcon(item, group.category)"
          class="deals-row__icon"
          mode="aspectFit"
          @error="onIconError(item, group.category)"
        />
        <view v-else class="deals-row__icon deals-row__icon--text">{{ group.category.slice(0, 1) }}</view>
        <view class="deals-row__main">
          <text class="deals-row__name">{{ item.name }}</text>
          <text v-if="item.desc" class="deals-row__desc">{{ item.desc }}</text>
        </view>
        <view class="deals-row__btn" :class="{ 'is-loading': opening === item.actId }">
          {{ opening === item.actId ? '领取中' : '去领取' }}
        </view>
        </view>
      </view>
      <text class="deals-note">优惠由合作平台提供，领取后请以平台页面为准</text>
    </scroll-view>
  </view>
</template>

<style lang="scss" scoped>
.deals-page {
  min-height: 100vh;
  padding: 0 32rpx calc(40rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}
.deals-scroll { height: calc(100vh - 220rpx); }

.deals-hero {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  padding: 20rpx 8rpx 24rpx;
}
.deals-hero__title { color: var(--mrc-text-strong); font-size: 36rpx; font-weight: var(--mrc-fw-heavy); }
.deals-hero__sub { color: var(--mrc-text-sub); font-size: 22rpx; }

.deals-group { margin-bottom: 8rpx; }
.deals-group__head { display: flex; align-items: baseline; gap: 12rpx; padding: 10rpx 8rpx 14rpx; }
.deals-group__name { color: var(--mrc-text-strong); font-size: 27rpx; font-weight: var(--mrc-fw-heavy); }
.deals-group__count { color: var(--mrc-text-light); font-size: 20rpx; }

.deals-row {
  display: flex;
  min-height: 128rpx;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx 26rpx;
  box-sizing: border-box;
  margin-bottom: 14rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 30rpx;
  background: var(--mrc-surface);
  box-shadow: var(--mrc-shadow-soft);
  transition: transform 0.15s ease;
}
.deals-row:active { transform: scale(.98); }
.deals-row__icon { width: 84rpx; height: 84rpx; flex-shrink: 0; border-radius: 22rpx; background: var(--mrc-surface-2); }
.deals-row__icon--text { display: flex; align-items: center; justify-content: center; color: #fff; background: linear-gradient(135deg, var(--mrc-surface-sun), var(--mrc-surface-peach)); font-size: 36rpx; font-weight: 800; }
.deals-row__main { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 4rpx; }
.deals-row__name { overflow: hidden; color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 800; text-overflow: ellipsis; white-space: nowrap; }
.deals-row__desc { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; color: var(--mrc-text-sub); font-size: 21rpx; line-height: 1.4; }
.deals-row__btn { flex-shrink: 0; padding: 14rpx 28rpx; border-radius: 999rpx; color: #fff; background: var(--mrc-primary-grad, #EF5A3C); font-size: 24rpx; font-weight: 800; box-shadow: var(--mrc-shadow-sm); }
.deals-row__btn.is-loading { opacity: .6; }

.deals-note { display: block; padding: 16rpx 8rpx 0; color: var(--mrc-text-light); font-size: 20rpx; text-align: center; }

.pressable { transition: transform 180ms ease, opacity 180ms ease; }
.pressable:active { transform: scale(.97); }
@media (prefers-reduced-motion: reduce) { .pressable { transition: none; } .pressable:active { transform: none; } }
</style>
