<script setup lang="ts">
import { navBack } from '@/composables/useNavBar'
import { ref, computed } from 'vue'
import LoadingState from '../../components/guozai/LoadingState.vue'
import EmptyState from '../../components/guozai/EmptyState.vue'
import { ensureLogin } from '../../utils/login'
import { deleteRecord, fetchRecords, type RecordItem } from '../../api/records'

definePage({ name: 'timeline', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '菜谱时光机' } })
const router = useRouter()
const loading = ref(true)
const records = ref<RecordItem[]>([])
const nearToday = ref(false)
const selected = ref<RecordItem | null>(null)
let lastTick = 0

const grouped = computed(() => {
  const map = new Map<string, RecordItem[]>()
  records.value.forEach(item => {
    const key = item.recordDate?.slice(0, 7) || '过去的日子'
    map.set(key, [...(map.get(key) || []), item])
  })
  return [...map.entries()].map(([month, items]) => ({ month, items }))
})
async function load() {
  loading.value = true
  try { records.value = await fetchRecords(await ensureLogin()) } finally { loading.value = false }
}
function onScroll(e: any) {
  const top = Number(e.detail?.scrollTop || 0)
  const now = Date.now()
  const next = top < 40
  if (next !== nearToday.value && now - lastTick > 500) {
    nearToday.value = next; lastTick = now
    try { uni.vibrateShort({ type: 'light' }) } catch {}
  }
}
onShow(load)
async function removeSelected() {
  if (!selected.value) return
  const choice = await uni.showModal({ title: '删除这条记录？', content: '删除后无法恢复。', confirmColor: '#D94A43' })
  if (!choice.confirm) return
  await deleteRecord(selected.value.id, await ensureLogin())
  records.value = records.value.filter(item => item.id !== selected.value?.id)
  selected.value = null
  uni.showToast({ title: '记录已删除', icon: 'success' })
}
</script>

<template>
  <view class="timeline-page">
    <wd-navbar title="菜谱时光机" left-arrow safe-area-inset-top @click-left="navBack"  custom-style="background-color: transparent !important;" />
    <LoadingState v-if="loading" text="锅仔正在翻找你的餐桌回忆…" />
    <EmptyState v-else-if="!records.length" image="/static/guozai/action_07_empty.png" title="时光机还是空的" text="记录第一餐，让锅仔替你把今天收好。" action-text="去记录" @action="router.pushTab({ name: 'record' })" />
    <scroll-view v-else scroll-y class="timeline-scroll" @scroll="onScroll">
      <view class="timeline-hero">
        <view><text class="timeline-kicker">GUOZAI TIME MACHINE</text><text class="timeline-title">你的每一餐，<br />都值得被记住。</text><text class="timeline-sub">向下滑回到更久以前，向上回到今天。</text></view>
        <image class="timeline-hero__img" src="/static/guozai/action_04_calendar.png" mode="aspectFit" />
      </view>
      <view class="today-chip" :class="{ 'today-chip--active': nearToday }"><text>{{ nearToday ? '已回到今天' : '继续向上，回到今天' }}</text></view>
      <view v-for="group in grouped" :key="group.month" class="timeline-group">
        <view class="timeline-month"><text>{{ group.month }}</text><view /></view>
        <view v-for="item in group.items" :key="item.id" class="timeline-item">
          <view class="timeline-dot" />
          <text class="timeline-date">{{ item.recordDate?.slice(5) }}</text>
          <view class="timeline-card" @click="selected=item">
            <image class="timeline-card__img" :src="item.imageUrl" mode="aspectFill" />
            <view class="timeline-card__body"><text class="timeline-card__dish">{{ item.dishName }}</text><text class="timeline-card__mood">{{ item.moodTag }} · {{ item.cookingTime || 30 }} 分钟</text><text v-if="item.note" class="timeline-card__note">{{ item.note }}</text></view>
          </view>
        </view>
      </view>
      <view class="timeline-end"><image src="/static/guozai/action_08_peek.png" mode="aspectFit" /><text>再往下，就是更久以前的你啦。</text></view>
    </scroll-view>
    <view v-if="selected" class="detail-mask" @click.self="selected=null"><view class="detail-sheet"><image :src="selected.imageUrl" mode="aspectFill"/><text class="detail-title">{{selected.dishName}}</text><text class="detail-meta">{{selected.recordDate}} · {{selected.moodTag}}</text><text v-if="selected.note" class="detail-note">{{selected.note}}</text><view class="detail-delete" @click="removeSelected">删除这条记录</view><view class="detail-close" @click="selected=null">收起</view></view></view>
  </view>
</template>

<style lang="scss" scoped>
.timeline-page{height:100vh;background:var(--mrc-bg)}.timeline-scroll{height:calc(100vh - 120rpx);padding:0 32rpx 80rpx;box-sizing:border-box}.timeline-hero{display:flex;min-height:300rpx;margin:12rpx 0 20rpx;padding:36rpx 32rpx;box-sizing:border-box;border-radius:36rpx;background:linear-gradient(135deg,var(--mrc-surface-peach),var(--mrc-surface-sun));border:2rpx solid var(--mrc-border);overflow:hidden}.timeline-kicker{display:block;color:var(--mrc-accent);font-size:19rpx;font-weight:800;letter-spacing:2rpx}.timeline-title{display:block;margin-top:16rpx;color:var(--mrc-text-deep);font-size:40rpx;font-weight:800;line-height:1.35}.timeline-sub{display:block;margin-top:12rpx;color:var(--mrc-text-sub);font-size:23rpx}.timeline-hero__img{width:220rpx;height:220rpx;margin:auto -44rpx -20rpx 0}.today-chip{display:flex;justify-content:center;margin:0 auto 32rpx;padding:12rpx 24rpx;border-radius:30rpx;background:var(--mrc-surface);color:var(--mrc-text-sub);font-size:23rpx}.today-chip--active{background:var(--mrc-accent-soft);color:var(--mrc-accent);font-weight:700}.timeline-group{position:relative;padding-bottom:16rpx}.timeline-month{display:flex;align-items:center;gap:16rpx;margin:0 0 20rpx 84rpx;color:var(--mrc-text-deep);font-size:30rpx;font-weight:800}.timeline-month view{flex:1;height:2rpx;background:var(--mrc-border-light)}.timeline-item{position:relative;display:grid;grid-template-columns:72rpx 70rpx 1fr;gap:12rpx;padding-bottom:20rpx}.timeline-item::before{content:'';position:absolute;left:34rpx;top:30rpx;bottom:-10rpx;width:3rpx;background:var(--mrc-border-light)}.timeline-item:last-child::before{bottom:10rpx}.timeline-dot{width:18rpx;height:18rpx;margin:12rpx auto 0;border-radius:50%;background:var(--mrc-primary);box-shadow:0 0 0 8rpx var(--mrc-accent-soft);z-index:1}.timeline-date{padding-top:4rpx;color:var(--mrc-text-sub);font-size:23rpx}.timeline-card{overflow:hidden;border:2rpx solid var(--mrc-border-light);border-radius:26rpx;background:var(--mrc-surface);box-shadow:var(--mrc-shadow-soft)}.timeline-card__img{width:100%;height:260rpx}.timeline-card__body{padding:20rpx 20rpx 24rpx}.timeline-card__dish{display:block;color:var(--mrc-text-deep);font-size:31rpx;font-weight:800}.timeline-card__mood{display:block;margin-top:8rpx;color:var(--mrc-accent);font-size:22rpx;font-weight:700}.timeline-card__note{display:block;margin-top:12rpx;color:var(--mrc-text-sub);font-size:23rpx;line-height:1.5}.timeline-end{display:flex;flex-direction:column;align-items:center;padding:32rpx;color:var(--mrc-text-sub);font-size:23rpx}.timeline-end image{width:120rpx;height:120rpx}.detail-mask{position:fixed;inset:0;z-index:100;display:flex;align-items:flex-end;background:rgba(54,39,29,.48)}.detail-sheet{width:100%;padding:32rpx 32rpx calc(40rpx + env(safe-area-inset-bottom));box-sizing:border-box;border-radius:36rpx 36rpx 0 0;background:var(--mrc-bg)}.detail-sheet image{width:100%;height:360rpx;border-radius:24rpx}.detail-title,.detail-meta,.detail-note{display:block}.detail-title{margin-top:22rpx;font-size:38rpx;font-weight:800;color:var(--mrc-text-deep)}.detail-meta,.detail-note{margin-top:10rpx;color:var(--mrc-text-sub);font-size:25rpx}.detail-note{line-height:1.6}.detail-delete,.detail-close{min-height:88rpx;margin-top:24rpx;border-radius:44rpx;display:flex;align-items:center;justify-content:center;font-size:29rpx;font-weight:700}.detail-delete{border:2rpx solid #e89a93;color:#c94b45}.detail-close{margin-top:14rpx;background:var(--mrc-primary-grad);color:#fff}
</style>
