<script setup lang="ts">
/**
 * 人格测试中心：干饭人格（eat）/ 做饭人格（cook）双测试，wd-tabs 切换。
 *
 * 判定链路：答题完成 → 服务端 LLM 读取 12 个选择 → 从统一 16 型目录判型
 * 并写专属点评（引用具体选择）；服务端不可用时退回本地确定性计分——
 * 结果永远有、语义永远统一。默契模式（分享链接对战）仅干饭人格提供。
 */
import type { CompatItem } from '../../data/growth'
import { onLoad, onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'
import { navBack } from '@/composables/useNavBar'
import { analyzePersonality, type PersonalityView } from '../../api/personality'
import {
  compatOf,
  decodeAnswers,
  encodeAnswers,
  TESTS,
  scorePersonality,
  type PersonalityResult,
  type TestKey,
} from '../../data/growth'
import { STATIC_BASE_URL } from '../../utils/assets'

definePage({
  name: 'personality',
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '人格测试',
  },
})

const router = useRouter()

/* ---- 双测试状态机：每套测试独立进度，切 tab 不丢 ---- */
interface TestState {
  step: 'intro' | 'quiz' | 'analyzing' | 'result'
  answers: number[]
  qIndex: number
  locked: boolean
  serverRes: PersonalityView | null
  shareMode: 'self' | 'dual'
}
const freshState = (): TestState => ({ step: 'intro', answers: [], qIndex: 0, locked: false, serverRes: null, shareMode: 'self' })

const activeTest = ref<TestKey>('eat')
const states = reactive<Record<TestKey, TestState>>({ eat: freshState(), cook: freshState() })
const st = computed(() => states[activeTest.value])
const cfg = computed(() => TESTS[activeTest.value])
const OPTION_TAGS = ['A', 'B', 'C', 'D']

/** 默契模式（仅干饭人格）：链接里带 ?dual=，好友进来直接进入「接招」状态 */
const isDual = ref(false)
const hostAnswers = ref<number[]>([])

onLoad((options: { dual?: string } = {}) => {
  const dual = decodeAnswers(options.dual)
  if (dual) {
    hostAnswers.value = dual
    isDual.value = true
    activeTest.value = 'eat'
  }
})

function start() {
  Object.assign(st.value, { step: 'quiz', qIndex: 0, answers: [] })
}

function pick(optionIndex: number) {
  if (st.value.locked)
    return
  st.value.locked = true
  st.value.answers.push(optionIndex)
  setTimeout(() => {
    st.value.locked = false
    if (st.value.qIndex < cfg.value.quiz.length - 1) {
      st.value.qIndex += 1
    }
    else {
      void finish()
    }
  }, 280)
}

/** 交卷 → AI 语义判定（loading 有锅仔陪伴）；失败静默退回本地确定性计分 */
async function finish() {
  st.value.step = 'analyzing'
  try {
    st.value.serverRes = await analyzePersonality(st.value.answers, activeTest.value)
  }
  catch {
    st.value.serverRes = null
  }
  st.value.step = 'result'
}

/* ---- 结果：服务端 AI 判型优先，本地确定性计分兜底（答案不全不出结果） ---- */
const resultComplete = computed(() => st.value.answers.length === cfg.value.quiz.length)
const localResult = computed<PersonalityResult | null>(() =>
  resultComplete.value ? scorePersonality(st.value.answers, activeTest.value) : null)

const finalRes = computed(() => {
  const local = localResult.value
  if (!local)
    return null
  const ai = st.value.serverRes
  if (ai) {
    const fallbackType = cfg.value.types[ai.typeCode] || local.type
    return {
      code: ai.typeCode,
      type: { ...fallbackType, name: ai.name, emoji: ai.emoji, tagline: ai.tagline },
      dimensions: local.dimensions,
      luckyMood: ai.luckyMood,
      reading: ai.reading,
      ai: ai.aiGenerated,
    }
  }
  return { ...local, ai: false }
})

const compat = computed(() => {
  if (activeTest.value !== 'eat')
    return null
  if (!isDual.value || !resultComplete.value || hostAnswers.value.length !== cfg.value.quiz.length)
    return null
  return compatOf(hostAnswers.value, st.value.answers)
})

function optionLabel(item: CompatItem, side: 'host' | 'guest') {
  return side === 'host' ? item.host : item.guest
}

/* ---- 分享：默认晒人格；干饭人格可切「测默契」带上答案的对战邀请 ---- */
function inviteDual() {
  st.value.shareMode = 'dual'
}

onShareAppMessage(() => {
  const test = activeTest.value
  const prefix = test === 'cook' ? '做饭人格' : '干饭人格'
  if (st.value.shareMode === 'dual' && resultComplete.value) {
    return {
      title: `我是「${finalRes.value?.type.name}」（${finalRes.value?.code}），来测测咱俩吃饭默契`,
      path: `/pages/personality/index?dual=${encodeAnswers(st.value.answers)}`,
    }
  }
  return { title: `我测出${prefix}是「${finalRes.value?.type.name}」，你呢？`, path: '/pages/personality/index' }
})
onShareTimeline(() => ({ title: '干饭人格 / 做饭人格 · 12 道题测出你的 4 字母 · 锅仔出品' }))

function again() {
  Object.assign(st.value, freshState(), { step: 'intro' as const })
}

/** 幸运菜出口：按判定维度映射的心情标签去菜谱库——测完不止一张卡，还能吃到 */
function goLuckyDishes() {
  if (!finalRes.value)
    return
  router.push({ name: 'recipe-library', query: { mood: finalRes.value.luckyMood } })
}
</script>

<template>
  <view class="pea">
    <wd-navbar title="人格测试" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <!-- 双测试切换：干饭（怎么吃）/ 做饭（怎么做） -->
    <view class="pea__tabs">
      <wd-tabs v-model="activeTest" swipeable>
        <wd-tab title="🍚 干饭人格" name="eat" />
        <wd-tab title="🍳 做饭人格" name="cook" />
      </wd-tabs>
    </view>

    <!-- 开场 -->
    <view v-if="st.step === 'intro'" class="pea__intro mrc-hero">
      <image class="pea__hero-img guozai-breathe" :src="`${STATIC_BASE_URL}/static/guozai/${activeTest === 'cook' ? 'action_02_soup' : 'action_09_celebrate'}.png`" mode="aspectFit" aria-hidden="true" />
      <text class="pea__eyebrow">{{ activeTest === 'cook' ? 'COOKING MBTI' : 'FOODIE MBTI' }}</text>
      <text class="pea__title">{{ activeTest === 'cook' ? '做饭人格 4 字母测试' : '干饭人格 4 字母测试' }}</text>
      <text class="pea__sub">
        {{ activeTest === 'cook'
          ? '12 道厨房场景题，四个选项随你挑。锅仔读完你的每一次颠勺，判断你到底是哪种做饭人格。'
          : '12 道场景题，四个选项随你挑。锅仔会读完你的每一次选择，认真判断你的人格——像 MBTI 一样认真，比 MBTI 好吃。' }}
      </text>
      <view class="pea__facts">
        <view class="pea__fact"><text class="pea__fact-num">12</text><text class="pea__fact-label">道场景题</text></view>
        <view class="pea__fact"><text class="pea__fact-num">4</text><text class="pea__fact-label">个维度</text></view>
        <view class="pea__fact"><text class="pea__fact-num">16</text><text class="pea__fact-label">型人格</text></view>
      </view>
      <view v-if="activeTest === 'eat' && isDual" class="pea__dual-banner">
        <text class="pea__dual-text">有饭搭子想跟你比吃饭默契，接招？</text>
      </view>
      <view class="pea__cta mrc-btn-primary" role="button" :aria-label="isDual && activeTest === 'eat' ? '接招开始答题' : '开始测试'" @click="start">
        {{ activeTest === 'eat' && isDual ? '接招，开测' : '开测' }}
      </view>
      <view v-if="activeTest === 'eat' && isDual" class="pea__skip" role="button" aria-label="先测自己的人格" @click="isDual = false; start()">
        先测自己的人格
      </view>
    </view>

    <!-- 答题：进度条 + 单题四选一 -->
    <view v-else-if="st.step === 'quiz'" class="pea__quiz">
      <view class="pea__progress-row">
        <text class="pea__progress-num">{{ st.qIndex + 1 }} / {{ cfg.quiz.length }}</text>
        <view class="pea__progress-bar">
          <view class="pea__progress-fill" :style="{ width: `${((st.qIndex + 1) / cfg.quiz.length) * 100}%` }" />
        </view>
      </view>
      <view :key="`${activeTest}-${st.qIndex}`" class="pea__q mrc-card pop-in">
        <text class="pea__q-emoji">{{ cfg.quiz[st.qIndex]?.emoji }}</text>
        <text class="pea__q-text">{{ cfg.quiz[st.qIndex]?.text }}</text>
        <view class="pea__options">
          <view
            v-for="(option, i) in cfg.quiz[st.qIndex]?.options"
            :key="i"
            class="pea__option"
            :class="{ 'is-locked': st.locked }"
            role="button"
            :aria-label="option.label"
            @click="pick(i)"
          >
            <text class="pea__option-tag">{{ OPTION_TAGS[i] }}</text>
            <text class="pea__option-emoji">{{ option.emoji }}</text>
            <text class="pea__option-label">{{ option.label }}</text>
          </view>
        </view>
        <text class="pea__q-hint">别想太久，第一反应最准</text>
      </view>
    </view>

    <!-- AI 判定中：锅仔陪读你的选择 -->
    <view v-else-if="st.step === 'analyzing'" class="pea__analyzing mrc-card pop-in">
      <image class="pea__analyzing-img guozai-breathe" :src="`${STATIC_BASE_URL}/static/guozai/action_10_thinking.png`" mode="aspectFit" aria-hidden="true" />
      <text class="pea__analyzing-title">锅仔正在认真判断…</text>
      <text class="pea__analyzing-sub">把你的 12 个选择一个一个读过去，看看你到底是个什么人格</text>
    </view>

    <!-- 结果：4 字母人格卡 → 维度条 → 行动区 -->
    <view v-else-if="resultComplete && finalRes" class="pea__result">
      <!-- 默契环 + 逐题对比（仅干饭人格的默契模式） -->
      <view v-if="compat" class="pea__card mrc-card pop-in">
        <text class="pea__eyebrow">CHOPSTICK CHEMISTRY</text>
        <view
          class="pea__ring"
          :style="{ background: `conic-gradient(var(--mrc-accent) 0 ${compat.score}%, var(--mrc-surface-3) ${compat.score}% 100%)` }"
          role="img"
          :aria-label="`吃饭默契 ${compat.score}%`"
        >
          <view class="pea__ring-inner">
            <text class="pea__ring-num">{{ compat.score }}%</text>
            <text class="pea__ring-label">吃饭默契</text>
          </view>
        </view>
        <text class="pea__tier">{{ compat.tier.name }}</text>
        <text class="pea__tier-line">{{ compat.tier.line }}</text>
        <text class="pea__compat-sum">12 题里你们撞了 {{ compat.sameCount }} 次</text>
        <view class="pea__compare">
          <view v-for="(item, i) in compat.per" :key="i" class="pea__compare-row">
            <text class="pea__compare-q">{{ item.q }}</text>
            <view class="pea__compare-ans">
              <text class="pea__compare-you">你：{{ optionLabel(item, 'guest') }}</text>
              <text class="pea__compare-ta">TA：{{ optionLabel(item, 'host') }}</text>
              <text class="pea__compare-tag" :class="`is-${item.level}`">{{ item.level === 'exact' ? '同款' : '两派' }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 4 字母人格卡 -->
      <view class="pea__card mrc-card pop-in">
        <text class="pea__eyebrow">{{ activeTest === 'cook' ? 'YOUR COOKING DNA' : 'YOUR FOODIE DNA' }}</text>
        <view class="pea__code" role="img" :aria-label="`人格代码 ${finalRes.code}`">
          <text v-for="(ch, i) in finalRes.code.split('')" :key="i" class="pea__code-letter">{{ ch }}</text>
        </view>
        <text class="pea__p-emoji">{{ finalRes.type.emoji }}</text>
        <text class="pea__p-name">{{ finalRes.type.name }}</text>
        <text class="pea__p-tagline">{{ finalRes.type.tagline }}</text>
        <text v-if="finalRes.ai" class="pea__ai-badge">锅仔读完你的 12 个选择后写的</text>

        <!-- 四维倾向条：MBTI 式左右两极 -->
        <view class="pea__dims">
          <view v-for="dim in finalRes.dimensions" :key="dim.key" class="pea__dim">
            <view class="pea__dim-labels">
              <text class="pea__dim-pole" :class="{ 'is-win': dim.letter === dim.left.letter }">{{ dim.left.label }} {{ dim.left.letter }}</text>
              <text class="pea__dim-name">{{ dim.name }}</text>
              <text class="pea__dim-pole" :class="{ 'is-win': dim.letter === dim.right.letter }">{{ dim.right.letter }} {{ dim.right.label }}</text>
            </view>
            <view class="pea__dim-bar">
              <view class="pea__dim-fill" :style="{ width: `${100 - dim.percent}%` }" />
              <view class="pea__dim-marker" :style="{ left: `${100 - dim.percent}%` }" />
            </view>
          </view>
        </view>

        <text class="pea__reading">{{ finalRes.reading }}</text>
        <view class="pea__lucky-go" role="button" :aria-label="`看${finalRes.luckyMood}的菜`" @click="goLuckyDishes">
          看看你的「{{ finalRes.luckyMood }}」系有哪些菜 ›
        </view>
      </view>

      <!-- 行动区：拉人对战（仅干饭人格）/ 晒结果 / 重测 -->
      <view class="pea__actions">
        <button v-if="activeTest === 'eat'" class="pea__share-btn pea__share-btn--primary" open-type="share" @click="inviteDual">
          测测和 TA 的默契
        </button>
        <button class="pea__share-btn pea__share-btn--outline" open-type="share" @click="st.shareMode = 'self'">
          晒出我的 4 字母
        </button>
        <view class="pea__again" role="button" aria-label="再测一次" @click="again">
          再测一次
        </view>
      </view>

      <view class="pea__foot">
        <text>人格由锅仔读完你的选择后判定，只认吃，不评判人</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* finesse · register=h5(小程序页) · 沿用 mrc token 体系 · SOUL=7 SPECTACLE=3 DENSITY=5 */
.pea {
  min-height: 100vh;
  box-sizing: border-box;
  background: var(--mrc-bg);
  padding: 0 32rpx;
  padding-bottom: calc(80rpx + env(safe-area-inset-bottom));
}

.pea__tabs { margin: 0 -32rpx 4rpx; }

/* ---- 开场 ---- */
.pea__intro {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 20rpx;
  padding: 60rpx 24rpx 48rpx;
  border-radius: var(--mrc-radius-lg);
}
.pea__hero-img { width: 240rpx; height: 240rpx; }
.pea__eyebrow { margin-top: 8rpx; color: var(--mrc-accent); font-size: 18rpx; font-weight: 800; letter-spacing: 3rpx; }
.pea__title { margin-top: 10rpx; color: var(--mrc-text-strong); font-size: 44rpx; font-weight: 900; text-align: center; }
.pea__sub { margin-top: 14rpx; max-width: 580rpx; color: var(--mrc-text-sub); font-size: 24rpx; line-height: 1.65; text-align: center; }
.pea__facts { display: flex; gap: 20rpx; margin-top: 30rpx; }
.pea__fact {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  min-width: 132rpx;
  padding: 18rpx 20rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 20rpx;
  background: var(--mrc-surface);
}
.pea__fact-num { color: var(--mrc-accent); font-size: 36rpx; font-weight: 900; }
.pea__fact-label { color: var(--mrc-text-sub); font-size: 20rpx; }
.pea__dual-banner {
  margin-top: 26rpx;
  padding: 12rpx 30rpx;
  border-radius: 999rpx;
  background: var(--mrc-accent-soft);
}
.pea__dual-text { color: var(--mrc-accent); font-size: 24rpx; font-weight: 700; }
.pea__cta { width: 100%; margin-top: 40rpx; }
.pea__skip { margin-top: 22rpx; color: var(--mrc-text-light); font-size: 22rpx; }
.pea__skip:active { opacity: 0.7; }

/* ---- 答题 ---- */
.pea__quiz { margin-top: 20rpx; }
.pea__progress-row { display: flex; align-items: center; gap: 16rpx; margin-bottom: 24rpx; }
.pea__progress-num { flex: 0 0 auto; color: var(--mrc-text-sub); font-size: 22rpx; font-weight: 800; }
.pea__progress-bar { flex: 1; height: 12rpx; border-radius: 999rpx; background: var(--mrc-surface-3); overflow: hidden; }
.pea__progress-fill { height: 100%; border-radius: 999rpx; background: var(--mrc-primary-grad); transition: width .3s ease; }
.pea__q { display: flex; flex-direction: column; padding: 36rpx 30rpx; }
.pea__q-emoji { font-size: 72rpx; line-height: 1.2; }
.pea__q-text { margin-top: 14rpx; color: var(--mrc-text-strong); font-size: 32rpx; font-weight: 800; line-height: 1.45; }
.pea__options { display: flex; flex-direction: column; gap: 16rpx; margin-top: 28rpx; }
.pea__option {
  display: flex;
  align-items: center;
  gap: 16rpx;
  min-height: var(--mrc-touch-min);
  padding: 18rpx 24rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: var(--mrc-radius-md);
  background: var(--mrc-surface);
  box-sizing: border-box;
  transition: transform var(--mrc-motion-fast), border-color var(--mrc-motion-fast), background var(--mrc-motion-fast);
}
.pea__option:active {
  transform: scale(0.97);
  border-color: var(--mrc-accent);
  background: var(--mrc-accent-soft);
}
.pea__option.is-locked { opacity: .7; }
.pea__option-tag {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44rpx;
  height: 44rpx;
  flex: 0 0 44rpx;
  border-radius: 12rpx;
  background: var(--mrc-surface-2);
  color: var(--mrc-text-sub);
  font-size: 22rpx;
  font-weight: 900;
}
.pea__option-emoji { font-size: 34rpx; }
.pea__option-label { flex: 1; min-width: 0; color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 600; }
.pea__q-hint { margin-top: 22rpx; color: var(--mrc-text-light); font-size: 20rpx; text-align: center; }

/* ---- AI 判定中 ---- */
.pea__analyzing {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 20rpx;
  padding: 80rpx 32rpx;
}
.pea__analyzing-img { width: 200rpx; height: 200rpx; }
.pea__analyzing-title { margin-top: 24rpx; color: var(--mrc-text-strong); font-size: 32rpx; font-weight: 800; }
.pea__analyzing-sub { margin-top: 12rpx; max-width: 520rpx; color: var(--mrc-text-sub); font-size: 23rpx; line-height: 1.6; text-align: center; }

/* ---- 结果 ---- */
.pea__result { margin-top: 20rpx; }
.pea__card {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 24rpx;
  padding: 36rpx 28rpx;
}
.pea__eyebrow { color: var(--mrc-accent); font-size: 18rpx; font-weight: 800; letter-spacing: 3rpx; }

/* 4 字母代码块：MBTI 式每字母一方 */
.pea__code { display: flex; gap: 14rpx; margin-top: 26rpx; }
.pea__code-letter {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88rpx;
  height: 96rpx;
  border-radius: 18rpx;
  background: var(--mrc-primary-grad);
  color: #fff;
  font-size: 48rpx;
  font-weight: 900;
  box-shadow: var(--mrc-shadow-coral);
}
.pea__ring {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 220rpx;
  height: 220rpx;
  margin-top: 26rpx;
  border-radius: 50%;
  box-shadow: var(--mrc-shadow-soft);
}
.pea__ring-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
  width: 172rpx;
  height: 172rpx;
  border-radius: 50%;
  background: var(--mrc-surface);
}
.pea__ring-num { color: var(--mrc-text-strong); font-size: 44rpx; font-weight: 800; }
.pea__ring-label { color: var(--mrc-text-sub); font-size: 20rpx; }

.pea__tier { margin-top: 24rpx; color: var(--mrc-accent); font-size: 34rpx; font-weight: 800; }
.pea__tier-line { margin-top: 8rpx; color: var(--mrc-text-sub); font-size: 24rpx; text-align: center; }
.pea__compat-sum { margin-top: 10rpx; color: var(--mrc-text-light); font-size: 20rpx; }

.pea__compare { display: flex; flex-direction: column; gap: 12rpx; width: 100%; margin-top: 24rpx; }
.pea__compare-row {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  padding: 14rpx 18rpx;
  border-radius: var(--mrc-radius-md);
  background: var(--mrc-surface-2);
  box-sizing: border-box;
}
.pea__compare-q { color: var(--mrc-text-sub); font-size: 20rpx; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.pea__compare-ans { display: flex; align-items: center; flex-wrap: wrap; gap: 10rpx; }
.pea__compare-you, .pea__compare-ta { color: var(--mrc-text); font-size: 22rpx; font-weight: 600; }
.pea__compare-tag {
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  font-size: 18rpx;
  font-weight: 700;
}
.pea__compare-tag.is-exact { background: var(--mrc-surface-mint); color: var(--mrc-mint); }
.pea__compare-tag.is-diff { background: var(--mrc-surface-3); color: var(--mrc-text-sub); }

.pea__p-emoji { margin-top: 22rpx; font-size: 80rpx; line-height: 1.2; }
.pea__p-name { margin-top: 6rpx; color: var(--mrc-text-strong); font-size: 40rpx; font-weight: 900; }
.pea__p-tagline { margin-top: 8rpx; color: var(--mrc-accent); font-size: 25rpx; font-weight: 700; }
.pea__ai-badge {
  margin-top: 12rpx;
  padding: 4rpx 18rpx;
  border-radius: 999rpx;
  background: var(--mrc-surface-sun);
  color: var(--mrc-text-sub);
  font-size: 19rpx;
}

/* 四维倾向条：MBTI 式左右两极，marker 落在胜侧 */
.pea__dims { display: flex; flex-direction: column; gap: 22rpx; width: 100%; margin-top: 30rpx; }
.pea__dim-labels { display: flex; align-items: center; justify-content: space-between; gap: 10rpx; }
.pea__dim-pole { color: var(--mrc-text-light); font-size: 20rpx; font-weight: 600; }
.pea__dim-pole.is-win { color: var(--mrc-accent); font-weight: 900; }
.pea__dim-name { color: var(--mrc-text-sub); font-size: 20rpx; }
.pea__dim-bar { position: relative; height: 18rpx; margin-top: 8rpx; border-radius: 999rpx; background: var(--mrc-surface-3); overflow: visible; }
.pea__dim-fill { height: 100%; border-radius: 999rpx 0 0 999rpx; background: var(--mrc-primary-grad); }
.pea__dim-marker {
  position: absolute;
  top: 50%;
  width: 26rpx;
  height: 26rpx;
  margin: -13rpx 0 0 -13rpx;
  border: 4rpx solid #fff;
  border-radius: 50%;
  background: var(--mrc-accent);
  box-shadow: 0 2rpx 8rpx rgba(61, 37, 25, 0.3);
  box-sizing: border-box;
}

.pea__reading { margin-top: 28rpx; color: var(--mrc-text); font-size: 26rpx; line-height: 1.8; text-align: center; }
.pea__lucky-go {
  margin-top: 24rpx;
  padding: 14rpx 34rpx;
  border-radius: 999rpx;
  background: var(--mrc-accent-soft);
  color: var(--mrc-accent);
  font-size: 24rpx;
  font-weight: 700;
}
.pea__lucky-go:active { transform: scale(0.96); }

.pea__actions { display: flex; flex-direction: column; gap: 18rpx; }
.pea__share-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 96rpx;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: var(--mrc-radius-pill);
  font-size: var(--mrc-fs-title);
  font-weight: 700;
  letter-spacing: 2rpx;
  line-height: 96rpx;
}
.pea__share-btn::after { border: none; }
.pea__share-btn--primary { background: var(--mrc-primary-grad); color: #fff; box-shadow: var(--mrc-shadow-coral), var(--mrc-gloss); }
.pea__share-btn--primary:active { transform: scale(0.97); }
.pea__share-btn--outline { background: var(--mrc-surface); border: 2rpx solid var(--mrc-primary); color: var(--mrc-accent); box-sizing: border-box; }
.pea__share-btn--outline:active { background: #fff3ec; }
.pea__again { display: flex; justify-content: center; padding: 16rpx 0; color: var(--mrc-text-light); font-size: 24rpx; }
.pea__again:active { opacity: 0.7; }

.pea__foot { display: flex; justify-content: center; margin-top: 20rpx; }
.pea__foot text { color: var(--mrc-text-light); font-size: 20rpx; }

@media (prefers-reduced-motion: reduce) {
  .pea__hero-img.guozai-breathe,
  .pea__analyzing-img.guozai-breathe,
  .pea__q.pop-in,
  .pea__card.pop-in {
    animation: none;
  }
}
</style>
