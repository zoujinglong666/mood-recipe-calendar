<script setup lang="ts">
import { useImagePreview } from '@wot-ui/ui'
import { computed, ref } from 'vue'
import { deleteRecord, fetchRecord, type RecordItem } from '@/api/records'
import EmptyState from '@/components/guozai/EmptyState.vue'
import GuozaiButton from '@/components/guozai/GuozaiButton.vue'
import LoadingState from '@/components/guozai/LoadingState.vue'
import { navBack } from '@/composables/useNavBar'
import { ensureLogin } from '@/utils/login'
import { toastError, toastSuccess } from '@/utils/toast'

definePage({ name: 'record-detail', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '这一餐的食光' } })

const router = useRouter()
const route = useRoute()
const { previewImage } = useImagePreview()
const loading = ref(true)
const record = ref<RecordItem>()
const photos = computed(() => record.value?.imageUrls?.length ? record.value.imageUrls : (record.value?.imageUrl ? [record.value.imageUrl] : []))

function photoClass() {
  return `record-detail__photos--${Math.min(photos.value.length, 9)}`
}

function preview(index: number) {
  if (!photos.value.length) return
  previewImage({ images: photos.value, startPosition: index, closeOnClick: true, loop: photos.value.length > 1 })
}

function edit() {
  if (!record.value) return
  // 注意：@wot-ui/router 在 name 跳转时会丢弃 query 字段，必须用 params（它会被拼成 URL 查询串）
  router.push({ name: 'record-edit', params: { id: String(record.value.id) } })
}

async function remove() {
  if (!record.value) return
  const confirmation = await uni.showModal({ title: '删除这条记录？', content: '删除后无法恢复。', confirmColor: '#D94A43' })
  if (!confirmation.confirm) return
  try {
    await ensureLogin()
    await deleteRecord(record.value.id)
    toastSuccess('记录已删除')
    router.back()
  }
  catch (e: any) { toastError(e, '删除失败') }
}

/**
 * 记录 ID 改为从 uni-app 原生 onLoad 的 options 取，不再只依赖 router 的 query。
 *
 * 原因（本页曾误报「记录不存在」的根因）：
 * @wot-ui/router 在 name 跳转时把 params 当作 query（query 字段会被丢弃），
 * 且 route.query 是用 page.$page?.fullPath 反解出来的，fullPath 不带查询串时
 * 解析结果为空对象；另外 syncRouteFromPage 在 path 相同时会直接 return 不更新 query。
 * 几者叠加导致 route.query.id 取不到。onLoad(options) 由小程序原生保证，最可靠。
 */
const recordId = ref(0)
onLoad((options: any) => {
  const raw = options?.id ?? (route as any)?.query?.id ?? (route as any)?.params?.id
  recordId.value = Number(raw)
})

async function load() {
  const id = recordId.value
  if (!id) {
    toastError(new Error('记录不存在'), '打不开这条记录')
    router.back()
    return
  }
  loading.value = true
  try { record.value = await fetchRecord(id) }
  catch (e: any) { toastError(e, '记录加载失败') }
  finally { loading.value = false }
}

onShow(load)
</script>

<template>
  <view class="record-detail-page mrc-hero">
    <wd-navbar title="这一餐的食光" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />
    <LoadingState v-if="loading" text="锅仔正在打开这段食光…" />
    <EmptyState v-else-if="!record" title="这条记录不见了" text="可能已被删除，回到时光机看看其他回忆吧。" action-text="回到时光机" @action="router.push({ name: 'timeline' })" />
    <template v-else>
      <view class="record-detail__intro">
        <view class="record-detail__intro-top">
          <text class="record-detail__eyebrow">FOOD MEMORY</text>
          <text class="record-detail__mood">{{ record.moodTag || '这一餐' }}</text>
        </view>
        <text class="record-detail__title">{{ record.dishName }}</text>
        <view class="record-detail__meta"><text>{{ record.recordDate }}</text><text class="record-detail__meta-dot">·</text><text>{{ record.cookingTime || 30 }} 分钟</text></view>
      </view>
      <view class="record-detail__photos" :class="photoClass()">
        <image v-for="(photo, index) in photos" :key="photo" :src="photo" mode="aspectFill" role="button" :aria-label="`预览第${index + 1}张照片`" @click="preview(index)" />
        <view v-if="photos.length > 1" class="record-detail__photo-count">{{ photos.length }} 张照片</view>
      </view>
      <view v-if="record.note" class="record-detail__note">
        <text class="record-detail__note-label">锅仔的这一餐</text>
        <text class="record-detail__note-copy">{{ record.note }}</text>
      </view>
      <view v-else class="record-detail__note record-detail__note--empty">
        <text class="record-detail__note-label">留下一句记忆</text>
        <text class="record-detail__note-copy">下次回来看，你会记得这顿饭的味道。</text>
      </view>
      <view class="record-detail__actions">
        <GuozaiButton variant="primary" aria-label="编辑这条记录" @click="edit">编辑这餐</GuozaiButton>
        <GuozaiButton variant="ghost" aria-label="删除这条记录" @click="remove">删除记录</GuozaiButton>
      </view>
    </template>
    <wd-image-preview />
  </view>
</template>

<style lang="scss" scoped>
.record-detail-page { min-height: 100vh; padding: 0 28rpx calc(56rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.record-detail__intro { margin: 14rpx 4rpx 24rpx; }.record-detail__intro-top { display: flex; align-items: center; justify-content: space-between; }.record-detail__eyebrow { color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; letter-spacing: 2.5rpx; }.record-detail__mood { padding: 8rpx 14rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 999rpx; background: var(--mrc-surface); color: var(--mrc-text-sub); font-size: 19rpx; }.record-detail__title { display: block; margin-top: 18rpx; color: var(--mrc-text-strong); font-size: 52rpx; font-weight: var(--mrc-fw-heavy); letter-spacing: -1rpx; line-height: 1.15; }.record-detail__meta { display: flex; align-items: center; gap: 12rpx; margin-top: 16rpx; color: var(--mrc-text-sub); font-size: 22rpx; }.record-detail__meta-dot { color: var(--mrc-accent); font-weight: 800; }
.record-detail__photos { position: relative; display: grid; gap: 8rpx; overflow: hidden; border-radius: 32rpx; background: var(--mrc-surface-2); box-shadow: 0 18rpx 40rpx rgba(88, 48, 31, .16); }.record-detail__photos image { width: 100%; height: 100%; min-height: 210rpx; background: var(--mrc-surface-2); }.record-detail__photos--1 { grid-template-columns: 1fr; }.record-detail__photos--1 image { min-height: 430rpx; }.record-detail__photos--2 { grid-template-columns: repeat(2, 1fr); }.record-detail__photos--2 image { min-height: 300rpx; }.record-detail__photos--3, .record-detail__photos--4 { grid-template-columns: repeat(2, 1fr); }.record-detail__photos--3 image:first-child { grid-row: span 2; }.record-detail__photos--5, .record-detail__photos--6, .record-detail__photos--7, .record-detail__photos--8, .record-detail__photos--9 { grid-template-columns: repeat(3, 1fr); }.record-detail__photo-count { position: absolute; right: 18rpx; bottom: 18rpx; padding: 8rpx 14rpx; border: 1rpx solid rgba(255,255,255,.5); border-radius: 999rpx; background: rgba(42, 31, 24, .58); color: #fff; font-size: 18rpx; }
.record-detail__note { display: flex; flex-direction: column; gap: 10rpx; margin-top: 22rpx; padding: 24rpx 26rpx; border-left: 6rpx solid var(--mrc-accent); border-radius: 4rpx 24rpx 24rpx 4rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }.record-detail__note-label { color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; letter-spacing: 1rpx; }.record-detail__note-copy { color: var(--mrc-text-deep); font-size: 25rpx; line-height: 1.55; }.record-detail__note--empty { background: var(--mrc-surface-sun); }.record-detail__note--empty .record-detail__note-copy { color: var(--mrc-text-sub); font-size: 22rpx; }
.record-detail__actions { display: flex; flex-direction: column; gap: 8rpx; margin-top: 28rpx; }.record-detail__actions :deep(.gz-btn) { min-height: 88rpx; }.record-detail__actions :deep(.gz-btn--ghost) { min-height: 68rpx; color: var(--mrc-color-danger); font-size: 22rpx; }
</style>
