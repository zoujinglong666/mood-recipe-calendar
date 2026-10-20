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

/** 测试类型 Tab 数据源（视觉为胶囊分段式，切换仍直接写 activeTest，业务等价） */
const TEST_TABS = [
  { key: 'eat' as TestKey, emoji: '', label: '干饭人格' },
  { key: 'cook' as TestKey, emoji: '', label: '做饭人格' },
]

/* ---- 锅仔 IP 延展：每题换一个姿势「出题」，配一句锅仔台词；进度条上锅仔随进度走 ---- */
const QUIZ_AVATARS = [
  'action_01_bowl',
  'action_16_chopsticks',
  'action_10_thinking',
  'action_06_glasses',
  'action_13_wave',
  'action_19_kungfu',
  'action_08_peek',
  'action_02_soup',
  'action_14_clap',
  'action_09_celebrate',
  'action_18_cheer',
  'action_17_full',
]
const QUIZ_QUIPS = [
  '别想太久，第一反应最准',
  '锅仔在小本本上记了一笔',
  '嗯…这个选择很 you',
  '不着急，锅仔等你五秒钟',
  '这题没有对错，只有好吃不好吃',
  '选完这题，锅仔又懂你一分',
  '锅仔发现你有点意思',
  '别装，装了也会被识破',
  '过半啦，锅仔已经开始猜你了',
  '锅仔的眼睛越睁越大了',
  '快了快了，就差一点点',
  '最后一题，深呼吸，开炫',
]
const quizAvatar = computed(() =>
  `${STATIC_BASE_URL}/static/guozai/${QUIZ_AVATARS[st.value.qIndex % QUIZ_AVATARS.length]}.png`)
const quizQuip = computed(() =>
  cfg.value.quiz[st.value.qIndex]?.quip || QUIZ_QUIPS[st.value.qIndex % QUIZ_QUIPS.length])
const progressPct = computed(() => `${((st.value.qIndex + 1) / cfg.value.quiz.length) * 100}%`)
const introQuip = computed(() => {
  if (activeTest.value === 'eat' && isDual.value) return '饭搭子已下单，接招吧'
  return activeTest.value === 'cook' ? '颠勺的事，锅仔最懂' : '锅仔出题，你负责说实话'
})

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

    <!-- 双测试切换：干饭（怎么吃）/ 做饭（怎么做）——胶囊分段式，切换逻辑不变 -->
    <view class="pea__tabs" role="tablist" aria-label="选择测试类型">
      <view
        v-for="t in TEST_TABS"
        :key="t.key"
        class="pea__tab"
        :class="{ 'is-active': activeTest === t.key }"
        role="tab"
        :aria-selected="activeTest === t.key"
        @click="activeTest = t.key"
      >
        <text class="pea__tab-emoji">{{ t.emoji }}</text>
        <text class="pea__tab-label">{{ t.label }}</text>
      </view>
    </view>

    <!-- 开场：主视觉容器（淡桃色 stage），一屏一个焦点 -->
    <view v-if="st.step === 'intro'" class="pea__intro">
      <!-- 轻量装饰：光晕 + 两颗星 + 圆点，全部 pointer-events none -->
      <view class="pea__stage-glow" aria-hidden="true" />
      <view class="pea__deco pea__deco--star-a" aria-hidden="true" />
      <view class="pea__deco pea__deco--star-b" aria-hidden="true" />
      <view class="pea__deco pea__deco--dot" aria-hidden="true" />

      <image class="pea__hero-img guozai-breathe" :src="`${STATIC_BASE_URL}/static/guozai/${activeTest === 'cook' ? 'action_02_soup' : 'action_09_celebrate'}.png`" mode="aspectFit" aria-hidden="true" />
      <view class="pea__hero-bubble"><text>{{ introQuip }}</text></view>

      <text class="pea__eyebrow">{{ activeTest === 'cook' ? 'COOKING MBTI · 4 LETTERS' : 'FOODIE MBTI · 4 LETTERS' }}</text>
      <view class="pea__title" role="heading" aria-level="1">
        <text class="pea__title-line">你的{{ activeTest === 'cook' ? '做饭' : '干饭' }}人格，</text>
        <text class="pea__title-line pea__title-line--accent">到底是哪一型？</text>
      </view>
      <text class="pea__sub">
        {{ activeTest === 'cook'
          ? '12 道厨房场景题，四个选项随你挑。锅仔读完你的每一次颠勺，判断你到底是哪种做饭人格。'
          : '12 道场景题，四个选项随你挑。锅仔会读完你的每一次选择，认真判断你的人格——像 MBTI 一样认真，比 MBTI 好吃。' }}
      </text>

      <view class="pea__facts">
        <view class="pea__fact">
          <text class="pea__fact-num">12</text>
          <text class="pea__fact-label">道场景题</text>
        </view>
        <view class="pea__fact">
          <text class="pea__fact-num">4</text>
          <text class="pea__fact-label">个维度</text>
        </view>
        <view class="pea__fact">
          <text class="pea__fact-num">16</text>
          <text class="pea__fact-label">型人格</text>
        </view>
      </view>

      <view v-if="activeTest === 'eat' && isDual" class="pea__dual-banner">
        <text class="pea__dual-text">有饭搭子想跟你比吃饭默契，接招？</text>
      </view>

      <view class="pea__cta" role="button" :aria-label="isDual && activeTest === 'eat' ? '接招开始答题' : '开始测试'" @click="start">
        {{ activeTest === 'eat' && isDual ? '接招，开测' : (activeTest === 'cook' ? '测测我的做饭人格 →' : '测测我的干饭人格 →') }}
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
          <view class="pea__progress-fill" :style="{ width: progressPct }" />
          <image class="pea__progress-walker" :style="{ left: progressPct }" :src="`${STATIC_BASE_URL}/static/guozai/mood_06_hungry.png`" mode="aspectFit" aria-hidden="true" />
        </view>
      </view>
      <view :key="`${activeTest}-${st.qIndex}`" class="pea__q mrc-card pop-in">
        <view class="pea__q-head">
          <image class="pea__q-avatar guozai-breathe" :src="quizAvatar" mode="aspectFit" aria-hidden="true" />
          <view class="pea__q-bubble">
            <text class="pea__q-quip">{{ quizQuip }}</text>
          </view>
        </view>
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
  --pea-stage-bg: #FBE1D1;        /* 主视觉容器：淡桃色（PRD §6.4，仅首页作用域） */
  --pea-stage-bg-soft: #FFF1E7;   /* 容器顶部渐变起点 */
  --pea-card-border: #EEDBC6;     /* 统计卡轻边框 */
  min-height: 100vh;
  box-sizing: border-box;
  background: var(--mrc-bg);
  padding: 0 32rpx;
  padding-bottom: calc(80rpx + env(safe-area-inset-bottom));
  max-width: 560px;               /* 宽屏（H5/平板预览）保持窄内容列居中 */
  margin: 0 auto;
}

/* ---- 测试类型 Tab：胶囊分段式（选中 = 浅珊瑚底 + 主色文字，含底色即非纯色彩区分） ---- */
.pea__tabs {
  display: flex;
  gap: 8rpx;
  margin: 12rpx 0 20rpx;
  padding: 8rpx;
  border-radius: 999rpx;
  background: var(--mrc-surface-2);
}
.pea__tab {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  height: 92rpx;
  border-radius: 999rpx;
  color: var(--mrc-text-sub);
  font-size: 28rpx;
  font-weight: 600;
  transition: background-color .2s ease, color .2s ease;
}
.pea__tab.is-active {
  background: var(--mrc-surface);
  color: var(--mrc-accent);
  font-weight: 800;
  box-shadow: var(--mrc-shadow-sm);
}
.pea__tab-emoji { font-size: 30rpx; }

/* ---- 开场主视觉容器：淡桃色 stage，一屏一个焦点 ---- */
.pea__intro {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 4rpx;
  padding: 56rpx 28rpx 44rpx;
  border-radius: 56rpx;
  background: linear-gradient(180deg, var(--pea-stage-bg-soft), var(--pea-stage-bg));
  overflow: hidden;
}
/* 光晕：低透明 radial，不新增图片 */
.pea__stage-glow {
  position: absolute;
  top: -120rpx;
  left: 50%;
  width: 640rpx;
  height: 420rpx;
  transform: translateX(-50%);
  background: radial-gradient(closest-side, rgba(255, 178, 142, 0.4), transparent);
  pointer-events: none;
}
/* 装饰星与圆点：CSS 绘制，克制、不遮文字 */
.pea__deco { position: absolute; pointer-events: none; background: var(--mrc-accent); }
.pea__deco--star-a {
  top: 64rpx;
  left: 56rpx;
  width: 26rpx;
  height: 26rpx;
  opacity: .4;
  clip-path: polygon(50% 0, 63% 37%, 100% 50%, 63% 63%, 50% 100%, 37% 63%, 0 50%, 37% 37%);
  animation: pea-twinkle 3.2s ease-in-out infinite alternate;
}
.pea__deco--star-b {
  top: 190rpx;
  right: 64rpx;
  width: 18rpx;
  height: 18rpx;
  opacity: .28;
  clip-path: polygon(50% 0, 63% 37%, 100% 50%, 63% 63%, 50% 100%, 37% 63%, 0 50%, 37% 37%);
  animation: pea-twinkle 3.2s ease-in-out .8s infinite alternate;
}
.pea__deco--dot {
  bottom: 200rpx;
  left: 64rpx;
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  opacity: .3;
}
@keyframes pea-twinkle { from { opacity: .18; } to { opacity: .45; } }

.pea__hero-img { width: 380rpx; height: 380rpx; }
.pea__hero-bubble {
  margin-top: 4rpx;
  padding: 12rpx 28rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 24rpx 24rpx 24rpx 8rpx;
  background: var(--mrc-surface);
  color: var(--mrc-text-deep);
  font-size: 22rpx;
  font-weight: 700;
  box-shadow: var(--mrc-shadow-sm);
}
.pea__eyebrow { margin-top: 12rpx; color: var(--mrc-accent); font-size: 18rpx; font-weight: 800; letter-spacing: 3rpx; }
.pea__title { display: flex; flex-direction: column; align-items: center; margin-top: 10rpx; text-align: center; }
.pea__title-line { color: var(--mrc-text-strong); font-size: 52rpx; font-weight: 900; line-height: 1.24; }
.pea__title-line--accent { color: var(--mrc-accent); }
.pea__sub { margin-top: 16rpx; max-width: 580rpx; color: var(--mrc-text-sub); font-size: 24rpx; line-height: 1.7; text-align: center; }

/* 统计信息：三列等宽轻卡，数字是主角，不放图标 */
.pea__facts { display: flex; gap: 16rpx; margin-top: 34rpx; width: 100%; }
.pea__fact {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  padding: 26rpx 12rpx;
  border: 1px solid var(--pea-card-border);
  border-radius: 34rpx;
  background: var(--mrc-surface);
}
.pea__fact-num { color: var(--mrc-accent); font-size: 54rpx; font-weight: 900; line-height: 1.1; }
.pea__fact-label { color: var(--mrc-text-sub); font-size: 20rpx; }
.pea__dual-banner {
  margin-top: 26rpx;
  padding: 12rpx 30rpx;
  border-radius: 999rpx;
  background: var(--mrc-accent-soft);
}
.pea__dual-text { color: var(--mrc-accent); font-size: 24rpx; font-weight: 700; }

/* 主 CTA：胶囊渐变 + 柔和阴影 + 短促按压反馈 */
.pea__cta {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 112rpx;
  margin-top: 40rpx;
  border-radius: 999rpx;
  background: var(--mrc-primary-grad);
  color: #fff;
  font-size: 34rpx;
  font-weight: 700;
  letter-spacing: 2rpx;
  box-shadow: 0 16rpx 40rpx rgba(239, 105, 91, 0.22), var(--mrc-gloss);
  transition: transform .12s ease;
}
.pea__cta:active { transform: scale(0.985); }
.pea__skip { margin-top: 22rpx; color: var(--mrc-text-light); font-size: 22rpx; }
.pea__skip:active { opacity: 0.7; }

/* 小屏（≤359px）：收紧内距与标题，防挤压溢出 */
@media (max-width: 359px) {
  .pea { padding-left: 24rpx; padding-right: 24rpx; }
  .pea__title-line { font-size: 46rpx; }
  .pea__fact { padding: 20rpx 8rpx; }
}

/* ---- 答题 ---- */
.pea__quiz { margin-top: 20rpx; }
.pea__progress-row { display: flex; align-items: center; gap: 16rpx; margin-bottom: 24rpx; }
.pea__progress-num { flex: 0 0 auto; color: var(--mrc-text-sub); font-size: 22rpx; font-weight: 800; }
.pea__progress-bar { position: relative; flex: 1; height: 12rpx; border-radius: 999rpx; background: var(--mrc-surface-3); }
.pea__progress-fill { height: 100%; border-radius: 999rpx; background: var(--mrc-primary-grad); transition: width .3s ease; }
/* 进度条上的锅仔：举筷饿锅仔随进度往前走，走到头就去看结果 */
.pea__progress-walker {
  position: absolute;
  top: 50%;
  width: 52rpx;
  height: 52rpx;
  transform: translate(-50%, -58%);
  transition: left .3s ease;
  pointer-events: none;
  filter: drop-shadow(0 4rpx 8rpx rgba(120, 40, 20, 0.2));
}
.pea__q { display: flex; flex-direction: column; padding: 36rpx 30rpx; }
/* 出题区：锅仔每题换姿势，旁边气泡说一句台词 */
.pea__q-head { display: flex; align-items: flex-end; gap: 16rpx; }
.pea__q-avatar { flex: 0 0 96rpx; width: 96rpx; height: 96rpx; }
.pea__q-bubble {
  flex: 1;
  min-width: 0;
  padding: 14rpx 20rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 20rpx 20rpx 20rpx 6rpx;
  background: var(--mrc-surface-2);
}
.pea__q-quip { color: var(--mrc-text-sub); font-size: 21rpx; }
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
  .pea__q-avatar.guozai-breathe,
  .pea__analyzing-img.guozai-breathe,
  .pea__q.pop-in,
  .pea__card.pop-in {
    animation: none;
  }
  .pea__deco--star-a,
  .pea__deco--star-b { animation: none; }
  .pea__progress-walker,
  .pea__progress-fill { transition: none; }
}
</style>
