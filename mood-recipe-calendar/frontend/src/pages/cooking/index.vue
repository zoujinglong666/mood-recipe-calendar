<script setup lang="ts">
import type { CookingAgentTurn, CookingChatMessage } from '@/api/cookingAgent'
import { computed, ref } from 'vue'
import { clearCookingLearning, cookingAgentTurn, sendCookingFeedback } from '@/api/cookingAgent'
import { navBack } from '@/composables/useNavBar'
import { STATIC_BASE_URL } from '@/utils/assets'
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
const guide = ref<CookingAgentTurn | null>(null)
const guideLoading = ref(false)
const coachOpen = ref(false)
const asking = ref(false)
const question = ref('')
const messages = ref<CookingChatMessage[]>([])
const personalized = ref(true)
const sourcesOpen = ref(false)
const memoryOpen = ref(false)
const sessionId = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 9)}`
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
const currentGuide = computed(() => guide.value?.guideSteps.find(item => item.index === stepIndex.value + 1))
const suggestions = computed(() => guide.value?.suggestions?.length ? guide.value.suggestions : ['怎样算熟？', '火太大怎么补救？', '没有这个食材怎么换？'])

async function loadGuide() {
  if (!recipe.value?.id || guideLoading.value)
    return
  guideLoading.value = true
  try {
    guide.value = await cookingAgentTurn({
      recipeId: recipe.value.id,
      currentStep: stepIndex.value,
      sessionId,
      action: 'GUIDE',
      personalized: personalized.value,
    })
  }
  catch {
    guide.value = null
  }
  finally { guideLoading.value = false }
}

async function ask(text = question.value) {
  const content = text.trim()
  if (!content || asking.value || !recipe.value?.id)
    return
  const history = messages.value.slice(-8)
  messages.value.push({ role: 'user', content })
  question.value = ''
  asking.value = true
  try {
    const response = await cookingAgentTurn({
      recipeId: recipe.value.id,
      currentStep: stepIndex.value,
      sessionId,
      message: content,
      action: 'ASK',
      personalized: personalized.value,
      history,
    })
    guide.value = { ...response, guideSteps: response.guideSteps.length ? response.guideSteps : (guide.value?.guideSteps || []) }
    messages.value.push({ role: 'assistant', content: response.reply })
  }
  catch { messages.value.push({ role: 'assistant', content: '刚才连接断了一下，原步骤和计时不受影响，可以再问一次。' }) }
  finally { asking.value = false }
}

async function feedback(eventType: 'COMPLETED' | 'TOO_HARD' | 'NOT_COMPLETED' | 'HELP') {
  if (!recipe.value?.id)
    return
  try {
    await sendCookingFeedback({ recipeId: recipe.value.id, stepIndex: stepIndex.value, stepType: currentStep.value.slice(0, 48), eventType })
    toast(eventType === 'COMPLETED' ? '记下啦，下次会更懂你的节奏' : '记下啦，下次这类步骤会讲得更细')
  }
  catch { toast('暂时没记上，不影响继续做菜') }
}

async function clearLearning() {
  const result = await uni.showModal({ title: '清除做菜学习？', content: '只清除做菜熟练度反馈，不会删除菜谱和做饭记录。', confirmText: '清除' })
  if (!result.confirm)
    return
  await clearCookingLearning()
  toast('做菜学习已清除')
}

function copySource(url: string) {
  uni.setClipboardData({ data: url, success: () => toast('来源链接已复制') })
}

function defaultSeconds() {
  const match = currentStep.value.match(/(\d+)\s*分钟/)
  return match ? Math.max(60, Math.min(Number(match[1]) * 60, 3600)) : 300
}

function persist() {
  if (!recipeKey.value)
    return
  uni.setStorageSync(COOKING_PROGRESS_KEY, {
    recipeKey: recipeKey.value,
    stepIndex: stepIndex.value,
    remaining: remaining.value,
    deadline: deadline.value,
    running: running.value,
  } satisfies CookingProgress)
}

function syncRemaining() {
  if (!running.value || !deadline.value)
    return
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
  if (ticker)
    clearInterval(ticker)
  ticker = undefined
}

function toggleTimer() {
  if (!remaining.value)
    remaining.value = defaultSeconds()
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
  try {
    uni.vibrateShort({ type: 'light' })
  }
  catch {}
  // #endif
}

function move(offset: number) {
  const next = stepIndex.value + offset
  if (next < 0 || next >= steps.value.length)
    return
  stepIndex.value = next
  resetTimer()
  vibrate()
}

async function restart() {
  const result = await uni.showModal({ title: '从第一步重新开始？', content: '当前步骤和计时会重置。', confirmText: '重新开始' })
  if (!result.confirm)
    return
  stepIndex.value = 0
  resetTimer()
}

async function finish() {
  if (!recipe.value || !draft.value)
    return
  stopTicker()
  if (recipe.value.id)
    await sendCookingFeedback({ recipeId: recipe.value.id, eventType: 'COMPLETED' }).catch(() => undefined)
  uni.removeStorageSync(COOKING_PROGRESS_KEY)
  saveRecordDraft(recipe.value, draft.value.mood)
  router.pushTab({ name: 'record' })
}

onLoad(() => {
  if (!recipe.value || !steps.value.length)
    return
  const saved = uni.getStorageSync(COOKING_PROGRESS_KEY) as CookingProgress | undefined
  if (saved?.recipeKey === recipeKey.value) {
    stepIndex.value = Math.min(Math.max(saved.stepIndex || 0, 0), steps.value.length - 1)
    remaining.value = Math.max(0, saved.remaining || defaultSeconds())
    deadline.value = saved.deadline || 0
    running.value = Boolean(saved.running && saved.deadline)
    syncRemaining()
  }
  else {
    resetTimer()
  }
  void loadGuide()
})

onShow(() => {
  if (running.value) {
    syncRemaining()
    if (running.value)
      startTicker()
  }
})
onHide(() => {
  syncRemaining()
  stopTicker()
})
onUnload(() => {
  syncRemaining()
  stopTicker()
})
</script>

<template>
  <view class="cooking-page">
    <wd-navbar title="跟锅仔做菜" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <view v-if="!recipe || !steps.length" class="cooking-empty">
      <image :src="`${STATIC_BASE_URL}/static/guozai/action_07_empty.png`" mode="aspectFit" />
      <text class="cooking-empty__title">
        这道菜还没有完整做法
      </text>
      <text class="cooking-empty__text">
        回到推荐页换一道，锅仔再陪你开始。
      </text>
      <view class="cooking-empty__button pressable" role="button" aria-label="返回上一页" @click="navBack">
        返回推荐
      </view>
    </view>

    <template v-else>
      <view class="cook-hero">
        <view class="cook-hero__copy">
          <text class="cook-hero__eyebrow">
            GUOZAI COOK MODE
          </text>
          <text class="cook-hero__title">
            {{ recipe.name }}
          </text>
          <text class="cook-hero__meta">
            {{ recipe.cookingTime || '--' }} 分钟 · {{ recipe.difficulty || '家常难度' }}
          </text>
        </view>
        <image class="cook-hero__guozai" :src="`${STATIC_BASE_URL}/static/guozai/action_16_chopsticks.png`" mode="aspectFit" aria-label="锅仔陪你做菜" />
      </view>

      <view class="cook-progress" aria-label="做菜步骤进度">
        <view class="cook-progress__line">
          <view :style="{ width: `${progressPercent}%` }" />
        </view>
        <text>第 {{ stepIndex + 1 }} 步，共 {{ steps.length }} 步</text>
        <view class="cook-restart pressable" role="button" aria-label="重新开始这道菜" @click="restart">
          重新开始
        </view>
      </view>

      <view class="step-focus">
        <view class="step-focus__number">
          {{ String(stepIndex + 1).padStart(2, '0') }}
        </view>
        <text class="step-focus__label">
          现在只做这一件事
        </text>
        <text class="step-focus__text">
          {{ currentStep }}
        </text>
        <view class="step-focus__aside">
          <image :src="`${STATIC_BASE_URL}/static/guozai/action_10_thinking.png`" mode="aspectFit" />
          <text>慢慢来，做好这一步再继续。</text>
        </view>
        <view v-if="guideLoading" class="step-coach step-coach--loading" aria-live="polite">
          <view class="thinking-dots">
            <i /><i /><i />
          </view>
          <text>锅仔正在结合这道菜整理火候和判断方法…</text>
        </view>
        <view v-else-if="currentGuide" class="step-coach">
          <view class="step-coach__top">
            <text>锅仔现场提醒</text><text>{{ guide?.teachingLevel === 'BEGINNER' ? '新手细讲' : guide?.teachingLevel === 'COMPACT' ? '熟练模式' : '跟做模式' }}</text>
          </view>
          <view class="step-coach__grid">
            <view><text>火候</text><text>{{ currentGuide.heat }}</text></view>
            <view><text>时间</text><text>{{ currentGuide.duration }}</text></view>
          </view>
          <view class="step-coach__line">
            <text>看到这样就对了</text><text>{{ currentGuide.successSigns }}</text>
          </view>
          <view class="step-coach__rescue">
            <text>没做好也能救</text><text>{{ currentGuide.rescue }}</text>
          </view>
          <text v-if="guide?.degraded" class="step-coach__degraded">
            当前为基础指导 · {{ guide.degradeReason === 'NO_EVIDENCE' ? '知识库暂无匹配依据' : '智能教学暂时降级' }}
          </text>
        </view>
      </view>

      <view class="coach-entry pressable" role="button" aria-label="打开锅仔做菜教练" @click="coachOpen = true">
        <image :src="`${STATIC_BASE_URL}/static/guozai/action_10_thinking.png`" mode="aspectFit" />
        <view>
          <text class="coach-entry__eyebrow">
            GUOZAI COOKING AGENT
          </text><text class="coach-entry__title">
            卡住了？直接问锅仔
          </text><text class="coach-entry__desc">
            会结合当前这一步、你的反馈和可靠知识回答
          </text>
        </view>
        <text class="coach-entry__arrow">
          ›
        </text>
      </view>

      <view class="timer-card">
        <view class="timer-card__head">
          <view>
            <text class="timer-card__eyebrow">
              厨房计时器
            </text><text class="timer-card__hint">
              会按真实经过时间恢复
            </text>
          </view>
          <Icon name="clock" :size="38" color="#EF5A3C" />
        </view>
        <text class="timer-card__digits" aria-live="polite">
          {{ timerText }}
        </text>
        <view class="timer-actions">
          <view class="timer-action timer-action--primary pressable" role="button" :aria-label="running ? '暂停计时' : '开始计时'" @click="toggleTimer">
            {{ running ? '暂停' : remaining ? '开始' : '再次计时' }}
          </view>
          <view class="timer-action pressable" role="button" aria-label="重置计时" @click="resetTimer">
            重置
          </view>
        </view>
      </view>

      <view class="ingredients-card">
        <view class="ingredients-card__head pressable" role="button" :aria-expanded="ingredientsOpen" aria-label="展开或收起食材清单" @click="ingredientsOpen = !ingredientsOpen">
          <view>
            <text class="ingredients-card__eyebrow">
              随时核对
            </text><text class="ingredients-card__title">
              食材清单 · {{ ingredients.length }} 项
            </text>
          </view>
          <text class="ingredients-card__arrow" :class="{ 'is-open': ingredientsOpen }">
            ›
          </text>
        </view>
        <view v-if="ingredientsOpen" class="ingredients-list">
          <view v-for="(item, index) in ingredients" :key="`${item}-${index}`" class="ingredients-item">
            <text>{{ index + 1 }}</text><text>{{ item }}</text>
          </view>
        </view>
      </view>

      <view class="cook-actions">
        <view class="cook-action cook-action--secondary pressable" :class="{ 'is-disabled': stepIndex === 0 }" role="button" aria-label="上一步" @click="move(-1)">
          上一步
        </view>
        <view v-if="stepIndex < steps.length - 1" class="cook-action cook-action--primary pressable" role="button" aria-label="完成当前步骤并进入下一步" @click="move(1)">
          完成这步，继续
        </view>
        <view v-else class="cook-action cook-action--primary pressable" role="button" aria-label="完成做菜并记录" @click="finish">
          做完了，记一笔
        </view>
      </view>

      <wd-popup v-model="coachOpen" position="bottom" :close-on-click-modal="!asking" custom-style="border-radius: 40rpx 40rpx 0 0; overflow: hidden; background: var(--mrc-bg);">
        <view class="coach-sheet">
          <view class="coach-sheet__head">
            <view>
              <text class="coach-sheet__eyebrow">
                锅仔做菜教练
              </text><text class="coach-sheet__title">
                正在陪你做第 {{ stepIndex + 1 }} 步
              </text>
            </view>
            <view class="coach-close pressable" role="button" aria-label="关闭做菜教练" @click="coachOpen = false">
              ×
            </view>
          </view>

          <scroll-view scroll-y class="coach-chat" :scroll-with-animation="true">
            <view class="coach-context">
              <text>当前步骤</text><text>{{ currentStep }}</text>
            </view>
            <view v-if="!messages.length" class="coach-welcome">
              <image :src="`${STATIC_BASE_URL}/static/guozai/action_16_chopsticks.png`" mode="aspectFit" />
              <text>你可以把锅里的真实情况告诉我，例如“肉变白了但里面还有粉色”。我会先告诉你现在怎么做。</text>
            </view>
            <view v-for="(message, index) in messages" :key="`${index}-${message.role}`" class="chat-row" :class="`chat-row--${message.role}`">
              <text>{{ message.content }}</text>
            </view>
            <view v-if="asking" class="chat-row chat-row--assistant chat-row--thinking" aria-live="polite">
              <view class="thinking-dots">
                <i /><i /><i />
              </view><text>锅仔正在看当前步骤和知识依据…</text>
            </view>

            <view v-if="guide?.sources?.length" class="evidence-card">
              <view class="evidence-card__head pressable" role="button" :aria-expanded="sourcesOpen" @click="sourcesOpen = !sourcesOpen">
                <view><text>这次依据</text><text>{{ guide.sources.length }} 条已审核知识</text></view><text>{{ sourcesOpen ? '收起' : '查看' }}</text>
              </view>
              <view v-if="sourcesOpen" class="evidence-list">
                <view v-for="source in guide.sources" :key="source.id" class="evidence-item pressable" role="button" :aria-label="`复制来源：${source.title}`" @click="copySource(source.sourceUrl)">
                  <text>{{ source.title }}</text><text>{{ source.sourceName }} · {{ source.version }}</text>
                </view>
              </view>
            </view>
            <view v-else-if="guide" class="no-evidence">
              这次没有命中已审核知识，回答会保持保守，不会伪造来源。
            </view>

            <view class="learning-card">
              <view class="learning-card__head">
                <view><text>因人施教</text><text>{{ personalized ? '已开启' : '已关闭' }}</text></view><view class="mini-switch pressable" :class="{ 'is-on': personalized }" role="switch" :aria-checked="personalized" @click="personalized = !personalized; loadGuide()">
                  <i />
                </view>
              </view>
              <view v-if="guide?.memoryUsed?.length" class="memory-used">
                <view class="pressable" role="button" :aria-expanded="memoryOpen" @click="memoryOpen = !memoryOpen">
                  <text>本次用了 {{ guide.memoryUsed.length }} 条记忆</text><text>{{ memoryOpen ? '收起' : '为什么' }}</text>
                </view>
                <text v-for="item in (memoryOpen ? guide.memoryUsed : [])" :key="item">
                  {{ item }}
                </text>
              </view>
              <view class="learning-actions">
                <text class="pressable" @click="feedback('TOO_HARD')">
                  这步太难
                </text><text class="pressable" @click="feedback('NOT_COMPLETED')">
                  没做成
                </text><text class="pressable" @click="clearLearning">
                  清除学习
                </text>
              </view>
            </view>
          </scroll-view>

          <scroll-view scroll-x class="quick-questions" :show-scrollbar="false">
            <view class="quick-questions__inner">
              <text v-for="item in suggestions" :key="item" class="pressable" @click="ask(item)">
                {{ item }}
              </text>
            </view>
          </scroll-view>
          <view class="coach-composer">
            <input v-model="question" :disabled="asking" :maxlength="500" confirm-type="send" placeholder="描述锅里的情况，越具体越好" @confirm="ask()">
            <button :disabled="asking || !question.trim()" @click="ask()">
              发送
            </button>
          </view>
        </view>
      </wd-popup>
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
.step-coach { margin-top: 22rpx; padding: 22rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 24rpx; background: var(--mrc-bg); }
.step-coach--loading { display: flex; align-items: center; gap: 16rpx; color: var(--mrc-text-sub); font-size: 22rpx; }
.step-coach__top { display: flex; align-items: center; justify-content: space-between; color: var(--mrc-accent); font-size: 21rpx; font-weight: 800; }
.step-coach__top text:last-child { padding: 6rpx 12rpx; border-radius: 14rpx; background: var(--mrc-accent-soft); font-size: 18rpx; }
.step-coach__grid { display: flex; gap: 12rpx; margin-top: 16rpx; }
.step-coach__grid view { flex: 1; padding: 16rpx; border-radius: 18rpx; background: var(--mrc-surface); }
.step-coach__grid text, .step-coach__line text, .step-coach__rescue text { display: block; }
.step-coach__grid text:first-child, .step-coach__line text:first-child, .step-coach__rescue text:first-child { margin-bottom: 5rpx; color: var(--mrc-text-light); font-size: 19rpx; font-weight: 700; }
.step-coach__grid text:last-child, .step-coach__line text:last-child, .step-coach__rescue text:last-child { color: var(--mrc-text-deep); font-size: 22rpx; line-height: 1.55; }
.step-coach__line, .step-coach__rescue { margin-top: 14rpx; }
.step-coach__rescue { padding: 14rpx 16rpx; border-radius: 16rpx; background: var(--mrc-surface-peach); }
.step-coach__degraded { display: block; margin-top: 14rpx; color: var(--mrc-text-light); font-size: 19rpx; }
.coach-entry { display: grid; grid-template-columns: 90rpx 1fr auto; align-items: center; gap: 18rpx; margin-top: 20rpx; padding: 22rpx 20rpx; border: 2rpx solid var(--mrc-border); border-radius: 30rpx; background: linear-gradient(130deg, var(--mrc-surface), var(--mrc-surface-peach)); box-shadow: var(--mrc-shadow-soft); }
.coach-entry image { width: 90rpx; height: 90rpx; }.coach-entry text { display: block; }
.coach-entry__eyebrow { color: var(--mrc-accent); font-size: 17rpx; font-weight: 800; letter-spacing: 1rpx; }
.coach-entry__title { margin-top: 5rpx; color: var(--mrc-text-deep); font-size: 28rpx; font-weight: 800; }
.coach-entry__desc { margin-top: 5rpx; color: var(--mrc-text-sub); font-size: 20rpx; line-height: 1.4; }
.coach-entry__arrow { color: var(--mrc-accent); font-size: 46rpx; }
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
.coach-sheet { display: flex; height: 82vh; height: 82dvh; flex-direction: column; box-sizing: border-box; padding: 28rpx 28rpx calc(20rpx + env(safe-area-inset-bottom)); }
.coach-sheet__head { display: flex; flex-shrink: 0; align-items: center; justify-content: space-between; padding-bottom: 20rpx; }.coach-sheet__head text { display: block; }
.coach-sheet__eyebrow { color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; letter-spacing: 2rpx; }.coach-sheet__title { margin-top: 5rpx; color: var(--mrc-text-deep); font-size: 30rpx; font-weight: 800; }
.coach-close { display: flex; width: 72rpx; height: 72rpx; align-items: center; justify-content: center; border-radius: 50%; background: var(--mrc-surface-peach); color: var(--mrc-text-sub); font-size: 44rpx; }
.coach-chat { min-height: 0; flex: 1; }
.coach-context { padding: 18rpx 20rpx; border-left: 6rpx solid var(--mrc-accent); border-radius: 0 20rpx 20rpx 0; background: var(--mrc-surface); }.coach-context text { display: block; }
.coach-context text:first-child { color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; }.coach-context text:last-child { margin-top: 5rpx; color: var(--mrc-text-deep); font-size: 22rpx; line-height: 1.5; }
.coach-welcome { display: flex; align-items: flex-start; gap: 14rpx; margin-top: 18rpx; }.coach-welcome image { width: 66rpx; height: 66rpx; flex-shrink: 0; }
.coach-welcome text, .chat-row { padding: 18rpx 20rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 8rpx 24rpx 24rpx; background: var(--mrc-surface); color: var(--mrc-text-deep); font-size: 23rpx; line-height: 1.6; }
.chat-row { display: block; max-width: 82%; margin-top: 16rpx; box-sizing: border-box; }.chat-row--user { margin-left: auto; border-color: transparent; border-radius: 24rpx 8rpx 24rpx 24rpx; background: var(--mrc-primary-grad); color: #fff; }.chat-row--assistant { margin-right: auto; }
.chat-row--thinking { display: flex; max-width: 100%; align-items: center; gap: 14rpx; color: var(--mrc-text-sub); }
.thinking-dots { display: flex; flex-shrink: 0; gap: 6rpx; }.thinking-dots i { width: 9rpx; height: 9rpx; border-radius: 50%; background: var(--mrc-accent); animation: thinking 1.1s ease-in-out infinite; }.thinking-dots i:nth-child(2) { animation-delay: .14s; }.thinking-dots i:nth-child(3) { animation-delay: .28s; }
@keyframes thinking { 0%, 60%, 100% { opacity: .3; transform: translateY(0); } 30% { opacity: 1; transform: translateY(-5rpx); } }
.evidence-card, .learning-card, .no-evidence { margin-top: 20rpx; padding: 20rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 24rpx; background: var(--mrc-surface); }
.evidence-card__head, .learning-card__head, .memory-used view { display: flex; align-items: center; justify-content: space-between; }.evidence-card__head view text, .learning-card__head view text { display: block; }
.evidence-card__head view text:first-child, .learning-card__head view text:first-child { color: var(--mrc-text-deep); font-size: 23rpx; font-weight: 800; }.evidence-card__head view text:last-child, .learning-card__head view text:last-child, .evidence-card__head > text { margin-top: 4rpx; color: var(--mrc-text-sub); font-size: 19rpx; }
.evidence-list { margin-top: 14rpx; padding-top: 10rpx; border-top: 2rpx solid var(--mrc-border-light); }.evidence-item { padding: 12rpx 0; }.evidence-item text { display: block; color: var(--mrc-text-deep); font-size: 21rpx; }.evidence-item text:last-child { margin-top: 3rpx; color: var(--mrc-text-light); font-size: 18rpx; }
.no-evidence { color: var(--mrc-text-sub); font-size: 20rpx; line-height: 1.5; }
.mini-switch { width: 72rpx; height: 40rpx; padding: 4rpx; border-radius: 24rpx; background: var(--mrc-border); box-sizing: border-box; }.mini-switch i { display: block; width: 32rpx; height: 32rpx; border-radius: 50%; background: #fff; transition: transform .18s ease; }.mini-switch.is-on { background: var(--mrc-accent); }.mini-switch.is-on i { transform: translateX(32rpx); }
.memory-used { margin-top: 14rpx; padding-top: 14rpx; border-top: 2rpx solid var(--mrc-border-light); }.memory-used view text { color: var(--mrc-text-sub); font-size: 19rpx; }.memory-used > text { display: block; margin-top: 8rpx; color: var(--mrc-text-light); font-size: 18rpx; line-height: 1.45; }
.learning-actions { display: flex; gap: 10rpx; margin-top: 16rpx; }.learning-actions text { flex: 1; padding: 12rpx 8rpx; border-radius: 16rpx; background: var(--mrc-bg); color: var(--mrc-text-sub); font-size: 19rpx; text-align: center; }
.quick-questions { flex-shrink: 0; width: 100%; margin-top: 12rpx; white-space: nowrap; }.quick-questions__inner { display: inline-flex; gap: 10rpx; padding-right: 24rpx; }.quick-questions text { padding: 13rpx 18rpx; border: 2rpx solid var(--mrc-border); border-radius: 22rpx; background: var(--mrc-surface); color: var(--mrc-text-sub); font-size: 20rpx; }
.coach-composer { display: flex; flex-shrink: 0; align-items: center; gap: 10rpx; margin-top: 12rpx; padding: 10rpx 10rpx 10rpx 20rpx; border: 2rpx solid var(--mrc-border); border-radius: 28rpx; background: var(--mrc-surface); }.coach-composer input { min-width: 0; flex: 1; color: var(--mrc-text-deep); font-size: 22rpx; }.coach-composer button { display: flex; width: 92rpx; min-height: 70rpx; align-items: center; justify-content: center; margin: 0; padding: 0; border: 0; border-radius: 20rpx; background: var(--mrc-text-deep); color: #fff; font-size: 21rpx; }.coach-composer button::after { border: 0; }.coach-composer button[disabled] { opacity: .4; }
@media (max-width: 350px) { .ingredients-list { grid-template-columns: 1fr; } .step-focus__text { font-size: 32rpx; } }
@media (prefers-reduced-motion: reduce) { .cook-progress__line view, .ingredients-card__arrow, .mini-switch i { transition: none; } .thinking-dots i { animation: none; opacity: 1; } }
</style>
