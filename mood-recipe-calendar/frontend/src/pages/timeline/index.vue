<script setup lang="ts">
import {useImagePreview} from '@wot-ui/ui'
import {navBack} from '@/composables/useNavBar'
import {STATIC_BASE_URL} from '@/utils/assets'
import {computed, ref} from 'vue'
import LoadingState from '../../components/guozai/LoadingState.vue'
import EmptyState from '../../components/guozai/EmptyState.vue'
import {ensureLogin} from '../../utils/login'
import {toastError, toastSuccess} from '../../utils/toast'
import {deleteRecord, fetchRecords, type RecordItem} from '../../api/records'
import {fetchRecipeDetail} from '../../api/recipes'
import {COOKING_PROGRESS_KEY, saveCookingDraft} from '../../utils/cookingDraft'

definePage({ name: 'timeline', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '菜谱时光机' } })
const router = useRouter()
const {previewImage} = useImagePreview()
const loading = ref(true)
const records = ref<RecordItem[]>([])
const nearToday = ref(false)
const selected = ref<RecordItem | null>(null)
const openingRecipe = ref(false)
let lastTick = 0

const grouped = computed(() => {
  const map = new Map<string, RecordItem[]>()
  records.value.forEach(item => {
    const key = item.recordDate?.slice(0, 7) || '过去的日子'
    map.set(key, [...(map.get(key) || []), item])
  })
  return [...map.entries()].map(([month, items]) => ({ month, items }))
})
const memorySummary = computed(() => `留下 ${records.value.length} 顿饭 · 跨过 ${grouped.value.length} 个月`)

function monthNumber(month: string) {
  const value = Number(month.slice(5))
  return Number.isFinite(value) ? String(value).padStart(2, '0') : '—'
}

function monthYear(month: string) {
  return /^\d{4}-\d{2}$/.test(month) ? `${month.slice(0, 4)} 年` : month
}

function dayNumber(date?: string) {
  return date?.slice(8, 10) || '--'
}

function weekday(date?: string) {
  if (!date) return '那天'
  const day = new Date(`${date}T12:00:00`).getDay()
  return `周${'日一二三四五六'[day]}`
}

function guozaiSticker(index: number) {
  return index % 2
    ? STATIC_BASE_URL + '/static/guozai/action_10_thinking.png'
    : STATIC_BASE_URL + '/static/guozai/action_08_peek.png'
}
async function load() {
  loading.value = true
  try {
    records.value = await fetchRecords(await ensureLogin())
    const targetId = Number(uni.getStorageSync('mrc_timeline_record_id'))
    if (targetId) {
      selected.value = records.value.find(item => item.id === targetId) || null
      uni.removeStorageSync('mrc_timeline_record_id')
    }
  }
  catch (e: any) { toastError(e, '时光机加载失败，请重试') }
  finally { loading.value = false }
}

async function openLinkedRecipe(restart: boolean) {
  const id = Number(selected.value?.recipeId)
  if (!id || openingRecipe.value) return
  if (!restart) {
    const recordMood = selected.value?.moodTag || '平静'
    selected.value = null
    uni.navigateTo({
      url: `/pages/recipe/index?recipeId=${id}&mood=${encodeURIComponent(recordMood)}`,
    })
    return
  }
  openingRecipe.value = true
  try {
    await ensureLogin()
    const recipe = await fetchRecipeDetail(id)
    saveCookingDraft(recipe, selected.value?.moodTag || '平静')
    if (restart) uni.removeStorageSync(COOKING_PROGRESS_KEY)
    selected.value = null
    router.push({ name: 'cooking' })
  }
  catch (e: any) { toastError(e, '菜谱暂时打不开，请重试') }
  finally { openingRecipe.value = false }
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
  toastSuccess('记录已删除')
}

/** 有照片的记录，按时光机顺序用于左右翻看 */
const photoRecords = computed(() => records.value.filter(item => Boolean(item.imageUrl)))

function previewRecordPhoto(record: RecordItem | null) {
  const photos = photoRecords.value
  if (!record?.imageUrl || !photos.length)
    return
  previewImage({
    images: photos.map(item => item.imageUrl),
    startPosition: Math.max(0, photos.findIndex(item => item.id === record.id)),
    closeOnClick: true,
    loop: photos.length > 1,
  })
}
</script>

<template>
  <view class="timeline-page">
    <wd-navbar title="菜谱时光机" left-arrow safe-area-inset-top @click-left="navBack"  custom-style="background-color: transparent !important;" />
    <LoadingState v-if="loading" text="锅仔正在翻找你的餐桌回忆…" />
    <EmptyState v-else-if="!records.length" :image="`${STATIC_BASE_URL}/static/guozai/action_07_empty.png`" title="时光机还是空的" text="记录第一餐，让锅仔替你把今天收好。" action-text="去记录" @action="router.pushTab({ name: 'record' })" />
    <scroll-view v-else scroll-y enhanced :show-scrollbar="false" class="timeline-scroll" @scroll="onScroll">
      <view class="timeline-hero">
        <view class="timeline-hero__copy">
          <text class="timeline-kicker">GUOZAI TIME MACHINE</text>
          <text class="timeline-title">食光，正在倒带</text>
          <text class="timeline-sub">越往下越久以前，锅仔陪你翻回每一顿。</text>
          <view class="timeline-summary"><text>{{ memorySummary }}</text></view>
        </view>
        <view class="timeline-hero__orbit" aria-hidden="true" />
        <image class="timeline-hero__img" :src="`${STATIC_BASE_URL}/static/guozai/action_04_calendar.png`" mode="aspectFit" aria-label="抱着日历的锅仔" />
      </view>
      <view class="today-chip" :class="{ 'today-chip--active': nearToday }"><view class="today-chip__dot" /><text>{{ nearToday ? '此刻 · 今天' : '时间正在向过去流动' }}</text></view>
      <view v-for="group in grouped" :key="group.month" class="timeline-group">
        <view class="timeline-month">
          <text class="timeline-month__number">{{ monthNumber(group.month) }}</text>
          <view class="timeline-month__copy"><text>{{ monthYear(group.month) }}</text><text>{{ group.items.length }} 顿被好好记住</text></view>
          <view class="timeline-month__line" />
        </view>
        <view v-for="(item, itemIndex) in group.items" :key="item.id" class="timeline-item">
          <view class="timeline-date"><text>{{ dayNumber(item.recordDate) }}</text><text>{{ weekday(item.recordDate) }}</text></view>
          <view class="timeline-rail"><view class="timeline-dot" /><view class="timeline-rail__line" /></view>
          <view class="timeline-card pressable" :class="{ 'timeline-card--alt': itemIndex % 2 }" role="button" :aria-label="`查看 ${item.recordDate} 的${item.dishName}记录`" @click="selected=item">
            <view class="timeline-card__photo">
              <image class="timeline-card__img" :src="item.imageUrl" mode="aspectFill" lazy-load :aria-label="item.dishName" />
              <view class="timeline-card__shade" />
              <view class="timeline-card__caption">
                <text class="timeline-card__eyebrow">{{ item.moodTag }}时，吃了</text>
                <text class="timeline-card__dish">{{ item.dishName }}</text>
                <text class="timeline-card__meta">{{ item.cookingTime || 30 }} 分钟 · 点击翻开</text>
              </view>
              <image class="timeline-card__sticker" :src="guozaiSticker(itemIndex)" mode="aspectFit" aria-hidden="true" />
            </view>
            <view v-if="item.note" class="timeline-card__story"><text>“</text><text>{{ item.note }}</text></view>
            <view class="timeline-card__footer"><text>这一餐，锅仔替你收好了</text><text>查看详情</text></view>
          </view>
        </view>
      </view>
      <view class="timeline-end">
        <image :src="`${STATIC_BASE_URL}/static/guozai/action_08_peek.png`" mode="aspectFit"/>
        <text>再往下，就是更久以前的你啦。</text>
      </view>
    </scroll-view>
    <view v-if="selected" class="detail-mask" @click.self="selected = null"><view class="detail-sheet"><view class="detail-grabber" /><view class="detail-photo"><image :src="selected.imageUrl" mode="aspectFill" :aria-label="`查看${selected.dishName}的大图`" role="button" @click="previewRecordPhoto(selected)"/><view class="detail-photo__date"><text>{{ dayNumber(selected.recordDate) }}</text><text>{{ weekday(selected.recordDate) }}</text></view></view><text class="detail-kicker">锅仔的食光存档</text><text class="detail-title">{{selected.dishName}}</text><text class="detail-meta">{{selected.recordDate}} · {{selected.moodTag}} · {{ selected.cookingTime || 30 }} 分钟</text><text v-if="selected.note" class="detail-note">“{{selected.note}}”</text><view v-if="Number(selected.recipeId)" class="detail-recipe-actions"><view class="detail-recipe detail-recipe--secondary pressable" role="button" aria-label="查看关联菜谱" @click="openLinkedRecipe(false)">查看菜谱</view><view class="detail-recipe detail-recipe--primary pressable" role="button" aria-label="重新做这道菜" @click="openLinkedRecipe(true)">{{ openingRecipe ? '正在打开…' : '再做一次' }}</view></view><view class="detail-delete pressable" role="button" aria-label="删除这条记录" @click="removeSelected">删除这条记录</view><view class="detail-close pressable" role="button" aria-label="收起记录详情" @click="selected=null">收起</view></view></view>
    <wd-image-preview />
  </view>
</template>

<style lang="scss" scoped>
.timeline-page { height: 100vh; background: var(--mrc-bg); }
.timeline-scroll { height: calc(100vh - 120rpx); padding: 0 28rpx 100rpx; box-sizing: border-box; }
.timeline-hero { position: relative; display: flex; min-height: 330rpx; margin: 12rpx 0 24rpx; padding: 38rpx 30rpx; box-sizing: border-box; overflow: hidden; border: 2rpx solid var(--mrc-border); border-radius: 40rpx; background: linear-gradient(140deg, var(--mrc-surface-peach), var(--mrc-surface-sun)); box-shadow: var(--mrc-shadow-soft); }
.timeline-hero__copy { position: relative; z-index: 2; width: 68%; }
.timeline-kicker, .timeline-title, .timeline-sub { display: block; }
.timeline-kicker { color: var(--mrc-accent); font-size: 18rpx; font-weight: 800; letter-spacing: 2rpx; }
.timeline-title { margin-top: 16rpx; color: var(--mrc-text-deep); font-size: 44rpx; font-weight: 900; line-height: 1.25; }
.timeline-sub { margin-top: 14rpx; color: var(--mrc-text-sub); font-size: 24rpx; line-height: 1.6; }
.timeline-summary { display: inline-flex; min-height: 52rpx; align-items: center; margin-top: 22rpx; padding: 0 20rpx; border: 2rpx solid var(--mrc-border); border-radius: 26rpx; color: var(--mrc-text-deep); background: var(--mrc-surface); font-size: 21rpx; font-weight: 700; }
.timeline-hero__orbit { position: absolute; right: -82rpx; bottom: -110rpx; width: 330rpx; height: 330rpx; border: 34rpx solid var(--mrc-accent-soft); border-radius: 50%; opacity: .72; }
.timeline-hero__img { position: absolute; z-index: 1; right: -12rpx; bottom: -12rpx; width: 230rpx; height: 230rpx; }
.today-chip { display: flex; min-height: 64rpx; align-items: center; justify-content: center; gap: 12rpx; margin: 0 auto 30rpx; color: var(--mrc-text-sub); font-size: 22rpx; }
.today-chip__dot { width: 12rpx; height: 12rpx; border-radius: 50%; background: var(--mrc-border); box-shadow: 0 0 0 8rpx var(--mrc-surface); }
.today-chip--active { color: var(--mrc-accent); font-weight: 750; }
.today-chip--active .today-chip__dot { background: var(--mrc-primary); box-shadow: 0 0 0 8rpx var(--mrc-accent-soft); }
.timeline-group { padding-bottom: 18rpx; }
.timeline-month { display: grid; grid-template-columns: 82rpx auto 1fr; align-items: center; gap: 14rpx; margin: 0 0 22rpx; }
.timeline-month__number { color: var(--mrc-text-deep); font-size: 58rpx; font-weight: 900; line-height: 1; letter-spacing: -3rpx; font-variant-numeric: tabular-nums; }
.timeline-month__copy text { display: block; }
.timeline-month__copy text:first-child { color: var(--mrc-text-deep); font-size: 23rpx; font-weight: 800; }
.timeline-month__copy text:last-child { margin-top: 5rpx; color: var(--mrc-text-sub); font-size: 19rpx; }
.timeline-month__line { height: 2rpx; background: var(--mrc-border-light); }
.timeline-item { display: grid; grid-template-columns: 58rpx 28rpx minmax(0, 1fr); gap: 10rpx; padding-bottom: 28rpx; }
.timeline-date { padding-top: 10rpx; text-align: right; font-variant-numeric: tabular-nums; }
.timeline-date text { display: block; }
.timeline-date text:first-child { color: var(--mrc-text-deep); font-size: 31rpx; font-weight: 850; line-height: 1; }
.timeline-date text:last-child { margin-top: 8rpx; color: var(--mrc-text-sub); font-size: 18rpx; }
.timeline-rail { position: relative; display: flex; justify-content: center; }
.timeline-dot { position: relative; z-index: 2; width: 16rpx; height: 16rpx; margin-top: 12rpx; border: 5rpx solid var(--mrc-bg); border-radius: 50%; background: var(--mrc-primary); box-shadow: 0 0 0 3rpx var(--mrc-accent-soft); }
.timeline-rail__line { position: absolute; top: 32rpx; bottom: -30rpx; width: 3rpx; background: var(--mrc-border); }
.timeline-item:last-child .timeline-rail__line { background: linear-gradient(var(--mrc-border), transparent); }
.timeline-card { min-height: 88rpx; overflow: hidden; border: 2rpx solid var(--mrc-border-light); border-radius: 32rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); transition: transform 180ms ease-out, opacity 180ms ease-out; }
.timeline-card--alt { border-color: var(--mrc-border); }
.timeline-card__photo { position: relative; height: 330rpx; overflow: hidden; background: var(--mrc-surface-2); }
.timeline-card__img { width: 100%; height: 100%; }
.timeline-card__shade { position: absolute; inset: 28% 0 0; background: linear-gradient(transparent, rgba(25, 17, 12, .84)); }
.timeline-card__caption { position: absolute; z-index: 2; right: 24rpx; bottom: 24rpx; left: 24rpx; padding-right: 66rpx; }
.timeline-card__eyebrow, .timeline-card__dish, .timeline-card__meta { display: block; color: #fff; text-shadow: 0 2rpx 10rpx rgba(20, 12, 8, .32); }
.timeline-card__eyebrow { font-size: 20rpx; font-weight: 700; opacity: .86; }
.timeline-card__dish { margin-top: 7rpx; font-size: 36rpx; font-weight: 900; line-height: 1.3; }
.timeline-card__meta { margin-top: 8rpx; font-size: 20rpx; opacity: .9; }
.timeline-card__sticker { position: absolute; z-index: 3; right: -4rpx; bottom: -2rpx; width: 104rpx; height: 104rpx; filter: drop-shadow(0 4rpx 8rpx rgba(31, 20, 12, .18)); }
.timeline-card__story { display: flex; gap: 10rpx; padding: 22rpx 24rpx 18rpx; color: var(--mrc-text-deep); font-size: 23rpx; line-height: 1.6; }
.timeline-card__story text:first-child { color: var(--mrc-accent); font-family: Georgia, serif; font-size: 46rpx; line-height: .85; }
.timeline-card__footer { display: flex; min-height: 76rpx; align-items: center; justify-content: space-between; gap: 18rpx; padding: 0 24rpx; border-top: 2rpx solid var(--mrc-border-light); color: var(--mrc-text-sub); font-size: 20rpx; }
.timeline-card__footer text:last-child { color: var(--mrc-accent); font-weight: 800; }
.timeline-end { display: flex; flex-direction: column; align-items: center; padding: 28rpx 32rpx 44rpx; color: var(--mrc-text-sub); font-size: 22rpx; }
.timeline-end image { width: 130rpx; height: 130rpx; }
.detail-mask { position: fixed; inset: 0; z-index: 100; display: flex; align-items: flex-end; background: rgba(34, 24, 18, .58); }
.detail-sheet { width: 100%; max-height: 88vh; overflow-y: auto; padding: 14rpx 30rpx calc(34rpx + env(safe-area-inset-bottom)); box-sizing: border-box; border-radius: 42rpx 42rpx 0 0; background: var(--mrc-bg); }
.detail-grabber { width: 72rpx; height: 8rpx; margin: 0 auto 22rpx; border-radius: 8rpx; background: var(--mrc-border); }
.detail-photo { position: relative; height: 350rpx; overflow: hidden; border-radius: 30rpx; background: var(--mrc-surface-2); }
.detail-photo image { width: 100%; height: 100%; }
.detail-photo__date { position: absolute; top: 18rpx; left: 18rpx; display: flex; min-width: 88rpx; min-height: 88rpx; flex-direction: column; align-items: center; justify-content: center; border-radius: 24rpx; color: var(--mrc-text-deep); background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.detail-photo__date text:first-child { font-size: 31rpx; font-weight: 900; line-height: 1; }
.detail-photo__date text:last-child { margin-top: 5rpx; font-size: 18rpx; }
.detail-kicker, .detail-title, .detail-meta, .detail-note { display: block; }
.detail-kicker { margin-top: 24rpx; color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; letter-spacing: 2rpx; }
.detail-title { margin-top: 8rpx; color: var(--mrc-text-deep); font-size: 40rpx; font-weight: 900; }
.detail-meta { margin-top: 10rpx; color: var(--mrc-text-sub); font-size: 23rpx; }
.detail-note { margin-top: 18rpx; padding: 20rpx 22rpx; border-left: 6rpx solid var(--mrc-primary); border-radius: 0 20rpx 20rpx 0; color: var(--mrc-text-deep); background: var(--mrc-surface); font-size: 24rpx; line-height: 1.65; }
.detail-recipe-actions { display: grid; grid-template-columns: 1fr 1.25fr; gap: 14rpx; margin-top: 24rpx; }
.detail-recipe, .detail-delete, .detail-close { display: flex; min-height: 88rpx; align-items: center; justify-content: center; border-radius: 44rpx; font-size: 27rpx; font-weight: 800; }
.detail-recipe--secondary { border: 2rpx solid var(--mrc-border); color: var(--mrc-text-deep); background: var(--mrc-surface); }
.detail-recipe--primary { color: #fff; background: var(--mrc-primary-grad); box-shadow: var(--mrc-shadow-coral); }
.detail-delete { margin-top: 18rpx; border: 2rpx solid var(--mrc-border); color: var(--mrc-danger, #a54235); }
.detail-close { margin-top: 12rpx; color: var(--mrc-text-sub); background: var(--mrc-surface-2); }
.pressable:active { transform: scale(.985); opacity: .82; }
@media (max-width: 350px) {
  .timeline-scroll { padding-right: 20rpx; padding-left: 20rpx; }
  .timeline-item { grid-template-columns: 52rpx 22rpx minmax(0, 1fr); gap: 7rpx; }
  .timeline-card__photo { height: 292rpx; }
  .timeline-card__dish { font-size: 32rpx; }
  .timeline-hero__copy { width: 74%; }
  .timeline-hero__img { right: -34rpx; width: 200rpx; height: 200rpx; }
}
@media (prefers-reduced-motion: reduce) { .timeline-card { transition: none; } }
</style>
