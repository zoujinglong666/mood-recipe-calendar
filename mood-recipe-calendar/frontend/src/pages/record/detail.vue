<script setup lang="ts">
import { useImagePreview } from '@wot-ui/ui'
import { computed, ref } from 'vue'
import { deleteRecord, fetchRecord, type RecordItem } from '@/api/records'
import { generateRecordPoster } from '@/api/recordPoster'
import EmptyState from '@/components/guozai/EmptyState.vue'
import GuozaiButton from '@/components/guozai/GuozaiButton.vue'
import LoadingState from '@/components/guozai/LoadingState.vue'
import { navBack } from '@/composables/useNavBar'
import { ensureLogin } from '@/utils/login'
import { toastError, toastSuccess } from '@/utils/toast'
import { useUserStore } from '@/stores/user'

definePage({ name: 'record-detail', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '这一餐的食光' } })

const router = useRouter()
const route = useRoute()
const { previewImage } = useImagePreview()
const loading = ref(true)
const record = ref<RecordItem>()
const userStore = useUserStore()
const posterMode = ref<'detail' | 'poster' | 'server'>('detail')
const backendPosterUrl = ref('')
const generatingPoster = ref(false)
const isMember = computed(() => userStore.userInfo?.isMember === 1 && !!userStore.userInfo.memberExpire && new Date(userStore.userInfo.memberExpire).getTime() > Date.now())
const photos = computed(() => record.value?.imageUrls?.length ? record.value.imageUrls : (record.value?.imageUrl ? [record.value.imageUrl] : []))
const posterDate = computed(() => {
  const [year = '', month = '', day = ''] = (record.value?.recordDate || '').split('-')
  return { year, month, day }
})

function photoClass() {
  return `record-detail__photos--${Math.min(Math.max(photos.value.length, 1), 9)}`
}

function posterPhotoClass() {
  return `record-detail__poster-grid--${Math.min(Math.max(photos.value.length, 1), 9)}`
}

async function selectMode(mode: 'detail' | 'poster' | 'server') {
  if (mode !== 'server') {
    posterMode.value = mode
    return
  }
  if (!isMember.value) {
    uni.showModal({ title: '会员专享模式', content: '海报+后端由锅仔为你生成可保存分享的海报，开通会员后即可使用。', confirmText: '查看会员', success: ({ confirm }) => { if (confirm) router.push({ name: 'membership' }) } })
    return
  }
  posterMode.value = 'server'
  if (backendPosterUrl.value || generatingPoster.value || !record.value) return
  generatingPoster.value = true
  try {
    backendPosterUrl.value = (await generateRecordPoster(record.value.id)).url
  }
  catch (error: any) { toastError(error, '海报生成失败') }
  finally { generatingPoster.value = false }
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
  try {
    record.value = await fetchRecord(id)
    backendPosterUrl.value = record.value.posterUrl || ''
  }
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
      <view class="record-detail__mode-switch" role="tablist" aria-label="食光展示模式">
        <view class="record-detail__mode" :class="{ 'is-active': posterMode === 'detail' }" role="tab" :aria-selected="posterMode === 'detail'" @click="selectMode('detail')">默认</view>
        <view class="record-detail__mode" :class="{ 'is-active': posterMode === 'poster' }" role="tab" :aria-selected="posterMode === 'poster'" @click="selectMode('poster')">海报</view>
        <view v-if="isMember" class="record-detail__mode record-detail__mode--member" :class="{ 'is-active': posterMode === 'server' }" role="tab" :aria-selected="posterMode === 'server'" @click="selectMode('server')">海报 + 后端</view>
      </view>
      <template v-if="posterMode === 'detail'">
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
      </template>
      <template v-else-if="posterMode === 'poster'">
        <view class="record-detail__poster">
          <view class="record-detail__poster-head">
            <view class="record-detail__poster-brand"><text>GUOZAI</text><text class="record-detail__poster-brand-line" /><text>FOOD MEMORY</text></view>
            <text class="record-detail__poster-folio">NO. {{ posterDate.day || '01' }}</text>
            <text class="record-detail__poster-kicker">这一餐的食光 / {{ posterDate.year || 'TODAY' }}</text>
            <text class="record-detail__poster-title">{{ record.dishName }}</text>
            <view class="record-detail__poster-meta"><text>{{ posterDate.month || '--' }}月{{ posterDate.day || '--' }}日</text><text>·</text><text>{{ record.cookingTime || 30 }} 分钟</text></view>
            <text class="record-detail__poster-mood">{{ record.moodTag || '这一餐' }}</text>
          </view>
          <view class="record-detail__poster-grid" :class="posterPhotoClass()">
            <image v-for="(photo, index) in photos" :key="`poster-${photo}`" :src="photo" mode="aspectFill" role="button" :aria-label="`预览第${index + 1}张照片`" @click="preview(index)" />
            <view v-if="photos.length > 1" class="record-detail__poster-count">{{ photos.length }} PHOTOS</view>
          </view>
          <view class="record-detail__poster-caption">
            <view class="record-detail__poster-caption-rule" />
            <view><text class="record-detail__poster-caption-label">锅仔编辑批注</text><text class="record-detail__poster-caption-copy">{{ record.note || '把今天的味道收好，下一次回来继续。' }}</text></view>
            <text class="record-detail__poster-caption-mark">记住这一口</text>
          </view>
          <view class="record-detail__poster-footer"><text>THE TASTE OF TODAY</text><text>{{ posterDate.year || '' }} / {{ posterDate.month || '' }}</text></view>
        </view>
      </template>
      <view v-else class="record-detail__server-poster">
        <view v-if="generatingPoster" class="record-detail__server-poster-loading"><view class="record-detail__server-poster-spinner" aria-hidden="true" /><text>锅仔正在排版这张海报…</text><text class="record-detail__server-poster-hint">会保留真实照片、中文菜名和你的这一句记忆</text></view>
        <image v-else-if="backendPosterUrl" class="record-detail__server-poster-image" :src="backendPosterUrl" mode="widthFix" role="button" aria-label="预览后端生成的海报" @click="previewImage({ images: [backendPosterUrl], startPosition: 0 })" />
        <view v-else class="record-detail__server-poster-error"><text>暂时没有生成成功</text><GuozaiButton variant="primary" @click="selectMode('server')">重新生成</GuozaiButton></view>
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
.record-detail__mode-switch { display: flex; width: fit-content; margin: 6rpx auto 22rpx; padding: 4rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 999rpx; background: rgba(255, 250, 243, .72); box-shadow: 0 8rpx 20rpx rgba(88, 48, 31, .06); }
.record-detail__mode { min-width: 112rpx; min-height: 64rpx; padding: 0 18rpx; display: flex; align-items: center; justify-content: center; border-radius: 999rpx; color: var(--mrc-text-sub); font-size: 22rpx; font-weight: 700; transition: background-color .2s ease, color .2s ease, box-shadow .2s ease; }
.record-detail__mode--member { min-width: 188rpx; color: var(--mrc-accent); }
.record-detail__mode.is-active { color: var(--mrc-text-strong); background: var(--mrc-surface); box-shadow: 0 4rpx 12rpx rgba(88, 48, 31, .12); }
.record-detail__intro { margin: 14rpx 4rpx 24rpx; }.record-detail__intro-top { display: flex; align-items: center; justify-content: space-between; }.record-detail__eyebrow { color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; letter-spacing: 2.5rpx; }.record-detail__mood { padding: 8rpx 14rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 999rpx; background: var(--mrc-surface); color: var(--mrc-text-sub); font-size: 19rpx; }.record-detail__title { display: block; margin-top: 18rpx; color: var(--mrc-text-strong); font-size: 52rpx; font-weight: var(--mrc-fw-heavy); letter-spacing: -1rpx; line-height: 1.15; }.record-detail__meta { display: flex; align-items: center; gap: 12rpx; margin-top: 16rpx; color: var(--mrc-text-sub); font-size: 22rpx; }.record-detail__meta-dot { color: var(--mrc-accent); font-weight: 800; }
.record-detail__photos { position: relative; display: grid; gap: 8rpx; overflow: hidden; border-radius: 32rpx; background: var(--mrc-surface-2); box-shadow: 0 18rpx 40rpx rgba(88, 48, 31, .16); }.record-detail__photos image { width: 100%; height: 100%; min-height: 210rpx; background: var(--mrc-surface-2); }.record-detail__photos--1 { grid-template-columns: 1fr; }.record-detail__photos--1 image { min-height: 430rpx; }.record-detail__photos--2 { grid-template-columns: repeat(2, 1fr); }.record-detail__photos--2 image { min-height: 300rpx; }.record-detail__photos--3, .record-detail__photos--4 { grid-template-columns: repeat(2, 1fr); }.record-detail__photos--3 image:first-child { grid-row: span 2; }.record-detail__photos--5, .record-detail__photos--6, .record-detail__photos--7, .record-detail__photos--8, .record-detail__photos--9 { grid-template-columns: repeat(3, 1fr); }.record-detail__photo-count { position: absolute; right: 18rpx; bottom: 18rpx; padding: 8rpx 14rpx; border: 1rpx solid rgba(255,255,255,.5); border-radius: 999rpx; background: rgba(42, 31, 24, .58); color: #fff; font-size: 18rpx; }
.record-detail__note { display: flex; flex-direction: column; gap: 10rpx; margin-top: 22rpx; padding: 24rpx 26rpx; border-left: 6rpx solid var(--mrc-accent); border-radius: 4rpx 24rpx 24rpx 4rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }.record-detail__note-label { color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; letter-spacing: 1rpx; }.record-detail__note-copy { color: var(--mrc-text-deep); font-size: 25rpx; line-height: 1.55; }.record-detail__note--empty { background: var(--mrc-surface-sun); }.record-detail__note--empty .record-detail__note-copy { color: var(--mrc-text-sub); font-size: 22rpx; }
.record-detail__poster { overflow: hidden; border: 2rpx solid var(--mrc-border-light); border-radius: 34rpx; background: #fff9ef; box-shadow: 0 20rpx 44rpx rgba(88, 48, 31, .14); }.record-detail__poster-head { position: relative; padding: 32rpx 30rpx 26rpx; background: linear-gradient(135deg, #fffaf1 0%, #f6e2ce 100%); }.record-detail__poster-brand { display: flex; align-items: center; gap: 10rpx; color: var(--mrc-accent); font-size: 18rpx; font-weight: 900; letter-spacing: 3rpx; }.record-detail__poster-brand-line { width: 34rpx; height: 2rpx; background: var(--mrc-accent); }.record-detail__poster-folio { position: absolute; top: 30rpx; right: 28rpx; color: var(--mrc-text-sub); font-family: monospace; font-size: 18rpx; letter-spacing: 1rpx; }.record-detail__poster-kicker { display: block; margin-top: 48rpx; color: var(--mrc-text-sub); font-size: 19rpx; letter-spacing: 2rpx; }.record-detail__poster-mood { position: absolute; right: 26rpx; bottom: 28rpx; padding: 8rpx 16rpx; border: 2rpx solid var(--mrc-accent-soft); border-radius: 999rpx; color: var(--mrc-accent); background: rgba(255, 255, 255, .62); font-size: 18rpx; }.record-detail__poster-title { display: -webkit-box; max-width: 82%; margin-top: 12rpx; color: var(--mrc-text-strong); font-size: 52rpx; font-weight: var(--mrc-fw-heavy); line-height: 1.1; overflow: hidden; text-overflow: ellipsis; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }.record-detail__poster-meta { display: flex; gap: 12rpx; margin-top: 18rpx; color: var(--mrc-text-sub); font-size: 21rpx; }
.record-detail__poster-grid { position: relative; display: grid; gap: 6rpx; margin: 0 18rpx; overflow: hidden; border-radius: 4rpx; background: var(--mrc-surface-2); }.record-detail__poster-grid image { width: 100%; height: 100%; min-height: 190rpx; background: var(--mrc-surface-2); }.record-detail__poster-grid--1 { grid-template-columns: 1fr; }.record-detail__poster-grid--1 image { min-height: 520rpx; }.record-detail__poster-grid--2 { grid-template-columns: repeat(2, 1fr); }.record-detail__poster-grid--2 image { min-height: 360rpx; }.record-detail__poster-grid--3 { grid-template-columns: 1.35fr 1fr; grid-template-rows: repeat(2, 220rpx); }.record-detail__poster-grid--3 image:first-child { grid-row: span 2; }.record-detail__poster-grid--4 { grid-template-columns: repeat(2, 1fr); grid-template-rows: repeat(2, 220rpx); }.record-detail__poster-grid--5, .record-detail__poster-grid--6, .record-detail__poster-grid--7, .record-detail__poster-grid--8, .record-detail__poster-grid--9 { grid-template-columns: repeat(3, 1fr); grid-auto-rows: 190rpx; }.record-detail__poster-count { position: absolute; right: 16rpx; bottom: 14rpx; padding: 8rpx 14rpx; border: 1rpx solid rgba(255,255,255,.55); border-radius: 999rpx; background: rgba(42, 31, 24, .62); color: #fff; font-size: 17rpx; letter-spacing: 1rpx; }.record-detail__poster-caption { display: flex; align-items: stretch; gap: 18rpx; margin: 22rpx 18rpx 16rpx; padding: 22rpx 22rpx 24rpx; border-top: 2rpx solid var(--mrc-border-light); border-bottom: 2rpx solid var(--mrc-border-light); background: rgba(255, 252, 246, .78); }.record-detail__poster-caption-rule { width: 6rpx; flex: 0 0 6rpx; border-radius: 999rpx; background: var(--mrc-accent); }.record-detail__poster-caption-label { display: block; color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; }.record-detail__poster-caption-copy { display: block; margin-top: 8rpx; color: var(--mrc-text-deep); font-size: 24rpx; line-height: 1.55; }.record-detail__poster-caption-mark { align-self: flex-end; margin-left: auto; color: var(--mrc-text-sub); font-size: 17rpx; writing-mode: vertical-rl; letter-spacing: 2rpx; }.record-detail__poster-footer { display: flex; justify-content: space-between; padding: 0 30rpx 24rpx; color: var(--mrc-text-sub); font-family: monospace; font-size: 16rpx; letter-spacing: 1rpx; }
.record-detail__server-poster { min-height: 720rpx; display: flex; align-items: center; justify-content: center; overflow: hidden; border: 2rpx solid var(--mrc-border-light); border-radius: 34rpx; background: #fff9ef; box-shadow: 0 20rpx 44rpx rgba(88, 48, 31, .14); }.record-detail__server-poster-image { display: block; width: 100%; }.record-detail__server-poster-loading, .record-detail__server-poster-error { display: flex; flex-direction: column; align-items: center; gap: 18rpx; padding: 60rpx 38rpx; color: var(--mrc-text-strong); font-size: 28rpx; text-align: center; }.record-detail__server-poster-hint { color: var(--mrc-text-sub); font-size: 21rpx; line-height: 1.5; }.record-detail__server-poster-spinner { width: 54rpx; height: 54rpx; border: 5rpx solid var(--mrc-accent-soft); border-top-color: var(--mrc-accent); border-radius: 50%; animation: record-poster-spin 1s linear infinite; }.record-detail__server-poster-error :deep(.gz-btn) { min-width: 260rpx; }
.record-detail__actions { display: flex; flex-direction: column; gap: 8rpx; margin-top: 28rpx; }.record-detail__actions :deep(.gz-btn) { min-height: 88rpx; }.record-detail__actions :deep(.gz-btn--ghost) { min-height: 68rpx; color: var(--mrc-color-danger); font-size: 22rpx; }
@media (prefers-reduced-motion: reduce) { .record-detail__mode { transition: none; } }
@media (prefers-reduced-motion: reduce) { .record-detail__server-poster-spinner { animation: none; } }
@keyframes record-poster-spin { to { transform: rotate(360deg); } }
</style>
