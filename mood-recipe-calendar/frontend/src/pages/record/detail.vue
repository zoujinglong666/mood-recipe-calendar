<script setup lang="ts">
import { useImagePreview } from '@wot-ui/ui'
import { computed, ref } from 'vue'
import LoadingState from '@/components/guozai/LoadingState.vue'
import EmptyState from '@/components/guozai/EmptyState.vue'
import { deleteRecord, fetchRecord, type RecordItem } from '@/api/records'
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
  uni.setStorageSync('mrc_record_edit_id', record.value.id)
  router.pushTab({ name: 'record' })
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

async function load() {
  const id = Number(route.query.id)
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
      <view class="record-detail__hero">
        <text class="record-detail__eyebrow">GUOZAI FOOD MEMORY</text>
        <text class="record-detail__title">{{ record.dishName }}</text>
        <text class="record-detail__meta">{{ record.recordDate }} · {{ record.moodTag }} · {{ record.cookingTime || 30 }} 分钟</text>
      </view>
      <view class="record-detail__photos" :class="photoClass()">
        <image v-for="(photo, index) in photos" :key="photo" :src="photo" mode="aspectFill" role="button" :aria-label="`预览第${index + 1}张照片`" @click="preview(index)" />
      </view>
      <view v-if="record.note" class="record-detail__note"><text>“</text><text>{{ record.note }}</text><text>”</text></view>
      <view class="record-detail__actions">
        <view class="record-detail__edit pressable" role="button" aria-label="编辑这条记录" @click="edit">编辑记录</view>
        <view class="record-detail__delete pressable" role="button" aria-label="删除这条记录" @click="remove">删除这条记录</view>
      </view>
    </template>
    <wd-image-preview />
  </view>
</template>

<style lang="scss" scoped>
.record-detail-page { min-height: 100vh; padding: 0 32rpx calc(64rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.record-detail__hero { margin: 10rpx 0 24rpx; padding: 34rpx 30rpx; border: 2rpx solid var(--mrc-border); border-radius: 36rpx; background: linear-gradient(145deg, var(--mrc-surface), var(--mrc-surface-peach)); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }
.record-detail__eyebrow, .record-detail__title, .record-detail__meta { display: block; }
.record-detail__eyebrow { color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; letter-spacing: 2rpx; }
.record-detail__title { margin-top: 12rpx; color: var(--mrc-text-strong); font-size: 46rpx; font-weight: var(--mrc-fw-heavy); line-height: 1.25; }
.record-detail__meta { margin-top: 14rpx; color: var(--mrc-text-sub); font-size: 24rpx; }
.record-detail__photos { display: grid; gap: 8rpx; overflow: hidden; border-radius: 28rpx; }
.record-detail__photos image { width: 100%; height: 100%; min-height: 210rpx; background: var(--mrc-surface-2); }
.record-detail__photos--1 { grid-template-columns: 1fr; }.record-detail__photos--1 image { min-height: 520rpx; }
.record-detail__photos--2 { grid-template-columns: repeat(2, 1fr); }.record-detail__photos--2 image { min-height: 360rpx; }
.record-detail__photos--3, .record-detail__photos--4 { grid-template-columns: repeat(2, 1fr); }.record-detail__photos--3 image:first-child { grid-row: span 2; }
.record-detail__photos--5, .record-detail__photos--6, .record-detail__photos--7, .record-detail__photos--8, .record-detail__photos--9 { grid-template-columns: repeat(3, 1fr); }
.record-detail__note { margin-top: 24rpx; padding: 28rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; background: var(--mrc-surface); color: var(--mrc-text-deep); font-size: 28rpx; line-height: 1.7; box-shadow: var(--mrc-shadow-soft); }
.record-detail__actions { margin-top: 32rpx; }.record-detail__edit { min-height: 96rpx; display: flex; align-items: center; justify-content: center; border-radius: 48rpx; background: var(--mrc-primary-grad); box-shadow: 0 10rpx 24rpx rgba(253, 145, 132, .3); color: #fff; font-size: 30rpx; font-weight: 800; }.record-detail__delete { margin-top: 18rpx; color: #C6524A; font-size: 25rpx; text-align: center; }
</style>
