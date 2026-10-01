<script setup lang="ts">
import { computed, ref } from 'vue'
import { createFridgeItem, consumeFridgeItem, deleteFridgeItem, fetchFridgeItems, fetchFridgeSummary, updateFridgeItem, type FridgeItem, type FridgeSummary } from '@/api/fridge'
import GuozaiButton from '@/components/guozai/GuozaiButton.vue'
import { navBack } from '@/composables/useNavBar'
import { ensureLogin } from '@/utils/login'
import { toastError } from '@/utils/toast'

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

const soonItems = computed(() => items.value.filter(item => item.status === 'SOON'))
const expiredItems = computed(() => items.value.filter(item => item.status === 'EXPIRED'))
const otherItems = computed(() => items.value.filter(item => item.status === 'FRESH' || item.status === 'NO_DATE'))

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

      <view v-if="!items.length" class="empty-card"><text class="empty-card__title">冰箱还没开张</text><text class="empty-card__copy">把手边的食材放进来，锅仔才能帮你优先消耗、少买一点。</text><GuozaiButton aria-label="添加第一项食材" @click="openCreate">添加第一项食材</GuozaiButton></view>

      <template v-else>
        <view v-if="expiredItems.length" class="inventory-section"><view class="section-title"><text>已经过期</text><text>{{ expiredItems.length }} 项</text></view><view v-for="item in expiredItems" :key="item.id" class="food-row food-row--expired"><view class="food-row__main"><text class="food-row__name">{{ item.name }}</text><text class="food-row__meta">{{ item.quantity }} {{ item.unit }} · {{ statusText(item) }}</text></view><view class="food-row__actions"><text @click="openEdit(item)">编辑</text><text @click="remove(item)">删除</text></view></view></view>
        <view v-if="soonItems.length" class="inventory-section"><view class="section-title"><text>优先消耗</text><text>{{ soonItems.length }} 项</text></view><view v-for="item in soonItems" :key="item.id" class="food-row food-row--soon"><view class="food-row__main"><text class="food-row__name">{{ item.name }}</text><text class="food-row__meta">{{ item.quantity }} {{ item.unit }} · {{ statusText(item) }}</text></view><view class="food-row__actions"><text @click="consume(item)">用掉 1{{ item.unit }}</text><text @click="openEdit(item)">编辑</text></view></view></view>
        <view v-if="otherItems.length" class="inventory-section"><view class="section-title"><text>其他食材</text><text>{{ otherItems.length }} 项</text></view><view v-for="item in otherItems" :key="item.id" class="food-row"><view class="food-row__main"><text class="food-row__name">{{ item.name }}</text><text class="food-row__meta">{{ item.quantity }} {{ item.unit }} · {{ statusText(item) }}</text></view><view class="food-row__actions"><text @click="consume(item)">用掉 1{{ item.unit }}</text><text @click="openEdit(item)">编辑</text></view></view></view>
      </template>
    </template>

    <view v-if="formOpen" class="form-mask" @click.self="closeForm"><view class="form-sheet"><view class="form-sheet__head"><view><text class="eyebrow">FRIDGE ITEM</text><text class="form-sheet__title">{{ editingId ? '编辑食材' : '放入冰箱' }}</text></view><text class="form-sheet__close" @click="closeForm">×</text></view><view class="form-field"><text>食材名称</text><input v-model="form.name" placeholder="例如：番茄" :maxlength="80"></view><view class="form-line"><view class="form-field"><text>数量</text><input v-model="form.quantity" type="digit" placeholder="1"></view><view class="form-field"><text>单位</text><input v-model="form.unit" placeholder="份" :maxlength="20"></view></view><view class="form-line"><view class="form-field"><text>购买日期</text><picker mode="date" :value="form.purchasedOn" @change="setDate('purchasedOn', $event)"><view class="date-field">{{ form.purchasedOn || '选择日期' }}</view></picker></view><view class="form-field"><text>保质期（可选）</text><picker mode="date" :value="form.expiresOn || today" @change="setDate('expiresOn', $event)"><view class="date-field">{{ form.expiresOn || '未设置' }}</view></picker></view></view><view class="form-field"><text>备注（可选）</text><input v-model="form.note" placeholder="例如：切开了，尽快吃" :maxlength="240"></view><text v-if="formError" class="form-error">{{ formError }}</text><GuozaiButton :loading="saving" aria-label="保存食材" @click="save">保存食材</GuozaiButton></view></view>
  </view>
</template>

<style lang="scss" scoped>
.fridge-page { min-height: 100vh; padding: 0 28rpx calc(48rpx + env(safe-area-inset-bottom)); box-sizing: border-box; color: var(--mrc-text); background: var(--mrc-bg); }.eyebrow { display: block; color: var(--mrc-accent); font-size: 18rpx; font-weight: 800; letter-spacing: 2rpx; }.overview-card { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; margin: 8rpx 0 28rpx; padding: 28rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 30rpx; background: linear-gradient(135deg, var(--mrc-text-deep), #66402d); box-shadow: var(--mrc-shadow-lift); color: #fff; }.overview-card__copy { display: flex; min-width: 0; flex-direction: column; gap: 8rpx; }.overview-card__title { font-size: 36rpx; font-weight: 800; line-height: 1.2; }.overview-card__copy-text { color: rgba(255,255,255,.72); font-size: 21rpx; line-height: 1.45; }.overview-card .eyebrow { color: #ffb19b; }.overview-card__count { display: flex; width: 104rpx; height: 104rpx; flex: 0 0 auto; flex-direction: column; align-items: center; justify-content: center; border-radius: 50%; background: var(--mrc-primary); color: var(--mrc-text-deep); }.overview-card__count text:first-child { font-size: 34rpx; font-weight: 800; line-height: 1; }.overview-card__count text:last-child { margin-top: 8rpx; font-size: 18rpx; font-weight: 700; }.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; margin-bottom: 18rpx; }.page-heading__title { display: block; color: var(--mrc-text-deep); font-size: 31rpx; font-weight: 800; }.page-heading__sub { display: block; margin-top: 6rpx; color: var(--mrc-text-sub); font-size: 20rpx; }.page-heading .gz-btn { min-height: 70rpx; padding: 0 20rpx; font-size: 21rpx; }.state-card, .empty-card, .inventory-section { border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }.state-card { padding: 100rpx 28rpx; text-align: center; color: var(--mrc-text-sub); font-size: 24rpx; }.state-card--error { color: var(--mrc-accent); }.empty-card { display: flex; flex-direction: column; gap: 16rpx; padding: 38rpx 28rpx; text-align: center; }.empty-card__title { color: var(--mrc-text-deep); font-size: 30rpx; font-weight: 800; }.empty-card__copy { color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.55; }.inventory-section { overflow: hidden; margin-bottom: 18rpx; }.section-title { display: flex; align-items: center; justify-content: space-between; padding: 22rpx 24rpx; border-bottom: 2rpx solid var(--mrc-border-light); color: var(--mrc-text-deep); font-size: 24rpx; font-weight: 800; }.section-title text:last-child { color: var(--mrc-text-sub); font-size: 19rpx; font-weight: 500; }.food-row { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; min-height: 96rpx; padding: 14rpx 24rpx; border-bottom: 2rpx solid var(--mrc-border-light); }.food-row:last-child { border-bottom: 0; }.food-row__main { display: flex; min-width: 0; flex-direction: column; gap: 7rpx; }.food-row__name { color: var(--mrc-text-deep); font-size: 25rpx; font-weight: 750; }.food-row__meta { color: var(--mrc-text-sub); font-size: 20rpx; }.food-row--soon .food-row__meta { color: var(--mrc-accent); }.food-row--expired .food-row__name, .food-row--expired .food-row__meta { color: var(--mrc-text-light); }.food-row__actions { display: flex; flex: 0 0 auto; gap: 14rpx; }.food-row__actions text { display: flex; min-height: 56rpx; align-items: center; color: var(--mrc-accent); font-size: 19rpx; font-weight: 700; }.food-row--expired .food-row__actions text { color: var(--mrc-text-sub); }.form-mask { position: fixed; z-index: 50; inset: 0; display: flex; align-items: flex-end; background: rgba(61, 37, 25, .38); }.form-sheet { width: 100%; padding: 28rpx 28rpx calc(28rpx + env(safe-area-inset-bottom)); border-radius: 32rpx 32rpx 0 0; background: var(--mrc-surface); box-shadow: 0 -14rpx 36rpx rgba(61, 37, 25, .18); box-sizing: border-box; }.form-sheet__head { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 22rpx; }.form-sheet__title { display: block; margin-top: 7rpx; color: var(--mrc-text-deep); font-size: 31rpx; font-weight: 800; }.form-sheet__close { color: var(--mrc-text-sub); font-size: 48rpx; line-height: .7; }.form-line { display: grid; grid-template-columns: 1fr 1fr; gap: 14rpx; }.form-field { display: flex; flex-direction: column; gap: 9rpx; margin-bottom: 16rpx; color: var(--mrc-text-deep); font-size: 21rpx; font-weight: 700; }.form-field input, .date-field { width: 100%; min-height: 72rpx; padding: 0 18rpx; border: 2rpx solid var(--mrc-border); border-radius: 18rpx; background: var(--mrc-bg); color: var(--mrc-text-deep); font-size: 22rpx; box-sizing: border-box; }.date-field { display: flex; align-items: center; font-weight: 500; }.form-error { display: block; margin: -4rpx 0 14rpx; color: var(--mrc-color-danger); font-size: 20rpx; }
</style>
