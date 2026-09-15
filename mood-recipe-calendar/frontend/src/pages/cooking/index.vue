<script setup lang="ts">
import { computed, ref } from 'vue'
import { navBack } from '@/composables/useNavBar'
import Icon from '../../components/common/Icon.vue'
import { COOKING_PROGRESS_KEY, loadCookingDraft, saveRecordDraft } from '../../utils/cookingDraft'
import { toast } from '../../utils/toast'

definePage({ name: 'cooking', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '跟锅仔做菜' } })

interface CookingProgress {
  recipeKey: string
  stepIndex: number
  remaining: number
  deadline: number
  running: boolean
}

const router = useRouter()
const draft = ref(loadCookingDraft())
const stepIndex = ref(0)
const ingredientsOpen = ref(false)
const remaining = ref(300)
const deadline = ref(0)
const running = ref(false)
let ticker: ReturnType<typeof setInterval> | undefined

function parseList(value?: string) {
  try {
    const parsed = JSON.parse(value || '[]')
    return Array.isArray(parsed) ? parsed.map(String).filter(Boolean) : []
  }
  catch { return [] }
}

const recipe = computed(() => draft.value?.recipe)
const steps = computed(() => parseList(recipe.value?.steps))
const ingredients = computed(() => parseList(recipe.value?.ingredients))
const currentStep = computed(() => steps.value[stepIndex.value] || '')
const recipeKey = computed(() => String(recipe.value?.id || recipe.value?.name || ''))
const progressPercent = computed(() => steps.value.length ? Math.round(((stepIndex.value + 1) / steps.value.length) * 100) : 0)
const timerText = computed(() => `${String(Math.floor(remaining.value / 60)).padStart(2, '0')}:${String(remaining.value % 60).padStart(2, '0')}`)

function defaultSeconds() {
  const match = currentStep.value.match(/(\d+)\s*分钟/)
  return match ? Math.max(60, Math.min(Number(match[1]) * 60, 3600)) : 300
}

function persist() {
  if (!recipeKey.value) return
  uni.setStorageSync(COOKING_PROGRESS_KEY, {
    recipeKey: recipeKey.value,
    stepIndex: stepIndex.value,
    remaining: remaining.value,
    deadline: deadline.value,
    running: running.value,
  } satisfies CookingProgress)
}

function syncRemaining() {
  if (!running.value || !deadline.value) return
  remaining.value = Math.max(0, Math.ceil((deadline.value - Date.now()) / 1000))
  if (remaining.value === 0) {
    running.value = false
    deadline.value = 0
    stopTicker()
    vibrate()
    toast('这一段计时完成啦')
  }
  persist()
}

function startTicker() {
  stopTicker()
  ticker = setInterval(syncRemaining, 500)
}

function stopTicker() {
  if (ticker) clearInterval(ticker)
  ticker = undefined
}

function toggleTimer() {
  if (!remaining.value) remaining.value = defaultSeconds()
  if (running.value) {
    syncRemaining()
    running.value = false
    deadline.value = 0
    stopTicker()
  }
  else {
    running.value = true
    deadline.value = Date.now() + remaining.value * 1000
    startTicker()
  }
  persist()
}

function resetTimer() {
  stopTicker()
  running.value = false
  deadline.value = 0
  remaining.value = defaultSeconds()
  persist()
}

function vibrate() {
  // #ifdef MP-WEIXIN
  try { uni.vibrateShort({ type: 'light' }) } catch {}
  // #endif
}

function move(offset: number) {
  const next = stepIndex.value + offset
  if (next < 0 || next >= steps.value.length) return
  stepIndex.value = next
  resetTimer()
  vibrate()
}

async function restart() {
  const result = await uni.showModal({ title: '从第一步重新开始？', content: '当前步骤和计时会重置。', confirmText: '重新开始' })
  if (!result.confirm) return
  stepIndex.value = 0
  resetTimer()
}

function finish() {
  if (!recipe.value || !draft.value) return
  stopTicker()
  uni.removeStorageSync(COOKING_PROGRESS_KEY)
  saveRecordDraft(recipe.value, draft.value.mood)
  router.pushTab({ name: 'record' })
}

onLoad(() => {
  if (!recipe.value || !steps.value.length) return
  const saved = uni.getStorageSync(COOKING_PROGRESS_KEY) as CookingProgress | undefined
  if (saved?.recipeKey === recipeKey.value) {
    stepIndex.value = Math.min(Math.max(saved.stepIndex || 0, 0), steps.value.length - 1)
    remaining.value = Math.max(0, saved.remaining || defaultSeconds())
    deadline.value = saved.deadline || 0
    running.value = Boolean(saved.running && saved.deadline)
    syncRemaining()
  }
  else resetTimer()
})

onShow(() => {
  if (running.value) {
    syncRemaining()
    if (running.value) startTicker()
  }
})
onHide(() => { syncRemaining(); stopTicker() })
onUnload(() => { syncRemaining(); stopTicker() })
</script>

<template>
  <view class="cooking-page">
    <wd-navbar title="跟锅仔做菜" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <view v-if="!recipe || !steps.length" class="cooking-empty">
      <image src="https://static.image-zero.art/mood-recipe/static/guozai/action_07_empty.png" mode="aspectFit" />
      <text class="cooking-empty__title">这道菜还没有完整做法</text>
      <text class="cooking-empty__text">回到推荐页换一道，锅仔再陪你开始。</text>
      <view class="cooking-empty__button pressable" role="button" aria-label="返回上一页" @click="navBack">返回推荐</view>
    </view>

    <template v-else>
      <view class="cook-hero">
        <view class="cook-hero__copy">
          <text class="cook-hero__eyebrow">GUOZAI COOK MODE</text>
          <text class="cook-hero__title">{{ recipe.name }}</text>
          <text class="cook-hero__meta">{{ recipe.cookingTime || '--' }} 分钟 · {{ recipe.difficulty || '家常难度' }}</text>
        </view>
        <image class="cook-hero__guozai" src="https://static.image-zero.art/mood-recipe/static/guozai/action_16_chopsticks.png" mode="aspectFit" aria-label="锅仔陪你做菜" />
      </view>

      <view class="cook-progress" aria-label="做菜步骤进度">
        <view class="cook-progress__line"><view :style="{ width: `${progressPercent}%` }" /></view>
        <text>第 {{ stepIndex + 1 }} 步，共 {{ steps.length }} 步</text>
        <view class="cook-restart pressable" role="button" aria-label="重新开始这道菜" @click="restart">重新开始</view>
      </view>

      <view class="step-focus">
        <view class="step-focus__number">{{ String(stepIndex + 1).padStart(2, '0') }}</view>
        <text class="step-focus__label">现在只做这一件事</text>
        <text class="step-focus__text">{{ currentStep }}</text>
        <view class="step-focus__aside">
          <image src="https://static.image-zero.art/mood-recipe/static/guozai/action_10_thinking.png" mode="aspectFit" />
          <text>慢慢来，做好这一步再继续。</text>
        </view>
      </view>

      <view class="timer-card">
        <view class="timer-card__head">
          <view><text class="timer-card__eyebrow">厨房计时器</text><text class="timer-card__hint">会按真实经过时间恢复</text></view>
          <Icon name="clock" :size="38" color="#EF5A3C" />
        </view>
        <text class="timer-card__digits" aria-live="polite">{{ timerText }}</text>
        <view class="timer-actions">
          <view class="timer-action timer-action--primary pressable" role="button" :aria-label="running ? '暂停计时' : '开始计时'" @click="toggleTimer">{{ running ? '暂停' : remaining ? '开始' : '再次计时' }}</view>
          <view class="timer-action pressable" role="button" aria-label="重置计时" @click="resetTimer">重置</view>
        </view>
      </view>

      <view class="ingredients-card">
        <view class="ingredients-card__head pressable" role="button" :aria-expanded="ingredientsOpen" aria-label="展开或收起食材清单" @click="ingredientsOpen = !ingredientsOpen">
          <view><text class="ingredients-card__eyebrow">随时核对</text><text class="ingredients-card__title">食材清单 · {{ ingredients.length }} 项</text></view>
          <text class="ingredients-card__arrow" :class="{ 'is-open': ingredientsOpen }">›</text>
        </view>
        <view v-if="ingredientsOpen" class="ingredients-list">
          <view v-for="(item, index) in ingredients" :key="`${item}-${index}`" class="ingredients-item"><text>{{ index + 1 }}</text><text>{{ item }}</text></view>
        </view>
      </view>

      <view class="cook-actions">
        <view class="cook-action cook-action--secondary pressable" :class="{ 'is-disabled': stepIndex === 0 }" role="button" aria-label="上一步" @click="move(-1)">上一步</view>
        <view v-if="stepIndex < steps.length - 1" class="cook-action cook-action--primary pressable" role="button" aria-label="完成当前步骤并进入下一步" @click="move(1)">完成这步，继续</view>
        <view v-else class="cook-action cook-action--primary pressable" role="button" aria-label="完成做菜并记录" @click="finish">做完了，记一笔</view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.cooking-page { min-height: 100vh; box-sizing: border-box; padding: 0 32rpx calc(148rpx + env(safe-area-inset-bottom)); background: radial-gradient(circle at 90% 12%, var(--mrc-surface-sun), transparent 24%), var(--mrc-bg); }
.cook-hero { position: relative; display: flex; min-height: 190rpx; align-items: center; overflow: hidden; padding: 28rpx 24rpx 28rpx 30rpx; border: 2rpx solid var(--mrc-border); border-radius: 34rpx; background: linear-gradient(140deg, var(--mrc-surface), var(--mrc-surface-peach)); box-shadow: var(--mrc-shadow-soft); }
.cook-hero__copy { position: relative; z-index: 1; width: 70%; }
.cook-hero__eyebrow, .cook-hero__title, .cook-hero__meta { display: block; }
.cook-hero__eyebrow { color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; letter-spacing: 2rpx; }
.cook-hero__title { margin-top: 10rpx; color: var(--mrc-text-deep); font-size: 38rpx; font-weight: 800; line-height: 1.3; }
.cook-hero__meta { margin-top: 9rpx; color: var(--mrc-text-sub); font-size: 23rpx; }
.cook-hero__guozai { position: absolute; right: -10rpx; bottom: -16rpx; width: 174rpx; height: 174rpx; }
.cook-progress { display: grid; grid-template-columns: 1fr auto; align-items: center; gap: 12rpx 20rpx; padding: 24rpx 6rpx; color: var(--mrc-text-sub); font-size: 22rpx; }
.cook-progress__line { grid-column: 1 / 3; height: 10rpx; overflow: hidden; border-radius: 8rpx; background: var(--mrc-border-light); }
.cook-progress__line view { height: 100%; border-radius: inherit; background: var(--mrc-primary-grad); transition: width .24s ease-out; }
.cook-restart { display: flex; min-height: 64rpx; align-items: center; padding: 0 16rpx; color: var(--mrc-accent); font-weight: 700; }
.step-focus { position: relative; min-height: 390rpx; padding: 34rpx 32rpx 28rpx; overflow: hidden; border: 2rpx solid var(--mrc-border); border-radius: 38rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }
.step-focus__number { color: var(--mrc-accent-soft); font-size: 112rpx; font-weight: 900; line-height: 1; letter-spacing: -6rpx; }
.step-focus__label { display: block; margin-top: -30rpx; color: var(--mrc-accent); font-size: 21rpx; font-weight: 800; letter-spacing: 2rpx; }
.step-focus__text { display: block; margin-top: 18rpx; color: var(--mrc-text-deep); font-size: 36rpx; font-weight: 750; line-height: 1.65; }
.step-focus__aside { display: flex; align-items: center; gap: 12rpx; margin-top: 28rpx; padding-top: 20rpx; border-top: 2rpx solid var(--mrc-border-light); color: var(--mrc-text-sub); font-size: 22rpx; }
.step-focus__aside image { width: 58rpx; height: 58rpx; }
.timer-card, .ingredients-card { margin-top: 20rpx; padding: 26rpx 28rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 30rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.timer-card__head, .ingredients-card__head { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
.timer-card__eyebrow, .timer-card__hint, .ingredients-card__eyebrow, .ingredients-card__title { display: block; }
.timer-card__eyebrow, .ingredients-card__eyebrow { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 800; }
.timer-card__hint, .ingredients-card__title { margin-top: 5rpx; color: var(--mrc-text-sub); font-size: 21rpx; }
.timer-card__digits { display: block; margin: 20rpx 0; color: var(--mrc-text-deep); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 68rpx; font-weight: 800; font-variant-numeric: tabular-nums; letter-spacing: 3rpx; text-align: center; }
.timer-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 14rpx; }
.timer-action { display: flex; min-height: 88rpx; align-items: center; justify-content: center; border: 2rpx solid var(--mrc-border); border-radius: 44rpx; color: var(--mrc-text-deep); font-size: 26rpx; font-weight: 750; }
.timer-action--primary { border-color: transparent; background: var(--mrc-accent-soft); color: var(--mrc-accent); }
.ingredients-card__head { min-height: 88rpx; }
.ingredients-card__arrow { color: var(--mrc-text-sub); font-size: 44rpx; transform: rotate(90deg); transition: transform .2s ease; }
.ingredients-card__arrow.is-open { transform: rotate(-90deg); }
.ingredients-list { display: grid; grid-template-columns: 1fr 1fr; gap: 12rpx; padding-top: 18rpx; border-top: 2rpx solid var(--mrc-border-light); }
.ingredients-item { display: flex; min-height: 68rpx; align-items: center; gap: 12rpx; padding: 8rpx 12rpx; border-radius: 18rpx; background: var(--mrc-bg); color: var(--mrc-text-deep); font-size: 23rpx; }
.ingredients-item text:first-child { display: flex; width: 34rpx; height: 34rpx; flex-shrink: 0; align-items: center; justify-content: center; border-radius: 50%; background: var(--mrc-accent-soft); color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; }
.cook-actions { position: fixed; right: 0; bottom: 0; left: 0; z-index: 30; display: grid; grid-template-columns: .7fr 1.3fr; gap: 14rpx; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom)); border-top: 2rpx solid var(--mrc-border-light); background: var(--mrc-surface); box-shadow: 0 -8rpx 24rpx rgba(40, 24, 16, .08); }
.cook-action { display: flex; min-height: 96rpx; align-items: center; justify-content: center; border-radius: 48rpx; font-size: 27rpx; font-weight: 800; }
.cook-action--secondary { border: 2rpx solid var(--mrc-border); color: var(--mrc-text-deep); background: var(--mrc-surface); }
.cook-action--primary { color: #fff; background: var(--mrc-primary-grad); box-shadow: var(--mrc-shadow-coral); }
.is-disabled { pointer-events: none; opacity: .42; }
.pressable:active { opacity: .76; }
.cooking-empty { display: flex; flex-direction: column; align-items: center; padding: 120rpx 30rpx; text-align: center; }
.cooking-empty image { width: 220rpx; height: 220rpx; }
.cooking-empty__title { margin-top: 24rpx; color: var(--mrc-text-deep); font-size: 34rpx; font-weight: 800; }
.cooking-empty__text { margin-top: 12rpx; color: var(--mrc-text-sub); font-size: 24rpx; line-height: 1.55; }
.cooking-empty__button { display: flex; min-width: 260rpx; min-height: 88rpx; align-items: center; justify-content: center; margin-top: 32rpx; border-radius: 44rpx; color: #fff; background: var(--mrc-primary-grad); font-size: 27rpx; font-weight: 800; }
@media (max-width: 350px) { .ingredients-list { grid-template-columns: 1fr; } .step-focus__text { font-size: 32rpx; } }
@media (prefers-reduced-motion: reduce) { .cook-progress__line view, .ingredients-card__arrow { transition: none; } }
</style>
