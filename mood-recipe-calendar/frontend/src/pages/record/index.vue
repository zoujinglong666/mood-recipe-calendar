<script setup lang="ts">
import type { LearningReceipt, RecordItem } from '../../api/records'
import { computed, ref, watch } from 'vue'
import { STATIC_BASE_URL } from '@/utils/assets'
import { imgThumb } from '@/utils/imageUrl'
import { fetchRecord, fetchRecords, fetchStats, saveRecord, updateRecord } from '../../api/records'
import { uploadFile } from '../../api/request'
import Icon from '../../components/common/Icon.vue'
import GuozaiChoiceChips from '../../components/guozai/GuozaiChoiceChips.vue'
import GuozaiInsightCard from '../../components/guozai/GuozaiInsightCard.vue'
import MoodPicker from '../../components/guozai/MoodPicker.vue'
import SuccessModal from '../../components/guozai/SuccessModal.vue'
import { chooseImageFiles } from '../../utils/chooseImage'
import { createRequestId, RECORD_DRAFT_KEY } from '../../utils/cookingDraft'
import { ensureLogin, navigateToLogin } from '../../utils/login'
import { navBack } from '@/composables/useNavBar'
import { toast, toastError, toastSuccess } from '../../utils/toast'
import { haptic } from '@/utils/haptic'
import { bus, MRC_EVENTS } from '@/utils/bus'
import { useUserStore } from '../../stores/user'
import { requestDailyReminderNotice } from '../../api/weeklyPlans'
import { funnel } from '../../utils/funnel'

/** 与 calendar 页约定：历史补记时由 calendar 写入，record 页 onShow 读取作为记录日期。 */
const RECORD_DATE_KEY = 'mrc_record_date'

definePage({
  name: 'record',
  layout: 'tabbar',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '记一笔今天',
  },
})

const router = useRouter()
const userStore = useUserStore()
/** 转发给好友/群 */
onShareAppMessage(() => {
  const sharer = userStore.openid
  const path = `/pages/record/index${sharer ? `?sharer=${encodeURIComponent(sharer)}` : ''}`
  // 记录分享：发放「分享家」徽章 + 非会员当日 +1 次对话激励
  void recordShare('record').catch(() => {})
  return { title: '锅仔 · 记录与翻看每一餐', path }
})
/** 分享到朋友圈 */
onShareTimeline(() => {
  const sharer = userStore.openid
  return {
    title: '锅仔 · 记录与翻看每一餐',
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

/* ---- 记录域聚合：统计反馈条 + 最近食光（原「我的」页内容，随记录意图迁到本 tab） ---- */
const MOOD_IMG_MAP: Record<string, string> = {
  开心: `${STATIC_BASE_URL}/static/guozai/mood_01_happy.png`,
  平静: `${STATIC_BASE_URL}/static/guozai/mood_02_calm.png`,
  疲惫: `${STATIC_BASE_URL}/static/guozai/mood_03_tired.png`,
  焦虑: `${STATIC_BASE_URL}/static/guozai/mood_04_anxious.png`,
  难过: `${STATIC_BASE_URL}/static/guozai/mood_05_sad.png`,
  嘴馋: `${STATIC_BASE_URL}/static/guozai/mood_06_hungry.png`,
  低落: `${STATIC_BASE_URL}/static/guozai/mood_07_low.png`,
  想家: `${STATIC_BASE_URL}/static/guozai/mood_08_homesick.png`,
}
const stats = ref({ totalRecords: 0, totalDays: 0, currentStreak: 0 })
const recent = ref<RecordItem[]>([])

/** 记录 tab 内部分区：记一笔（表单）/ 食光（翻看），切换不丢表单状态 */
const recordTab = ref<'compose' | 'log'>('compose')

/** 统计与最近列表独立于表单加载：失败静默，不阻塞记一笔主流程 */
async function loadRecent() {
  if (!userStore.isLoggedIn) {
    stats.value = { totalRecords: 0, totalDays: 0, currentStreak: 0 }
    recent.value = []
    return
  }
  try {
    recent.value = (await fetchRecords()).slice(0, 3)
    fetchStats().then(s => { stats.value = s }).catch(() => {})
  }
  catch { /* 静默 */ }
}

function openRecentRecord(item: RecordItem) {
  router.push({ name: 'record-detail', params: { id: String(item.id) } }).catch(() => {})
}

/** 时光机入口：记录 tab 是「翻看」的意图归属地，「我的」页不再保留 */
function goTimeline() {
  router.push({ name: 'timeline' }).catch(() => {
    router.replace({ name: 'timeline' }).catch(() => {
      toast('暂时打不开时光机，请稍后再试')
    })
  })
}

// tabbar 页通过 switchTab 进入，无法带 query；从推荐页跳转时由 storage 暂存菜名与心情
onShow(async () => {
  void loadRecent()
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
      if (d.note)
        note.value = d.note
      if (d.recordDate)
        recordDate.value = String(d.recordDate)
      if (Array.isArray(d.feedback) && d.feedback.length)
        feedbackSelections.value = d.feedback
      if (Array.isArray(d.photos) && d.photos.length)
        photos.value = d.photos.map((url: string) => ({ localUrl: url, remoteUrl: url, uploading: false }))
    }
    // 历史补记：日历点过去某天时写入指定日期，这里接管为记录日期
    const backfillDate = uni.getStorageSync(RECORD_DATE_KEY)
    if (backfillDate)
      recordDate.value = String(backfillDate)
  }
  catch { /* ignore */ }
})

/** 防丢：新建记录时用户输入实时落盘；切走 tab / 后台后再回来可原样恢复，而非回到初始草稿 */
let draftPersistTimer: ReturnType<typeof setTimeout> | undefined
function persistDraft() {
  uni.setStorageSync(RECORD_DRAFT_KEY, {
    dish: dishName.value.trim(),
    mood: selectedMood.value,
    note: note.value,
    cookingTime: Number.parseInt(cookingTime.value, 10) || 30,
    recipeId: recipeId.value,
    exposureId: exposureId.value,
    recordDate: recordDate.value,
    clientRequestId: clientRequestId.value,
    feedback: feedbackSelections.value,
    // 只保留已上传成功的照片（临时本地路径重启后无效），避免恢复出坏图
    photos: photos.value.filter(p => p.remoteUrl).map(p => p.remoteUrl),
    source: 'record-form',
  })
}
function schedulePersistDraft() {
  if (draftPersistTimer) clearTimeout(draftPersistTimer)
  draftPersistTimer = setTimeout(persistDraft, 400)
}
watch(
  [dishName, selectedMood, note, cookingTime, recordDate, feedbackSelections, photos],
  () => { if (!isEditing.value) schedulePersistDraft() },
  { deep: true },
)

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
    // 本页是 tabBar 页：navigateBack 会抛「invalid tabbar page」，必须走带栈检查的 navBack
    navBack()
  }
}

async function publish() {
  if (submitting.value)
    return
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

  haptic('light')
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
      // P0 留存召回：记录完一顿饭后请求「日程提醒」订阅授权，后端每天 10:30 推「今天的菜已留好」
      requestDailyReminderNotice()
      // 新解锁成就随保存响应返回：轻量庆祝 + 累计未读，点亮「我的」页形象馆入口红点
      if (result.newlyUnlocked?.length) {
        toastSuccess(` 解锁成就：${result.newlyUnlocked.join('、')}`)
        const unread = Number(uni.getStorageSync('mrc_achv_unread') || 0)
        uni.setStorageSync('mrc_achv_unread', unread + result.newlyUnlocked.length)
      }
    }
    savedRecordId.value = saved.id
    funnel('record_saved', { dish: dishName.value, mood: selectedMood.value })
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
  // 保存成功就地切到「食光」tab：刚记的这顿就在最近列表里，不用跳页
  recordTab.value = 'log'
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
    <wd-navbar title="记一笔今天" safe-area-inset-top custom-style="background-color: transparent !important;" />

    <!-- 记录 tab 内部分区：记一笔（表单）/ 食光（翻看记录） -->
    <view class="record-tabs" role="tablist" aria-label="记录页分区">
      <view
        class="record-tabs__item"
        :class="{ 'is-active': recordTab === 'compose' }"
        role="tab"
        :aria-selected="recordTab === 'compose'"
        @click="recordTab = 'compose'"
      >
        <text class="record-tabs__label">记一笔</text>
      </view>
      <view
        class="record-tabs__item"
        :class="{ 'is-active': recordTab === 'log' }"
        role="tab"
        :aria-selected="recordTab === 'log'"
        @click="recordTab = 'log'"
      >
        <text class="record-tabs__label">食光</text>
      </view>
    </view>

    <view v-if="recordTab === 'compose'">
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

    </view>

    <!-- 食光 tab：统计反馈 + 最近记录，写完顺手就能翻 -->
    <view v-else class="record-log">
    <view v-if="!userStore.isLoggedIn" class="record-log__guest">
      <image :src="`${STATIC_BASE_URL}/static/guozai/action_08_peek.png`" mode="aspectFit" />
      <text class="record-log__guest-title">登录后才能翻看食光</text>
      <text class="record-log__guest-sub">记录跟着账号走，换设备也不会丢</text>
      <view class="record-log__guest-btn" role="button" aria-label="微信登录" @click="navigateToLogin()">
        微信登录
      </view>
    </view>
    <!-- 统计反馈条：记录的「坚持感」留在记录域，替代原「我的」页统计卡 -->
    <view v-if="userStore.isLoggedIn" class="record-stats" role="button" aria-label="查看全部饮食记录" @click="goTimeline">
      <view class="record-stats__main">
        <text class="record-stats__num">{{ stats.totalRecords }}</text>
        <text class="record-stats__unit">顿已收好</text>
        <text class="record-stats__dot">·</text>
        <text class="record-stats__num">{{ stats.currentStreak }}</text>
        <text class="record-stats__unit">天连续打卡</text>
      </view>
      <text class="record-stats__link">去翻看 ›</text>
    </view>

    <!-- 最近的食光：原「我的」页记录列表迁到记录 tab，写完顺手就能翻 -->
    <view v-if="userStore.isLoggedIn" class="record-recent">
      <view class="record-recent__head" role="button" aria-label="查看全部饮食记录" @click="goTimeline">
        <text class="record-recent__title">最近的食光</text>
        <text class="record-recent__all">查看全部 ›</text>
      </view>
      <view v-if="!recent.length" class="record-recent__empty" role="button" aria-label="去记一笔" @click="recordTab = 'compose'">
        <image :src="`${STATIC_BASE_URL}/static/guozai/action_03_camera.png`" mode="aspectFit" />
        <text class="record-recent__empty-title">第一顿饭，等你来记</text>
        <text class="record-recent__empty-sub">记下后，锅仔会把它收进这里 · 点这个就去记</text>
      </view>
      <template v-else>
        <view
          v-for="item in recent"
          :key="item.id"
          class="record-recent__item"
          role="button"
          :aria-label="`查看 ${item.recordDate} 的${item.dishName}记录`"
          @click="openRecentRecord(item)"
        >
          <image class="record-recent__img" :src="imgThumb(item.imageUrl)" mode="aspectFill" lazy-load />
          <view class="record-recent__main">
            <view class="record-recent__title-row">
              <text class="record-recent__dish">{{ item.dishName }}</text>
              <image class="record-recent__mood" :src="MOOD_IMG_MAP[item.moodTag] || MOOD_IMG_MAP['开心']" mode="aspectFit" />
            </view>
            <text class="record-recent__meta">{{ item.moodTag }} · {{ item.recordDate?.slice(5) }} · {{ item.cookingTime || 30 }} 分钟</text>
          </view>
          <text class="record-recent__arrow">›</text>
        </view>
      </template>
    </view>

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

/* 记录 tab 内部分区：记一笔 / 食光，胶囊分段式 */
.record-tabs { display: flex; gap: 8rpx; margin: 8rpx 0 20rpx; padding: 8rpx; border-radius: 999rpx; background: var(--mrc-surface-2); }
.record-tabs__item { display: flex; flex: 1; align-items: center; justify-content: center; height: 72rpx; border-radius: 999rpx; color: var(--mrc-text-sub); font-size: 27rpx; font-weight: 600; transition: background-color .2s ease, color .2s ease; }
.record-tabs__item.is-active { background: var(--mrc-surface); color: var(--mrc-accent); font-weight: 800; box-shadow: var(--mrc-shadow-sm); }
.record-log__guest { display: flex; min-height: 560rpx; flex-direction: column; align-items: center; justify-content: center; gap: 12rpx; }
.record-log__guest image { width: 200rpx; height: 200rpx; opacity: .85; }
.record-log__guest-title { color: var(--mrc-text-deep); font-size: 30rpx; font-weight: 800; }
.record-log__guest-sub { color: var(--mrc-text-sub); font-size: 22rpx; }
.record-log__guest-btn { margin-top: 16rpx; padding: 18rpx 56rpx; border-radius: 999rpx; background: var(--mrc-primary-grad); color: #fff; font-size: 27rpx; font-weight: 800; box-shadow: var(--mrc-shadow-coral); }
.record-log__guest-btn:active { transform: scale(.97); }

.record-intro { position: relative; display: flex; min-height: 176rpx; margin: 4rpx 0 24rpx; padding: 28rpx 28rpx 24rpx; overflow: hidden; box-sizing: border-box; border: 2rpx solid var(--mrc-border); border-radius: 36rpx; background: linear-gradient(145deg, var(--mrc-surface), var(--mrc-surface-peach)); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }
.record-intro__copy { position: relative; z-index: 1; max-width: 76%; }
.record-intro__eyebrow, .record-intro__title, .record-intro__sub { display: block; }
.record-intro__eyebrow { margin-bottom: 8rpx; color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; letter-spacing: 2rpx; }
.record-intro__title { color: var(--mrc-text-strong); font-size: 34rpx; font-weight: var(--mrc-fw-heavy); line-height: 1.3; }
.record-intro__sub { margin-top: 8rpx; color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.45; }
.record-intro__guozai { position: absolute; right: -4rpx; bottom: -12rpx; width: 142rpx; height: 142rpx; }
.record-backfill-tip { display: flex; align-items: center; gap: 12rpx; margin: 0 0 20rpx; padding: 18rpx 22rpx; border-radius: 24rpx; color: var(--mrc-accent); background: var(--mrc-surface-sun); font-size: 22rpx; line-height: 1.4; }
.record-backfill-tip__date { font-weight: 800; }

/* 统计反馈条：一行读完「已记 N 顿 · 连续 N 天」，整条可点进时光机 */
.record-stats { display: flex; align-items: center; justify-content: space-between; margin: 0 0 24rpx; padding: 20rpx 26rpx; border: 2rpx var(--mrc-border-light) solid; border-radius: 26rpx; background: var(--mrc-surface-2); }
.record-stats__main { display: flex; align-items: baseline; gap: 6rpx; }
.record-stats__num { color: var(--mrc-accent); font-size: 34rpx; font-weight: 900; }
.record-stats__unit { color: var(--mrc-text-sub); font-size: 22rpx; font-weight: 600; }
.record-stats__dot { margin: 0 6rpx; color: var(--mrc-text-light); }
.record-stats__link { color: var(--mrc-text-sub); font-size: 22rpx; font-weight: 700; }

/* 最近的食光：紧凑列表，承接原「我的」页记录域 */
.record-recent { margin-bottom: 24rpx; padding: 26rpx 28rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 32rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }
.record-recent__head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18rpx; }
.record-recent__title { color: var(--mrc-text-strong); font-size: 29rpx; font-weight: 800; }
.record-recent__all { color: var(--mrc-accent); font-size: 22rpx; font-weight: 700; }
.record-recent__empty { display: flex; flex-direction: column; align-items: center; gap: 10rpx; padding: 40rpx 0 20rpx; color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.6; text-align: center; }
.record-recent__empty:active { opacity: .75; }
.record-recent__empty image { width: 180rpx; height: 180rpx; opacity: .85; }
.record-recent__empty-title { color: var(--mrc-text-deep); font-size: 28rpx; font-weight: 800; }
.record-recent__empty-sub { max-width: 460rpx; font-size: 21rpx; }
.record-recent__item { display: flex; align-items: center; gap: 18rpx; padding: 14rpx 0; border-top: 2rpx solid var(--mrc-border-light); }
.record-recent__item:first-of-type { border-top: none; }
.record-recent__img { width: 104rpx; height: 104rpx; flex-shrink: 0; border-radius: 18rpx; background: var(--mrc-surface-2); }
.record-recent__main { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 6rpx; }
.record-recent__title-row { display: flex; align-items: center; gap: 10rpx; }
.record-recent__dish { overflow: hidden; color: var(--mrc-text-deep); font-size: 28rpx; font-weight: 800; text-overflow: ellipsis; white-space: nowrap; }
.record-recent__mood { width: 36rpx; height: 36rpx; flex-shrink: 0; }
.record-recent__meta { overflow: hidden; color: var(--mrc-text-sub); font-size: 20rpx; text-overflow: ellipsis; white-space: nowrap; }
.record-recent__arrow { color: var(--mrc-text-light); font-size: 30rpx; }

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
