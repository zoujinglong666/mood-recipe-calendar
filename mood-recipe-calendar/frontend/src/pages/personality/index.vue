<script setup lang="ts">
/**
 * 干饭人格 / 默契测试。
 *
 * 诚实计分原则：人格结果由真实答案计分（argmax）算出，
 * 默契度由两人答案逐题比对算出——不是假随机，锅仔不糊弄。
 *
 * 默契模式零后端：出题人答完后把答案编进分享链接
 * （/pages/personality/index?dual=01234），好友打开答题即出两人对比。
 */
import type { CompatItem } from '../../data/growth'
import { onLoad, onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { navBack } from '@/composables/useNavBar'
import {
  COMPAT_LEVEL_TEXT,
  compatOf,
  decodeAnswers,
  encodeAnswers,
  PERSONALITIES,
  QUIZ,
  scorePersonality,
} from '../../data/growth'
import { STATIC_BASE_URL } from '../../utils/assets'

definePage({
  name: 'personality',
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '干饭人格',
  },
})

const router = useRouter()

/* ---- 流程状态：intro → quiz → result ---- */
const step = ref<'intro' | 'quiz' | 'result'>('intro')
const isDual = ref(false)
const hostAnswers = ref<number[]>([])
const answers = ref<number[]>([])
const qIndex = ref(0)
const locked = ref(false)

/** 默契模式：链接里带 ?dual=，好友进来直接进入「接招」状态 */
onLoad((options: { dual?: string } = {}) => {
  const dual = decodeAnswers(options.dual)
  if (dual) {
    hostAnswers.value = dual
    isDual.value = true
  }
})

function start() {
  step.value = 'quiz'
  qIndex.value = 0
  answers.value = []
}

function pick(optionIndex: number) {
  if (locked.value)
    return
  locked.value = true
  answers.value.push(optionIndex)
  setTimeout(() => {
    locked.value = false
    if (qIndex.value < QUIZ.length - 1) {
      qIndex.value += 1
    }
    else {
      finish()
    }
  }, 260)
}

function finish() {
  step.value = 'result'
}

/* ---- 结果（真实计分，答案不全时不出结果） ---- */
const scored = computed(() => scorePersonality(answers.value))
const result = computed(() => PERSONALITIES[scored.value.key])
const resultComplete = computed(() => answers.value.length === QUIZ.length)

const compat = computed(() => {
  if (!isDual.value || !resultComplete.value || hostAnswers.value.length !== QUIZ.length)
    return null
  return compatOf(hostAnswers.value, answers.value)
})

function levelText(level: CompatItem['level']) {
  return COMPAT_LEVEL_TEXT[level]
}

/* ---- 分享：默认晒人格；点「测默契」后改为带上自己答案的对战邀请 ---- */
const shareMode = ref<'self' | 'dual'>('self')

function inviteDual() {
  shareMode.value = 'dual'
}

onShareAppMessage(() => {
  if (shareMode.value === 'dual' && resultComplete.value) {
    return {
      title: `我是「${result.value.name}」，来测测咱俩吃饭默契`,
      path: `/pages/personality/index?dual=${encodeAnswers(answers.value)}`,
    }
  }
  return { title: `我测出是「${result.value.name}」，你呢？`, path: '/pages/personality/index' }
})
onShareTimeline(() => ({ title: '测测你的干饭人格 · 锅仔出品' }))

function again() {
  step.value = 'intro'
  answers.value = []
  shareMode.value = 'self'
}

/** 幸运菜出口：按人格映射的心情标签去菜谱库看真实存在的菜——测完不止一张卡，还能吃到 */
function goLuckyDishes() {
  router.push({ name: 'recipe-library', query: { mood: result.value.luckyMood } })
}
</script>

<template>
  <view class="pea">
    <wd-navbar title="干饭人格" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <!-- 开场 -->
    <view v-if="step === 'intro'" class="pea__intro mrc-hero">
      <image class="pea__hero-img guozai-breathe" :src="`${STATIC_BASE_URL}/static/guozai/action_09_celebrate.png`" mode="aspectFit" aria-hidden="true" />
      <text class="pea__title">
        测测你的干饭人格
      </text>
      <text class="pea__sub">
        5 道题，锅仔帮你看看你的干饭人格——结果由你真实的选择算出，锅仔只负责宣读。
      </text>
      <view v-if="isDual" class="pea__dual-banner">
        <text class="pea__dual-text">
          有饭搭子想跟你比吃饭默契，接招？
        </text>
      </view>
      <view class="pea__cta mrc-btn-primary" role="button" :aria-label="isDual ? '接招开始答题' : '开始测试'" @click="start">
        {{ isDual ? '接招，开测' : '开测' }}
      </view>
      <view v-if="isDual" class="pea__skip" role="button" aria-label="先测自己的人格" @click="isDual = false; start()">
        先测自己的人格
      </view>
    </view>

    <!-- 答题：进度点 + 单题卡 -->
    <view v-else-if="step === 'quiz'" class="pea__quiz">
      <view class="pea__progress" aria-hidden="true">
        <view v-for="(q, i) in QUIZ" :key="i" class="pea__dot" :class="{ 'is-done': i <= qIndex }" />
      </view>
      <view :key="qIndex" class="pea__q mrc-card pop-in">
        <text class="pea__q-no">
          第 {{ qIndex + 1 }} / {{ QUIZ.length }} 题
        </text>
        <text class="pea__q-text">
          {{ QUIZ[qIndex]?.emoji }} {{ QUIZ[qIndex]?.text }}
        </text>
        <view class="pea__options">
          <view
            v-for="(option, i) in QUIZ[qIndex]?.options"
            :key="i"
            class="pea__option"
            role="button"
            :aria-label="option.label"
            @click="pick(i)"
          >
            <text class="pea__option-emoji">
              {{ option.emoji }}
            </text>
            <text class="pea__option-label">
              {{ option.label }}
            </text>
            <text class="pea__option-arrow">
              ›
            </text>
          </view>
        </view>
      </view>
    </view>

    <!-- 结果：默契模式先出默契环，再出双方人格卡 -->
    <view v-else-if="resultComplete" class="pea__result">
      <!-- 默契环 + 逐题对比 -->
      <view v-if="compat" class="pea__card mrc-card pop-in">
        <text class="pea__eyebrow">
          CHOPSTICK CHEMISTRY
        </text>
        <view
          class="pea__ring"
          :style="{ background: `conic-gradient(var(--mrc-accent) 0 ${compat.score}%, var(--mrc-surface-3) ${compat.score}% 100%)` }"
          role="img"
          :aria-label="`吃饭默契 ${compat.score}%`"
        >
          <view class="pea__ring-inner">
            <text class="pea__ring-num">
              {{ compat.score }}%
            </text>
            <text class="pea__ring-label">
              吃饭默契
            </text>
          </view>
        </view>
        <text class="pea__tier">
          {{ compat.tier.name }}
        </text>
        <text class="pea__tier-line">
          {{ compat.tier.line }}
        </text>
        <view class="pea__compare">
          <view v-for="(item, i) in compat.per" :key="i" class="pea__compare-row">
            <text class="pea__compare-q">
              {{ item.q }}
            </text>
            <view class="pea__compare-ans">
              <text class="pea__compare-you">
                你：{{ item.guest }}
              </text>
              <text class="pea__compare-ta">
                TA：{{ item.host }}
              </text>
              <text class="pea__compare-tag" :class="`is-${item.level}`">
                {{ levelText(item.level) }}
              </text>
            </view>
          </view>
        </view>
      </view>

      <!-- 人格卡 -->
      <view class="pea__card mrc-card pop-in">
        <text class="pea__eyebrow">
          FOODIE DNA
        </text>
        <text class="pea__p-emoji">
          {{ result.emoji }}
        </text>
        <text class="pea__p-name">
          {{ result.name }}
        </text>
        <text class="pea__p-tagline">
          {{ result.tagline }}
        </text>
        <text class="pea__p-reading">
          {{ result.reading }}
        </text>
        <view class="pea__traits">
          <text v-for="trait in result.traits" :key="trait" class="mrc-tag mrc-tag--accent">
            {{ trait }}
          </text>
        </view>
        <text class="pea__lucky">
          幸运菜类型：{{ result.lucky }}
        </text>
        <view class="pea__lucky-go" role="button" :aria-label="`看看${result.lucky}有哪些菜`" @click="goLuckyDishes">
          看看「{{ result.lucky }}」有哪些菜 ›
        </view>
      </view>

      <!-- 行动区：拉人对战 / 晒结果 / 重测 -->
      <view class="pea__actions">
        <button class="pea__share-btn pea__share-btn--primary" open-type="share" @click="inviteDual">
          测测和 TA 的默契
        </button>
        <button class="pea__share-btn pea__share-btn--outline" open-type="share" @click="shareMode = 'self'">
          晒出我的人格
        </button>
        <view class="pea__again" role="button" aria-label="再测一次" @click="again">
          再测一次
        </view>
      </view>

      <view class="pea__foot">
        <text>人格由你的答案算出，锅仔只负责宣读结果</text>
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

/* ---- 开场 ---- */
.pea__intro {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 20rpx;
  padding: 60rpx 24rpx 48rpx;
  border-radius: var(--mrc-radius-lg);
}
.pea__hero-img { width: 260rpx; height: 260rpx; }
.pea__title { margin-top: 16rpx; color: var(--mrc-text-strong); font-size: 44rpx; font-weight: 800; }
.pea__sub { margin-top: 14rpx; color: var(--mrc-text-sub); font-size: 24rpx; line-height: 1.6; text-align: center; }
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
.pea__progress { display: flex; justify-content: center; gap: 14rpx; margin-bottom: 24rpx; }
.pea__dot { width: 14rpx; height: 14rpx; border-radius: 50%; background: var(--mrc-surface-3); transition: background var(--mrc-motion-fast); }
.pea__dot.is-done { background: var(--mrc-accent); }

.pea__q { display: flex; flex-direction: column; padding: 32rpx 28rpx; }
.pea__q-no { color: var(--mrc-text-light); font-size: 20rpx; font-weight: 600; letter-spacing: 1rpx; }
.pea__q-text { margin-top: 12rpx; color: var(--mrc-text-strong); font-size: 32rpx; font-weight: 800; line-height: 1.4; }
.pea__options { display: flex; flex-direction: column; gap: 18rpx; margin-top: 28rpx; }
.pea__option {
  display: flex;
  align-items: center;
  gap: 18rpx;
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
.pea__option-emoji { font-size: 36rpx; }
.pea__option-label { flex: 1; min-width: 0; color: var(--mrc-text-deep); font-size: 28rpx; font-weight: 600; }
.pea__option-arrow { color: var(--mrc-text-light); font-size: 28rpx; }

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

/* 默契环：conic-gradient 着色，与转盘同一套装饰语言 */
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

.pea__compare { display: flex; flex-direction: column; gap: 16rpx; width: 100%; margin-top: 28rpx; }
.pea__compare-row {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  padding: 18rpx 20rpx;
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
.pea__compare-tag.is-same { background: var(--mrc-surface-sun); color: var(--mrc-pop); }
.pea__compare-tag.is-diff { background: var(--mrc-surface-3); color: var(--mrc-text-sub); }

.pea__p-emoji { margin-top: 20rpx; font-size: 84rpx; line-height: 1.2; }
.pea__p-name { margin-top: 8rpx; color: var(--mrc-text-strong); font-size: 40rpx; font-weight: 800; }
.pea__p-tagline { margin-top: 6rpx; color: var(--mrc-accent); font-size: 24rpx; font-weight: 700; }
.pea__p-reading { margin-top: 18rpx; color: var(--mrc-text); font-size: 26rpx; line-height: 1.7; text-align: center; }
.pea__traits { display: flex; flex-wrap: wrap; justify-content: center; gap: 12rpx; margin-top: 22rpx; }
.pea__traits .mrc-tag { margin-right: 0; }
.pea__lucky { margin-top: 20rpx; color: var(--mrc-text-sub); font-size: 22rpx; }
.pea__lucky-go {
  margin-top: 16rpx;
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
  .pea__q.pop-in,
  .pea__card.pop-in {
    animation: none;
  }
}
</style>
