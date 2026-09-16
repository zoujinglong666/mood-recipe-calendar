<script setup lang="ts">
import type { FoodMemoryView } from '@/api/preferences'
import type { MealAgentState, MealAgentTurn, WeeklyPlan } from '@/api/weeklyPlans'
import { computed, nextTick, ref } from 'vue'
import { submitFeedback } from '@/api/feedback'
import { fetchFoodMemory } from '@/api/preferences'
import { generateWeeklyPlan, getCurrentPlan, requestWeeklyPlanCompletionNotice, runMealAgentTurn } from '@/api/weeklyPlans'
import { navBack } from '@/composables/useNavBar'
import { STATIC_BASE_URL } from '@/utils/assets'
import { toastError } from '@/utils/toast'

definePage({ name: 'meal-agent', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '锅仔管饭' } })

const router = useRouter()
const memory = ref<FoodMemoryView>()
const currentPlan = ref<WeeklyPlan>()
const loading = ref(true)
const generating = ref(false)
const people = ref(3)
const dishesPerDay = ref(2)
const cookingDays = ref([0, 1, 2, 3, 4, 5, 6])
const healthGoal = ref('BALANCED')
const budget = ref('DAILY')
const activeStep = ref(-1)
const completed = ref(false)
const generatedPlanId = ref<number>()
const rating = ref('')
const hasElder = ref(false)
const hasChild = ref(false)
const spiceLevel = ref('微辣')
const sessionCuisine = ref('')
interface ChatMessage { id: number, role: 'agent' | 'user', text: string, tags?: string[] }
const composerText = ref('')
const messages = ref<ChatMessage[]>([])
const agentState = ref<MealAgentState>({})
const agentTurn = ref<MealAgentTurn>()
const agentBusy = ref(false)
const householdSelection = ref<string[]>([])
let messageId = 0
let progressTimer: ReturnType<typeof setInterval> | undefined

const agentSteps = [
  { title: '读取锅仔记忆', copy: '口味、忌口和最近做过的菜' },
  { title: '检查家庭情况', copy: '老人、小孩和吃辣程度' },
  { title: '检查本周安排', copy: '做饭日期和每天菜数' },
  { title: '搭配主菜与配菜', copy: '避开拒绝过的菜，减少重复' },
  { title: '合并买菜清单', copy: '复用食材，整理成一张清单' },
]

const memoryTags = computed(() => {
  const value = memory.value
  if (!value)
    return ['正在认识你']
  const tags: string[] = []
  const cuisines = value.explicit.favoriteCuisines?.split(/[,，、]/).filter(Boolean) || []
  tags.push(...cuisines.slice(0, 2))
  if (value.explicit.eatCilantro === false)
    tags.push('不吃香菜')
  if (value.explicit.eatScallion === false)
    tags.push('不吃葱')
  if (value.behavior.topDish)
    tags.push(`常做${value.behavior.topDish}`)
  return tags.length ? tags.slice(0, 4) : ['口味还在慢慢积累']
})

const agentGreeting = computed(() => {
  const value = memory.value
  if (!value)
    return '我会带上你已经留下的口味和忌口，替你把这一周安排好。'
  const cuisine = value.explicit.favoriteCuisines?.split(/[,，、]/).filter(Boolean)[0]
  const avoid = value.explicit.eatCilantro === false ? '不放香菜' : value.explicit.eatScallion === false ? '不放葱' : '避开你的忌口'
  return cuisine ? `我记得你喜欢${cuisine}、${avoid}。这周的变化，再告诉我一点就好。` : `我会${avoid}，也会参考你最近做过的菜。这周想怎么吃？`
})

const conversationNotes = computed(() => {
  const state = agentState.value
  return [
    state.hasElder ? '家有老人，菜品软烂少盐' : '',
    state.hasChild ? '家有小孩，少刺少骨、口味温和' : '',
    state.spiceLevel ? `吃辣程度：${state.spiceLevel}` : '',
    state.favoriteCuisine ? `偏爱${state.favoriteCuisine}` : '',
    state.mealContext || '',
  ].filter(Boolean).join('；')
})

onShow(load)
onUnmounted(stopProgress)

async function load() {
  loading.value = true
  const [memoryResult, planResult] = await Promise.allSettled([fetchFoodMemory(), getCurrentPlan()])
  memory.value = memoryResult.status === 'fulfilled' ? memoryResult.value : undefined
  currentPlan.value = planResult.status === 'fulfilled' ? planResult.value : undefined
  if (memory.value?.explicit.healthGoal)
    healthGoal.value = memory.value.explicit.healthGoal
  if (!messages.value.length) {
    addAgent(agentGreeting.value, memoryTags.value)
    await runAgent('')
  }
  loading.value = false
}

function addAgent(text: string, tags?: string[]) {
  messages.value.push({ id: ++messageId, role: 'agent', text, tags })
}

async function scrollToLatest() {
  await nextTick()
  uni.pageScrollTo({ scrollTop: 999999, duration: 220 })
}

async function runAgent(message: string, echo = false, echoLabel = message) {
  if (agentBusy.value)
    return
  if (echo && message)
    messages.value.push({ id: ++messageId, role: 'user', text: echoLabel })
  agentBusy.value = true
  await scrollToLatest()
  try {
    const turn = await runMealAgentTurn(message, agentState.value)
    agentTurn.value = turn
    agentState.value = turn.state
    householdSelection.value = turn.action === 'ASK_HOUSEHOLD'
      ? [turn.state.hasElder ? 'elder' : '', turn.state.hasChild ? 'child' : ''].filter(Boolean)
      : []
    people.value = turn.state.people || people.value
    cookingDays.value = turn.state.cookingDays || []
    dishesPerDay.value = turn.state.dishesPerDay || dishesPerDay.value
    healthGoal.value = turn.state.healthGoal || healthGoal.value
    budget.value = turn.state.budget || budget.value
    hasElder.value = turn.state.hasElder ?? hasElder.value
    hasChild.value = turn.state.hasChild ?? hasChild.value
    spiceLevel.value = turn.state.spiceLevel || spiceLevel.value
    sessionCuisine.value = turn.state.favoriteCuisine || sessionCuisine.value
    addAgent(turn.reply)
  }
  catch (error) {
    toastError(error, '锅仔刚刚走神了，请再说一次')
  }
  finally {
    agentBusy.value = false
    await scrollToLatest()
  }
}

function householdValue(value: string) {
  if (value === 'elder=yes' || value.includes('elder'))
    return 'elder'
  if (value === 'child=yes' || value.includes('child'))
    return 'child'
  return 'none'
}

function toggleHousehold(value: string) {
  const selected = householdValue(value)
  if (selected === 'none') {
    householdSelection.value = ['none']
    return
  }
  const current = householdSelection.value.filter(item => item !== 'none')
  householdSelection.value = current.includes(selected)
    ? current.filter(item => item !== selected)
    : [...current, selected]
}

async function confirmHousehold() {
  if (!householdSelection.value.length)
    return
  const none = householdSelection.value.includes('none')
  const value = none ? 'household=none' : `household=${householdSelection.value.join(',')}`
  const label = none ? '都是成人' : householdSelection.value.map(item => item === 'elder' ? '有老人' : '有小孩').join('、')
  await runAgent(value, true, label)
}

async function submitComposer() {
  const text = composerText.value.trim()
  if (!text)
    return
  composerText.value = ''
  await runAgent(text, true)
}

function startProgress() {
  completed.value = false
  activeStep.value = 0
  progressTimer = setInterval(() => {
    if (activeStep.value < agentSteps.length - 1)
      activeStep.value += 1
  }, 900)
}

function stopProgress() {
  if (progressTimer)
    clearInterval(progressTimer)
  progressTimer = undefined
}

async function generate() {
  if (generating.value)
    return
  generating.value = true
  startProgress()
  try {
    const notify = await requestWeeklyPlanCompletionNotice()
    const plan = await generateWeeklyPlan({
      people: people.value,
      days: cookingDays.value.length,
      cookingDays: cookingDays.value,
      healthGoal: healthGoal.value,
      dishesPerDay: dishesPerDay.value,
      budget: budget.value,
      conversationNotes: conversationNotes.value,
      sendNotification: notify,
    })
    stopProgress()
    activeStep.value = agentSteps.length
    completed.value = true
    generatedPlanId.value = plan.id
  }
  catch (error) {
    stopProgress()
    activeStep.value = -1
    toastError(error, '锅仔这次没排好，请再试一次')
  }
  finally {
    generating.value = false
  }
}

async function rateConversation(value: string) {
  if (rating.value)
    return
  rating.value = value
  try {
    await submitFeedback({ category: '体验问题', content: `锅仔管饭对话评分：${value}` })
  }
  catch {
    // 评分不影响查看已生成的菜单。
  }
}

function onRatingChange(event: { value: string | number | boolean }) {
  void rateConversation(String(event.value))
}

function openGeneratedPlan() {
  if (generatedPlanId.value)
    router.replace({ name: 'weekly-plan-detail', query: { id: String(generatedPlanId.value) } })
}

function openCurrentPlan() {
  if (currentPlan.value)
    router.push({ name: 'weekly-plan-detail', query: { id: String(currentPlan.value.id) } })
}
</script>

<template>
  <view class="agent-page">
    <wd-navbar title="锅仔管饭" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <view class="agent-hero">
      <view class="agent-hero__top">
        <text>GUOZAI AGENT</text><view class="agent-online">
          <view />正在陪你
        </view>
      </view>
      <view class="agent-hero__copy">
        <text class="agent-hero__eyebrow">
          少想一点，认真吃饭
        </text>
        <text class="agent-hero__title">
          这一周，<br>交给锅仔管饭
        </text>
        <text class="agent-hero__sub">
          记得你的口味，也记得哪天不做饭。
        </text>
      </view>
      <image class="agent-hero__guozai" :src="`${STATIC_BASE_URL}/static/guozai/action_06_glasses.png`" mode="aspectFit" aria-label="正在安排菜单的锅仔" />
      <view class="agent-hero__orbit agent-hero__orbit--one" />
      <view class="agent-hero__orbit agent-hero__orbit--two" />
    </view>

    <view v-if="loading" class="agent-loading" aria-live="polite">
      锅仔正在翻看你的记忆…
    </view>

    <template v-else>
      <view v-if="currentPlan" class="current-plan" role="button" aria-label="打开锅仔上次安排的菜单" @click="openCurrentPlan">
        <view>
          <text class="current-plan__label">
            上次安排还在
          </text><text class="current-plan__title">
            {{ currentPlan.days.length }} 天菜单和买菜清单
          </text>
        </view>
        <text class="current-plan__action">
          继续照着做 ›
        </text>
      </view>

      <view v-if="generating || completed" class="tool-panel" aria-live="polite">
        <view class="tool-panel__head">
          <text>{{ completed ? '这一周已经排好' : '锅仔正在调用工具' }}</text><text>{{ completed ? '完成' : `${Math.min(activeStep + 1, agentSteps.length)}/${agentSteps.length}` }}</text>
        </view>
        <view v-for="(step, index) in agentSteps" :key="step.title" class="tool-step" :class="{ 'tool-step--done': completed || index < activeStep, 'tool-step--active': !completed && index === activeStep }">
          <view class="tool-step__state">
            <text v-if="completed || index < activeStep">
              ✓
            </text><view v-else-if="index === activeStep" class="tool-step__pulse" /><text v-else>
              {{ index + 1 }}
            </text>
          </view>
          <view>
            <text class="tool-step__title">
              {{ step.title }}
            </text><text class="tool-step__copy">
              {{ step.copy }}
            </text>
          </view>
        </view>
        <view v-if="completed" class="conversation-rating">
          <text>这次锅仔问得合适吗？</text>
          <wd-radio-group custom-class="rating-selector" type="button" direction="horizontal" :model-value="rating" @change="onRatingChange">
            <wd-radio value="满意">
              刚刚好
            </wd-radio>
            <wd-radio value="一般">
              还行
            </wd-radio>
            <wd-radio value="不满意">
              不太对
            </wd-radio>
          </wd-radio-group>
          <button class="open-plan-button" @click="openGeneratedPlan">
            查看这周菜单
          </button>
        </view>
      </view>
      <view v-if="generating || completed" class="composer composer--fixed">
        <input v-model="composerText" :disabled="agentBusy" confirm-type="send" placeholder="还想补充什么？直接告诉锅仔" aria-label="告诉锅仔你的安排" @confirm="submitComposer">
        <button :disabled="agentBusy" :aria-label="agentBusy ? '锅仔正在思考' : '发送'" @click="submitComposer">
          {{ agentBusy ? '思考中' : '发送' }}
        </button>
      </view>

      <view v-else class="chat-shell">
        <view class="chat-list">
          <view v-for="message in messages" :key="message.id" class="chat-row" :class="`chat-row--${message.role}`">
            <image v-if="message.role === 'agent'" :src="`${STATIC_BASE_URL}/static/guozai/action_08_peek.png`" mode="aspectFit" aria-hidden="true" />
            <view class="chat-bubble">
              <text>{{ message.text }}</text>
              <view v-if="message.tags?.length" class="memory-tags">
                <text v-for="tag in message.tags" :key="tag">
                  {{ tag }}
                </text>
              </view>
            </view>
          </view>
          <view v-if="agentBusy" class="chat-row chat-row--thinking" role="status" aria-live="polite">
            <image :src="`${STATIC_BASE_URL}/static/guozai/action_08_peek.png`" mode="aspectFit" aria-hidden="true" />
            <view class="chat-bubble thinking-bubble">
              <view class="thinking-dots" aria-hidden="true">
                <view class="thinking-dot thinking-dot--1" /><view class="thinking-dot thinking-dot--2" /><view class="thinking-dot thinking-dot--3" />
              </view>
              <text>锅仔正在理解你的话，准备下一步…</text>
            </view>
          </view>
        </view>

        <view v-if="agentTurn && !agentBusy" class="choice-card" :class="{ 'choice-card--cuisine': agentTurn.card.type === 'CUISINE' }" aria-live="polite">
          <view class="agent-card__head">
            <view><text>{{ agentTurn.card.title }}</text><text>{{ agentTurn.card.description }}</text></view>
            <image v-if="agentTurn.card.type === 'CUISINE'" :src="`${STATIC_BASE_URL}/static/guozai/action_06_glasses.png`" mode="aspectFit" aria-hidden="true" />
          </view>
          <view v-if="agentTurn.action === 'ASK_HOUSEHOLD'" class="household-picker">
            <view class="choice-grid choice-grid--household">
              <button v-for="option in agentTurn.card.options" :key="option.value" :class="{ selected: householdSelection.includes(householdValue(option.value)) }" :aria-pressed="householdSelection.includes(householdValue(option.value))" :disabled="agentBusy" @click="toggleHousehold(option.value)">
                <text class="selection-mark" aria-hidden="true">
                  {{ householdSelection.includes(householdValue(option.value)) ? '✓' : '' }}
                </text>
                <text>{{ option.label }}</text>
              </button>
            </view>
            <button class="household-confirm" :disabled="!householdSelection.length || agentBusy" @click="confirmHousehold">
              确认选择
            </button>
          </view>
          <view v-else class="choice-grid" :class="{ 'choice-grid--cuisine': agentTurn.card.type === 'CUISINE' }">
            <button v-for="option in agentTurn.card.options" :key="option.value" :disabled="agentBusy" @click="option.value === 'generate' ? generate() : runAgent(option.value, true, option.label)">
              {{ option.label }}
            </button>
          </view>
        </view>

        <view class="composer composer--fixed">
          <input v-model="composerText" :disabled="agentBusy" confirm-type="send" placeholder="也可以直接说：周三不做饭，想减脂" aria-label="告诉锅仔你的安排" @confirm="submitComposer">
          <button :disabled="agentBusy" :aria-label="agentBusy ? '锅仔正在思考' : '发送'" @click="submitComposer">
            {{ agentBusy ? '思考中' : '发送' }}
          </button>
        </view>
        <view class="archive-link" role="button" aria-label="查看备餐档案" @click="router.push({ name: 'weekly-plan' })">
          查看以前的菜单 ›
        </view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.agent-page { min-height: 100vh; padding: 0 28rpx calc(176rpx + env(safe-area-inset-bottom)); color: var(--mrc-text); background: var(--mrc-bg); box-sizing: border-box; }
.agent-hero { position: relative; min-height: 214rpx; overflow: hidden; padding: 22rpx 26rpx; border: 2rpx solid var(--mrc-border); border-radius: 32rpx; background: linear-gradient(145deg, var(--mrc-text-deep), #5b3325); box-shadow: var(--mrc-shadow-lift); box-sizing: border-box; }
.agent-hero__top { position: relative; z-index: 2; display: flex; align-items: center; justify-content: space-between; color: rgba(255,255,255,.7); font-size: 19rpx; font-weight: 800; letter-spacing: 2rpx; }
.agent-online { display: flex; align-items: center; gap: 8rpx; letter-spacing: 0; }.agent-online view { width: 12rpx; height: 12rpx; border-radius: 50%; background: var(--mrc-mint); box-shadow: 0 0 0 6rpx rgba(74,220,171,.13); }
.agent-hero__copy { position: relative; z-index: 2; display: flex; width: 66%; flex-direction: column; padding-top: 22rpx; }.agent-hero__eyebrow { color: #ff9d87; font-size: 20rpx; font-weight: 700; }.agent-hero__title { margin-top: 8rpx; color: #fff; font-size: 38rpx; font-weight: 800; line-height: 1.2; }.agent-hero__sub { margin-top: 8rpx; color: rgba(255,255,255,.72); font-size: 20rpx; line-height: 1.45; }
.agent-hero__guozai { position: absolute; z-index: 2; right: 6rpx; bottom: -18rpx; width: 204rpx; height: 204rpx; }.agent-hero__orbit { position: absolute; border: 2rpx solid rgba(255,255,255,.1); border-radius: 50%; }.agent-hero__orbit--one { right: -70rpx; bottom: -108rpx; width: 290rpx; height: 290rpx; }.agent-hero__orbit--two { right: 74rpx; top: 56rpx; width: 96rpx; height: 96rpx; }
.agent-loading { padding: 80rpx 0; text-align: center; color: var(--mrc-text-sub); font-size: 24rpx; }
.dialog-row { display: flex; align-items: flex-start; gap: 12rpx; margin: 24rpx 4rpx 18rpx; }.dialog-row > image { width: 72rpx; height: 72rpx; flex: 0 0 auto; }.agent-bubble { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 10rpx; padding: 22rpx 24rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 8rpx 28rpx 28rpx 28rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); color: var(--mrc-text-deep); font-size: 25rpx; line-height: 1.55; }.agent-bubble__name { color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; letter-spacing: 2rpx; }.memory-tags { display: flex; flex-wrap: wrap; gap: 8rpx; }.memory-tags text { padding: 7rpx 14rpx; border-radius: 999rpx; background: var(--mrc-surface-peach); color: var(--mrc-text-sub); font-size: 19rpx; line-height: 1.3; }
.current-plan { display: flex; align-items: center; justify-content: space-between; min-height: 100rpx; margin-bottom: 18rpx; padding: 16rpx 22rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 24rpx; background: var(--mrc-surface-sun); box-sizing: border-box; }.current-plan:active { opacity: .74; }.current-plan > view { display: flex; flex-direction: column; gap: 5rpx; }.current-plan__label { color: var(--mrc-accent); font-size: 19rpx; font-weight: 700; }.current-plan__title { color: var(--mrc-text-deep); font-size: 25rpx; font-weight: 700; }.current-plan__action { color: var(--mrc-accent); font-size: 21rpx; font-weight: 700; }
.tool-panel { overflow: hidden; border: 2rpx solid var(--mrc-border); border-radius: 32rpx; background: linear-gradient(180deg, var(--mrc-surface) 0%, var(--mrc-surface-sun) 100%); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }
.chat-shell { padding-bottom: 12rpx; }
.chat-list { display: flex; flex-direction: column; gap: 18rpx; padding: 10rpx 4rpx 22rpx; }
.chat-row { display: flex; align-items: flex-end; gap: 10rpx; }.chat-row > image { width: 62rpx; height: 62rpx; flex: 0 0 auto; }.chat-row--user { justify-content: flex-end; }.chat-bubble { max-width: 78%; padding: 19rpx 22rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 8rpx 25rpx 25rpx 25rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); color: var(--mrc-text-deep); font-size: 25rpx; line-height: 1.55; box-sizing: border-box; }.chat-row--user .chat-bubble { border-color: var(--mrc-accent); border-radius: 25rpx 8rpx 25rpx 25rpx; background: var(--mrc-accent); color: #fff; }.memory-tags { display: flex; flex-wrap: wrap; gap: 8rpx; margin-top: 12rpx; }.memory-tags text { padding: 7rpx 14rpx; border-radius: 999rpx; background: var(--mrc-surface-peach); color: var(--mrc-text-sub); font-size: 19rpx; line-height: 1.3; }
.chat-row--thinking { animation: thinking-in .22s ease-out both; }.thinking-bubble { display: flex; align-items: center; gap: 14rpx; color: var(--mrc-text-sub); }.thinking-dots { display: flex; align-items: center; gap: 7rpx; height: 24rpx; }.thinking-dot { width: 11rpx; height: 11rpx; border-radius: 50%; background: var(--mrc-accent); box-shadow: 0 3rpx 8rpx var(--mrc-accent-soft); animation: thinking-dot 1.05s cubic-bezier(.45, 0, .55, 1) infinite; will-change: transform, opacity; }.thinking-dot--2 { animation-delay: .14s; }.thinking-dot--3 { animation-delay: .28s; }
.choice-card { position: relative; padding: 22rpx; border: 2rpx solid var(--mrc-border); border-radius: 30rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-lift); }
.cuisine-card { overflow: hidden; padding: 22rpx; border-radius: 22rpx; background: linear-gradient(135deg, var(--mrc-surface-sun), var(--mrc-surface-peach)); }.cuisine-card__head { display: flex; align-items: center; justify-content: space-between; }.cuisine-card__head > view { display: flex; flex-direction: column; gap: 8rpx; }.cuisine-card__head text:first-child { color: var(--mrc-text-deep); font-size: 29rpx; font-weight: 800; }.cuisine-card__head text:last-child { color: var(--mrc-accent); font-size: 19rpx; font-weight: 700; }.cuisine-card__head image { width: 92rpx; height: 92rpx; }.cuisine-card__dishes { display: flex; flex-wrap: wrap; gap: 10rpx; margin: 18rpx 0; }.cuisine-card__dishes text { padding: 10rpx 14rpx; border: 2rpx solid rgba(255, 107, 91, .22); border-radius: 999rpx; background: rgba(255,255,255,.54); color: var(--mrc-text-deep); font-size: 20rpx; }.cuisine-card button { min-height: 80rpx; margin: 0; border-radius: 20rpx; font-size: 23rpx; font-weight: 750; }.cuisine-card button::after { display: none; }.cuisine-card__primary { border: 0; background: var(--mrc-primary-grad); color: #fff; }.cuisine-card__secondary { border: 0; background: transparent; color: var(--mrc-text-sub); }
.choice-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12rpx; }.choice-grid--people { grid-template-columns: repeat(5, 1fr); }.choice-grid button { display: flex; min-height: 88rpx; flex-direction: column; align-items: center; justify-content: center; margin: 0; padding: 12rpx 6rpx; border: 2rpx solid var(--mrc-border); border-radius: 20rpx; background: var(--mrc-surface); color: var(--mrc-text-deep); font-size: 25rpx; font-weight: 750; line-height: 1.25; }.choice-grid button::after, .choice-confirm::after, .generate-button::after, .restart-button::after, .composer button::after { display: none; }.choice-grid button:active { border-color: var(--mrc-accent); background: var(--mrc-accent-soft); }.choice-grid button text { margin-top: 7rpx; color: var(--mrc-text-sub); font-size: 18rpx; font-weight: 500; }
.choice-grid--household { grid-template-columns: repeat(3, 1fr); }.choice-grid--household button { position: relative; min-height: 92rpx; padding: 12rpx 8rpx; transition: border-color .18s ease, background-color .18s ease, color .18s ease; }.choice-grid--household button.selected { border-color: var(--mrc-accent); background: var(--mrc-accent-soft); color: var(--mrc-accent); }.choice-grid--household button .selection-mark { position: absolute; top: 8rpx; right: 10rpx; display: flex; width: 28rpx; height: 28rpx; align-items: center; justify-content: center; border: 2rpx solid var(--mrc-border); border-radius: 50%; color: transparent; font-size: 18rpx; line-height: 1; }.choice-grid--household button.selected .selection-mark { border-color: var(--mrc-accent); background: var(--mrc-accent); color: #fff; }.household-confirm { min-height: 82rpx; margin: 16rpx 0 0; border: 0; border-radius: 20rpx; background: var(--mrc-text-deep); color: #fff; font-size: 24rpx; font-weight: 750; }.household-confirm::after { display: none; }.household-confirm[disabled] { opacity: .38; }.choice-grid--spice { grid-template-columns: repeat(3, 1fr); }
.weekdays { display: grid; grid-template-columns: repeat(7, 1fr); gap: 8rpx; }.weekdays view { display: flex; min-height: 76rpx; align-items: center; justify-content: center; border: 2rpx solid var(--mrc-border); border-radius: 18rpx; color: var(--mrc-text-sub); font-size: 22rpx; }.weekdays view.selected { border-color: var(--mrc-accent); background: var(--mrc-accent-soft); color: var(--mrc-accent); font-weight: 800; }.choice-confirm { min-height: 88rpx; margin: 18rpx 0 0; border: 0; border-radius: 22rpx; background: var(--mrc-text-deep); color: #fff; font-size: 25rpx; font-weight: 750; }
.plan-confirm__summary { display: flex; justify-content: space-between; padding: 4rpx 4rpx 18rpx; color: var(--mrc-text-deep); font-size: 24rpx; font-weight: 750; }.plan-confirm__summary text:last-child { color: var(--mrc-accent); font-size: 21rpx; }.generate-button { display: flex; min-height: 98rpx; align-items: center; justify-content: space-between; margin: 0; padding: 0 26rpx; border: 0; border-radius: 24rpx; background: var(--mrc-primary-grad); color: #fff; box-shadow: var(--mrc-shadow-coral); line-height: 1.2; }.generate-button:active { transform: scale(.98); }.generate-button { font-size: 28rpx; font-weight: 800; }.generate-button text { font-size: 19rpx; font-weight: 500; opacity: .86; }.restart-button { min-height: 72rpx; margin: 8rpx 0 0; border: 0; background: transparent; color: var(--mrc-text-sub); font-size: 21rpx; }
.composer { display: flex; align-items: center; gap: 10rpx; min-height: 96rpx; margin-top: 18rpx; padding: 10rpx 12rpx 10rpx 22rpx; border: 2rpx solid var(--mrc-border); border-radius: 28rpx; background: var(--mrc-surface); box-sizing: border-box; }.composer--fixed { position: fixed; z-index: 20; right: 28rpx; bottom: calc(18rpx + env(safe-area-inset-bottom)); left: 28rpx; margin: 0; box-shadow: 0 12rpx 40rpx rgba(69, 37, 24, .16); }.composer input { min-width: 0; flex: 1; color: var(--mrc-text-deep); font-size: 23rpx; }.composer input[disabled] { opacity: .58; }.composer button { display: flex; width: 88rpx; min-height: 70rpx; align-items: center; justify-content: center; margin: 0; padding: 0; border: 0; border-radius: 20rpx; background: var(--mrc-text-deep); color: #fff; font-size: 21rpx; }.composer button[disabled] { opacity: .55; }.archive-link { display: flex; min-height: 82rpx; align-items: center; justify-content: center; color: var(--mrc-text-sub); font-size: 21rpx; }
.conversation-rating { display: flex; flex-direction: column; gap: 16rpx; margin-top: 18rpx; padding-top: 22rpx; border-top: 2rpx solid var(--mrc-border-light); color: var(--mrc-text-deep); font-size: 25rpx; font-weight: 750; }:deep(.rating-selector) { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12rpx; width: 100%; }:deep(.rating-selector .wd-radio.is-button) { display: flex; width: 100%; min-width: 0; max-width: none; min-height: 76rpx; align-items: center; justify-content: center; margin: 0; border-color: var(--mrc-border); border-radius: 18rpx; background: var(--mrc-surface); box-sizing: border-box; }:deep(.rating-selector .wd-radio__label) { display: flex; min-height: 72rpx; align-items: center; justify-content: center; padding: 0 12rpx; color: var(--mrc-text-sub); font-size: 22rpx; }:deep(.rating-selector .wd-radio.is-checked) { border-color: var(--mrc-accent); background: var(--mrc-accent-soft); }:deep(.rating-selector .wd-radio.is-checked .wd-radio__label) { color: var(--mrc-accent); font-weight: 800; }.open-plan-button { min-height: 88rpx !important; border: 0 !important; background: var(--mrc-primary-grad) !important; color: #fff !important; font-size: 25rpx !important; }
.tool-panel { margin-top: 8rpx; padding: 26rpx; }.tool-panel__head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24rpx; color: var(--mrc-text-deep); font-size: 28rpx; font-weight: 800; }.tool-panel__head text:last-child { padding: 7rpx 13rpx; border-radius: 999rpx; background: var(--mrc-accent-soft); color: var(--mrc-accent); font-size: 20rpx; }.tool-step { display: flex; align-items: center; gap: 18rpx; min-height: 98rpx; opacity: .46; }.tool-step--active, .tool-step--done { opacity: 1; }.tool-step__state { display: flex; width: 54rpx; height: 54rpx; flex: 0 0 auto; align-items: center; justify-content: center; border: 2rpx solid var(--mrc-border); border-radius: 50%; color: var(--mrc-text-sub); background: var(--mrc-surface); font-size: 20rpx; }.tool-step--done .tool-step__state { border-color: var(--mrc-primary-deep); background: var(--mrc-primary-grad); color: #fff; box-shadow: 0 6rpx 14rpx rgba(239, 90, 60, .2); }.tool-step--active .tool-step__state { border-color: var(--mrc-accent); background: var(--mrc-surface-peach); box-shadow: 0 0 0 6rpx var(--mrc-accent-soft); }.tool-step__pulse { width: 15rpx; height: 15rpx; border-radius: 50%; background: var(--mrc-accent); animation: pulse 1s ease-in-out infinite; }.tool-step > view:last-child { display: flex; min-width: 0; flex-direction: column; gap: 6rpx; }.tool-step__title { color: var(--mrc-text-deep); font-size: 24rpx; font-weight: 750; }.tool-step__copy { color: var(--mrc-text-sub); font-size: 20rpx; }.tool-step + .tool-step { border-top: 2rpx solid var(--mrc-border-light); }
@keyframes pulse { 50% { opacity: .35; transform: scale(.7); } }
@keyframes thinking-in { from { opacity: 0; transform: translateY(10rpx); } }
@keyframes thinking-dot { 0%, 65%, 100% { opacity: .32; transform: translateY(3rpx) scale(.82); } 32% { opacity: 1; transform: translateY(-7rpx) scale(1.12); } }
@media (prefers-reduced-motion: reduce) { .chat-row--thinking, .thinking-dots view { animation: none; } }
</style>
