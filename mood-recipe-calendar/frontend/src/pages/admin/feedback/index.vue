<script setup lang="ts">
/**
 * 用户反馈中心（仅管理员可见）：入口在「设置 → 管理后台」，
 * 非管理员即使直接进页面也会被后端 403 拦下（列表拉取失败即显示无权限）。
 */
import { computed, ref } from 'vue'
import { navBack } from '@/composables/useNavBar'
import { adminFeedbackList, adminFeedbackSetStatus, type AdminFeedback } from '@/api/admin'
import { toast, toastError } from '@/utils/toast'

definePage({
  name: 'admin-feedback',
  layout: 'default',
  style: { navigationStyle: 'custom', navigationBarTitleText: '用户反馈中心' },
})

const STATUS_LABELS: Record<string, string> = {
  OPEN: '已收到',
  PROCESSING: '处理中',
  RESOLVED: '已完成',
  CLOSED: '已关闭',
}
const FILTERS: Array<{ key: string, label: string }> = [
  { key: '', label: '全部' },
  { key: 'OPEN', label: '已收到' },
  { key: 'PROCESSING', label: '处理中' },
  { key: 'RESOLVED', label: '已完成' },
  { key: 'CLOSED', label: '已关闭' },
]
const PAGE_SIZE = 20

const activeFilter = ref('')
const list = ref<AdminFeedback[]>([])
const counts = ref<Record<string, number>>({})
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const denied = ref(false)
/** 正在流转状态的反馈 id，防止连点 */
const pendingId = ref(0)

const filterChips = computed(() => FILTERS.map(f => ({
  ...f,
  count: f.key === '' ? (counts.value.ALL ?? 0) : (counts.value[f.key] ?? 0),
})))

function statusLabel(status: string) {
  return STATUS_LABELS[status] || status
}

function formatTime(value: string) {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 16)
}

function openidTail(openid: string) {
  return openid && openid.length > 6 ? `…${openid.slice(-6)}` : openid
}

async function load(reset = false) {
  if (loading.value) return
  if (reset) {
    page.value = 1
    finished.value = false
    list.value = []
  }
  if (finished.value) return
  loading.value = true
  try {
    const res = await adminFeedbackList({ status: activeFilter.value || undefined, page: page.value, size: PAGE_SIZE })
    counts.value = res.counts || {}
    total.value = res.total || 0
    list.value.push(...(res.list || []))
    if (list.value.length >= total.value) finished.value = true
    else page.value += 1
    denied.value = false
  }
  catch (e: any) {
    if (page.value === 1) {
      // 403=非管理员；其它错误给重试提示
      denied.value = true
      toastError(e, '加载失败，请稍后重试')
    }
    else {
      toast('已经到底啦')
      finished.value = true
    }
  }
  finally {
    loading.value = false
  }
}

function switchFilter(key: string) {
  if (activeFilter.value === key) return
  activeFilter.value = key
  load(true)
}

/** 状态流转动作：按当前状态给下一步，RESOLVED/CLOSED 可重开 */
const ACTIONS: Array<{ to: string, label: string, ghost?: boolean }> = [
  { to: 'PROCESSING', label: '开始处理' },
  { to: 'RESOLVED', label: '标记完成' },
  { to: 'CLOSED', label: '关闭', ghost: true },
  { to: 'OPEN', label: '重新打开', ghost: true },
]
function actionsFor(item: AdminFeedback) {
  return ACTIONS.filter(a => a.to !== item.status)
}

async function setStatus(item: AdminFeedback, status: string) {
  if (pendingId.value) return
  pendingId.value = item.id
  try {
    const updated = await adminFeedbackSetStatus(item.id, status)
    const previous = item.status
    Object.assign(item, updated)
    // [2026-10-28] 计数本地更新，不再每次操作重拉列表——此前每次点击发 2 个请求，
    // 连续快速操作会撞敏感桶（/api/admin 按防爆破配额计数，生产环境曾配 20/分钟），
    // 触发"请求过于频繁"。ALL 计数不变（状态流转不增减总数）。
    if (previous !== status) {
      const next = { ...counts.value }
      next[previous] = Math.max(0, (next[previous] || 0) - 1)
      next[status] = (next[status] || 0) + 1
      counts.value = next
    }
    toast(`已标记为「${statusLabel(status)}」`)
  }
  catch (e: any) {
    toastError(e, '状态更新失败')
  }
  finally {
    pendingId.value = 0
  }
}

load(true)
</script>

<template>
  <view class="admin-page">
    <wd-navbar title="用户反馈中心" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <view class="admin-content">
      <!-- 状态筛选 chips：带各状态条数 -->
      <view class="filter-row">
        <button
          v-for="chip in filterChips"
          :key="chip.key"
          class="filter-chip"
          :class="{ 'filter-chip--active': activeFilter === chip.key }"
          @click="switchFilter(chip.key)"
        >
          {{ chip.label }} {{ chip.count }}
        </button>
      </view>

      <view v-if="denied" class="empty-card">
        <text class="empty-title">无权限或加载失败</text>
        <text class="empty-sub">反馈中心仅管理员可用；网络问题可下拉重试</text>
      </view>

      <view v-else-if="!list.length && !loading" class="empty-card">
        <text class="empty-title">暂无反馈</text>
        <text class="empty-sub">用户在「我的 → 反馈建议」提交的内容会出现在这里</text>
      </view>

      <view v-for="item in list" :key="item.id" class="fb-card">
        <view class="fb-card__head">
          <text class="fb-card__cat">{{ item.category }}</text>
          <text class="fb-card__status" :class="`is-${item.status.toLowerCase()}`">{{ statusLabel(item.status) }}</text>
        </view>
        <text class="fb-card__content">{{ item.content }}</text>
        <view class="fb-card__meta">
          <text>#{{ item.id }} · {{ openidTail(item.openid) }}</text>
          <text v-if="item.contact">联系：{{ item.contact }}</text>
          <text>{{ formatTime(item.createdAt) }}</text>
        </view>
        <view class="fb-card__actions">
          <button
            v-for="action in actionsFor(item)"
            :key="action.to"
            class="fb-btn"
            :class="{ 'fb-btn--ghost': action.ghost }"
            :disabled="pendingId === item.id"
            @click="setStatus(item, action.to)"
          >
            {{ action.label }}
          </button>
        </view>
      </view>

      <view v-if="loading" class="list-tip">加载中…</view>
      <view v-else-if="!finished && list.length" class="list-tip list-tip--more" @click="load()">加载更多</view>
      <view v-else-if="finished && list.length" class="list-tip">共 {{ total }} 条，已经到底啦</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.admin-page { min-height: 100vh; background: var(--mrc-bg); }
.admin-content { padding: 20rpx 28rpx calc(48rpx + env(safe-area-inset-bottom)); }
.filter-row { display: flex; flex-wrap: wrap; gap: 12rpx; margin-bottom: 20rpx; }
.filter-chip { padding: 10rpx 22rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 999rpx; background: var(--mrc-surface); color: var(--mrc-text-sub); font-size: 22rpx; font-weight: 700; }
.filter-chip::after { border: none; }
.filter-chip--active { border-color: var(--mrc-accent); background: var(--mrc-accent-soft); color: var(--mrc-accent); }
.fb-card { margin-bottom: 18rpx; padding: 22rpx 24rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 24rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.fb-card__head { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
.fb-card__cat { color: var(--mrc-text-strong); font-size: 26rpx; font-weight: 800; }
.fb-card__status { padding: 4rpx 16rpx; border-radius: 999rpx; background: var(--mrc-surface-2); color: var(--mrc-text-sub); font-size: 19rpx; font-weight: 700; }
.fb-card__status.is-open { background: var(--mrc-accent-soft); color: var(--mrc-accent); }
.fb-card__status.is-processing { background: #EDF3FF; color: #3B6FD4; }
.fb-card__status.is-resolved { background: #EAF7EE; color: #2E8B57; }
.fb-card__status.is-closed { background: var(--mrc-surface-2); color: var(--mrc-text-light); }
.fb-card__content { display: block; margin-top: 12rpx; color: var(--mrc-text-deep); font-size: 24rpx; line-height: 1.5; }
.fb-card__meta { display: flex; flex-wrap: wrap; gap: 16rpx; margin-top: 12rpx; color: var(--mrc-text-light); font-size: 19rpx; }
.fb-card__actions { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 16rpx; }
.fb-btn { padding: 8rpx 24rpx; border-radius: 999rpx; background: var(--mrc-primary-grad); color: #fff; font-size: 21rpx; font-weight: 700; }
.fb-btn::after { border: none; }
.fb-btn[disabled] { opacity: 0.5; color: #fff; }
.fb-btn--ghost { background: var(--mrc-surface); color: var(--mrc-text-deep); border: 2rpx solid var(--mrc-border-light); }
.empty-card { display: flex; flex-direction: column; align-items: center; gap: 10rpx; padding: 80rpx 40rpx; border: 2rpx dashed var(--mrc-border-light); border-radius: 24rpx; }
.empty-title { color: var(--mrc-text-deep); font-size: 26rpx; font-weight: 800; }
.empty-sub { color: var(--mrc-text-light); font-size: 21rpx; text-align: center; }
.list-tip { padding: 20rpx 0; text-align: center; color: var(--mrc-text-light); font-size: 21rpx; }
.list-tip--more { color: var(--mrc-accent); font-weight: 700; }
</style>
