<script setup lang="ts">
import { computed, ref } from 'vue'
import { createFridgeItem, consumeFridgeItem, deleteFridgeItem, fetchFridgeItems, fetchFridgeSummary, fetchStorageAdvice, notifyFridgeExpiring, recognizeFridgeImage, updateFridgeItem, type FridgeItem, type FridgeSummary, type RecognizedItem } from '@/api/fridge'
import GuozaiButton from '@/components/guozai/GuozaiButton.vue'
import { navBack } from '@/composables/useNavBar'
import { uploadFile } from '@/api/request'
import { chooseImageFile } from '@/utils/chooseImage'
import { ensureLogin } from '@/utils/login'
import { useUserStore } from '@/stores/user'
import { toast, toastError } from '@/utils/toast'

definePage({ name: 'fridge', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '我的冰箱' } })

const now = new Date()
const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
const items = ref<FridgeItem[]>([])
const summary = ref<FridgeSummary>({ total: 0, soon: 0, expired: 0, priorityItems: [] })
const loading = ref(true)
const saving = ref(false)
const formOpen = ref(false)
const editingId = ref<number>()
const error = ref('')
const formError = ref('')
const form = ref({ name: '', quantity: '1', unit: '份', purchasedOn: today, expiresOn: '', note: '' })

// 手动录入时的实时存放提醒（如输入「香蕉」提示别放冰箱）
const formAdvice = ref<{ level: 'AVOID' | 'WORSE', tip: string } | null>(null)
let adviceTimer: ReturnType<typeof setTimeout> | undefined

watch(() => form.value.name, (name) => {
  if (adviceTimer) clearTimeout(adviceTimer)
  const trimmed = name?.trim()
  if (!trimmed) {
    formAdvice.value = null
    return
  }
  // 防抖，避免每敲一个字都请求
  adviceTimer = setTimeout(async () => {
    try {
      const advice = await fetchStorageAdvice(trimmed)
      formAdvice.value = advice.inFridgeWarned && advice.level
        ? { level: advice.level as 'AVOID' | 'WORSE', tip: advice.tip || '' }
        : null
    }
    catch {
      formAdvice.value = null
    }
  }, 350)
})

const soonItems = computed(() => items.value.filter(item => item.status === 'SOON'))
const expiredItems = computed(() => items.value.filter(item => item.status === 'EXPIRED'))
const otherItems = computed(() => items.value.filter(item => item.status === 'FRESH' || item.status === 'NO_DATE'))

// ===== 会员专享：拍照识别 =====
const userStore = useUserStore()
/** 会员判定：isMember=1 且会员未过期 */
const isMember = computed(() => {
  const info = userStore.userInfo
  if (!info || info.isMember !== 1 || !info.memberExpire) return false
  return new Date(info.memberExpire).getTime() > Date.now()
})

/** 识别确认清单：每条可勾选/改名，确认后才入库 */
interface DraftItem extends RecognizedItem { checked: boolean }
const recognizing = ref(false)
const recognizeOpen = ref(false)
const recognizeNotice = ref('')
const drafts = ref<DraftItem[]>([])
const recognizedCount = computed(() => drafts.value.filter(d => d.checked).length)

/** 非会员：拦截并引导开通，绝不发起识别请求 */
function requireMember(): boolean {
  if (isMember.value) return true
  uni.showModal({
    title: '会员专享功能',
    content: '冰箱拍照识别（自动认食材、算保质期、临期提醒）为会员功能，开通后即可使用。',
    confirmText: '去开通',
    cancelText: '暂不',
    success: (res) => { if (res.confirm) uni.navigateTo({ url: '/pages/membership/index' }) },
  })
  return false
}

/** 拍照/选图 → 上传 → 识别 → 弹确认清单 */
function startRecognize() {
  if (!requireMember()) return
  // 复用项目统一的选图工具：已处理相册权限被拒、隐私协议未同意等失败场景
  chooseImageFile({ onSelected: path => void runRecognize(path) })
}

async function runRecognize(localPath: string) {
  recognizing.value = true
  try {
    await ensureLogin()
    const uploaded = await uploadFile(localPath)
    if (!uploaded?.url) throw new Error('照片上传失败，请重试')
    const result = await recognizeFridgeImage(uploaded.url)
    if (result.degraded || !result.items?.length) {
      toastError(new Error(result.notice || '没认出食材'), '识别失败')
      return
    }
    drafts.value = result.items.map(item => ({ ...item, checked: true }))
    recognizeNotice.value = result.notice || ''
    recognizeOpen.value = true
  }
  catch (e: any) {
    toastError(e, '识别失败，请稍后再试')
  }
  finally {
    recognizing.value = false
  }
}

function closeRecognize() {
  if (saving.value) return
  recognizeOpen.value = false
  drafts.value = []
}

/** 确认清单 → 批量入库（保质期已由后端按常识库算好） */
async function confirmRecognize() {
  const picked = drafts.value.filter(d => d.checked && d.name.trim())
  if (!picked.length) {
    toast('请至少勾选一项食材')
    return
  }
  // 入库前拦截：含「会加速变质」的食材时，二次确认让用户知道放冰箱是错的
  const risky = picked.filter(d => d.storageLevel === 'WORSE')
  if (risky.length) {
    const confirmed = await new Promise<boolean>((resolve) => {
      uni.showModal({
        title: '这些食材其实不该放冰箱',
        content: `${risky.map(d => d.name).join('、')} 放冰箱会坏得更快，确定仍要放入吗？`,
        confirmText: '仍要放入',
        cancelText: '再看看',
        success: res => resolve(res.confirm),
        fail: () => resolve(false),
      })
    })
    if (!confirmed) return
  }
  saving.value = true
  try {
    for (const draft of picked) {
      await createFridgeItem({
        name: draft.name.trim(),
        quantity: 1,
        unit: '份',
        purchasedOn: today,
        expiresOn: draft.expiresOn || undefined,
        note: draft.shelfLifeMatched ? '' : '保质期为估算，请按实际情况调整',
      })
    }
    recognizeOpen.value = false
    drafts.value = []
    await load()
    uni.showToast({ title: `已放入 ${picked.length} 项食材`, icon: 'success' })
  }
  catch (e) {
    toastError(e, '保存失败，请稍后再试')
  }
  finally {
    saving.value = false
  }
}

/** 发送临期提醒（会员专享） */
async function sendExpiryNotice() {
  if (!requireMember()) return
  try {
    const result = await notifyFridgeExpiring()
    if (result.sent) toast(`已提醒你 ${result.count} 项临期食材：${result.names}`)
    else toast('暂时没有临期食材，冰箱状态不错')
  }
  catch (e) {
    toastError(e, '提醒发送失败')
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    await ensureLogin()
    const [list, overview] = await Promise.all([fetchFridgeItems(), fetchFridgeSummary()])
    items.value = list
    summary.value = overview
  }
  catch (e) {
    error.value = '冰箱暂时打不开，请稍后再试'
    toastError(e, error.value)
  }
  finally {
    loading.value = false
  }
}

onShow(load)

function openCreate() {
  editingId.value = undefined
  formError.value = ''
  form.value = { name: '', quantity: '1', unit: '份', purchasedOn: today, expiresOn: '', note: '' }
  formOpen.value = true
}

function openEdit(item: FridgeItem) {
  editingId.value = item.id
  formError.value = ''
  form.value = { name: item.name, quantity: String(item.quantity), unit: item.unit, purchasedOn: item.purchasedOn || today, expiresOn: item.expiresOn || '', note: item.note || '' }
  formOpen.value = true
}

function closeForm() {
  if (!saving.value) formOpen.value = false
}

function setDate(field: 'purchasedOn' | 'expiresOn', event: any) {
  form.value[field] = event.detail.value
}

function validateForm() {
  const quantity = Number(form.value.quantity)
  if (!form.value.name.trim()) return '请填写食材名称'
  if (!Number.isFinite(quantity) || quantity <= 0) return '数量要大于 0'
  if (!form.value.unit.trim()) return '请填写单位'
  if (form.value.expiresOn && form.value.purchasedOn && form.value.expiresOn < form.value.purchasedOn) return '保质期不能早于购买日期'
  return ''
}

async function save() {
  formError.value = validateForm()
  if (formError.value) return
  saving.value = true
  try {
    const payload = { name: form.value.name.trim(), quantity: Number(form.value.quantity), unit: form.value.unit.trim(), purchasedOn: form.value.purchasedOn, expiresOn: form.value.expiresOn || undefined, note: form.value.note.trim() || undefined }
    if (editingId.value) await updateFridgeItem(editingId.value, payload)
    else await createFridgeItem(payload)
    formOpen.value = false
    await load()
    uni.showToast({ title: editingId.value ? '已更新' : '已放入冰箱', icon: 'success' })
  }
  catch (e) {
    toastError(e, '保存失败，请稍后再试')
  }
  finally {
    saving.value = false
  }
}

async function consume(item: FridgeItem) {
  try {
    await consumeFridgeItem(item.id)
    await load()
    uni.showToast({ title: '已记为用掉 1 份', icon: 'success' })
  }
  catch (e) {
    toastError(e, '更新库存失败')
  }
}

function remove(item: FridgeItem) {
  uni.showModal({ title: '移出冰箱？', content: `确认删除「${item.name}」吗？`, success: async (result) => {
    if (!result.confirm) return
    try {
      await deleteFridgeItem(item.id)
      await load()
    }
    catch (e) {
      toastError(e, '删除失败')
    }
  } })
}

function statusText(item: FridgeItem) {
  if (item.status === 'EXPIRED') return '已过期'
  if (item.status === 'SOON') return item.daysLeft === 0 ? '今天到期' : `${item.daysLeft}天内到期`
  if (item.status === 'NO_DATE') return '未设置日期'
  return '新鲜'
}
</script>

<template>
  <view class="fridge-page">
    <wd-navbar title="我的冰箱" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <view v-if="loading" class="state-card">锅仔正在清点你的冰箱…</view>
    <view v-else-if="error" class="state-card state-card--error" @click="load">点击重试</view>
    <template v-else>
      <view class="overview-card">
        <view class="overview-card__copy">
          <text class="eyebrow">FRIDGE · TODAY</text>
          <text class="overview-card__title">今天先吃这些</text>
          <text class="overview-card__copy-text">{{ summary.soon ? `有 ${summary.soon} 项食材需要优先消耗` : '暂时没有临期食材，冰箱状态不错' }}</text>
        </view>
        <view class="overview-card__count"><text>{{ summary.soon }}</text><text>临期</text></view>
      </view>

      <view class="page-heading"><view><text class="page-heading__title">冰箱库存</text><text class="page-heading__sub">{{ summary.total }} 项食材 · 做完一项就记得划掉</text></view><GuozaiButton variant="secondary" :block="false" aria-label="添加食材" @click="openCreate">＋ 添加</GuozaiButton></view>

      <!-- 会员专享：拍照识别 + 临期提醒 -->
      <view class="member-actions">
        <view class="member-action member-action--scan" role="button" aria-label="拍照识别食材" @click="startRecognize">
          <text class="member-action__badge">会员</text>
          <text class="member-action__title">拍照识别食材</text>
          <text class="member-action__sub">拍一张冰箱照，自动认食材、算保质期</text>
        </view>
        <view class="member-action member-action--notify" role="button" aria-label="发送临期提醒" @click="sendExpiryNotice">
          <text class="member-action__badge">会员</text>
          <text class="member-action__title">临期提醒</text>
          <text class="member-action__sub">快过期时提醒你，别浪费</text>
        </view>
      </view>

      <view v-if="recognizing" class="recognizing-card">锅仔正在看照片里的食材…</view>

      <view v-if="!items.length" class="empty-card"><text class="empty-card__title">冰箱还没开张</text><text class="empty-card__copy">把手边的食材放进来，锅仔才能帮你优先消耗、少买一点。</text><GuozaiButton aria-label="添加第一项食材" @click="openCreate">添加第一项食材</GuozaiButton></view>

      <template v-else>
        <view v-if="expiredItems.length" class="inventory-section"><view class="section-title"><text>已经过期</text><text>{{ expiredItems.length }} 项</text></view><view v-for="item in expiredItems" :key="item.id" class="food-row food-row--expired"><view class="food-row__main"><text class="food-row__name">{{ item.name }}</text><text class="food-row__meta">{{ item.quantity }} {{ item.unit }} · {{ statusText(item) }}</text></view><view class="food-row__actions"><text @click="openEdit(item)">编辑</text><text @click="remove(item)">删除</text></view></view></view>
        <view v-if="soonItems.length" class="inventory-section"><view class="section-title"><text>优先消耗</text><text>{{ soonItems.length }} 项</text></view><view v-for="item in soonItems" :key="item.id" class="food-row food-row--soon"><view class="food-row__main"><text class="food-row__name">{{ item.name }}</text><text class="food-row__meta">{{ item.quantity }} {{ item.unit }} · {{ statusText(item) }}</text></view><view class="food-row__actions"><text @click="consume(item)">用掉 1{{ item.unit }}</text><text @click="openEdit(item)">编辑</text></view></view></view>
        <view v-if="otherItems.length" class="inventory-section"><view class="section-title"><text>其他食材</text><text>{{ otherItems.length }} 项</text></view><view v-for="item in otherItems" :key="item.id" class="food-row"><view class="food-row__main"><text class="food-row__name">{{ item.name }}</text><text class="food-row__meta">{{ item.quantity }} {{ item.unit }} · {{ statusText(item) }}</text></view><view class="food-row__actions"><text @click="consume(item)">用掉 1{{ item.unit }}</text><text @click="openEdit(item)">编辑</text></view></view></view>
      </template>
    </template>

    <!-- 识别结果确认清单：识别只作预填，用户勾选/修改后才入库 -->
    <view v-if="recognizeOpen" class="form-mask" @click="closeRecognize"><view class="form-sheet" @click.stop><view class="form-sheet__head"><view><text class="eyebrow">AI RECOGNIZED</text><text class="form-sheet__title">确认要放入的食材</text></view><text class="form-sheet__close" @click="closeRecognize">×</text></view><text class="recognize-hint">{{ recognizeNotice || '已自动识别并估算保质期，勾选后放入冰箱' }}</text><view v-if="!drafts.length" class="recognize-empty">没有识别到食材，换个角度再拍一张吧</view><view v-for="(draft, idx) in drafts" :key="idx" class="draft-row"><view class="draft-row__check" :class="{ 'is-on': draft.checked }" @click="draft.checked = !draft.checked">{{ draft.checked ? '✓' : '' }}</view><view class="draft-row__body"><input v-model="draft.name" class="draft-row__name" :maxlength="80"><text class="draft-row__meta">{{ draft.shelfLifeDays }} 天保质期 · {{ draft.expiresOn }} 到期{{ draft.shelfLifeMatched ? '' : '（估算）' }}</text><view v-if="draft.storageLevel" class="draft-row__warn" :class="`is-${draft.storageLevel.toLowerCase()}`"><text class="draft-row__warn-title">{{ draft.storageLevel === 'WORSE' ? '⚠ 放冰箱会坏得更快' : '· 其实不用放冰箱' }}</text><text class="draft-row__warn-tip">{{ draft.storageTip }}</text></view></view></view><GuozaiButton :loading="saving" aria-label="确认放入冰箱" @click="confirmRecognize">放入冰箱（{{ recognizedCount }} 项）</GuozaiButton></view></view>

    <view v-if="formOpen" class="form-mask" @click="closeForm"><view class="form-sheet" @click.stop><view class="form-sheet__head"><view><text class="eyebrow">FRIDGE ITEM</text><text class="form-sheet__title">{{ editingId ? '编辑食材' : '放入冰箱' }}</text></view><text class="form-sheet__close" @click="closeForm">×</text></view><view class="form-field"><text>食材名称</text><input v-model="form.name" placeholder="例如：番茄" :maxlength="80"></view><view v-if="formAdvice" class="form-advice" :class="`is-${formAdvice.level.toLowerCase()}`"><text class="form-advice__title">{{ formAdvice.level === 'WORSE' ? '⚠ 这种食材放冰箱会坏得更快' : '· 这种食材其实不用放冰箱' }}</text><text class="form-advice__tip">{{ formAdvice.tip }}</text></view><view class="form-line"><view class="form-field"><text>数量</text><input v-model="form.quantity" type="digit" placeholder="1"></view><view class="form-field"><text>单位</text><input v-model="form.unit" placeholder="份" :maxlength="20"></view></view><view class="form-line"><view class="form-field"><text>购买日期</text><picker mode="date" :value="form.purchasedOn" @change="setDate('purchasedOn', $event)"><view class="date-field">{{ form.purchasedOn || '选择日期' }}</view></picker></view><view class="form-field"><text>保质期（可选）</text><picker mode="date" :value="form.expiresOn || today" @change="setDate('expiresOn', $event)"><view class="date-field">{{ form.expiresOn || '未设置' }}</view></picker></view></view><view class="form-field"><text>备注（可选）</text><input v-model="form.note" placeholder="例如：切开了，尽快吃" :maxlength="240"></view><text v-if="formError" class="form-error">{{ formError }}</text><GuozaiButton :loading="saving" aria-label="保存食材" @click="save">保存食材</GuozaiButton></view></view>
  </view>
</template>

<style lang="scss" scoped>
.fridge-page { min-height: 100vh; padding: 0 28rpx calc(48rpx + env(safe-area-inset-bottom)); box-sizing: border-box; color: var(--mrc-text); background: var(--mrc-bg); }.eyebrow { display: block; color: var(--mrc-accent); font-size: 18rpx; font-weight: 800; letter-spacing: 2rpx; }.overview-card { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; margin: 8rpx 0 28rpx; padding: 28rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 30rpx; background: linear-gradient(135deg, var(--mrc-text-deep), #66402d); box-shadow: var(--mrc-shadow-lift); color: #fff; }.overview-card__copy { display: flex; min-width: 0; flex-direction: column; gap: 8rpx; }.overview-card__title { font-size: 36rpx; font-weight: 800; line-height: 1.2; }.overview-card__copy-text { color: rgba(255,255,255,.72); font-size: 21rpx; line-height: 1.45; }.overview-card .eyebrow { color: #ffb19b; }.overview-card__count { display: flex; width: 104rpx; height: 104rpx; flex: 0 0 auto; flex-direction: column; align-items: center; justify-content: center; border-radius: 50%; background: var(--mrc-primary); color: var(--mrc-text-deep); }.overview-card__count text:first-child { font-size: 34rpx; font-weight: 800; line-height: 1; }.overview-card__count text:last-child { margin-top: 8rpx; font-size: 18rpx; font-weight: 700; }.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; margin-bottom: 18rpx; }.page-heading__title { display: block; color: var(--mrc-text-deep); font-size: 31rpx; font-weight: 800; }.page-heading__sub { display: block; margin-top: 6rpx; color: var(--mrc-text-sub); font-size: 20rpx; }.page-heading .gz-btn { min-height: 70rpx; padding: 0 20rpx; font-size: 21rpx; }.state-card, .empty-card, .inventory-section { border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }.state-card { padding: 100rpx 28rpx; text-align: center; color: var(--mrc-text-sub); font-size: 24rpx; }.state-card--error { color: var(--mrc-accent); }.empty-card { display: flex; flex-direction: column; gap: 16rpx; padding: 38rpx 28rpx; text-align: center; }.empty-card__title { color: var(--mrc-text-deep); font-size: 30rpx; font-weight: 800; }.empty-card__copy { color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.55; }.inventory-section { overflow: hidden; margin-bottom: 18rpx; }.section-title { display: flex; align-items: center; justify-content: space-between; padding: 22rpx 24rpx; border-bottom: 2rpx solid var(--mrc-border-light); color: var(--mrc-text-deep); font-size: 24rpx; font-weight: 800; }.section-title text:last-child { color: var(--mrc-text-sub); font-size: 19rpx; font-weight: 500; }.food-row { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; min-height: 96rpx; padding: 14rpx 24rpx; border-bottom: 2rpx solid var(--mrc-border-light); }.food-row:last-child { border-bottom: 0; }.food-row__main { display: flex; min-width: 0; flex-direction: column; gap: 7rpx; }.food-row__name { color: var(--mrc-text-deep); font-size: 25rpx; font-weight: 750; }.food-row__meta { color: var(--mrc-text-sub); font-size: 20rpx; }.food-row--soon .food-row__meta { color: var(--mrc-accent); }.food-row--expired .food-row__name, .food-row--expired .food-row__meta { color: var(--mrc-text-light); }.food-row__actions { display: flex; flex: 0 0 auto; gap: 14rpx; }.food-row__actions text { display: flex; min-height: 56rpx; align-items: center; color: var(--mrc-accent); font-size: 19rpx; font-weight: 700; }.food-row--expired .food-row__actions text { color: var(--mrc-text-sub); }.form-mask { position: fixed; z-index: 50; inset: 0; display: flex; align-items: flex-end; background: rgba(61, 37, 25, .38); }.form-sheet { width: 100%; padding: 28rpx 28rpx calc(28rpx + env(safe-area-inset-bottom)); border-radius: 32rpx 32rpx 0 0; background: var(--mrc-surface); box-shadow: 0 -14rpx 36rpx rgba(61, 37, 25, .18); box-sizing: border-box; }.form-sheet__head { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 22rpx; }.form-sheet__title { display: block; margin-top: 7rpx; color: var(--mrc-text-deep); font-size: 31rpx; font-weight: 800; }.form-sheet__close { color: var(--mrc-text-sub); font-size: 48rpx; line-height: .7; }.form-line { display: grid; grid-template-columns: 1fr 1fr; gap: 14rpx; }.form-field { display: flex; flex-direction: column; gap: 9rpx; margin-bottom: 16rpx; color: var(--mrc-text-deep); font-size: 21rpx; font-weight: 700; }.form-field input, .date-field { width: 100%; min-height: 72rpx; padding: 0 18rpx; border: 2rpx solid var(--mrc-border); border-radius: 18rpx; background: var(--mrc-bg); color: var(--mrc-text-deep); font-size: 22rpx; box-sizing: border-box; }.date-field { display: flex; align-items: center; font-weight: 500; }.form-error { display: block; margin: -4rpx 0 14rpx; color: var(--mrc-color-danger); font-size: 20rpx; }
/* 会员专享入口 */
.member-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 16rpx; margin-bottom: 20rpx; }
.member-action { position: relative; display: flex; flex-direction: column; gap: 8rpx; padding: 24rpx 22rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 26rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); overflow: hidden; }
.member-action--scan { background: linear-gradient(135deg, var(--mrc-surface-peach), var(--mrc-surface)); }
.member-action--notify { background: linear-gradient(135deg, var(--mrc-surface-mint, var(--mrc-surface)), var(--mrc-surface)); }
.member-action__badge { align-self: flex-start; padding: 2rpx 12rpx; border-radius: 14rpx; background: var(--mrc-accent); color: #fff; font-size: 17rpx; font-weight: 800; letter-spacing: 1rpx; }
.member-action__title { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 800; }
.member-action__sub { color: var(--mrc-text-sub); font-size: 19rpx; line-height: 1.45; }
.recognizing-card { margin-bottom: 18rpx; padding: 34rpx 28rpx; border: 2rpx dashed var(--mrc-accent); border-radius: 26rpx; background: var(--mrc-surface-peach); color: var(--mrc-accent); font-size: 23rpx; font-weight: 700; text-align: center; }
/* 识别确认清单 */
.recognize-hint { display: block; margin-bottom: 18rpx; color: var(--mrc-text-sub); font-size: 21rpx; line-height: 1.5; }
.recognize-empty { padding: 48rpx 0; color: var(--mrc-text-sub); font-size: 23rpx; text-align: center; }
.draft-row { display: flex; align-items: center; gap: 18rpx; padding: 16rpx 0; border-bottom: 2rpx solid var(--mrc-border-light); }
.draft-row:last-of-type { margin-bottom: 20rpx; }
.draft-row__check { display: flex; width: 46rpx; height: 46rpx; flex: 0 0 auto; align-items: center; justify-content: center; border: 2rpx solid var(--mrc-border); border-radius: 14rpx; color: #fff; font-size: 26rpx; font-weight: 800; }
.draft-row__check.is-on { border-color: var(--mrc-accent); background: var(--mrc-accent); }
.draft-row__body { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 6rpx; }
.draft-row__name { width: 100%; min-height: 56rpx; padding: 0 14rpx; border: 2rpx solid var(--mrc-border); border-radius: 14rpx; background: var(--mrc-bg); color: var(--mrc-text-deep); font-size: 24rpx; font-weight: 700; box-sizing: border-box; }
.draft-row__meta { color: var(--mrc-text-sub); font-size: 19rpx; }
/* 不宜冷藏提醒（识别清单 + 手动表单共用） */
.draft-row__warn, .form-advice { display: flex; flex-direction: column; gap: 4rpx; margin-top: 8rpx; padding: 14rpx 16rpx; border-radius: 16rpx; border-left: 6rpx solid var(--mrc-warning, #e0a020); background: var(--mrc-surface-warn, #fdf3e0); }
.form-advice { margin: -4rpx 0 16rpx; }
.draft-row__warn.is-worse, .form-advice.is-worse { border-left-color: var(--mrc-color-danger, #d64545); background: var(--mrc-surface-danger, #fdecec); }
.draft-row__warn-title, .form-advice__title { color: var(--mrc-text-deep); font-size: 21rpx; font-weight: 800; }
.draft-row__warn-tip, .form-advice__tip { color: var(--mrc-text-sub); font-size: 19rpx; line-height: 1.45; }
</style>
