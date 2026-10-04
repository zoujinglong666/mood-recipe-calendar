<script setup lang="ts">
import type { FoodMemoryView } from '@/api/preferences'
import type { MealAgentConversationSnapshot, MealAgentHistoryMessage, MealAgentOption, MealAgentState, MealAgentTurn, WeeklyPlan, UsageQuotaView } from '@/api/weeklyPlans'
import { computed, nextTick, ref } from 'vue'
import { fetchFoodMemory } from '@/api/preferences'
import { fetchAgentConversationQuota, fetchCurrentAgentConversation, generateWeeklyPlan, getCurrentPlan, requestWeeklyPlanCompletionNotice, rateAgentConversation, resetAgentConversation, runMealAgentTurn } from '@/api/weeklyPlans'
import { navBack } from '@/composables/useNavBar'
import { STATIC_BASE_URL } from '@/utils/assets'
import { safeDecodePrompt } from '@/utils/safeDecodePrompt'
import { toastError } from '@/utils/toast'
import { useUserStore } from '@/stores/user'
import { ensureLogin, refreshUserInfo } from '@/utils/login'
import { requireMember } from '@/utils/memberGate'
import { recordShare } from '@/api/share'
import GuozaiChipGroup from '@/components/guozai/GuozaiChipGroup.vue'
import GuozaiButton from '@/components/guozai/GuozaiButton.vue'
import Icon from '@/components/common/Icon.vue'

definePage({ name: 'meal-agent', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '锅仔管饭' } })

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
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
const RATING_OPTIONS = [
  { value: '满意', label: '刚刚好' },
  { value: '一般', label: '还行' },
  { value: '不满意', label: '不太对' },
]
const hasElder = ref(false)
const hasChild = ref(false)
const spiceLevel = ref('微辣')
const sessionCuisine = ref('')
interface ChatMessage { id: number, role: 'agent' | 'user', text: string, tags?: string[], selected?: boolean }
interface MealAgentDraft { conversationId: string, messages: ChatMessage[], state: MealAgentState, turn?: MealAgentTurn, completed?: boolean, generatedPlanId?: number }
const composerText = ref('')
const messages = ref<ChatMessage[]>([])
const agentState = ref<MealAgentState>({})
const agentTurn = ref<MealAgentTurn>()
const agentQuota = ref<UsageQuotaView>()
const agentBusy = ref(false)
const agentTyping = ref(false)
// 对话轮进行中，按锅仔真实会做的子任务分步点亮（读记忆 → 看冰箱 → 组织回答），替代原来的静态三点动画，消除「像卡死」的等待感。
const thinkingSteps = ['读取你的口味和忌口', '看看冰箱和朋友的情况', '组织这一轮的回答']
const thinkingStepIndex = ref(-1)
let thinkingTimer: ReturnType<typeof setInterval> | undefined
let typingTimer: ReturnType<typeof setInterval> | undefined

function startThinking() {
  stopThinking()
  thinkingStepIndex.value = 0
  thinkingTimer = setInterval(() => {
    if (thinkingStepIndex.value < thinkingSteps.length - 1)
      thinkingStepIndex.value += 1
    else
      clearInterval(thinkingTimer)
  }, 800)
}
function stopThinking() {
  if (thinkingTimer) { clearInterval(thinkingTimer); thinkingTimer = undefined }
  thinkingStepIndex.value = -1
}
function stopTyping() {
  if (typingTimer) { clearInterval(typingTimer); typingTimer = undefined }
  agentTyping.value = false
}
const householdSelection = ref<string[]>([])
const fridgeSelection = ref<string[]>([])
const otherInput = ref(false)
const agentCards = computed(() => {
  const cards = agentTurn.value?.cards
  if (cards?.length)
    return cards.slice(0, 1)
  return agentTurn.value?.card ? [agentTurn.value.card] : []
})
// 家庭多选选择器只用于模型给出了结构化 household 选项的卡；
// 模型现写的自由文本选项（如"就我们俩，不用照顾"）走通用单选，交给后端模型理解
const householdPickerActive = computed(() => {
  if (agentTurn.value?.action !== 'ASK_HOUSEHOLD')
    return false
  const options = agentCards.value[0]?.options || []
  return options.some(option => /^(household=|elder=|child=|pregnant=)/.test(option.value))
})
let messageId = 0
let progressTimer: ReturnType<typeof setInterval> | undefined
let agentConversationId = ''

const agentSteps = [
  { title: '读取锅仔记忆', copy: '口味、忌口和最近做过的菜' },
  { title: '检查家庭情况', copy: '老人、小孩和吃辣程度' },
  { title: '检查本周安排', copy: '做饭日期和每天菜数' },
  { title: '搭配主菜与配菜', copy: '避开拒绝过的菜，减少重复' },
  { title: '合并买菜清单', copy: '复用食材，整理成一张清单' },
]

const isMember = computed(() => userStore.isActiveMember)
/** 联网搜索：会员专属开关，开启后本次会话所有对话都走联网 */
const webSearchEnabled = ref(false)
const canUseAgent = computed(() => isMember.value || (agentQuota.value?.remaining || 0) > 0)

/** 切换联网搜索：非会员拦截并引导开通，会员切换开关 */
function toggleWebSearch() {
  if (!requireMember('meal_agent_web_search'))
    return
  webSearchEnabled.value = !webSearchEnabled.value
}
const hasConversation = computed(() => messages.value.length > 0)

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
    state.requestedIngredients?.length ? `指定食材：${state.requestedIngredients.join('、')}` : '',
    state.mealContext || '',
  ].filter(Boolean).join('；')
})

onShow(load)
onUnmounted(() => {
  stopProgress()
  stopThinking()
  stopTyping()
})

function conversationDraftKey() {
  const openid = userStore.openid || userStore.userInfo?.openid || 'guest'
  return `mrc_meal_agent_draft:${openid}`
}

/** 生成会话标识；非会员的额度是按 conversationId 计一次，必须稳定且非空。 */
function newConversationId() {
  return `agent-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

/** 所有请求前统一取会话标识，兜底自愈，避免非会员命中「缺少对话会话标识」。 */
function currentConversationId() {
  if (!agentConversationId)
    agentConversationId = newConversationId()
  return agentConversationId
}

function persistConversation() {
  if (!agentConversationId)
    return
  const draft: MealAgentDraft = {
    conversationId: currentConversationId(),
    messages: messages.value.slice(-80),
    state: agentState.value,
    turn: agentTurn.value,
    completed: completed.value,
    generatedPlanId: generatedPlanId.value,
  }
  uni.setStorageSync(conversationDraftKey(), draft)
}

function restoreConversation() {
  const raw = uni.getStorageSync(conversationDraftKey())
  const draft = typeof raw === 'string' ? (() => {
    try { return JSON.parse(raw) as MealAgentDraft } catch { return undefined }
  })() : raw as MealAgentDraft | undefined
  if (!draft?.conversationId || !Array.isArray(draft.messages)) {
    agentConversationId = newConversationId()
    return
  }
  agentConversationId = draft.conversationId
  // 旧草稿把所有用户消息都标了 selected=true，恢复后全变成「已选择」卡；历史消息一律按普通气泡处理，
  // 选项回执只在发生它的那轮会话里有意义。
  messages.value = draft.messages
    .filter(message => message && (message.role === 'agent' || message.role === 'user') && typeof message.text === 'string')
    .map(message => ({ ...message, selected: false }))
  messageId = messages.value.reduce((max, message) => Math.max(max, Number(message.id) || 0), 0)
  agentState.value = draft.state || {}
  agentTurn.value = draft.turn
  completed.value = Boolean(draft.completed)
  generatedPlanId.value = draft.generatedPlanId
  people.value = agentState.value.people || people.value
  cookingDays.value = agentState.value.cookingDays || cookingDays.value
  dishesPerDay.value = agentState.value.dishesPerDay || dishesPerDay.value
  healthGoal.value = agentState.value.healthGoal || healthGoal.value
  budget.value = agentState.value.budget || budget.value
  hasElder.value = agentState.value.hasElder ?? hasElder.value
  hasChild.value = agentState.value.hasChild ?? hasChild.value
  spiceLevel.value = agentState.value.spiceLevel || spiceLevel.value
  sessionCuisine.value = agentState.value.favoriteCuisine || sessionCuisine.value
}

function restoreRemoteConversation(snapshot: MealAgentConversationSnapshot) {
  // 远端快照可能来自早期脏数据（conversationId 为空），此时不要覆盖成本地已生成的标识
  agentConversationId = snapshot.conversationId || currentConversationId()
  const restored = (snapshot.messages || []).filter(message => message && (message.role === 'agent' || message.role === 'user') && typeof message.text === 'string')
  // 旧会话的 transcript 缺最后一条锅仔回复（历史 bug：只存了 turnJson 没存进 transcript）；补回，避免刷新后最后一句消失
  const turnReply = (snapshot.turn?.reply || '').trim()
  const lastAgent = [...restored].reverse().find(message => message.role === 'agent')
  if (turnReply && (!lastAgent || lastAgent.text !== turnReply))
    restored.push({ role: 'agent', text: turnReply })
  // 远端 transcript 同样消毒：旧数据里用户消息普遍带 selected=true（历史 bug），恢复时一律按普通气泡渲染
  messages.value = restored.map((message, index) => ({ ...message, id: index + 1, selected: false }))
  messageId = messages.value.length
  agentState.value = snapshot.state || {}
  agentTurn.value = snapshot.turn || undefined
  people.value = agentState.value.people || people.value
  cookingDays.value = agentState.value.cookingDays || cookingDays.value
  dishesPerDay.value = agentState.value.dishesPerDay || dishesPerDay.value
  healthGoal.value = agentState.value.healthGoal || healthGoal.value
  budget.value = agentState.value.budget || budget.value
  hasElder.value = agentState.value.hasElder ?? hasElder.value
  hasChild.value = agentState.value.hasChild ?? hasChild.value
  spiceLevel.value = agentState.value.spiceLevel || spiceLevel.value
  sessionCuisine.value = agentState.value.favoriteCuisine || sessionCuisine.value
}

async function load() {
  loading.value = true
  try {
    await ensureLogin()
    await refreshUserInfo(true)
  }
  catch (error) {
    toastError(error, '登录后才能安排菜单')
    loading.value = false
    return
  }
  const [memoryResult, planResult, quotaResult, conversationResult] = await Promise.allSettled([fetchFoodMemory(), getCurrentPlan(), fetchAgentConversationQuota(), fetchCurrentAgentConversation()])
  memory.value = memoryResult.status === 'fulfilled' ? memoryResult.value : undefined
  currentPlan.value = planResult.status === 'fulfilled' ? planResult.value : undefined
  agentQuota.value = quotaResult.status === 'fulfilled' ? quotaResult.value : undefined
  if (conversationResult.status === 'fulfilled' && conversationResult.value?.messages?.length)
    restoreRemoteConversation(conversationResult.value)
  else
    restoreConversation()
  if (memory.value?.explicit.healthGoal)
    healthGoal.value = memory.value.explicit.healthGoal
  if (canUseAgent.value && !messages.value.length) {
    addAgent(agentGreeting.value, memoryTags.value)
    const initialPrompt = safeDecodePrompt(route.query.prompt)
    await runAgent(initialPrompt, Boolean(initialPrompt))
  }
  loading.value = false
}

function addAgent(text: string, tags?: string[]) {
  messages.value.push({ id: ++messageId, role: 'agent', text: localizeAgentText(text), tags })
}

/** 开启一轮新对话：归档后端会话 + 清空本地现场；锅仔的长期口味记忆不受影响。 */
async function startNewConversation() {
  if (agentBusy.value || loading.value)
    return
  try {
    await resetAgentConversation()
  }
  catch {
    // 归档失败不阻塞：本地换新 conversationId 后，下一轮会落成新会话行
  }
  agentConversationId = newConversationId()
  messages.value = []
  messageId = 0
  agentTurn.value = undefined
  agentState.value = {}
  quickReplies.value = []
  rating.value = ''
  otherInput.value = false
  completed.value = false
  generatedPlanId.value = undefined
  persistConversation()
  addAgent(agentGreeting.value, memoryTags.value)
  await scrollToLatest()
}

/** 对话过长被判停时，弹窗引导用户一键开新对话。 */
async function offerNewConversation() {
  const confirmed = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '这轮对话太长啦',
      content: '开启一轮新对话吧，锅仔记住的口味偏好还会一直在。',
      confirmText: '新对话',
      cancelText: '暂不',
      success: res => resolve(Boolean(res.confirm)),
      fail: () => resolve(false),
    })
  })
  if (confirmed)
    await startNewConversation()
}

/** 模型偶尔会把内部枚举原样带进回复，用户界面统一显示中文。 */
function localizeAgentText(text: string) {
  return text
    .replace(/\bbudget\b/gi, '预算')
    .replace(/\bhealthGoal\b/gi, '饮食目标')
    .replace(/\bmealContext\b/gi, '用餐场景')
    .replace(/\bSAVE\b/g, '省钱')
    .replace(/\bDAILY\b/g, '日常')
    .replace(/\bTREAT\b/g, '丰盛')
    .replace(/\bFITNESS\b/g, '均衡')
    .replace(/\bLEAN\b/g, '清淡')
    .replace(/\bBALANCED\b/g, '均衡')
}

async function scrollToLatest() {
  await nextTick()
  uni.pageScrollTo({ scrollTop: 999999, duration: 220 })
}

/** echoMode：option=点卡片选项/快捷回复发出的（回执显示成选中的选项），text=手动输入的（普通气泡）。 */
async function runAgent(message: string, echo = false, echoLabel = message, webSearch = webSearchEnabled.value, echoMode: 'option' | 'text' = 'text') {
  if (agentBusy.value || agentTyping.value)
    return
  if (echo && message)
    messages.value.push({ id: ++messageId, role: 'user', text: echoLabel, selected: echoMode === 'option' })
  agentBusy.value = true
  quickReplies.value = []
  startThinking()
  await scrollToLatest()
  try {
    const history: MealAgentHistoryMessage[] = messages.value.map(({ role, text, tags, selected }) => ({ role, text, tags, selected }))
    const turn = await runMealAgentTurn(message, agentState.value, currentConversationId(), history, webSearch)
    stopThinking()
    agentTurn.value = turn
    otherInput.value = false
    agentState.value = turn.state
    householdSelection.value = turn.action === 'ASK_HOUSEHOLD'
      ? [turn.state.hasElder ? 'elder' : '', turn.state.hasChild ? 'child' : ''].filter(Boolean)
      : []
    fridgeSelection.value = []
    people.value = turn.state.people || people.value
    cookingDays.value = turn.state.cookingDays || []
    dishesPerDay.value = turn.state.dishesPerDay || dishesPerDay.value
    healthGoal.value = turn.state.healthGoal || healthGoal.value
    budget.value = turn.state.budget || budget.value
    hasElder.value = turn.state.hasElder ?? hasElder.value
    hasChild.value = turn.state.hasChild ?? hasChild.value
    spiceLevel.value = turn.state.spiceLevel || spiceLevel.value
    sessionCuisine.value = turn.state.favoriteCuisine || sessionCuisine.value
    await streamAgentReply(turn.reply)
    // 快捷追问由模型按本轮语境现写（后端 followups），不再用前端写死映射
    quickReplies.value = (turn.followups || []).map(text => text.trim()).filter(Boolean).slice(0, 3)
    persistConversation()
  }
  catch (error) {
    stopThinking()
    stopTyping()
    const message = String((error as any)?.message || '')
    if (message.includes('重新开始') || message.includes('过长')) {
      // 后端判定本轮对话过长：主动引导开启新对话，而不是让用户卡死
      await offerNewConversation()
      return
    }
    toastError(error, message.includes('签到') || message.includes('用完') ? '今日签到赠送的对话已用完，明天再来' : '锅仔刚刚走神了，请再说一次')
  }
  finally {
    agentBusy.value = false
    stopThinking()
    await scrollToLatest()
  }
}

// 把锅仔的回复以打字机方式逐字浮现；回复落定后才解除输入锁定。
async function streamAgentReply(reply: string) {
  const full = localizeAgentText(reply || '')
  const index = messages.value.length
  messages.value.push({ id: ++messageId, role: 'agent', text: '' })
  await scrollToLatest()
  if (!full)
    return
  agentTyping.value = true
  // 教学类回复（带来源标注的分步做法）信息密度高，一次性整卡呈现，不逐字打字
  if (parseTeachingReply(full).steps.length) {
    messages.value[index].text = full
    agentTyping.value = false
    return
  }
  const chars = Array.from(full)
  const step = Math.max(1, Math.round(chars.length / 90))
  await new Promise<void>((resolve) => {
    let i = 0
    typingTimer = setInterval(() => {
      i = Math.min(chars.length, i + step)
      messages.value[index].text = chars.slice(0, i).join('')
      if (i >= chars.length) {
        clearInterval(typingTimer)
        typingTimer = undefined
        agentTyping.value = false
        resolve()
      }
    }, 28)
  })
}

// ===== A. 快捷追问：由后端模型按本轮语境生成（turn.followups），点一下就能继续 =====
const quickReplies = ref<string[]>([])

async function sendQuickReply(text: string) {
  await runAgent(text, true, text, webSearchEnabled.value, 'option')
}

// ===== B. 教学卡：带来源标注的做法回复解析为分步卡片，替代文字墙 =====
const TEACH_SOURCES: Record<string, string> = {
  'baike.baidu.com': '百度百科',
  'xiachufang.com': '下厨房',
  'meishij.net': '美食杰',
  'douguo.com': '豆果美食',
}

interface TeachingReply { intro: string, steps: string[], source: string }

function parseTeachingReply(raw: string): TeachingReply {
  const text = (raw || '').trim()
  const result: TeachingReply = { intro: '', steps: [], source: '' }
  if (!text) return result
  let body = text
  const sourceMatch = text.match(/[（(]来源[：:]\s*([^）)]+)[）)]\s*$/)
  if (sourceMatch) {
    body = text.slice(0, sourceMatch.index).trim()
    const rawSource = sourceMatch[1].trim()
    if (/^https?:\/\//i.test(rawSource)) {
      // 兼容历史长 URL：映射常见站点名，其余统一显示“网络资料”
      const host = Object.keys(TEACH_SOURCES).find(key => rawSource.includes(key))
      result.source = host ? TEACH_SOURCES[host] : '网络资料'
    }
    else {
      result.source = rawSource
    }
  }
  // 显式编号（1. / 1️⃣ / 步骤1 / ①）达到 3 段才按编号拆
  const numbered = body.split(/(?=(?:[1-9][.、）)]|[1-9]️⃣|步骤\s*[1-9]|①|②|③|④|⑤))/).map(s => s.trim()).filter(Boolean)
  if (numbered.length >= 3 && body.length > 60) {
    result.steps = numbered.map(s => s.replace(/^[1-9][.、）)]\s*|^[1-9]️⃣\s*|^步骤\s*[1-9][：:.、]?\s*|^[①②③④⑤]\s*/, ''))
    return result
  }
  // 无编号时按分号/句号分句，3-8 段且每段成句才列表化，避免误伤普通回复
  const sentences = body.split(/[；;。]/).map(s => s.trim()).filter(s => s.length >= 8)
  if (sentences.length >= 3 && sentences.length <= 8 && body.length > 50) {
    result.steps = sentences
    return result
  }
  result.intro = body
  return result
}

// ===== C. 记忆可视化：锅仔已记住的要点常驻顶部，点一下就能改 =====
const GOAL_LABELS: Record<string, string> = { LEAN: '清淡', FITNESS: '均衡', BALANCED: '均衡', DAILY: '日常', TREAT: '丰盛', SAVE: '省钱' }

const memoryChips = computed(() => {
  const state = agentState.value || {}
  const chips: { key: string, label: string, intent: string }[] = []
  if (state.people) chips.push({ key: 'people', label: `${state.people} 人吃`, intent: '人数要调整一下' })
  if (state.hasElder) chips.push({ key: 'elder', label: '家有老人', intent: '家庭情况要调整一下' })
  if (state.hasChild) chips.push({ key: 'child', label: '家有小孩', intent: '家庭情况要调整一下' })
  if (state.spiceLevel) chips.push({ key: 'spice', label: `口味:${state.spiceLevel}`, intent: '口味要改一下，重新说说辣度' })
  if (state.favoriteCuisine) chips.push({ key: 'cuisine', label: `偏爱${state.favoriteCuisine}`, intent: '想换个菜系' })
  if (state.healthGoal) chips.push({ key: 'goal', label: `目标:${GOAL_LABELS[state.healthGoal] || state.healthGoal}`, intent: '饮食目标要调整' })
  if (state.budget) chips.push({ key: 'budget', label: `预算:${GOAL_LABELS[state.budget] || state.budget}`, intent: '预算标准要调整' })
  if (state.cookingDays?.length) chips.push({ key: 'days', label: `做 ${state.cookingDays.length} 天`, intent: '做饭的天数要调整' })
  if (state.dishesPerDay) chips.push({ key: 'dishes', label: `每天 ${state.dishesPerDay} 道`, intent: '每天做的菜数要调整' })
  for (const item of state.requestedIngredients || [])
    chips.push({ key: `want-${item}`, label: `想吃${item}`, intent: `不想吃${item}了，换点别的` })
  return chips
})

async function onMemoryChipTap(intent: string) {
  await runAgent(intent, true, intent, webSearchEnabled.value, 'option')
}

function householdValue(value: string) {
  if (value === 'elder=yes' || value.includes('elder'))
    return 'elder'
  if (value === 'child=yes' || value.includes('child'))
    return 'child'
  if (value === 'pregnant=yes' || value.includes('pregnant'))
    return 'pregnant'
  if (value.includes('adult') || value.includes('none'))
    return 'adult'
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
  const adult = householdSelection.value.includes('adult') || householdSelection.value.includes('none')
  const value = adult ? 'household=adult' : `household=${householdSelection.value.join(',')}`
  const label = adult ? '都是成人' : householdSelection.value.map(item => item === 'elder' ? '有老人' : item === 'child' ? '有小孩' : '有孕妇').join('、')
  await runAgent(value, true, label, webSearchEnabled.value, 'option')
}

async function submitComposer() {
  const text = composerText.value.trim()
  if (!text)
    return
  composerText.value = ''
  await runAgent(text, true)
}

async function selectCardOption(value: string, label: string) {
  if (value === 'other') {
    otherInput.value = true
    return
  }
  await runAgent(value, true, label, webSearchEnabled.value, 'option')
}

interface OptionCard { type: string, title: string, description: string, options: MealAgentOption[] }

/** READY 卡里真正的「生成」按钮；其余是模型现写的对话式选项，走消息而不是触发生成。 */
function readyGenerateOptions(card: OptionCard) {
  return card.options.filter(option => option.value === 'generate')
}
function readyOtherOptions(card: OptionCard) {
  return card.options.filter(option => option.value !== 'generate')
}

/** 动态选项统一走专用 ChipGroup：一行一个，点选即作为消息发给锅仔。 */
async function onCardOptionChange(card: OptionCard, value: string | string[]) {
  const picked = (Array.isArray(value) ? value[0] : value) as string
  if (!picked)
    return
  if (picked === 'generate') {
    generate()
    return
  }
  const label = card.options.find(option => option.value === picked)?.label || picked
  await selectCardOption(picked, label)
}

function toggleFridgeItem(value: string) {
  if (value === 'fridge_priority=soon') {
    fridgeSelection.value = [value]
    return
  }
  fridgeSelection.value = fridgeSelection.value.filter(item => item !== 'fridge_priority=soon')
  fridgeSelection.value = fridgeSelection.value.includes(value)
    ? fridgeSelection.value.filter(item => item !== value)
    : [...fridgeSelection.value, value]
}

async function confirmFridgeSelection() {
  const priority = !fridgeSelection.value.length || fridgeSelection.value[0] === 'fridge_priority=soon'
  const values = priority
    ? fridgeSelection.value
    : [`fridge_select=${fridgeSelection.value.map(value => value.replace(/^fridge_item=/, '')).join(',')}`]
  const label = priority ? '优先消耗临期食材' : `选用：${fridgeSelection.value.map(value => value.replace(/^fridge_item=/, '')).join('、')}`
  await runAgent(values[0], true, label, webSearchEnabled.value, 'option')
}

async function submitOther() {
  const text = composerText.value.trim()
  if (!text) return
  composerText.value = ''
  otherInput.value = false
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
    persistConversation()
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
    await rateAgentConversation(value)
    // 让用户感知到反馈真的被记住：评分进入锅仔的复盘，下一轮对话生效
    if (value !== '满意')
      uni.showToast({ title: '锅仔记下了，下次会更懂你', icon: 'none', duration: 2200 })
    else
      uni.showToast({ title: '收到！锅仔继续保持', icon: 'none', duration: 1800 })
  }
  catch {
    // 评分不影响查看已生成的菜单。
  }
}

function onRatingChange(value: string | string[]) {
  const ratingValue = Array.isArray(value) ? value[0] : value
  if (ratingValue)
    void rateConversation(ratingValue)
}

function openGeneratedPlan() {
  if (generatedPlanId.value)
    router.replace({ name: 'weekly-plan-detail', params: { id: String(generatedPlanId.value) } })
}

function openCurrentPlan() {
  if (currentPlan.value)
    router.push({ name: 'weekly-plan-detail', params: { id: String(currentPlan.value.id) } })
}

function openMembership() {
  router.push({ name: 'membership' })
}

function openGallery() {
  router.push({ name: 'gallery' })
}

onShareAppMessage(() => {
  const sharer = userStore.openid
  const path = `/pages/meal-agent/index${sharer ? `?sharer=${encodeURIComponent(sharer)}` : ''}`
  // 记录分享：发放「分享家」徽章 + 非会员当日 +1 次对话激励（失败静默，不影响转发）
  void recordShare('meal-agent').catch(() => {})
  return {
    title: '锅仔管饭：这一周交给锅仔安排，少想一点认真吃饭',
    path,
  }
})
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
      <button class="agent-share" open-type="share">分享给朋友 · 一起让锅仔管饭</button>
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

      <view v-if="canUseAgent && (generating || completed)" class="tool-panel" aria-live="polite">
        <view class="tool-panel__head">
          <text>{{ completed ? '这一周已经排好' : (isMember ? '锅仔正在调用工具' : '正在生成简单菜单') }}</text><text>{{ completed ? '完成' : `${Math.min(activeStep + 1, agentSteps.length)}/${agentSteps.length}` }}</text>
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
        <view v-if="completed && isMember" class="conversation-rating">
          <text>这次锅仔问得合适吗？</text>
          <GuozaiChipGroup
            :model-value="rating"
            :options="RATING_OPTIONS"
            :columns="3"
            aria-label="为这次对话评分"
            @change="onRatingChange"
          />
          <GuozaiButton variant="primary" aria-label="查看这周菜单" @click="openGeneratedPlan">
            查看这周菜单
          </GuozaiButton>
        </view>
        <GuozaiButton v-else-if="completed" variant="primary" aria-label="查看这周菜单" @click="openGeneratedPlan">
          查看这周菜单
        </GuozaiButton>
      </view>
      <view v-if="canUseAgent && (generating || completed)" class="composer composer--fixed">
        <input v-model="composerText" :disabled="agentBusy || agentTyping" confirm-type="send" placeholder="还想补充什么？直接告诉锅仔" aria-label="告诉锅仔你的安排" @confirm="submitComposer">
        <GuozaiButton class="composer__send" variant="primary" :block="false" :disabled="agentBusy || agentTyping || !composerText.trim()" :loading="agentBusy || agentTyping" :aria-label="agentBusy || agentTyping ? '锅仔正在思考' : '发送'" @click="submitComposer">
          发送
        </GuozaiButton>
      </view>

      <view v-else-if="canUseAgent || hasConversation" class="chat-shell" :class="{ 'chat-shell--readonly': !canUseAgent }">
        <view v-if="!canUseAgent" class="history-readonly" role="status">
          今日对话次数已用完，历史内容仍保留；次数恢复后可以继续聊。
        </view>
        <scroll-view v-if="memoryChips.length" class="memory-strip" scroll-x :show-scrollbar="false" aria-label="锅仔记住的信息，点击可修改">
          <view class="memory-strip__inner">
            <button v-for="chip in memoryChips" :key="chip.key" class="memory-strip__chip" :disabled="agentBusy || agentTyping" @click="onMemoryChipTap(chip.intent)">
              {{ chip.label }}
            </button>
          </view>
        </scroll-view>
        <view class="chat-list">
          <view v-for="message in messages" :key="message.id" class="chat-row" :class="[`chat-row--${message.role}`, { 'chat-row--selection': message.selected }]">
            <image v-if="message.role === 'agent'" :src="`${STATIC_BASE_URL}/static/guozai/action_08_peek.png`" mode="aspectFit" aria-hidden="true" />
            <view v-if="message.selected" class="selection-card" aria-label="已选择的回答">
              <text class="selection-card__eyebrow">已选择</text>
              <view class="selection-card__row">
                <text class="selection-card__check">✓</text>
                <text class="selection-card__value">{{ message.text }}</text>
              </view>
            </view>
            <view v-else class="agent-reply">
              <view v-if="parseTeachingReply(message.text).steps.length" class="chat-bubble teach-bubble">
                <text class="teach-bubble__eyebrow">菜谱小课堂</text>
                <view class="teach-bubble__steps">
                  <view v-for="(step, stepIdx) in parseTeachingReply(message.text).steps" :key="stepIdx" class="teach-bubble__step">
                    <text class="teach-bubble__no">{{ stepIdx + 1 }}</text>
                    <text class="teach-bubble__copy">{{ step }}</text>
                  </view>
                </view>
                <text v-if="parseTeachingReply(message.text).source" class="teach-bubble__source">来源：{{ parseTeachingReply(message.text).source }}</text>
              </view>
              <view v-else class="chat-bubble">
                <text>{{ message.text }}</text>
                <view v-if="message.tags?.length" class="memory-tags">
                  <text v-for="tag in message.tags" :key="tag">
                    {{ tag }}
                  </text>
                </view>
              </view>
            </view>
          </view>
          <view v-if="agentBusy && thinkingStepIndex >= 0" class="chat-row chat-row--thinking" role="status" aria-live="polite">
            <image :src="`${STATIC_BASE_URL}/static/guozai/action_08_peek.png`" mode="aspectFit" aria-hidden="true" />
            <view class="chat-bubble thinking-bubble">
              <view class="thinking-steps">
                <view v-for="(step, idx) in thinkingSteps" :key="step" class="thinking-step" :class="{ 'thinking-step--done': idx < thinkingStepIndex, 'thinking-step--active': idx === thinkingStepIndex }">
                  <view class="thinking-step__state">
                    <text v-if="idx < thinkingStepIndex">✓</text>
                    <view v-else-if="idx === thinkingStepIndex" class="thinking-step__spin" />
                    <text v-else>{{ idx + 1 }}</text>
                  </view>
                  <text class="thinking-step__label">{{ step }}</text>
                </view>
              </view>
            </view>
          </view>
          <view v-if="!agentBusy && !agentTyping && quickReplies.length && !agentCards.length" class="quick-replies" aria-label="你可以继续问">
            <button v-for="reply in quickReplies" :key="reply" class="quick-replies__chip" @click="sendQuickReply(reply)">
              {{ reply }}
            </button>
          </view>
        </view>

        <view v-if="agentCards.length && !agentBusy" v-for="(card, cardIndex) in agentCards" :key="`${card.title}-${cardIndex}`" class="choice-card" :class="{ 'choice-card--cuisine': card.type === 'CUISINE', 'choice-card--ready': card.type === 'READY' }" aria-live="polite">
          <view class="agent-card__head">
            <view class="agent-card__copy">
              <text class="agent-card__eyebrow">{{ card.type === 'READY' ? '准备好了' : '锅仔想确认' }}</text>
              <text class="agent-card__title">{{ card.title }}</text>
              <text class="agent-card__description">{{ card.description }}</text>
            </view>
            <image v-if="card.type === 'CUISINE' || card.type === 'READY'" :src="`${STATIC_BASE_URL}/static/guozai/action_06_glasses.png`" mode="aspectFit" aria-hidden="true" />
          </view>
          <view v-if="card.type === 'READY'" class="ready-summary" aria-label="将生成的内容">
            <view><text class="ready-summary__dot">01</text><text>一周菜单</text></view>
            <view><text class="ready-summary__dot">02</text><text>买菜清单</text></view>
            <view><text class="ready-summary__dot">03</text><text>详细做法</text></view>
          </view>
          <view v-if="householdPickerActive" class="household-picker">
            <view class="choice-grid choice-grid--household">
              <button v-for="option in card.options" :key="option.value" :class="{ selected: householdSelection.includes(householdValue(option.value)) }" :aria-pressed="householdSelection.includes(householdValue(option.value))" :disabled="agentBusy" @click="option.value === 'other' ? (otherInput = true) : toggleHousehold(option.value)">
                <text class="selection-mark" aria-hidden="true">
                  {{ householdSelection.includes(householdValue(option.value)) ? '✓' : '' }}
                </text>
                <text>{{ option.label }}</text>
              </button>
            </view>
            <button class="household-confirm" :disabled="!householdSelection.length || agentBusy" @click="confirmHousehold">
              确认选择
            </button>
            <view v-if="otherInput" class="other-input"><input v-model="composerText" class="other-input__field" :disabled="agentBusy" inputmode="text" confirm-type="send" placeholder="直接告诉锅仔你的情况" @confirm="submitOther"><GuozaiButton class="other-input__send" variant="primary" :block="false" :disabled="agentBusy || !composerText.trim()" :loading="agentBusy" aria-label="发送自定义家庭情况" @click="submitOther">发送</GuozaiButton></view>
          </view>
          <view v-else-if="card.type === 'FRIDGE_INVENTORY'" class="fridge-picker">
            <view class="fridge-picker__hint">可多选本次想用的食材，过期食材不会进入推荐。</view>
            <view class="choice-grid fridge-picker__grid">
              <button v-for="option in card.options" :key="option.value" :class="{ selected: fridgeSelection.includes(option.value) }" :disabled="agentBusy" @click="toggleFridgeItem(option.value)">
                <text class="selection-mark" aria-hidden="true">{{ fridgeSelection.includes(option.value) ? '✓' : '' }}</text>
                <text>{{ option.label }}</text>
              </button>
            </view>
            <button class="household-confirm" :disabled="agentBusy" @click="confirmFridgeSelection">{{ fridgeSelection.length ? '用这些食材安排菜谱' : '按临期优先安排菜谱' }}</button>
          </view>
          <view v-else-if="card.type === 'READY'" class="ready-actions">
            <GuozaiButton
              v-for="option in readyGenerateOptions(card)"
              :key="option.value"
              variant="primary"
              aria-label="开始生成本周菜单"
              :disabled="agentBusy"
              @click="generate"
            >
              {{ option.label }}
            </GuozaiButton>
            <GuozaiChipGroup
              v-if="readyOtherOptions(card).length"
              :options="readyOtherOptions(card)"
              :columns="1"
              :disabled="agentBusy"
              aria-label="生成前再调整一下"
              @change="value => onCardOptionChange(card, value)"
            />
          </view>
          <view v-else class="card-options">
            <GuozaiChipGroup
              :options="card.options"
              :columns="1"
              :disabled="agentBusy"
              :aria-label="card.title || '选择一个选项'"
              @change="value => onCardOptionChange(card, value)"
            />
          </view>
          <view v-if="otherInput && !householdPickerActive" class="other-input"><input v-model="composerText" class="other-input__field" :disabled="agentBusy" inputmode="text" confirm-type="send" placeholder="直接告诉锅仔你的想法" @confirm="submitOther"><GuozaiButton class="other-input__send" variant="primary" :block="false" :disabled="agentBusy || !composerText.trim()" :loading="agentBusy" aria-label="发送自定义回答" @click="submitOther">发送</GuozaiButton></view>
        </view>

        <view class="composer composer--fixed">
          <view class="composer__web" :class="{ 'is-on': webSearchEnabled, 'is-locked': !isMember }" role="button" :aria-label="isMember ? '联网搜索开关' : '会员专享：联网搜索'" @click="toggleWebSearch">
            <Icon name="search" :size="26" :color="webSearchEnabled ? '#fff' : (isMember ? '#EF5A3C' : '#B9A99C')" />
            <text class="composer__web-text">联网</text>
            <text v-if="!isMember" class="composer__web-lock">会员</text>
          </view>
          <input v-model="composerText" :disabled="agentBusy || agentTyping || !canUseAgent" inputmode="text" confirm-type="send" placeholder="也可以直接说：周三不做饭，想减脂" aria-label="告诉锅仔你的安排" @confirm="submitComposer">
          <GuozaiButton class="composer__send" variant="primary" :block="false" :disabled="agentBusy || agentTyping || !canUseAgent || !composerText.trim()" :loading="agentBusy || agentTyping" :aria-label="agentBusy || agentTyping ? '锅仔正在思考' : '发送'" @click="submitComposer">
            发送
          </GuozaiButton>
        </view>
        <view class="archive-link" role="button" aria-label="查看备餐档案" @click="router.push({ name: 'weekly-plan' })">
          查看以前的菜单 ›
        </view>
        <view class="archive-link archive-link--new" role="button" aria-label="开启新对话" @click="startNewConversation">
          ✳ 开启新对话
        </view>
      </view>

      <view v-else class="simple-plan">
        <view class="simple-plan__tag">每日签到 · 送 3 次锅仔对话</view>
        <text class="simple-plan__title">手动选好，生成简单周菜单</text>
        <text class="simple-plan__copy">锅仔会按人数、做饭天数和菜数，避开你的忌口，整理出一份基础菜单和买菜清单。</text>
        <text class="simple-plan__gift">去「锅仔形象馆」签到，今天就能和锅仔聊 3 轮；一次签到对应 3 个多轮对话。</text>
        <button class="simple-plan__checkin" @click="openGallery">去签到领锅仔对话</button>
        <view class="simple-plan__section"><text>几个人吃</text><view><button v-for="value in [1, 2, 3, 4]" :key="value" :class="{ selected: people === value }" @click="people = value">{{ value }} 人</button></view></view>
        <view class="simple-plan__section"><text>每天几道菜</text><view><button v-for="value in [1, 2, 3]" :key="value" :class="{ selected: dishesPerDay === value }" @click="dishesPerDay = value">{{ value }} 道</button></view></view>
        <button class="simple-plan__generate" :disabled="generating" @click="generate">生成本周简单菜单</button>
        <view class="simple-plan__divider" />
        <text class="simple-plan__member-title">开通会员，交给锅仔来安排</text>
        <text class="simple-plan__member-copy">每日 20 次首页推荐，专享对话式周菜单和随时重排。</text>
        <button class="simple-plan__member" @click="openMembership">查看会员权益</button>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.agent-page { min-height: 100vh; padding: 0 28rpx calc(176rpx + env(safe-area-inset-bottom)); color: var(--mrc-text); background: var(--mrc-bg); box-sizing: border-box; }
.selection-card { display: flex; min-width: 180rpx; max-width: 78%; flex-direction: column; gap: 8rpx; padding: 16rpx 20rpx 18rpx; border: 2rpx solid var(--mrc-accent); border-radius: 24rpx 24rpx 8rpx 24rpx; background: var(--mrc-accent-soft); box-shadow: 0 10rpx 22rpx rgba(239, 90, 60, .12); box-sizing: border-box; }.selection-card__eyebrow { color: var(--mrc-accent); font-size: 18rpx; font-weight: 700; letter-spacing: 1.5rpx; }.selection-card__row { display: flex; align-items: center; gap: 10rpx; }.selection-card__check { display: flex; width: 32rpx; height: 32rpx; flex: 0 0 auto; align-items: center; justify-content: center; border-radius: 50%; background: var(--mrc-accent); color: #fff; font-size: 18rpx; line-height: 1; }.selection-card__value { color: var(--mrc-accent); font-size: 25rpx; font-weight: 800; line-height: 1.35; }
.agent-hero { position: relative; min-height: 214rpx; overflow: hidden; padding: 22rpx 26rpx; border: 2rpx solid var(--mrc-border); border-radius: 32rpx; background: linear-gradient(145deg, var(--mrc-text-deep), #5b3325); box-shadow: var(--mrc-shadow-lift); box-sizing: border-box; }
.agent-hero__top { position: relative; z-index: 2; display: flex; align-items: center; justify-content: space-between; color: rgba(255,255,255,.7); font-size: 19rpx; font-weight: 800; letter-spacing: 2rpx; }
.agent-online { display: flex; align-items: center; gap: 8rpx; letter-spacing: 0; }.agent-online view { width: 12rpx; height: 12rpx; border-radius: 50%; background: var(--mrc-mint); box-shadow: 0 0 0 6rpx rgba(74,220,171,.13); }
.agent-hero__copy { position: relative; z-index: 2; display: flex; width: 66%; flex-direction: column; padding-top: 22rpx; }.agent-hero__eyebrow { color: #ff9d87; font-size: 20rpx; font-weight: 700; }.agent-hero__title { margin-top: 8rpx; color: #fff; font-size: 38rpx; font-weight: 800; line-height: 1.2; }.agent-hero__sub { margin-top: 8rpx; color: rgba(255,255,255,.72); font-size: 20rpx; line-height: 1.45; }
.agent-hero__guozai { position: absolute; z-index: 2; right: 6rpx; bottom: -18rpx; width: 204rpx; height: 204rpx; }.agent-hero__orbit { position: absolute; border: 2rpx solid rgba(255,255,255,.1); border-radius: 50%; }.agent-hero__orbit--one { right: -70rpx; bottom: -108rpx; width: 290rpx; height: 290rpx; }.agent-hero__orbit--two { right: 74rpx; top: 56rpx; width: 96rpx; height: 96rpx; }
.agent-share { position: relative; z-index: 3; margin-top: 28rpx; width: 86%; padding: 20rpx 0; background: rgba(255,255,255,.14); border: 2rpx solid rgba(255,255,255,.3); border-radius: 999rpx; color: #fff; font-size: 24rpx; font-weight: 700; line-height: normal; }
.agent-share::after { border: none; }
.agent-loading { padding: 80rpx 0; text-align: center; color: var(--mrc-text-sub); font-size: 24rpx; }
.dialog-row { display: flex; align-items: flex-start; gap: 12rpx; margin: 24rpx 4rpx 18rpx; }.dialog-row > image { width: 72rpx; height: 72rpx; flex: 0 0 auto; }.agent-bubble { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 10rpx; padding: 22rpx 24rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 8rpx 28rpx 28rpx 28rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); color: var(--mrc-text-deep); font-size: 25rpx; line-height: 1.55; }.agent-bubble__name { color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; letter-spacing: 2rpx; }.memory-tags { display: flex; flex-wrap: wrap; gap: 8rpx; }.memory-tags text { padding: 7rpx 14rpx; border-radius: 999rpx; background: var(--mrc-surface-peach); color: var(--mrc-text-sub); font-size: 19rpx; line-height: 1.3; }
.current-plan { display: flex; align-items: center; justify-content: space-between; min-height: 100rpx; margin-bottom: 18rpx; padding: 16rpx 22rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 24rpx; background: var(--mrc-surface-sun); box-sizing: border-box; }.current-plan:active { opacity: .74; }.current-plan > view { display: flex; flex-direction: column; gap: 5rpx; }.current-plan__label { color: var(--mrc-accent); font-size: 19rpx; font-weight: 700; }.current-plan__title { color: var(--mrc-text-deep); font-size: 25rpx; font-weight: 700; }.current-plan__action { color: var(--mrc-accent); font-size: 21rpx; font-weight: 700; }
.tool-panel { overflow: hidden; border: 2rpx solid var(--mrc-border); border-radius: 32rpx; background: linear-gradient(180deg, var(--mrc-surface) 0%, var(--mrc-surface-sun) 100%); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }
.chat-shell { padding-bottom: 12rpx; }
.chat-shell--readonly .choice-card { opacity: .72; pointer-events: none; }
.history-readonly { margin: 8rpx 4rpx 16rpx; padding: 16rpx 20rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 18rpx; background: var(--mrc-surface-sun); color: var(--mrc-text-sub); font-size: 21rpx; line-height: 1.45; }
.simple-plan { display: flex; flex-direction: column; gap: 18rpx; margin-top: 10rpx; padding: 30rpx 26rpx; border: 2rpx solid var(--mrc-border); border-radius: 30rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }.simple-plan__tag { align-self: flex-start; padding: 8rpx 14rpx; border-radius: 999rpx; background: var(--mrc-surface-peach); color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; }.simple-plan__title { color: var(--mrc-text-deep); font-size: 30rpx; font-weight: 800; }.simple-plan__copy, .simple-plan__member-copy { color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.6; }.simple-plan__section { display: flex; flex-direction: column; gap: 12rpx; color: var(--mrc-text-deep); font-size: 23rpx; font-weight: 750; }.simple-plan__section > view { display: flex; gap: 12rpx; }.simple-plan__section button { min-width: 92rpx; min-height: 64rpx; margin: 0; padding: 0 18rpx; border: 2rpx solid var(--mrc-border); border-radius: 18rpx; background: var(--mrc-surface); color: var(--mrc-text-sub); font-size: 21rpx; }.simple-plan__section button::after, .simple-plan__generate::after, .simple-plan__member::after { display: none; }.simple-plan__section button.selected { border-color: var(--mrc-accent); background: var(--mrc-accent-soft); color: var(--mrc-accent); font-weight: 800; }.simple-plan__generate, .simple-plan__member { min-height: 86rpx; margin: 0; border: 0; border-radius: 22rpx; font-size: 24rpx; font-weight: 800; }.simple-plan__generate { background: var(--mrc-primary-grad); color: #fff; }.simple-plan__generate[disabled] { opacity: .55; }.simple-plan__divider { height: 2rpx; background: var(--mrc-border-light); }.simple-plan__member-title { color: var(--mrc-text-deep); font-size: 25rpx; font-weight: 800; }.simple-plan__member { background: var(--mrc-text-deep); color: #fff; }
.simple-plan__gift { color: var(--mrc-accent); font-size: 21rpx; line-height: 1.5; }
.simple-plan__checkin { min-height: 74rpx; margin: 0; border: 2rpx solid var(--mrc-accent); border-radius: 20rpx; background: var(--mrc-surface); color: var(--mrc-accent); font-size: 23rpx; font-weight: 800; }
.chat-list { display: flex; flex-direction: column; gap: 18rpx; padding: 10rpx 4rpx 22rpx; }
.chat-row { display: flex; align-items: flex-end; gap: 10rpx; }.chat-row > image { width: 62rpx; height: 62rpx; flex: 0 0 auto; }.chat-row--user { justify-content: flex-end; }.chat-bubble { max-width: 78%; padding: 19rpx 22rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 8rpx 25rpx 25rpx 25rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); color: var(--mrc-text-deep); font-size: 25rpx; line-height: 1.55; box-sizing: border-box; word-break: break-all; overflow-wrap: anywhere; }.chat-row--user .chat-bubble { border-color: var(--mrc-accent); border-radius: 25rpx 8rpx 25rpx 25rpx; background: var(--mrc-accent); color: #fff; }.memory-tags { display: flex; flex-wrap: wrap; gap: 8rpx; margin-top: 12rpx; }.memory-tags text { padding: 7rpx 14rpx; border-radius: 999rpx; background: var(--mrc-surface-peach); color: var(--mrc-text-sub); font-size: 19rpx; line-height: 1.3; }
.chat-row--thinking { animation: thinking-in .22s ease-out both; }.thinking-bubble { display: flex; align-items: center; gap: 14rpx; color: var(--mrc-text-sub); }.thinking-dots { display: flex; align-items: center; gap: 7rpx; height: 24rpx; }.thinking-dot { width: 11rpx; height: 11rpx; border-radius: 50%; background: var(--mrc-accent); box-shadow: 0 3rpx 8rpx var(--mrc-accent-soft); animation: thinking-dot 1.05s cubic-bezier(.45, 0, .55, 1) infinite; will-change: transform, opacity; }.thinking-dot--2 { animation-delay: .14s; }.thinking-dot--3 { animation-delay: .28s; }
.agent-reply { display: flex; flex-direction: column; gap: 10rpx; min-width: 0; }
.memory-strip { margin: 0 0 14rpx; white-space: nowrap; }
.memory-strip__inner { display: inline-flex; gap: 12rpx; padding: 2rpx 4rpx; }
.memory-strip__chip { flex: 0 0 auto; min-height: 56rpx; margin: 0; padding: 0 20rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 999rpx; background: var(--mrc-surface); color: var(--mrc-text-sub); font-size: 21rpx; line-height: 52rpx; }
.memory-strip__chip::after { border: none; }
.quick-replies { display: flex; flex-wrap: wrap; gap: 12rpx; margin: 2rpx 0 6rpx 72rpx; }
.quick-replies__chip { min-height: 60rpx; margin: 0; padding: 0 22rpx; border: 2rpx dashed var(--mrc-accent-soft, rgba(232, 101, 43, .35)); border-radius: 999rpx; background: var(--mrc-surface); color: var(--mrc-accent); font-size: 22rpx; line-height: 56rpx; }
.quick-replies__chip::after { border: none; }
.teach-bubble { display: flex; flex-direction: column; gap: 12rpx; }
.teach-bubble__eyebrow { color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; letter-spacing: 1.5rpx; }
.teach-bubble__steps { display: flex; flex-direction: column; gap: 10rpx; }
.teach-bubble__step { display: flex; align-items: flex-start; gap: 10rpx; }
.teach-bubble__no { display: flex; align-items: center; justify-content: center; width: 30rpx; height: 30rpx; margin-top: 3rpx; flex: 0 0 auto; border-radius: 50%; background: var(--mrc-surface-peach); color: var(--mrc-accent); font-size: 18rpx; font-weight: 800; }
.teach-bubble__copy { flex: 1; color: var(--mrc-text-deep); font-size: 23rpx; line-height: 1.6; }
.teach-bubble__source { color: var(--mrc-text-sub); font-size: 19rpx; opacity: .8; }
.thinking-bubble { display: flex; align-items: center; gap: 14rpx; color: var(--mrc-text-sub); }
.thinking-steps { display: flex; flex-direction: column; gap: 9rpx; }
.thinking-step { display: flex; align-items: center; gap: 12rpx; color: var(--mrc-text-sub); font-size: 23rpx; transition: color .25s ease; }
.thinking-step--done { color: var(--mrc-text-deep); }
.thinking-step--active { color: var(--mrc-accent); font-weight: 700; }
.thinking-step__state { display: flex; align-items: center; justify-content: center; width: 30rpx; height: 30rpx; flex: 0 0 auto; border-radius: 50%; background: var(--mrc-surface-peach); color: var(--mrc-accent); font-size: 18rpx; font-weight: 800; }
.thinking-step--active .thinking-step__state { background: var(--mrc-accent-soft); }
.thinking-step--done .thinking-step__state { background: var(--mrc-mint-soft, rgba(74, 220, 171, .16)); color: var(--mrc-mint, #2bbd8c); }
.thinking-step__spin { width: 16rpx; height: 16rpx; border: 3rpx solid var(--mrc-accent-soft); border-top-color: var(--mrc-accent); border-radius: 50%; animation: thinking-spin .7s linear infinite; }
@keyframes thinking-spin { to { transform: rotate(360deg); } }
.choice-card { position: relative; padding: 22rpx; border: 2rpx solid var(--mrc-border); border-radius: 30rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-lift); }
.agent-card__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18rpx; padding: 4rpx 2rpx 20rpx; }.agent-card__copy { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 7rpx; }.agent-card__eyebrow { color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; letter-spacing: 1.5rpx; }.agent-card__title { color: var(--mrc-text-deep); font-size: 29rpx; font-weight: 800; line-height: 1.25; }.agent-card__description { color: var(--mrc-text-sub); font-size: 21rpx; line-height: 1.45; }.agent-card__head image { width: 92rpx; height: 92rpx; flex: 0 0 auto; margin-top: -8rpx; }
.choice-card--ready { padding: 26rpx; border-color: rgba(239, 90, 60, .22); background: linear-gradient(145deg, var(--mrc-surface) 0%, var(--mrc-surface-sun) 100%); box-shadow: 0 14rpx 34rpx rgba(113, 63, 36, .12); }.choice-card--ready .agent-card__eyebrow { display: flex; align-items: center; gap: 8rpx; }.choice-card--ready .agent-card__eyebrow::before { width: 14rpx; height: 14rpx; border-radius: 50%; background: var(--mrc-mint); box-shadow: 0 0 0 6rpx rgba(74, 220, 171, .15); content: ''; }.choice-card--ready .agent-card__title { font-size: 33rpx; letter-spacing: -.3rpx; }.choice-card--ready .agent-card__description { max-width: 88%; }.ready-summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10rpx; margin: 2rpx 0 20rpx; padding: 12rpx; border: 2rpx solid rgba(239, 90, 60, .1); border-radius: 20rpx; background: rgba(255, 255, 255, .52); }.ready-summary view { display: flex; min-width: 0; flex-direction: column; gap: 7rpx; color: var(--mrc-text-sub); font-size: 19rpx; line-height: 1.25; }.ready-summary__dot { color: var(--mrc-accent); font-size: 17rpx; font-weight: 800; letter-spacing: 1px; }/* 动态选项统一走 GuozaiChipGroup（一行一个）；READY 卡里生成按钮与对话选项纵向排列 */
.ready-actions { display: flex; flex-direction: column; gap: 14rpx; }
.card-options { padding-top: 2rpx; }
.cuisine-card { overflow: hidden; padding: 22rpx; border-radius: 22rpx; background: linear-gradient(135deg, var(--mrc-surface-sun), var(--mrc-surface-peach)); }.cuisine-card__head { display: flex; align-items: center; justify-content: space-between; }.cuisine-card__head > view { display: flex; flex-direction: column; gap: 8rpx; }.cuisine-card__head text:first-child { color: var(--mrc-text-deep); font-size: 29rpx; font-weight: 800; }.cuisine-card__head text:last-child { color: var(--mrc-accent); font-size: 19rpx; font-weight: 700; }.cuisine-card__head image { width: 92rpx; height: 92rpx; }.cuisine-card__dishes { display: flex; flex-wrap: wrap; gap: 10rpx; margin: 18rpx 0; }.cuisine-card__dishes text { padding: 10rpx 14rpx; border: 2rpx solid rgba(255, 107, 91, .22); border-radius: 999rpx; background: rgba(255,255,255,.54); color: var(--mrc-text-deep); font-size: 20rpx; }.cuisine-card button { min-height: 80rpx; margin: 0; border-radius: 20rpx; font-size: 23rpx; font-weight: 750; }.cuisine-card button::after { display: none; }.cuisine-card__primary { border: 0; background: var(--mrc-primary-grad); color: #fff; }.cuisine-card__secondary { border: 0; background: transparent; color: var(--mrc-text-sub); }
.choice-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12rpx; }.choice-grid--people { grid-template-columns: repeat(5, 1fr); }.choice-grid button { display: flex; min-height: 88rpx; flex-direction: column; align-items: center; justify-content: center; margin: 0; padding: 12rpx 6rpx; border: 2rpx solid var(--mrc-border); border-radius: 20rpx; background: var(--mrc-surface); color: var(--mrc-text-deep); font-size: 25rpx; font-weight: 750; line-height: 1.25; }.choice-grid button.choice-option--long { min-height: 104rpx; padding-right: 14rpx; padding-left: 14rpx; font-size: 22rpx; line-height: 1.4; }.choice-grid button::after, .choice-confirm::after, .generate-button::after, .restart-button::after, .composer button::after { display: none; }.choice-grid button:active { border-color: var(--mrc-accent); background: var(--mrc-accent-soft); }.choice-grid button text { margin-top: 7rpx; color: var(--mrc-text-sub); font-size: 18rpx; font-weight: 500; }
.choice-grid--household { grid-template-columns: repeat(3, 1fr); }.choice-grid--household button { position: relative; min-height: 92rpx; padding: 12rpx 8rpx; transition: border-color .18s ease, background-color .18s ease, color .18s ease; }.choice-grid--household button.selected { border-color: var(--mrc-accent); background: var(--mrc-accent-soft); color: var(--mrc-accent); }.choice-grid--household button .selection-mark { position: absolute; top: 8rpx; right: 10rpx; display: flex; width: 28rpx; height: 28rpx; align-items: center; justify-content: center; border: 2rpx solid var(--mrc-border); border-radius: 50%; color: transparent; font-size: 18rpx; line-height: 1; }.choice-grid--household button.selected .selection-mark { border-color: var(--mrc-accent); background: var(--mrc-accent); color: #fff; }.household-confirm { min-height: 82rpx; margin: 16rpx 0 0; border: 0; border-radius: 20rpx; background: var(--mrc-text-deep); color: #fff; font-size: 24rpx; font-weight: 750; }.household-confirm::after { display: none; }.household-confirm[disabled] { opacity: .38; }.choice-grid--spice { grid-template-columns: repeat(3, 1fr); }
.fridge-picker__hint { margin: -4rpx 2rpx 14rpx; color: var(--mrc-text-sub); font-size: 20rpx; line-height: 1.45; }.fridge-picker__grid { grid-template-columns: repeat(2, 1fr); }.fridge-picker__grid button { position: relative; min-height: 82rpx; padding-right: 34rpx; }.fridge-picker__grid button.selected { border-color: var(--mrc-accent); background: var(--mrc-accent-soft); color: var(--mrc-accent); }.fridge-picker__grid .selection-mark { position: absolute; top: 10rpx; right: 10rpx; display: flex; width: 28rpx; height: 28rpx; align-items: center; justify-content: center; border: 2rpx solid var(--mrc-border); border-radius: 50%; color: transparent; font-size: 18rpx; }.fridge-picker__grid button.selected .selection-mark { border-color: var(--mrc-accent); background: var(--mrc-accent); color: #fff; }
.weekdays { display: grid; grid-template-columns: repeat(7, 1fr); gap: 8rpx; }.weekdays view { display: flex; min-height: 76rpx; align-items: center; justify-content: center; border: 2rpx solid var(--mrc-border); border-radius: 18rpx; color: var(--mrc-text-sub); font-size: 22rpx; }.weekdays view.selected { border-color: var(--mrc-accent); background: var(--mrc-accent-soft); color: var(--mrc-accent); font-weight: 800; }.choice-confirm { min-height: 88rpx; margin: 18rpx 0 0; border: 0; border-radius: 22rpx; background: var(--mrc-text-deep); color: #fff; font-size: 25rpx; font-weight: 750; }
.plan-confirm__summary { display: flex; justify-content: space-between; padding: 4rpx 4rpx 18rpx; color: var(--mrc-text-deep); font-size: 24rpx; font-weight: 750; }.plan-confirm__summary text:last-child { color: var(--mrc-accent); font-size: 21rpx; }.generate-button { display: flex; min-height: 98rpx; align-items: center; justify-content: space-between; margin: 0; padding: 0 26rpx; border: 0; border-radius: 24rpx; background: var(--mrc-primary-grad); color: #fff; box-shadow: var(--mrc-shadow-coral); line-height: 1.2; }.generate-button:active { transform: scale(.98); }.generate-button { font-size: 28rpx; font-weight: 800; }.generate-button text { font-size: 19rpx; font-weight: 500; opacity: .86; }.restart-button { min-height: 72rpx; margin: 8rpx 0 0; border: 0; background: transparent; color: var(--mrc-text-sub); font-size: 21rpx; }
.composer { display: flex; align-items: center; gap: 10rpx; min-height: 96rpx; margin-top: 18rpx; padding: 10rpx 12rpx 10rpx 22rpx; border: 2rpx solid var(--mrc-border); border-radius: 28rpx; background: var(--mrc-surface); box-sizing: border-box; }.composer--fixed { position: fixed; z-index: 20; right: 28rpx; bottom: calc(18rpx + env(safe-area-inset-bottom)); left: 28rpx; margin: 0; box-shadow: 0 12rpx 40rpx rgba(69, 37, 24, .16); }.composer input { min-width: 0; flex: 1; color: var(--mrc-text-deep); font-size: 23rpx; }.composer input[disabled] { opacity: .58; }
.composer__web { display: flex; align-items: center; gap: 6rpx; padding: 10rpx 16rpx; border-radius: 999rpx; background: var(--mrc-surface-sun); border: 2rpx solid var(--mrc-border-light); color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; flex: 0 0 auto; }
.composer__web.is-on { background: var(--mrc-primary-grad); border-color: transparent; color: #fff; }
.composer__web.is-locked { color: #B9A99C; }
.composer__web-text { line-height: 1; }
.composer__web-lock { padding: 1rpx 8rpx; border-radius: 16rpx; background: var(--mrc-accent-soft); color: var(--mrc-accent); font-size: 15rpx; }.composer__send { width: 104rpx; min-height: 70rpx; padding: 0 14rpx !important; border-radius: 20rpx !important; font-size: 21rpx !important; }.archive-link { display: flex; min-height: 82rpx; align-items: center; justify-content: center; color: var(--mrc-text-sub); font-size: 21rpx; }
.archive-link--new { margin-top: -8rpx; color: var(--mrc-accent); }
.other-input { display: flex; align-items: center; gap: 12rpx; margin-top: 16rpx; padding: 10rpx 12rpx 10rpx 20rpx; border: 2rpx solid var(--mrc-border); border-radius: 24rpx; background: var(--mrc-surface-sun); box-sizing: border-box; }.other-input__field { min-width: 0; flex: 1; color: var(--mrc-text-deep); font-size: 23rpx; line-height: 1.35; }.other-input__field[disabled] { opacity: .58; }.other-input__send { width: 104rpx; min-height: 66rpx; padding: 0 14rpx !important; border-radius: 18rpx !important; font-size: 21rpx !important; }
.conversation-rating { display: flex; flex-direction: column; gap: 16rpx; margin-top: 18rpx; padding-top: 22rpx; border-top: 2rpx solid var(--mrc-border-light); color: var(--mrc-text-deep); font-size: 25rpx; font-weight: 750; }
.tool-panel { margin-top: 8rpx; padding: 26rpx; }.tool-panel__head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24rpx; color: var(--mrc-text-deep); font-size: 28rpx; font-weight: 800; }.tool-panel__head text:last-child { padding: 7rpx 13rpx; border-radius: 999rpx; background: var(--mrc-accent-soft); color: var(--mrc-accent); font-size: 20rpx; }.tool-step { display: flex; align-items: center; gap: 18rpx; min-height: 98rpx; opacity: .46; }.tool-step--active, .tool-step--done { opacity: 1; }.tool-step__state { display: flex; width: 54rpx; height: 54rpx; flex: 0 0 auto; align-items: center; justify-content: center; border: 2rpx solid var(--mrc-border); border-radius: 50%; color: var(--mrc-text-sub); background: var(--mrc-surface); font-size: 20rpx; }.tool-step--done .tool-step__state { border-color: var(--mrc-primary-deep); background: var(--mrc-primary-grad); color: #fff; box-shadow: 0 6rpx 14rpx rgba(239, 90, 60, .2); }.tool-step--active .tool-step__state { border-color: var(--mrc-accent); background: var(--mrc-surface-peach); box-shadow: 0 0 0 6rpx var(--mrc-accent-soft); }.tool-step__pulse { width: 15rpx; height: 15rpx; border-radius: 50%; background: var(--mrc-accent); animation: pulse 1s ease-in-out infinite; }.tool-step > view:last-child { display: flex; min-width: 0; flex-direction: column; gap: 6rpx; }.tool-step__title { color: var(--mrc-text-deep); font-size: 24rpx; font-weight: 750; }.tool-step__copy { color: var(--mrc-text-sub); font-size: 20rpx; }.tool-step + .tool-step { border-top: 2rpx solid var(--mrc-border-light); }
@keyframes pulse { 50% { opacity: .35; transform: scale(.7); } }
@keyframes thinking-in { from { opacity: 0; transform: translateY(10rpx); } }
@keyframes thinking-dot { 0%, 65%, 100% { opacity: .32; transform: translateY(3rpx) scale(.82); } 32% { opacity: 1; transform: translateY(-7rpx) scale(1.12); } }
@media (prefers-reduced-motion: reduce) { .chat-row--thinking, .thinking-dots view, .thinking-step__spin { animation: none; } }
</style>
