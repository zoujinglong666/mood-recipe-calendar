<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { createFridgeItem, consumeFridgeItem, deleteFridgeItem, fetchFridgeItems, fetchFridgeSummary, fetchStorageAdvice, notifyFridgeExpiring, recognizeFridgeImage, updateFridgeItem, type FridgeItem, type FridgeSummary, type RecognizedItem } from '@/api/fridge'
import GuozaiButton from '@/components/guozai/GuozaiButton.vue'
import { navBack } from '@/composables/useNavBar'
import { STATIC_BASE_URL } from '@/utils/assets'
import { uploadFile } from '@/api/request'
import { chooseImageFile } from '@/utils/chooseImage'
import { ensureLogin } from '@/utils/login'
import { requireMember } from '@/utils/memberGate'
import { toast, toastError } from '@/utils/toast'

/** 锅仔 IP 姿态（全部复用 COS 现有资源，不新增素材） */
const GZ_INSPECT = `${STATIC_BASE_URL}/static/guozai/action_07_empty.png` // 清点冰箱的锅仔
const GZ_CAMERA = `${STATIC_BASE_URL}/static/guozai/action_03_camera.png` // 举相机识别
const GZ_BELL = `${STATIC_BASE_URL}/static/guozai/action_14_clap.png` // 临期提醒

definePage({ name: 'fridge', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '我的冰箱' } })

import { onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'

// 直接本页写生命周期，比 useShare 组合式更可靠
onShareAppMessage(() => {
  const pages = getCurrentPages()
  const route = (pages[pages.length - 1] as any)?.route || ''
  return { title: '锅仔 · 按心情帮你决定今天吃什么', path: `/${route}` }
})
onShareTimeline(() => ({ title: '锅仔 · 按心情帮你决定今天吃什么' }))

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

/**
 * 需要优先处理的数量 = 已过期 + 临期。
 * 顶部面板与「优先消耗」分区读的是同一个值，避免两处数字对不上。
 */
const dueCount = computed(() => soonItems.value.length + expiredItems.value.length)

/** 面板数字滚动到位（R1 · 零依赖，减少动效时直接落到终值） */
const shownDue = ref(0)
let rollTimer: ReturnType<typeof setInterval> | undefined

/** 是否要求减少动态效果（小程序无 matchMedia，直接返回 false 保持不动效） */
function prefersReducedMotion() {
  // #ifdef H5
  return typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches
  // #endif
  // #ifndef H5
  return false
  // #endif
}

function rollDue(to: number) {
  if (rollTimer) {
    clearInterval(rollTimer!)
    rollTimer = undefined
  }
  if (prefersReducedMotion() || !to) {
    shownDue.value = to
    return
  }
  shownDue.value = 0
  const step = Math.max(1, Math.ceil(to / 8))
  rollTimer = setInterval(() => {
    const next = shownDue.value + step
    if (next >= to) {
      shownDue.value = to
      clearInterval(rollTimer!)
      rollTimer = undefined
    }
    else {
      shownDue.value = next
    }
  }, 70)
}

/** 「吃掉一项」等操作后重新滚动数字；只在数值真的变了时跑 */
watch(dueCount, (to) => { rollDue(to) })

onUnmounted(() => {
  if (rollTimer) clearInterval(rollTimer!)
})

// ===== 会员专享：拍照识别 =====

/** 识别确认清单：每条可勾选/改名，确认后才入库 */
interface DraftItem extends RecognizedItem { checked: boolean }
const recognizing = ref(false)
const recognizeOpen = ref(false)
const recognizeNotice = ref('')
const drafts = ref<DraftItem[]>([])
const recognizedCount = computed(() => drafts.value.filter(d => d.checked).length)

/** 拍照/选图 → 上传 → 识别 → 弹确认清单 */
function startRecognize() {
  if (!requireMember('fridge_recognize')) return
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
    closeRecognize()
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
  if (!requireMember('fridge_expiry')) return
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
    // 首屏也让数字滚到位；减少动效时这里直接落终值
    rollDue(soonItems.value.length + expiredItems.value.length)
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
      <!-- 吧台面板：左侧锅仔出面清点，右侧数字只回答一个问题「今天有几样得先吃」 -->
      <view class="counter">
        <view class="counter__glow" />
        <image class="counter__gz" :src="GZ_INSPECT" mode="aspectFit" aria-label="正在清点冰箱的锅仔" />
        <view class="counter__copy">
          <text class="counter__title">今天先吃这些</text>
          <text class="counter__line">{{ dueCount ? `有 ${dueCount} 项再不吃就坏了` : '都还新鲜，慢慢吃' }}</text>
        </view>
        <view class="counter__dial" :class="{ 'is-clear': !dueCount }">
          <text class="counter__num">{{ shownDue }}</text>
          <text class="counter__unit">项待吃</text>
        </view>
      </view>

      <view class="page-heading">
        <view><text class="page-heading__title">冰箱库存</text><text class="page-heading__sub">{{ summary.total }} 项食材 · 吃过就划掉，别让它烂在里面</text></view>
        <GuozaiButton variant="secondary" :block="false" aria-label="添加食材" @click="openCreate">＋ 添加</GuozaiButton>
      </view>

      <!-- 抽屉把手：锅仔举着相机拉出识别，整条都是触点，不再是两张并排白卡 -->
      <view class="drawer-handle" role="button" aria-label="拍照识别食材" @click="startRecognize">
        <view class="drawer-handle__grab" />
        <image class="drawer-handle__gz" :src="GZ_CAMERA" mode="aspectFit" aria-label="举相机的锅仔" />
        <view class="drawer-handle__body">
          <view class="drawer-handle__head"><text class="drawer-handle__title">拍照识别食材</text><text class="drawer-handle__badge">会员</text></view>
          <text class="drawer-handle__sub">拍一张冰箱照，锅仔认食材、算保质期</text>
        </view>
        <text class="drawer-handle__arrow">›</text>
      </view>

      <view v-if="recognizing" class="recognizing-card">锅仔正在看照片里的食材…</view>

      <view v-if="!items.length" class="empty-card"><text class="empty-card__title">冰箱还没开张</text><text class="empty-card__copy">把手边的食材放进来，锅仔才能帮你优先消耗、少买一点。</text><GuozaiButton aria-label="添加第一项食材" @click="openCreate">添加第一项食材</GuozaiButton></view>

      <template v-else>
        <view v-if="expiredItems.length" class="inventory-section">
          <view class="section-title"><text>已经过期</text><text>{{ expiredItems.length }} 项 · 该扔就扔</text></view>
          <view v-for="item in expiredItems" :key="item.id" class="food-row food-row--expired">
            <view class="food-row__main"><text class="food-row__name">{{ item.name }}</text><text class="food-row__meta">{{ item.quantity }} {{ item.unit }} · {{ statusText(item) }}</text></view>
            <view class="food-row__actions"><text class="food-row__act" @click="openEdit(item)">编辑</text><text class="food-row__act is-quiet" @click="remove(item)">扔掉</text></view>
          </view>
        </view>

        <view v-if="soonItems.length" class="inventory-section">
          <view class="section-title"><text>优先消耗</text><text>{{ soonItems.length }} 项 · 越快越好</text></view>
          <view v-for="item in soonItems" :key="item.id" class="food-row food-row--soon">
            <view class="food-row__mark" />
            <view class="food-row__main"><text class="food-row__name">{{ item.name }}</text><text class="food-row__meta">{{ item.quantity }} {{ item.unit }} · {{ statusText(item) }}</text></view>
            <view class="food-row__actions">
              <text class="food-row__act is-primary" @click="consume(item)">吃掉 1 {{ item.unit }}</text>
              <text class="food-row__act is-quiet" @click="openEdit(item)">编辑</text>
            </view>
          </view>
        </view>

        <view v-if="otherItems.length" class="inventory-section">
          <view class="section-title"><text>其他食材</text><text>{{ otherItems.length }} 项</text></view>
          <view v-for="item in otherItems" :key="item.id" class="food-row">
            <view class="food-row__main"><text class="food-row__name">{{ item.name }}</text><text class="food-row__meta">{{ item.quantity }} {{ item.unit }} · {{ statusText(item) }}</text></view>
            <view class="food-row__actions">
              <text class="food-row__act" @click="consume(item)">吃掉 1 {{ item.unit }}</text>
              <text class="food-row__act is-quiet" @click="openEdit(item)">编辑</text>
            </view>
          </view>
        </view>

        <!-- 临期提醒：只在真的有临期食材时出现，位置在提醒最该呆的地方——食材旁边 -->
        <view v-if="soonItems.length" class="rake">
          <view class="rake__copy">
            <text class="rake__title">这些要不要提醒你一下</text>
            <text class="rake__sub">{{ soonItems.map(i => i.name).join('、') }}</text>
          </view>
          <view class="rake__go" role="button" aria-label="发送临期提醒（会员专享）" @click="sendExpiryNotice">
            <image class="rake__gz" :src="GZ_BELL" mode="aspectFit" aria-label="锅仔提醒你" />
            <text class="rake__go-text">提醒我</text><text class="rake__go-badge">会员</text>
          </view>
        </view>
      </template>
    </template>

    <!-- 识别结果确认清单：识别只作预填，用户勾选/修改后才入库 -->
    <view v-if="recognizeOpen" class="form-mask" @click="closeRecognize">
      <view class="form-sheet" @click.stop>
        <view class="form-sheet__grab" />
        <view class="form-sheet__head">
          <view><text class="eyebrow">AI RECOGNIZED</text><text class="form-sheet__title">确认要放入的食材</text></view>
          <view class="sheet-close" role="button" aria-label="关闭" @click="closeRecognize"><text>×</text></view>
        </view>
        <text class="recognize-hint">{{ recognizeNotice || '已自动识别并估算保质期，勾选后放入冰箱' }}</text>
        <view v-if="!drafts.length" class="recognize-empty">没有识别到食材，换个角度再拍一张吧</view>
        <view v-for="(draft, idx) in drafts" :key="idx" class="draft-row">
          <view class="draft-row__check" :class="{ 'is-on': draft.checked }" role="checkbox" :aria-checked="draft.checked" @click="draft.checked = !draft.checked"><text>{{ draft.checked ? '✓' : '' }}</text></view>
          <view class="draft-row__body">
            <view class="draft-row__line">
              <input v-model="draft.name" class="draft-row__name" :maxlength="80">
              <text class="draft-row__life">{{ draft.shelfLifeDays }} 天</text>
            </view>
            <text class="draft-row__meta">{{ draft.expiresOn }} 到期{{ draft.shelfLifeMatched ? '' : '（估算）' }}</text>
            <view v-if="draft.storageLevel" class="warn-chip" :class="`is-${draft.storageLevel.toLowerCase()}`">
              <text class="warn-chip__title">{{ draft.storageLevel === 'WORSE' ? '⚠ 放冰箱会坏得更快' : '· 其实不用放冰箱' }}</text>
              <text class="warn-chip__tip">{{ draft.storageTip }}</text>
            </view>
          </view>
        </view>
        <GuozaiButton :loading="saving" aria-label="确认放入冰箱" @click="confirmRecognize">放入冰箱（{{ recognizedCount }} 项）</GuozaiButton>
      </view>
    </view>

    <view v-if="formOpen" class="form-mask" @click="closeForm">
      <view class="form-sheet" @click.stop>
        <view class="form-sheet__grab" />
        <view class="form-sheet__head">
          <view><text class="eyebrow">FRIDGE ITEM</text><text class="form-sheet__title">{{ editingId ? '编辑食材' : '放入冰箱' }}</text></view>
          <view class="sheet-close" role="button" aria-label="关闭" @click="closeForm"><text>×</text></view>
        </view>
        <view class="form-field"><text>食材名称</text><input v-model="form.name" placeholder="例如：番茄" :maxlength="80"></view>
        <view v-if="formAdvice" class="warn-chip" :class="`is-${formAdvice.level.toLowerCase()}`">
          <text class="warn-chip__title">{{ formAdvice.level === 'WORSE' ? '⚠ 这种食材放冰箱会坏得更快' : '· 这种食材其实不用放冰箱' }}</text>
          <text class="warn-chip__tip">{{ formAdvice.tip }}</text>
        </view>
        <view class="form-line">
          <view class="form-field"><text>数量</text><input v-model="form.quantity" type="digit" placeholder="1"></view>
          <view class="form-field"><text>单位</text><input v-model="form.unit" placeholder="份" :maxlength="20"></view>
        </view>
        <view class="form-line">
          <view class="form-field"><text>购买日期</text><picker mode="date" :value="form.purchasedOn" @change="setDate('purchasedOn', $event)"><view class="date-field">{{ form.purchasedOn || '选择日期' }}</view></picker></view>
          <view class="form-field"><text>保质期（可选）</text><picker mode="date" :value="form.expiresOn || today" @change="setDate('expiresOn', $event)"><view class="date-field">{{ form.expiresOn || '未设置' }}</view></picker></view>
        </view>
        <view class="form-field"><text>备注（可选）</text><input v-model="form.note" placeholder="例如：切开了，尽快吃" :maxlength="240"></view>
        <text v-if="formError" class="form-error">{{ formError }}</text>
        <GuozaiButton :loading="saving" aria-label="保存食材" @click="save">保存食材</GuozaiButton>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* finesse · register=h5 · morph=A.1-daily-desk · A=warm-cream+coral+deep-cocoa
 * B=heavy-800/regular · C=counter-panel+drawer-handle+marked-rows · D=R1-css-only
 * E=kitchen-counter-woodgrain · SOUL=7 SPECTACLE=3 DENSITY=5 */

/* 页面局部令牌：全部由主题变量推导，不新增硬编码色板 */
.fridge-page {
  --mrc-counter-deep: #3D2519;
  --mrc-counter-mid: #5A3728;
  --mrc-counter-line: rgba(255, 231, 200, .14);
  min-height: 100vh;
  padding: 0 28rpx calc(48rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  color: var(--mrc-text);
  background: var(--mrc-bg);
}
.eyebrow { display: block; color: var(--mrc-accent); font-size: 18rpx; font-weight: 800; letter-spacing: 2rpx; }

/* ===== 顶部吧台面板：角色 + 一句结论 + 一个数字 ===== */
.counter {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin: 8rpx 0 26rpx;
  padding: 26rpx 24rpx;
  border: 2rpx solid var(--mrc-counter-line);
  border-radius: 34rpx;
  background: linear-gradient(150deg, var(--mrc-counter-deep) 0%, var(--mrc-counter-mid) 72%, #6E4430 100%);
  box-shadow: 0 16rpx 40rpx -14rpx rgba(61, 37, 25, .55);
  overflow: hidden;
}
/* 台面反光：一圈极淡的弧线，只有一层，不堆玻璃效果 */
.counter__glow { position: absolute; top: -150rpx; right: -110rpx; width: 330rpx; height: 330rpx; border: 2rpx solid rgba(255, 214, 176, .16); border-radius: 50%; }
.counter__gz { position: relative; z-index: 1; width: 142rpx; height: 142rpx; flex: 0 0 auto; margin: -14rpx -6rpx -14rpx -10rpx; }
.counter__copy { position: relative; z-index: 1; display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 10rpx; }
.counter__title { color: #FFF6EA; font-size: 37rpx; font-weight: 800; line-height: 1.15; letter-spacing: -.5rpx; }
.counter__line { color: rgba(255, 236, 216, .68); font-size: 21rpx; line-height: 1.45; }
.counter__dial { position: relative; z-index: 1; display: flex; width: 112rpx; height: 112rpx; flex: 0 0 auto; flex-direction: column; align-items: center; justify-content: center; border-radius: 50%; background: var(--mrc-primary); box-shadow: inset 0 0 0 6rpx rgba(61, 37, 25, .12); }
.counter__dial.is-clear { background: rgba(255, 246, 234, .16); box-shadow: inset 0 0 0 2rpx rgba(255, 236, 216, .34); }
.counter__num { color: var(--mrc-text-deep); font-size: 40rpx; font-weight: 800; font-variant-numeric: tabular-nums; line-height: 1; }
.counter__dial.is-clear .counter__num, .counter__dial.is-clear .counter__unit { color: #FFF6EA; }
.counter__unit { margin-top: 6rpx; color: var(--mrc-text-deep); font-size: 17rpx; font-weight: 700; opacity: .72; }

/* ===== 库存标题 ===== */
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; margin-bottom: 16rpx; }
.page-heading__title { display: block; color: var(--mrc-text-deep); font-size: 31rpx; font-weight: 800; }
.page-heading__sub { display: block; margin-top: 6rpx; color: var(--mrc-text-sub); font-size: 20rpx; }
.page-heading .gz-btn { min-height: 70rpx; padding: 0 20rpx; font-size: 21rpx; }

/* ===== 木质抽屉把手：锅仔举相机，整条可点 ===== */
.drawer-handle {
  position: relative;
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-bottom: 22rpx;
  padding: 22rpx 24rpx 22rpx 20rpx;
  border-radius: 28rpx;
  border: 2rpx solid var(--mrc-border);
  background: linear-gradient(135deg, var(--mrc-surface-2) 0%, var(--mrc-surface) 62%);
  box-shadow: 0 6rpx 20rpx -8rpx rgba(176, 116, 74, .34);
  overflow: hidden;
  transition: transform var(--mrc-motion-fast), box-shadow var(--mrc-motion-fast);
}
.drawer-handle:active { transform: scale(.985); box-shadow: 0 3rpx 10rpx -6rpx rgba(176, 116, 74, .4); }
/* 把手凹槽：一条内阴影，不是装饰线条 */
.drawer-handle__grab { position: absolute; top: 22rpx; bottom: 22rpx; left: 0; width: 10rpx; background: linear-gradient(180deg, var(--mrc-primary) 0%, var(--mrc-accent) 100%); border-radius: 0 8rpx 8rpx 0; opacity: .85; }
.drawer-handle__gz { width: 104rpx; height: 104rpx; flex: 0 0 auto; }
.drawer-handle__body { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 7rpx; }
.drawer-handle__head { display: flex; align-items: center; gap: 12rpx; }
.drawer-handle__title { color: var(--mrc-text-deep); font-size: 28rpx; font-weight: 800; }
.drawer-handle__badge { padding: 2rpx 12rpx; border-radius: 20rpx; background: var(--mrc-accent-soft); color: var(--mrc-accent); font-size: 17rpx; font-weight: 800; letter-spacing: 1rpx; }
.drawer-handle__sub { color: var(--mrc-text-sub); font-size: 20rpx; line-height: 1.45; }
.drawer-handle__arrow { flex: 0 0 auto; color: var(--mrc-text-light); font-size: 40rpx; font-weight: 300; line-height: 1; }

.recognizing-card { margin-bottom: 18rpx; padding: 34rpx 28rpx; border: 2rpx dashed var(--mrc-accent); border-radius: 26rpx; background: var(--mrc-surface-peach); color: var(--mrc-accent); font-size: 23rpx; font-weight: 700; text-align: center; }

/* ===== 分区与食材行 ===== */
.state-card, .empty-card, .inventory-section { border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.state-card { padding: 100rpx 28rpx; text-align: center; color: var(--mrc-text-sub); font-size: 24rpx; }
.state-card--error { color: var(--mrc-accent); }
.empty-card { display: flex; flex-direction: column; gap: 16rpx; padding: 38rpx 28rpx; text-align: center; }
.empty-card__title { color: var(--mrc-text-deep); font-size: 30rpx; font-weight: 800; }
.empty-card__copy { color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.55; }
.inventory-section { overflow: hidden; margin-bottom: 18rpx; }
.section-title { display: flex; align-items: baseline; justify-content: space-between; gap: 14rpx; padding: 22rpx 24rpx; border-bottom: 2rpx solid var(--mrc-border-light); color: var(--mrc-text-deep); font-size: 25rpx; font-weight: 800; }
.section-title text:last-child { flex: 0 0 auto; color: var(--mrc-text-sub); font-size: 18rpx; font-weight: 500; }

.food-row { position: relative; display: flex; align-items: center; justify-content: space-between; gap: 16rpx; min-height: 104rpx; padding: 16rpx 24rpx; border-bottom: 2rpx solid var(--mrc-border-light); transition: opacity 220ms ease-out; }
.food-row:last-child { border-bottom: 0; }
/* 被划掉的一道：临期行左侧的记号，像真的在清单上划了一笔 */
.food-row__mark { position: absolute; top: 50%; left: 0; width: 7rpx; height: 46rpx; margin-top: -23rpx; border-radius: 0 6rpx 6rpx 0; background: var(--mrc-accent); }
.food-row__main { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 7rpx; }
.food-row__name { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 800; }
.food-row__meta { color: var(--mrc-text-sub); font-size: 20rpx; font-variant-numeric: tabular-nums; }
.food-row--soon .food-row__meta { color: var(--mrc-accent); font-weight: 700; }
.food-row--expired .food-row__name { color: var(--mrc-text-sub); text-decoration: line-through; }
.food-row--expired .food-row__meta { color: var(--mrc-text-light); }

/* 动作按主次排：主动作是一枚药丸，编辑退成安静文字 */
.food-row__actions { display: flex; flex: 0 0 auto; align-items: center; gap: 8rpx; }
.food-row__act { display: flex; min-height: 62rpx; align-items: center; justify-content: center; padding: 0 20rpx; border-radius: 999rpx; color: var(--mrc-text); font-size: 20rpx; font-weight: 750; }
.food-row__act.is-primary { background: var(--mrc-accent-soft); color: var(--mrc-accent); }
.food-row__act.is-quiet { padding: 0 12rpx; color: var(--mrc-text-light); font-weight: 600; }
.food-row--expired .food-row__act { color: var(--mrc-text-sub); }
.food-row__act:active { opacity: .6; }

/* ===== 临期提醒：出现在食材旁边，而不是页面顶部 ===== */
.rake { display: flex; align-items: center; gap: 18rpx; margin-bottom: 18rpx; padding: 22rpx 24rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; background: var(--mrc-surface-warn, #FDF3E0); box-shadow: var(--mrc-shadow-soft); }
.rake__copy { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 6rpx; }
.rake__title { color: var(--mrc-text-deep); font-size: 24rpx; font-weight: 800; }
.rake__sub { color: var(--mrc-text-sub); font-size: 20rpx; line-height: 1.4; }
.rake__go { display: flex; flex: 0 0 auto; align-items: center; gap: 8rpx; min-height: 72rpx; padding: 0 22rpx 0 10rpx; border-radius: 999rpx; background: var(--mrc-surface); border: 2rpx solid var(--mrc-border); }
.rake__go:active { transform: scale(.97); }
.rake__gz { width: 56rpx; height: 56rpx; }
.rake__go-text { color: var(--mrc-accent); font-size: 21rpx; font-weight: 800; }
.rake__go-badge { padding: 2rpx 10rpx; border-radius: 20rpx; background: var(--mrc-accent-soft); color: var(--mrc-accent); font-size: 16rpx; font-weight: 800; letter-spacing: 1rpx; }

/* ===== 抽屉式表单 ===== */
.form-mask { position: fixed; z-index: 50; inset: 0; display: flex; align-items: flex-end; background: rgba(61, 37, 25, .42); animation: mask-in 240ms ease-out both; }
@keyframes mask-in { from { opacity: 0; } to { opacity: 1; } }
@keyframes sheet-up { from { opacity: 0; transform: translateY(38rpx); } to { opacity: 1; transform: none; } }
.form-sheet { width: 100%; max-height: 86vh; overflow-y: auto; padding: 16rpx 28rpx calc(28rpx + env(safe-area-inset-bottom)); border-radius: 36rpx 36rpx 0 0; background: var(--mrc-surface); box-shadow: 0 -14rpx 36rpx rgba(61, 37, 25, .2); box-sizing: border-box; animation: sheet-up 340ms cubic-bezier(.32, .86, .28, 1) both; }
/* 抓取把手：让用户知道这个抽屉能拉下来 */
.form-sheet__grab { width: 76rpx; height: 8rpx; margin: 0 auto 20rpx; border-radius: 999rpx; background: var(--mrc-border-strong); opacity: .6; }
.form-sheet__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16rpx; margin-bottom: 22rpx; }
.form-sheet__title { display: block; margin-top: 7rpx; color: var(--mrc-text-deep); font-size: 31rpx; font-weight: 800; }
.sheet-close { display: flex; width: 64rpx; height: 64rpx; flex: 0 0 auto; align-items: center; justify-content: center; border-radius: 50%; background: var(--mrc-surface-2); }
.sheet-close text { color: var(--mrc-text-sub); font-size: 38rpx; line-height: 1; }
.sheet-close:active { transform: scale(.92); }

.form-line { display: grid; grid-template-columns: 1fr 1fr; gap: 14rpx; }
.form-field { display: flex; flex-direction: column; gap: 9rpx; margin-bottom: 16rpx; color: var(--mrc-text-deep); font-size: 21rpx; font-weight: 700; }
.form-field input, .date-field { width: 100%; min-height: 76rpx; padding: 0 18rpx; border: 2rpx solid var(--mrc-border); border-radius: 18rpx; background: var(--mrc-bg); color: var(--mrc-text-deep); font-size: 22rpx; box-sizing: border-box; }
.date-field { display: flex; align-items: center; font-weight: 500; }
.form-error { display: block; margin: -4rpx 0 14rpx; color: var(--mrc-color-danger); font-size: 20rpx; }

/* 识别确认清单 */
.recognize-hint { display: block; margin-bottom: 18rpx; color: var(--mrc-text-sub); font-size: 21rpx; line-height: 1.5; }
.recognize-empty { padding: 48rpx 0; color: var(--mrc-text-sub); font-size: 23rpx; text-align: center; }
.draft-row { display: flex; align-items: flex-start; gap: 18rpx; padding: 18rpx 0; border-bottom: 2rpx solid var(--mrc-border-light); }
.draft-row:last-of-type { margin-bottom: 20rpx; }
.draft-row__check { display: flex; width: 48rpx; height: 48rpx; margin-top: 6rpx; flex: 0 0 auto; align-items: center; justify-content: center; border: 2rpx solid var(--mrc-border-strong); border-radius: 15rpx; transition: transform 160ms ease-out; }
.draft-row__check.is-on { border-color: var(--mrc-accent); background: var(--mrc-accent); }
.draft-row__check.is-on text { color: #fff; font-size: 26rpx; font-weight: 800; }
.draft-row__check:active { transform: scale(.9); }
.draft-row__body { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 7rpx; }
.draft-row__line { display: flex; align-items: center; gap: 12rpx; }
.draft-row__name { min-width: 0; flex: 1; min-height: 62rpx; padding: 0 16rpx; border: 2rpx solid var(--mrc-border); border-radius: 16rpx; background: var(--mrc-bg); color: var(--mrc-text-deep); font-size: 25rpx; font-weight: 750; box-sizing: border-box; }
.draft-row__life { flex: 0 0 auto; color: var(--mrc-accent); font-size: 21rpx; font-weight: 800; font-variant-numeric: tabular-nums; }
.draft-row__meta { color: var(--mrc-text-sub); font-size: 19rpx; font-variant-numeric: tabular-nums; }

/* 不宜冷藏提醒（识别清单 + 手动表单共用） */
.warn-chip { display: flex; flex-direction: column; gap: 5rpx; margin-top: 8rpx; padding: 14rpx 18rpx; border-radius: 18rpx; background: var(--mrc-surface-warn, #FDF3E0); box-shadow: inset 6rpx 0 0 var(--mrc-warning, #E0A020); }
.warn-chip.is-worse { background: var(--mrc-surface-danger, #FDECEC); box-shadow: inset 6rpx 0 0 var(--mrc-color-danger, #D64545); }
.warn-chip__title { color: var(--mrc-text-deep); font-size: 21rpx; font-weight: 800; }
.warn-chip__tip { color: var(--mrc-text-sub); font-size: 19rpx; line-height: 1.45; }
</style>
