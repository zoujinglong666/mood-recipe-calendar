<script setup lang="ts">
/**
 * 锅仔九宫格·翻牌版（复刻 NutUI-Bingo squarenine 机制）。
 *
 * 原版交互链：中心「开始」→ 卡片波浪洗牌（逐张延迟外扩再归位）→ 用户任选一张
 * → 中奖项被换到所点卡下 → 该卡 3D 翻转显奖 → 稍后全部翻开 → 再来一轮。
 *
 * 诚实原则：结果先于翻牌确定——父组件从真实 AI 菜池抽出那道菜并 deal() 给组件，
 * 用户翻开的那张牌永远是这道菜，绝不假随机。
 */
import { computed, ref, watch } from 'vue'
import { STATIC_BASE_URL } from '@/utils/assets'

interface FlipSlot {
  name: string
  image?: string
}

const props = withDefaults(defineProps<{
  visible: boolean
  /** 牌面池：真实 AI 菜抽样 8 道（全翻开时展示），不足用彩蛋词补位 */
  items?: FlipSlot[]
}>(), {
  visible: false,
  items: () => [],
})

const emit = defineEmits<{
  (e: 'close'): void
  /** 用户按下中心按钮：父组件定结果后调 deal(prize) */
  (e: 'start'): void
  /** 用户确认「就做这道」 */
  (e: 'cook'): void
}>()

const DECOR = ['干饭大吉', '香迷糊', '锅气满满', '好胃口', '慢慢炖', '暖暖的', '吃饱饱', '锅仔拿手']

const cells = ref<FlipSlot[]>(Array.from({ length: 8 }, (_, i) => ({ name: DECOR[i] })))
const phase = ref<'idle' | 'dealing' | 'ready' | 'picking' | 'revealed'>('idle')
const allOpen = ref(false)
const picked = ref(-1)
const prize = ref<FlipSlot>({ name: '' })
let timers: ReturnType<typeof setTimeout>[] = []

watch(() => props.visible, (v) => {
  if (v)
    reset()
  else
    clearTimers()
})

function clearTimers() {
  timers.forEach(clearTimeout)
  timers = []
}

function later(fn: () => void, ms: number) {
  timers.push(setTimeout(fn, ms))
}

function reset() {
  clearTimers()
  phase.value = 'idle'
  allOpen.value = false
  picked.value = -1
  buildCells()
}

function buildCells() {
  const pool = props.items.filter(item => item?.name).slice(0, 8)
  cells.value = Array.from({ length: 8 }, (_, i) => pool[i] || { name: DECOR[i] })
}

const centerText = computed(() => {
  if (phase.value === 'idle') return '开翻'
  if (phase.value === 'dealing') return '洗牌中'
  if (phase.value === 'ready') return '选一张'
  if (phase.value === 'picking') return '翻中'
  return '再来一轮'
})
const hint = computed(() => {
  if (phase.value === 'idle') return '按下锅仔，洗牌后选一张'
  if (phase.value === 'dealing') return '锅仔正在洗牌…'
  if (phase.value === 'ready') return '选一张牌，翻开今天吃的'
  if (phase.value === 'picking') return '就是它——'
  return `就吃「${prize.value.name}」！`
})

/** 原地波浪洗牌：每张卡依次轻跳，涟漪扫过（不做位移，卡片不出宫格） */
function waveStyle(index: number) {
  if (phase.value !== 'dealing')
    return {}
  return { animationDelay: `${index * 80}ms` }
}

function pressCenter() {
  if (phase.value === 'dealing' || phase.value === 'ready' || phase.value === 'picking')
    return
  if (phase.value === 'revealed') {
    reset()
  }
  phase.value = 'dealing'
  emit('start')
}

/** 父组件定好中奖菜后调用：波浪洗牌 → 就绪，等待用户选牌。 */
function deal(winner: FlipSlot) {
  if (phase.value !== 'dealing')
    return
  prize.value = winner
  later(() => { phase.value = 'ready' }, 1700)
}

/** 用户点开一张牌：所点卡立即翻出中奖菜，1.2s 后全部翻开。 */
function pickCard(index: number) {
  if (phase.value !== 'ready')
    return
  picked.value = index
  phase.value = 'picking'
  later(() => {
    allOpen.value = true
    phase.value = 'revealed'
  }, 1200)
}

/** 该卡是否处于翻开态 */
function isFlipped(index: number): boolean {
  return allOpen.value || picked.value === index
}

/** 卡背内容：所点卡 = 中奖菜，其余 = 各自牌面 */
function cardBack(index: number): FlipSlot {
  return index === picked.value ? prize.value : cells.value[index]
}

function cook() {
  if (phase.value === 'revealed')
    emit('cook')
}

function onMask() {
  if (phase.value !== 'dealing' && phase.value !== 'picking')
    emit('close')
}

defineExpose({ deal })
</script>

<template>
  <view v-if="visible" class="gnf" aria-label="锅仔九宫格翻牌">
    <view class="gnf__mask" @click="onMask" />
    <view class="gnf__panel">
      <view class="gnf__close" role="button" aria-label="关闭九宫格" @click="onMask">✕</view>
      <view class="gnf__head">
        <text class="gnf__eyebrow">GUOZAI SQUARENINE</text>
        <text class="gnf__title">锅仔翻一翻</text>
        <text class="gnf__sub">{{ hint }}</text>
      </view>
      <view class="gnf__grid" aria-label="菜品九宫格">
        <template v-for="p in 9" :key="p">
          <view
            v-if="p - 1 === 4"
            class="gnf__center"
            :class="{ 'is-dealing': phase === 'dealing' }"
            role="button"
            :aria-label="centerText"
            @click="pressCenter"
          >
            <image class="gnf__center-img" :src="STATIC_BASE_URL + '/static/guozai/action_16_chopsticks.png'" mode="aspectFit" />
            <text class="gnf__center-text">{{ centerText }}</text>
          </view>
          <view v-else class="gnf__cellbox">
            <view
              class="gnf__flip"
              :class="{ 'is-flipped': isFlipped(p - 1 > 4 ? p - 2 : p - 1), 'is-waving': phase === 'dealing' }"
              :style="waveStyle(p - 1 > 4 ? p - 2 : p - 1)"
              role="button"
              :aria-label="phase === 'ready' ? '翻开这张牌' : (cardBack(p - 1 > 4 ? p - 2 : p - 1)?.name || '锅仔牌')"
              @click="pickCard(p - 1 > 4 ? p - 2 : p - 1)"
            >
              <view class="gnf__face gnf__face--front">
                <image class="gnf__face-logo" :src="STATIC_BASE_URL + '/static/guozai/action_01_bowl.png'" mode="aspectFit" />
                <text class="gnf__face-mark">锅仔牌</text>
              </view>
              <view class="gnf__face gnf__face--back">
                <view v-if="cardBack(p - 1 > 4 ? p - 2 : p - 1)?.image" class="gnf__card-img">
                  <image :src="cardBack(p - 1 > 4 ? p - 2 : p - 1)!.image" mode="aspectFill" />
                </view>
                <view v-else class="gnf__card-img gnf__card-img--blank"><text>锅</text></view>
                <text class="gnf__card-name">{{ cardBack(p - 1 > 4 ? p - 2 : p - 1)?.name }}</text>
              </view>
            </view>
          </view>
        </template>
      </view>
      <view v-if="phase === 'revealed'" class="gnf__result" role="status">
        <image v-if="prize.image" class="gnf__result-img" :src="prize.image" mode="aspectFill" />
        <view class="gnf__result-copy">
          <text class="gnf__result-label">翻到了</text>
          <text class="gnf__result-name">{{ prize.name }}</text>
        </view>
        <view class="gnf__result-btn" role="button" aria-label="就做这道菜" @click="cook">就做这道</view>
      </view>
      <view class="gnf__foot">
        <text class="gnf__foot-text">你翻开的就是今天吃的，锅仔说话算数</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.gnf { position: fixed; inset: 0; z-index: 1200; display: flex; align-items: center; justify-content: center; }
.gnf__mask { position: absolute; inset: 0; background: rgba(43, 24, 14, 0.55); }
.gnf__panel { position: relative; z-index: 1; display: flex; flex-direction: column; align-items: center; width: 640rpx; padding: 44rpx 30rpx calc(40rpx + env(safe-area-inset-bottom)); border: 2rpx solid var(--mrc-border-light); border-radius: 44rpx; background: var(--mrc-surface); box-shadow: 0 30rpx 70rpx rgba(60, 30, 14, 0.35); animation: gnf-in 0.32s cubic-bezier(0.22, 0.9, 0.3, 1.2) both; }
@keyframes gnf-in { from { opacity: 0; transform: scale(0.86) translateY(40rpx); } to { opacity: 1; transform: scale(1) translateY(0); } }
.gnf__close { position: absolute; top: 24rpx; right: 28rpx; z-index: 5; display: flex; align-items: center; justify-content: center; width: 56rpx; height: 56rpx; border-radius: 50%; background: var(--mrc-surface-2); color: var(--mrc-text-sub); font-size: 26rpx; }
.gnf__head { display: flex; flex-direction: column; align-items: center; gap: 8rpx; margin-bottom: 30rpx; }
.gnf__eyebrow { color: var(--mrc-accent); font-size: 18rpx; font-weight: 800; letter-spacing: 3rpx; }
.gnf__title { color: var(--mrc-text-strong); font-size: 40rpx; font-weight: 800; }
.gnf__sub { min-height: 34rpx; color: var(--mrc-text-sub); font-size: 22rpx; }

.gnf__grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14rpx; width: 100%; padding: 22rpx; border: 4rpx solid #FFD93D; border-radius: 32rpx; background: var(--mrc-primary-grad); box-shadow: var(--mrc-shadow-coral); box-sizing: border-box; }
.gnf__cellbox { perspective: 700rpx; }
.gnf__flip { position: relative; width: 100%; height: 170rpx; transform-style: preserve-3d; transition: transform 0.7s cubic-bezier(0.35, 0.1, 0.25, 1.05); }
.gnf__flip.is-flipped { transform: rotateY(180deg); }
/* 原地波浪洗牌：轻跳 + 微转，逐张推进，卡片不出宫格 */
.gnf__flip.is-waving { animation: gnf-wave 0.6s ease-in-out 2; }
@keyframes gnf-wave { 0%, 100% { transform: none; } 50% { transform: translateY(-12rpx) rotate(1.5deg) scale(1.04); } }
.gnf__face { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8rpx; overflow: hidden; border: 2rpx solid rgba(255, 255, 255, 0.75); border-radius: 22rpx; box-sizing: border-box; backface-visibility: hidden; -webkit-backface-visibility: hidden; }
.gnf__face--front { background: linear-gradient(150deg, #FFF6EC 0%, #FFE3CC 100%); box-shadow: inset 0 0 0 6rpx rgba(255, 255, 255, 0.55); }
.gnf__face-logo { width: 76rpx; height: 76rpx; opacity: 0.9; }
.gnf__face-mark { color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; letter-spacing: 4rpx; }
.gnf__face--back { background: #FFF9F0; transform: rotateY(180deg); }
.gnf__card-img { width: 92rpx; height: 84rpx; overflow: hidden; border-radius: 16rpx; background: var(--mrc-surface-2); }
.gnf__card-img image { width: 100%; height: 100%; }
.gnf__card-img--blank { display: flex; align-items: center; justify-content: center; background: var(--mrc-surface-peach); color: var(--mrc-accent); font-size: 32rpx; font-weight: 800; }
.gnf__card-name { max-width: 100%; padding: 0 8rpx; color: var(--mrc-text-deep); font-size: 20rpx; font-weight: 700; text-align: center; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; box-sizing: border-box; }

.gnf__center { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4rpx; min-height: 170rpx; border-radius: 22rpx; background: var(--mrc-primary-grad); box-shadow: 0 6rpx 18rpx rgba(200, 70, 30, 0.4); }
.gnf__center.is-dealing { animation: gnf-shake 0.5s ease infinite; }
@keyframes gnf-shake { 0%, 100% { transform: rotate(0); } 30% { transform: rotate(-4deg); } 70% { transform: rotate(4deg); } }
.gnf__center-img { width: 88rpx; height: 88rpx; filter: drop-shadow(0 4rpx 8rpx rgba(0, 0, 0, 0.15)); }
.gnf__center-text { color: #fff; font-size: 22rpx; font-weight: 800; }

.gnf__result { display: flex; align-items: center; gap: 20rpx; width: 100%; margin-top: 30rpx; padding: 18rpx 22rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; background: var(--mrc-surface-sun); box-sizing: border-box; animation: gnf-pop 0.4s cubic-bezier(0.22, 0.9, 0.3, 1.3) both; }
@keyframes gnf-pop { from { opacity: 0; transform: translateY(24rpx) scale(0.94); } to { opacity: 1; transform: translateY(0) scale(1); } }
.gnf__result-img { width: 84rpx; height: 84rpx; flex: 0 0 84rpx; overflow: hidden; border-radius: 20rpx; background: var(--mrc-surface-2); }
.gnf__result-copy { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 4rpx; }
.gnf__result-label { color: var(--mrc-text-sub); font-size: 19rpx; }
.gnf__result-name { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 800; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.gnf__result-btn { flex: 0 0 auto; padding: 14rpx 30rpx; border-radius: 999rpx; background: var(--mrc-primary-grad); color: #fff; font-size: 24rpx; font-weight: 800; box-shadow: var(--mrc-shadow-coral); }
.gnf__result-btn:active { transform: scale(0.95); }

.gnf__foot { margin-top: 30rpx; }
.gnf__foot-text { color: var(--mrc-text-light); font-size: 20rpx; }

@media (prefers-reduced-motion: reduce) {
  .gnf__flip { transition-duration: 0.25s; }
  .gnf__flip.is-waving { animation: none; }
  .gnf__center.is-dealing, .gnf__panel, .gnf__result { animation: none; }
}
</style>
