<script setup lang="ts">
import type { LearningReceipt } from '../../api/records'
import { computed, ref } from 'vue'
import { STATIC_BASE_URL } from '@/utils/assets'
import { fetchRecord, saveRecord, updateRecord } from '../../api/records'
import { uploadFile } from '../../api/request'
import Icon from '../../components/common/Icon.vue'
import GuozaiChoiceChips from '../../components/guozai/GuozaiChoiceChips.vue'
import GuozaiInsightCard from '../../components/guozai/GuozaiInsightCard.vue'
import MoodPicker from '../../components/guozai/MoodPicker.vue'
import SuccessModal from '../../components/guozai/SuccessModal.vue'
import { chooseImageFiles } from '../../utils/chooseImage'
import { createRequestId, RECORD_DRAFT_KEY } from '../../utils/cookingDraft'
import { ensureLogin } from '../../utils/login'
import { toast, toastError, toastSuccess } from '../../utils/toast'
import { bus, MRC_EVENTS } from '@/utils/bus'
import { useUserStore } from '../../stores/user'

/** 与 calendar 页约定：历史补记时由 calendar 写入，record 页 onShow 读取作为记录日期。 */
const RECORD_DATE_KEY = 'mrc_record_date'

definePage({
  name: 'record',
  layout: 'tabbar',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '记录今日伙食',
  },
})

const router = useRouter()
const userStore = useUserStore()
/** 转发给好友/群 */
onShareAppMessage(() => {
  const sharer = userStore.openid
  const path = `/pages/record/index${sharer ? `?sharer=${encodeURIComponent(sharer)}` : ''}`
  return { title: '锅仔 · 记录今天吃了什么', path }
})
/** 分享到朋友圈 */
onShareTimeline(() => {
  const sharer = userStore.openid
  return {
    title: '锅仔 · 记录今天吃了什么',
    query: sharer ? `sharer=${encodeURIComponent(sharer)}` : '',
  }
})

const dishName = ref('')
const selectedMood = ref('')
const note = ref('')
const cookingTime = ref('30分钟')
interface RecordPhoto { localUrl: string, remoteUrl: string, uploading: boolean }
const photos = ref<RecordPhoto[]>([])
const recipeId = ref<number | undefined>()
const exposureId = ref<string | undefined>()
const clientRequestId = ref(createRequestId())
const savedRecordId = ref<number>()
const showSuccess = ref(false)
const submitting = ref(false)
const editingId = ref<number>()
const recordDate = ref<string>()
const feedbackSelections = ref<string[]>([])
const learningReceipt = ref<LearningReceipt>()
const isEditing = computed(() => Boolean(editingId.value))
const uploading = computed(() => photos.value.some(photo => photo.uploading))
const feedbackOptions = [
  { value: 'liked', label: '喜欢这道菜' },
  { value: 'tooHard', label: '做起来太难' },
  { value: 'leftover', label: '有剩菜' },
]

// tabbar 页通过 switchTab 进入，无法带 query；从推荐页跳转时由 storage 暂存菜名与心情
onShow(async () => {
  const editId = Number(uni.getStorageSync('mrc_record_edit_id'))
  if (editId && editId !== editingId.value) {
    uni.removeStorageSync('mrc_record_edit_id')
    await loadForEdit(editId)
    return
  }
  if (isEditing.value)
    return
  try {
    const d = uni.getStorageSync(RECORD_DRAFT_KEY)
    if (d) {
      if (d.dish)
        dishName.value = d.dish
      if (d.mood)
        selectedMood.value = d.mood
      if (d.recipeId !== undefined && d.recipeId !== null)
        recipeId.value = Number(d.recipeId) || undefined
      if (d.exposureId)
        exposureId.value = String(d.exposureId)
      if (d.cookingTime)
        cookingTime.value = `${Number(d.cookingTime)}分钟`
      if (d.clientRequestId)
        clientRequestId.value = String(d.clientRequestId)
    }
    // 历史补记：日历点过去某天时写入指定日期，这里接管为记录日期
    const backfillDate = uni.getStorageSync(RECORD_DATE_KEY)
    if (backfillDate)
      recordDate.value = String(backfillDate)
  }
  catch { /* ignore */ }
})

/** 是否处于"补记过去某天"模式（用于页面顶部提示，避免误记到今天） */
function isBackfill() {
  if (!recordDate.value)
    return false
  return recordDate.value < new Date().toISOString().slice(0, 10)
}

async function addImages() {
  if (uploading.value) {
    toast('图片上传中，请稍候')
    return
  }
  const capacity = 9 - photos.value.length
  if (!capacity) {
    toast('一条记录最多添加 9 张照片')
    return
  }
  chooseImageFiles({
    count: capacity,
    onSelected: tempPaths => uploadImages(tempPaths),
    onFail: () => toast('选择图片失败，请重试'),
  })
}

/**
 * 限并发上传：多选照片时若同时发起 N 个 uploadFile，会因并发竞态导致只有一张成功
 * （会话/连接资源被抢占）。这里改为「最多同时上传 2 张」，其余排队，逐批推进，
 * 既避免竞态，又保留比纯串行更快的整体速度。
 */
const UPLOAD_CONCURRENCY = 2

async function uploadImages(tempPaths: string[]) {
  const startIndex = photos.value.length
  photos.value.push(...tempPaths.slice(0, 9 - photos.value.length)
    .map(localUrl => ({ localUrl, remoteUrl: '', uploading: true })))
  // 必须从 reactive 数组取代理对象：闭包若持有 push 前的 raw 对象，
  // 后续 photo.uploading/remoteUrl 赋值不触发渲染，UI 会永远停在「上传中…」
  const queue = photos.value.slice(startIndex)
  console.log(`[upload] 照片队列开始: ${queue.length} 张（并发 ${UPLOAD_CONCURRENCY}），已有 ${startIndex} 张`)

  const failed = new Set<string>()
  let cursor = 0
  async function worker() {
    while (cursor < queue.length) {
      const photo = queue[cursor++]
      try {
        photo.remoteUrl = (await uploadFile(photo.localUrl)).url
        console.log('[upload] 单张完成:', photo.localUrl)
      }
      catch (e: any) {
        console.warn('[upload] 单张失败:', photo.localUrl, e?.message)
        failed.add(photo.localUrl)
      }
      finally { photo.uploading = false }
    }
  }
  await Promise.all(Array.from({ length: Math.min(UPLOAD_CONCURRENCY, queue.length) }, () => worker()))

  // 批量失败后再统一移除，避免并发中多次 filter 互相覆盖
  if (failed.size) {
    photos.value = photos.value.filter(item => !failed.has(item.localUrl))
    toastError(new Error(`${failed.size} 张照片上传失败，请重新添加`), '部分照片上传失败')
  }
  console.log(`[upload] 照片队列结束: 成功 ${queue.filter(photo => photo.remoteUrl).length}/${queue.length} 张`)
  if (queue.some(photo => photo.remoteUrl) && !failed.size)
    toastSuccess('照片已收好')
}

function removePhoto(index: number) {
  photos.value.splice(index, 1)
}

function movePhoto(index: number, direction: -1 | 1) {
  const target = index + direction
  if (target < 0 || target >= photos.value.length)
    return
  const [photo] = photos.value.splice(index, 1)
  photos.value.splice(target, 0, photo)
}

async function loadForEdit(id: number) {
  try {
    const record = await fetchRecord(id)
    editingId.value = record.id
    recordDate.value = record.recordDate
    dishName.value = record.dishName
    selectedMood.value = record.moodTag
    note.value = record.note || ''
    cookingTime.value = `${record.cookingTime || 30}分钟`
    recipeId.value = record.recipeId
    exposureId.value = record.exposureId
    photos.value = (record.imageUrls || [record.imageUrl]).map(url => ({ localUrl: url, remoteUrl: url, uploading: false }))
  }
  catch (e: any) {
    toastError(e, '记录加载失败')
    router.back()
  }
}

async function publish() {
  if (submitting.value)
    return
  if (!photos.value.length) {
    toast('请先上传菜品照片')
    return
  }
  if (uploading.value) {
    toast('图片上传中，请稍候')
    return
  }
  if (!dishName.value.trim()) {
    toast('请输入菜名')
    return
  }
  if (!selectedMood.value) {
    toast('请选择今天的心情')
    return
  }

  submitting.value = true
  try {
    const openid = await ensureLogin()
    const payload = {
      openid,
      imageUrl: photos.value[0]?.remoteUrl || photos.value[0]?.localUrl || '',
      imageUrls: photos.value.map(photo => photo.remoteUrl || photo.localUrl),
      dishName: dishName.value.trim(),
      moodTag: selectedMood.value,
      note: note.value,
      recipeId: recipeId.value,
      exposureId: exposureId.value,
      clientRequestId: clientRequestId.value,
      cookingTime: Number.parseInt(cookingTime.value) || 30,
      recordDate: recordDate.value,
      liked: feedbackSelections.value.includes('liked'),
      tooHard: feedbackSelections.value.includes('tooHard'),
      leftover: feedbackSelections.value.includes('leftover'),
    }
    let saved
    if (isEditing.value) {
      saved = await updateRecord(editingId.value!, payload)
      learningReceipt.value = undefined
    }
    else {
      const result = await saveRecord(payload)
      saved = result.record
      learningReceipt.value = result.learningReceipt
    }
    savedRecordId.value = saved.id
    uni.removeStorageSync(RECORD_DRAFT_KEY)
    uni.removeStorageSync('mrc_companion_message')
    // 记录已落库，通知日历等页面刷新；并清除历史补记意图（下次默认记今天）
    bus.emit(MRC_EVENTS.RECORDS_CHANGED)
    uni.removeStorageSync(RECORD_DATE_KEY)
    showSuccess.value = true
  }
  catch (e: any) {
    console.error(e)
    toastError(e, '保存失败')
  }
  finally {
    submitting.value = false
  }
}

function onSuccessConfirm() {
  showSuccess.value = false
  resetForm()
  router.push({ name: 'timeline' })
}

function onSuccessSecondary() {
  showSuccess.value = false
  resetForm()
  router.pushTab({ name: 'home' })
}

function resetForm() {
  dishName.value = ''
  selectedMood.value = ''
  note.value = ''
  cookingTime.value = '30分钟'
  photos.value = []
  recipeId.value = undefined
  exposureId.value = undefined
  clientRequestId.value = createRequestId()
  editingId.value = undefined
  recordDate.value = undefined
  savedRecordId.value = undefined
  feedbackSelections.value = []
  learningReceipt.value = undefined
}

const COOKING_TIME_OPTIONS = ['10分钟', '20分钟', '30分钟', '45分钟', '60分钟', '1小时以上']
function chooseCookingTime() {
  uni.showActionSheet({
    itemList: COOKING_TIME_OPTIONS,
    success: (res) => {
      cookingTime.value = COOKING_TIME_OPTIONS[res.tapIndex]
    },
  })
}
</script>

<template>
  <view class="record-page mrc-hero">
    <wd-navbar title="记录今日伙食" safe-area-inset-top custom-style="background-color: transparent !important;" />

    <view class="record-intro">
      <view class="record-intro__copy">
        <text class="record-intro__eyebrow">今天也有好好吃饭</text>
        <text class="record-intro__title">把这一餐，留给以后的你</text>
        <text class="record-intro__sub">锅仔会记住味道，也记住你今天的心情</text>
      </view>
      <image class="record-intro__guozai" :src="`${STATIC_BASE_URL}/static/guozai/action_03_camera.png`" mode="aspectFit" />
    </view>

    <view v-if="isBackfill()" class="record-backfill-tip">
      <Icon name="calendar" :size="30" color="#EF5A3C" />
      <text>正在补记 <text class="record-backfill-tip__date">{{ recordDate }}</text> 的伙食，提交后会记入那一天</text>
    </view>

    <view class="record-photo-section">
      <view class="record-photo-section__head"><text>这一餐的照片</text><text>{{ photos.length }}/9 · 可调整顺序</text></view>
      <view class="record-photo-grid">
        <view v-for="(photo, index) in photos" :key="photo.localUrl" class="record-photo-item">
          <image class="record-photo-item__image" :src="photo.localUrl" mode="aspectFill" />
          <view v-if="photo.uploading" class="record-photo-item__uploading">上传中…</view>
          <view class="record-photo-item__tools">
            <text role="button" :aria-label="`将第${index + 1}张照片向前移动`" :class="{ 'record-photo-item__tool--disabled': index === 0 }" @click.stop="movePhoto(index, -1)">‹</text>
            <text role="button" :aria-label="`删除第${index + 1}张照片`" @click.stop="removePhoto(index)">×</text>
            <text role="button" :aria-label="`将第${index + 1}张照片向后移动`" :class="{ 'record-photo-item__tool--disabled': index === photos.length - 1 }" @click.stop="movePhoto(index, 1)">›</text>
          </view>
        </view>
        <view v-if="photos.length < 9" class="record-photo-add" role="button" aria-label="添加菜品照片" @click="addImages">
          <Icon name="camera" :size="48" color="#EF5A3C" />
          <text>{{ photos.length ? '添加照片' : '拍照或从相册选择' }}</text>
        </view>
      </view>
      <text class="record-photo-section__tip">第一张将作为封面；点击左右箭头调整展示顺序</text>
    </view>

    <view class="record-form-card">
      <view class="record-section-title">
        <view class="record-section-title__num">2</view>
        <view><text class="record-section-title__main">这一餐吃了什么？</text><text class="record-section-title__sub">菜名必填，时间可以慢慢选</text></view>
      </view>
      <view class="record-input">
        <text class="record-input__label">菜名</text>
        <input
          v-model="dishName"
          class="record-input__field"
          placeholder="比如：番茄炒蛋"
          placeholder-class="record-input__placeholder"
        />
      </view>
      <view class="record-time" role="button" aria-label="选择烹饪时间" @click="chooseCookingTime">
        <view class="record-time__label"><Icon name="clock" :size="34" color="#EF5A3C" /><text>烹饪时间</text></view>
        <view class="record-time__tag">{{ cookingTime }}<text class="record-time__arrow">›</text></view>
      </view>
    </view>

    <view class="record-mood-card">
      <MoodPicker v-model="selectedMood" :show-hero="false" title="吃完这顿，你是什么心情？" />
    </view>

    <view v-if="!isEditing" class="record-feedback-card">
      <view class="record-textarea__head">
        <view><text class="record-textarea__title">这次做饭感觉怎么样？</text><text class="record-textarea__sub">可跳过、可多选，锅仔只学习你主动告诉它的</text></view>
      </view>
      <GuozaiChoiceChips v-model="feedbackSelections" :options="feedbackOptions" :disabled="submitting" />
    </view>

    <view class="record-textarea">
      <view class="record-textarea__head">
        <view><text class="record-textarea__title">留一句话给今天</text><text class="record-textarea__sub">可选 · 锅仔不会催你写很多</text></view>
        <text class="record-textarea__count">{{ note.length }}/50</text>
      </view>
      <textarea
        v-model="note"
        class="record-textarea__field"
        placeholder="今天这顿饭，有什么想记住的？"
        placeholder-class="record-textarea__placeholder"
        :maxlength="50"
        :auto-height="true"
      />
    </view>

    <view class="record-submit">
      <view class="record-submit__btn mrc-btn-primary" :class="{ 'record-submit__btn--disabled': submitting || uploading }" role="button" aria-label="保存今日伙食记录" @click="publish">
        <text>{{ submitting ? '正在保存…' : (isEditing ? '保存这次修改' : '收进我的时光机') }}</text>
      </view>
      <text v-if="!submitting" class="record-submit__sub">{{ isEditing ? '照片顺序会同步更新' : '以后翻到今天，还能想起这一餐' }}</text>
    </view>

    <!-- 成功弹窗 -->
    <SuccessModal
      :visible="showSuccess"
      :title="isEditing ? '记录已更新！' : '记录成功！'"
      :subtitle="isEditing ? '这一餐的新样子已经收好' : '今天也好好吃饭了呢'"
      :confirm-text="isEditing ? '回到这条记录' : '查看这条记录'"
      :secondary-text="isEditing ? '' : '返回首页'"
      @confirm="onSuccessConfirm"
      @secondary="onSuccessSecondary"
    >
      <GuozaiInsightCard
        v-if="learningReceipt"
        :title="learningReceipt.title"
        :items="learningReceipt.items"
        :variant="learningReceipt.status === 'LEARNED' ? 'learned' : 'saved'"
      />
    </SuccessModal>
  </view>
</template>

<style lang="scss" scoped>
.record-page {
  min-height: 100vh;
  padding: 0 32rpx;
  padding-bottom: calc(56rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.record-intro { position: relative; display: flex; min-height: 176rpx; margin: 4rpx 0 24rpx; padding: 28rpx 28rpx 24rpx; overflow: hidden; box-sizing: border-box; border: 2rpx solid var(--mrc-border); border-radius: 36rpx; background: linear-gradient(145deg, var(--mrc-surface), var(--mrc-surface-peach)); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }
.record-intro__copy { position: relative; z-index: 1; max-width: 76%; }
.record-intro__eyebrow, .record-intro__title, .record-intro__sub { display: block; }
.record-intro__eyebrow { margin-bottom: 8rpx; color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; letter-spacing: 2rpx; }
.record-intro__title { color: var(--mrc-text-strong); font-size: 34rpx; font-weight: var(--mrc-fw-heavy); line-height: 1.3; }
.record-intro__sub { margin-top: 8rpx; color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.45; }
.record-intro__guozai { position: absolute; right: -4rpx; bottom: -12rpx; width: 142rpx; height: 142rpx; }
.record-backfill-tip { display: flex; align-items: center; gap: 12rpx; margin: 0 0 20rpx; padding: 18rpx 22rpx; border-radius: 24rpx; color: var(--mrc-accent); background: var(--mrc-surface-sun); font-size: 22rpx; line-height: 1.4; }
.record-backfill-tip__date { font-weight: 800; }

.record-photo-section { margin-bottom: 24rpx; padding: 28rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 32rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }
.record-photo-section__head { display: flex; justify-content: space-between; margin-bottom: 20rpx; color: var(--mrc-text-strong); font-size: 29rpx; font-weight: 800; }
.record-photo-section__head text:last-child, .record-photo-section__tip { color: var(--mrc-text-sub); font-size: 21rpx; font-weight: 500; }
.record-photo-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12rpx; }
.record-photo-item, .record-photo-add { position: relative; aspect-ratio: 1; overflow: hidden; border-radius: 18rpx; }
.record-photo-item { background: var(--mrc-surface-2); }
.record-photo-item__image { width: 100%; height: 100%; }
.record-photo-add { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10rpx; min-height: 190rpx; border: 2rpx dashed var(--mrc-border-strong); background: var(--mrc-surface-sun); color: var(--mrc-text-sub); font-size: 22rpx; text-align: center; }
.record-photo-item__uploading { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(44, 24, 16, .45); color: #fff; font-size: 22rpx; }
.record-photo-item__tools { position: absolute; right: 8rpx; bottom: 8rpx; display: flex; overflow: hidden; border-radius: 22rpx; background: rgba(44, 24, 16, .7); color: #fff; }
.record-photo-item__tools text { min-width: 38rpx; min-height: 38rpx; display: flex; align-items: center; justify-content: center; font-size: 34rpx; line-height: 1; }
.record-photo-item__tool--disabled { opacity: .35; pointer-events: none; }
.record-photo-section__tip { display: block; margin-top: 16rpx; }

.record-form-card, .record-mood-card, .record-feedback-card, .record-textarea { margin-bottom: 24rpx; padding: 28rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 32rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }
.record-section-title { display: flex; align-items: center; gap: 16rpx; margin-bottom: 22rpx; }
.record-section-title__num { width: 48rpx; height: 48rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 50%; background: var(--mrc-accent-soft); color: var(--mrc-accent); font-size: 24rpx; font-weight: var(--mrc-fw-heavy); }
.record-section-title__main, .record-section-title__sub { display: block; }
.record-section-title__main { color: var(--mrc-text-strong); font-size: 29rpx; font-weight: 800; }
.record-section-title__sub { margin-top: 4rpx; color: var(--mrc-text-sub); font-size: 20rpx; }

.record-input {
  display: flex;
  align-items: center;
  background: var(--mrc-surface-2);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 24rpx;
  padding: 0 24rpx;
  height: 92rpx;
  margin-bottom: 14rpx;
}
.record-input__label {
  font-size: 27rpx;
  color: var(--mrc-text-deep);
  font-weight: 600;
  margin-right: 24rpx;
  flex-shrink: 0;
}
.record-input__field {
  flex: 1;
  font-size: 30rpx;
  color: var(--mrc-text-deep);
  text-align: right;
}
.record-input__placeholder {
  color: var(--mrc-text-light);
}

.record-textarea__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 20rpx; margin-bottom: 20rpx; }
.record-textarea__title, .record-textarea__sub { display: block; }
.record-textarea__title { color: var(--mrc-text-strong); font-size: 29rpx; font-weight: 800; }
.record-textarea__sub { margin-top: 5rpx; color: var(--mrc-text-sub); font-size: 20rpx; }
.record-textarea__count { color: var(--mrc-text-light); font-size: 21rpx; }
.record-textarea__field {
  width: 100%;
  min-height: 130rpx;
  padding: 20rpx 22rpx;
  box-sizing: border-box;
  border-radius: 22rpx;
  background: var(--mrc-surface-2);
  font-size: 27rpx;
  color: var(--mrc-text-deep);
  line-height: 1.6;
}
.record-textarea__placeholder {
  color: var(--mrc-text-light);
}

/* 烹饪时间 */
.record-time {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 88rpx;
  padding: 0 18rpx 0 22rpx;
  border-radius: 24rpx;
  background: var(--mrc-surface-sun);
}
.record-time__label {
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: 26rpx;
  color: var(--mrc-text-deep);
  font-weight: 600;
}
.record-time__tag {
  font-size: 25rpx;
  color: var(--mrc-text-deep);
  background: var(--mrc-surface-sun);
  padding: 10rpx 12rpx 10rpx 22rpx;
  border-radius: 32rpx;
  border: 2rpx solid var(--mrc-border-light);
  display: flex;
  align-items: center;
}
.record-time__arrow {
  margin-left: 8rpx;
  font-size: 32rpx;
  color: var(--mrc-text-light);
}

/* 发布按钮 */
.record-submit { padding-top: 4rpx; }
.record-submit__btn {
  width: 100%;
}
.record-submit__sub { display: block; margin-top: 12rpx; color: var(--mrc-text-sub); font-size: 20rpx; text-align: center; }
.record-submit__btn--disabled { opacity: .58; box-shadow: none; }

@media (prefers-reduced-motion: reduce) {
  .record-submit__btn { transition: none; }
}
</style>
