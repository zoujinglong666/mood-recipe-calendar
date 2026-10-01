<script setup lang="ts">
import type { RecordItem } from '@/api/records'
import { computed, onMounted, ref } from 'vue'
import { fetchRecord, updateRecord } from '@/api/records'
import { uploadFile } from '@/api/request'
import Icon from '@/components/common/Icon.vue'
import MoodPicker from '@/components/guozai/MoodPicker.vue'
import { chooseImageFiles } from '@/utils/chooseImage'
import { ensureLogin } from '@/utils/login'
import { toast, toastError, toastSuccess } from '@/utils/toast'

interface RecordPhoto {
  localUrl: string
  remoteUrl: string
  uploading: boolean
}

const props = defineProps<{ recordId: number }>()
const emit = defineEmits<{
  (event: 'saved', record: RecordItem): void
  (event: 'cancel'): void
}>()

const loading = ref(true)
const saving = ref(false)
const dishName = ref('')
const selectedMood = ref('')
const note = ref('')
const cookingTime = ref('30分钟')
const recordDate = ref('')
const photos = ref<RecordPhoto[]>([])
const recipeId = ref<number>()
const exposureId = ref<string>()
const uploading = computed(() => photos.value.some(photo => photo.uploading))

const COOKING_TIME_OPTIONS = ['10分钟', '20分钟', '30分钟', '45分钟', '60分钟', '1小时以上']

async function load() {
  loading.value = true
  try {
    const record = await fetchRecord(props.recordId)
    dishName.value = record.dishName || ''
    selectedMood.value = record.moodTag || ''
    note.value = record.note || ''
    cookingTime.value = `${record.cookingTime || 30}分钟`
    recordDate.value = record.recordDate || ''
    recipeId.value = record.recipeId
    exposureId.value = record.exposureId
    photos.value = (record.imageUrls?.length ? record.imageUrls : [record.imageUrl])
      .filter(Boolean)
      .map(url => ({ localUrl: url, remoteUrl: url, uploading: false }))
  }
  catch (error: any) {
    toastError(error, '记录加载失败')
    emit('cancel')
  }
  finally {
    loading.value = false
  }
}

function addImages() {
  if (uploading.value) {
    toast('图片上传中，请稍候')
    return
  }
  const count = 9 - photos.value.length
  if (!count) {
    toast('一条记录最多添加 9 张照片')
    return
  }
  chooseImageFiles({
    count,
    onSelected: uploadImages,
    onFail: () => toast('选择图片失败，请重试'),
  })
}

async function uploadImages(tempPaths: string[]) {
  const startIndex = photos.value.length
  photos.value.push(...tempPaths.slice(0, 9 - startIndex).map(localUrl => ({ localUrl, remoteUrl: '', uploading: true })))
  const queue = photos.value.slice(startIndex)
  await Promise.all(queue.map(async (photo) => {
    try {
      photo.remoteUrl = (await uploadFile(photo.localUrl)).url
    }
    catch (error: any) {
      photos.value = photos.value.filter(item => item !== photo)
      toastError(error, '有照片上传失败，请重新添加')
    }
    finally {
      photo.uploading = false
    }
  }))
  if (queue.some(photo => photo.remoteUrl))
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

function chooseCookingTime() {
  uni.showActionSheet({
    itemList: COOKING_TIME_OPTIONS,
    success: ({ tapIndex }) => { cookingTime.value = COOKING_TIME_OPTIONS[tapIndex] },
  })
}

async function save() {
  if (saving.value || loading.value)
    return
  if (!dishName.value.trim()) {
    toast('请输入菜名')
    return
  }
  if (!selectedMood.value) {
    toast('请选择这顿饭的心情')
    return
  }
  if (uploading.value) {
    toast('图片上传中，请稍候')
    return
  }
  if (!photos.value.length) {
    toast('至少保留一张菜品照片')
    return
  }

  saving.value = true
  try {
    const openid = await ensureLogin()
    const saved = await updateRecord(props.recordId, {
      openid,
      imageUrl: photos.value[0].remoteUrl || photos.value[0].localUrl,
      imageUrls: photos.value.map(photo => photo.remoteUrl || photo.localUrl),
      dishName: dishName.value.trim(),
      moodTag: selectedMood.value,
      note: note.value.trim(),
      recipeId: recipeId.value,
      exposureId: exposureId.value,
      cookingTime: Number.parseInt(cookingTime.value, 10) || 30,
      recordDate: recordDate.value,
    })
    toastSuccess('这一餐已更新')
    emit('saved', saved)
  }
  catch (error: any) {
    toastError(error, '保存失败，请稍后重试')
  }
  finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <view class="record-editor">
    <view v-if="loading" class="record-editor__loading">
      <text>锅仔正在打开这顿饭…</text>
    </view>
    <template v-else>
      <view class="record-editor__intro">
        <view>
          <text class="record-editor__eyebrow">
            GUOZAI FOOD MEMORY
          </text>
          <text class="record-editor__title">
            把这一餐，改成你记得的样子
          </text>
          <text class="record-editor__sub">
            照片、心情和一句话，都可以重新整理。
          </text>
        </view>
      </view>

      <view class="record-editor__card">
        <view class="record-editor__section-head">
          <text>这一餐的照片</text><text>{{ photos.length }}/9</text>
        </view>
        <view class="record-editor__photo-grid">
          <view v-for="(photo, index) in photos" :key="`${photo.localUrl}-${index}`" class="record-editor__photo">
            <image :src="photo.localUrl" mode="aspectFill" />
            <view v-if="photo.uploading" class="record-editor__uploading">
              上传中…
            </view>
            <view class="record-editor__photo-tools">
              <text role="button" :aria-label="`第${index + 1}张照片前移`" :class="{ 'is-disabled': index === 0 }" @click.stop="movePhoto(index, -1)">
                ‹
              </text>
              <text role="button" :aria-label="`删除第${index + 1}张照片`" @click.stop="removePhoto(index)">
                ×
              </text>
              <text role="button" :aria-label="`第${index + 1}张照片后移`" :class="{ 'is-disabled': index === photos.length - 1 }" @click.stop="movePhoto(index, 1)">
                ›
              </text>
            </view>
          </view>
          <view v-if="photos.length < 9" class="record-editor__add" role="button" aria-label="添加菜品照片" @click="addImages">
            <Icon name="camera" :size="42" color="#EF5A3C" />
            <text>添加照片</text>
          </view>
        </view>
        <text class="record-editor__hint">
          第一张照片会作为封面，可用左右按钮调整顺序。
        </text>
      </view>

      <view class="record-editor__card">
        <text class="record-editor__label">
          菜名
        </text>
        <input v-model.trim="dishName" class="record-editor__input" placeholder="比如：番茄炒蛋" placeholder-class="record-editor__placeholder">
      </view>

      <view class="record-editor__card record-editor__row-card">
        <view class="record-editor__row-label">
          <Icon name="calendar" :size="32" color="#EF5A3C" /><text>记录日期</text>
        </view>
        <picker mode="date" :value="recordDate" @change="recordDate = $event.detail.value">
          <view class="record-editor__picker-value">
            {{ recordDate || '选择日期' }} <text>›</text>
          </view>
        </picker>
      </view>

      <view class="record-editor__card record-editor__row-card">
        <view class="record-editor__row-label">
          <Icon name="clock" :size="32" color="#EF5A3C" /><text>烹饪时间</text>
        </view>
        <view class="record-editor__picker-value" role="button" aria-label="选择烹饪时间" @click="chooseCookingTime">
          {{ cookingTime }} <text>›</text>
        </view>
      </view>

      <view class="record-editor__card">
        <MoodPicker v-model="selectedMood" :show-hero="false" title="吃完这顿，你是什么心情？" />
      </view>

      <view class="record-editor__card">
        <view class="record-editor__section-head">
          <text>留一句话给今天</text><text>{{ note.length }}/50</text>
        </view>
        <textarea v-model="note" class="record-editor__textarea" :maxlength="50" :auto-height="true" placeholder="今天这顿饭，有什么想记住的？" placeholder-class="record-editor__placeholder" />
      </view>

      <view class="record-editor__actions">
        <view class="record-editor__cancel pressable" role="button" aria-label="取消编辑" @click="emit('cancel')">
          取消
        </view>
        <view class="record-editor__save mrc-btn-primary pressable" :class="{ 'is-disabled': saving || uploading }" role="button" aria-label="保存记录修改" @click="save">
          {{ saving ? '正在保存…' : '保存修改' }}
        </view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.record-editor { padding: 0 32rpx calc(56rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.record-editor__loading { display: flex; min-height: 70vh; align-items: center; justify-content: center; color: var(--mrc-text-sub); font-size: 26rpx; }
.record-editor__intro { margin: 8rpx 0 24rpx; padding: 30rpx; border: 2rpx solid var(--mrc-border); border-radius: 34rpx; background: linear-gradient(145deg, var(--mrc-surface), var(--mrc-surface-peach)); box-shadow: var(--mrc-shadow-soft); }
.record-editor__eyebrow { display: block; color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; letter-spacing: 2rpx; }
.record-editor__title { display: block; margin-top: 12rpx; color: var(--mrc-text-strong); font-size: 35rpx; font-weight: 900; line-height: 1.35; }
.record-editor__sub { display: block; margin-top: 10rpx; color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.5; }
.record-editor__card { margin-bottom: 20rpx; padding: 26rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 30rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.record-editor__section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18rpx; color: var(--mrc-text-strong); font-size: 28rpx; font-weight: 800; }.record-editor__section-head text:last-child { color: var(--mrc-text-sub); font-size: 21rpx; font-weight: 500; }
.record-editor__photo-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12rpx; }.record-editor__photo, .record-editor__add { position: relative; aspect-ratio: 1; overflow: hidden; border-radius: 18rpx; }.record-editor__photo { background: var(--mrc-surface-2); }.record-editor__photo image { width: 100%; height: 100%; }.record-editor__add { display: flex; min-height: 180rpx; flex-direction: column; align-items: center; justify-content: center; gap: 8rpx; border: 2rpx dashed var(--mrc-border-strong); color: var(--mrc-text-sub); font-size: 21rpx; }
.record-editor__uploading { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: #fff; background: rgba(44, 24, 16, .5); font-size: 21rpx; }.record-editor__photo-tools { position: absolute; right: 8rpx; bottom: 8rpx; display: flex; overflow: hidden; border-radius: 22rpx; color: #fff; background: rgba(44, 24, 16, .72); }.record-editor__photo-tools text { display: flex; min-width: 38rpx; min-height: 38rpx; align-items: center; justify-content: center; font-size: 34rpx; line-height: 1; }.record-editor__photo-tools .is-disabled { opacity: .35; pointer-events: none; }.record-editor__hint { display: block; margin-top: 14rpx; color: var(--mrc-text-sub); font-size: 20rpx; }
.record-editor__label { display: block; margin-bottom: 12rpx; color: var(--mrc-text-deep); font-size: 25rpx; font-weight: 700; }.record-editor__input, .record-editor__textarea { width: 100%; box-sizing: border-box; border-radius: 20rpx; color: var(--mrc-text-deep); background: var(--mrc-surface-2); font-size: 28rpx; }.record-editor__input { height: 88rpx; padding: 0 22rpx; }.record-editor__textarea { min-height: 130rpx; padding: 18rpx 20rpx; line-height: 1.6; }.record-editor__placeholder { color: var(--mrc-text-light); }
.record-editor__row-card { display: flex; min-height: 88rpx; align-items: center; justify-content: space-between; padding-top: 18rpx; padding-bottom: 18rpx; }.record-editor__row-label { display: flex; align-items: center; gap: 12rpx; color: var(--mrc-text-deep); font-size: 26rpx; font-weight: 700; }.record-editor__picker-value { display: flex; min-height: 54rpx; align-items: center; gap: 8rpx; padding: 0 16rpx 0 20rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; color: var(--mrc-text-deep); background: var(--mrc-surface-sun); font-size: 24rpx; }.record-editor__picker-value text { color: var(--mrc-text-light); font-size: 31rpx; }
.record-editor__actions { display: grid; grid-template-columns: .8fr 1.4fr; gap: 14rpx; padding-top: 6rpx; }.record-editor__cancel, .record-editor__save { display: flex; min-height: 92rpx; align-items: center; justify-content: center; border-radius: 46rpx; font-size: 28rpx; font-weight: 800; }.record-editor__cancel { border: 2rpx solid var(--mrc-border); color: var(--mrc-text-deep); background: var(--mrc-surface); }.record-editor__save.is-disabled { opacity: .55; pointer-events: none; }
</style>
