<script setup lang="ts">
/**
 * 锅仔九宫格·翻牌版（复刻 NutUI-Bingo squarenine 机制）。
 *
 * 原版交互链：中心「开始」→ 卡片波浪洗牌（逐张延迟外扩再归位）→ 用户任选一张
 * → 中奖项被换到所点卡下 → 该卡 3D 翻转显奖 → 稍后全部翻开 → 再来一轮。
 *
 * 真选择：8 张牌各自背一道真实 AI 菜，用户翻哪张哪张就是结果；不再预设中奖菜，
 * 也不假随机——点中的牌背后就是那道真实菜，翻到彩蛋格才提示重洗。
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
  /** 用户按下中心按钮：父组件准备牌面后调 deal() */
  (e: 'start'): void
  /** 用户翻开某张牌：回传牌序号，父组件据此定位真实菜 */
  (e: 'pick', index: number): void
  /** 用户翻到彩蛋装饰格：通知父组件重洗（不再弹错误提示） */
  (e: 'decoy', index: number): void
  /** 用户确认「就做这道」 */
  (e: 'cook'): void
}>()

const DECOR = ['干饭大吉', '香迷糊', '锅气满满', '好胃口', '慢慢炖', '暖暖的', '吃饱饱', '锅仔拿手']

const cells = ref<FlipSlot[]>(Array.from({ length: 8 }, (_, i) => ({ name: DECOR[i] })))
const phase = ref<'idle' | 'dealing' | 'ready' | 'picking' | 'revealed'>('idle')
const allOpen = ref(false)
const picked = ref(-1)
const prize = ref<FlipSlot>({ name: '' })
const celebrating = ref(false)
let timers: ReturnType<typeof setTimeout>[] = []
let celebrateTimer: ReturnType<typeof setTimeout> | undefined

watch(() => props.visible, (v) => {
  if (v)
    reset()
  else
    clearTimers()
})

function clearTimers() {
  timers.forEach(clearTimeout)
  timers = []
  if (celebrateTimer) clearTimeout(celebrateTimer)
}

/** 撒花粒子：翻中瞬间从牌阵中心向四周炸开（与转一转/跑一圈同一套庆祝语言） */
const CONFETTI_COLORS = ['#FF8A5C', '#FFD93D', '#7BC47F', '#6FB7FF', '#F472B6', '#FFB86C']
const confetti = computed(() => Array.from({ length: 18 }, (_, i) => {
  const angle = (i / 18) * Math.PI * 2 + (i % 3) * 0.22
  const dist = 180 + (i % 5) * 50
  return {
    x: Math.round(Math.cos(angle) * dist),
    y: Math.round(Math.sin(angle) * dist),
    rot: (i * 47) % 360,
    delay: (i % 6) * 0.05,
    color: CONFETTI_COLORS[i % CONFETTI_COLORS.length],
    size: i % 3 === 0 ? 16 : i % 3 === 1 ? 12 : 14,
  }
}))

/** 翻中庆祝：撒花 2.2s + 轻震动 */
function celebrate() {
  celebrating.value = true
  if (celebrateTimer) clearTimeout(celebrateTimer)
  celebrateTimer = setTimeout(() => { celebrating.value = false }, 2200)
  try { uni.vibrateShort({ type: 'medium' }) } catch {}
}

function later(fn: () => void, ms: number) {
  timers.push(setTimeout(fn, ms))
}

function reset() {
  clearTimers()
  phase.value = 'idle'
  allOpen.value = false
  picked.value = -1
  celebrating.value = false
  buildCells()
}

function buildCells() {
  const pool = props.items.filter(item => item?.name).slice(0, 8)
  cells.value = Array.from({ length: 8 }, (_, i) => pool[i] || { name: DECOR[i] })
}

/** 菜池异步到达时重建牌面：父组件先开弹层后请求，visible 的 watch 只会拿到空池 */
watch(() => props.items, () => {
  if (phase.value === 'idle')
    buildCells()
})

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

interface WaveSeed { x: number, r: number, s: number }
const waveSeeds = ref<WaveSeed[]>([])

/** 洗牌前掷一份随机种子：每张卡的横向漂移、倾角、放大都不同，避免整组像鼓点一样整齐呆滞 */
function rollWaves() {
  waveSeeds.value = Array.from({ length: 8 }, () => ({
    x: Math.round(Math.random() * 34 - 17),
    r: Math.round((Math.random() * 9 - 4.5) * 10) / 10,
    s: 1 + 0.03 + Math.random() * 0.04,
  }))
}

/** 原地波浪洗牌：逐张延迟 + 随机横向漂移/倾角/缩放，卡片不出宫格 */
function waveStyle(index: number): Record<string, string> {
  if (phase.value !== 'dealing')
    return {}
  const seed = waveSeeds.value[index]
  if (!seed)
    return { animationDelay: `${index * 70}ms` }
  return {
    animationDelay: `${index * 70}ms`,
    '--wave-x': `${seed.x}rpx`,
    '--wave-r': `${seed.r}deg`,
    '--wave-s': String(seed.s),
  }
}

function pressCenter() {
  if (phase.value === 'dealing' || phase.value === 'ready' || phase.value === 'picking')
    return
  if (phase.value === 'revealed') {
    reset()
  }
  rollWaves()
  phase.value = 'dealing'
  emit('start')
}

/** 父组件备好牌面后调用：波浪洗牌 → 就绪，等待用户翻牌。中奖菜不再预设，由用户翻哪张决定。 */
function deal() {
  if (phase.value !== 'dealing')
    return
  // 1900ms 覆盖最慢一张卡的动画（490ms 延迟 + 2×650ms），避免洗牌没演完就进入选牌
  later(() => { phase.value = 'ready' }, 1900)
}

/** 菜池未就绪等异常时由父组件调用：终止洗牌回到待机，避免永远卡在「洗牌中」。 */
function fail() {
  if (phase.value !== 'dealing')
    return
  clearTimers()
  phase.value = 'idle'
}

/** 用户点开一张牌：翻出的就是这张牌背后背着的真实菜，1.2s 后全部翻开。翻到彩蛋装饰格则直接重洗，不撒假花。 */
function pickCard(index: number) {
  if (phase.value !== 'ready')
    return
  const cell = cells.value[index]
  if (!cell?.name)
    return
  // 彩蛋装饰格（超出真实菜数量）：不撒花、不显奖，通知父组件重洗后让用户再翻
  if (index >= (props.items?.length || 0)) {
    emit('decoy', index)
    return
  }
  picked.value = index
  prize.value = cell
  phase.value = 'picking'
  celebrate()
  emit('pick', index)
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

/** 翻到彩蛋格后由父组件调用：原地重洗一轮（不弹错误提示），让用户再翻一次。 */
function reshuffle() {
  reset()
  rollWaves()
  phase.value = 'dealing'
  deal()
}
defineExpose({ deal, fail, reset, reshuffle })
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
        <view v-if="celebrating" class="gnf__confetti" aria-hidden="true">
          <view v-for="(p, i) in confetti" :key="i" class="gnf__confetti-piece" :style="{ background: p.color, width: `${p.size}rpx`, height: `${Math.round(p.size * 0.45)}rpx`, '--cx': `${p.x}rpx`, '--cy': `${p.y}rpx`, '--cr': `${p.rot}deg`, animationDelay: `${p.delay}s` }" />
        </view>
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
          <text class="gnf__result-label">撒花！翻到了</text>
          <text class="gnf__result-name">{{ prize.name }}</text>
        </view>
        <view class="gnf__result-btn" role="button" aria-label="就做这道菜" @click="cook">就做这道</view>
      </view>
      <view class="gnf__foot">
        <text class="gnf__foot-text">翻哪张吃哪张，锅仔不耍赖</text>
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

.gnf__grid { position: relative; display: grid; grid-template-columns: repeat(3, 1fr); gap: 14rpx; width: 100%; padding: 22rpx; border: 4rpx solid #FFD93D; border-radius: 32rpx; background: var(--mrc-primary-grad); box-shadow: var(--mrc-shadow-coral); box-sizing: border-box; }

/* 撒花：翻中瞬间从牌阵中心炸开的彩纸屑（与转一转/跑一圈同一套庆祝语言） */
.gnf__confetti { position: absolute; z-index: 6; top: 50%; left: 50%; pointer-events: none; }
.gnf__confetti-piece { position: absolute; border-radius: 4rpx; opacity: 0; animation: gnf-burst 1.5s cubic-bezier(0.16, 0.84, 0.3, 1) forwards; }
@keyframes gnf-burst {
  0% { opacity: 0; transform: translate(0, 0) rotate(0deg) scale(0.6); }
  12% { opacity: 1; }
  70% { opacity: 1; }
  100% { opacity: 0; transform: translate(var(--cx), var(--cy)) rotate(var(--cr)) scale(1); }
}
.gnf__cellbox { perspective: 700rpx; }
.gnf__flip { position: relative; width: 100%; height: 170rpx; transform-style: preserve-3d; transition: transform 0.7s cubic-bezier(0.35, 0.1, 0.25, 1.05); }
.gnf__flip.is-flipped { transform: rotateY(180deg); }
/* 原地波浪洗牌：随机横向漂移 + 倾角 + 缩放，逐张推进，卡片不出宫格 */
.gnf__flip.is-waving { animation: gnf-wave 0.65s ease-in-out 2; }
@keyframes gnf-wave {
  0% { transform: translate3d(0, 0, 0) rotate(0deg) scale(1); }
  50% { transform: translate3d(var(--wave-x, 0), -20rpx, 0) rotate(var(--wave-r, 4deg)) scale(var(--wave-s, 1.06)); }
  100% { transform: translate3d(0, 0, 0) rotate(0deg) scale(1); }
}
.gnf__face { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8rpx; overflow: hidden; border: 2rpx solid rgba(255, 255, 255, 0.75); border-radius: 22rpx; box-sizing: border-box; backface-visibility: hidden; -webkit-backface-visibility: hidden; }
.gnf__face--front { background: linear-gradient(150deg, #FFF6EC 0%, #FFE3CC 100%); box-shadow: inset 0 0 0 6rpx rgba(255, 255, 255, 0.55); }
.gnf__face-logo { width: 76rpx; height: 76rpx; opacity: 0.9; }
.gnf__face-mark { color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; letter-spacing: 4rpx; }
/* 牌背：手账便签风——通栏大图 + 虚线分隔 + 菜名条，替代原来上下挤在一起的小图小字 */
.gnf__face--back { padding: 10rpx 10rpx 0; background: linear-gradient(170deg, #FFFDF7 0%, #FFF3E2 100%); transform: rotateY(180deg); }
.gnf__face--back::after { content: ''; position: absolute; top: 124rpx; left: 14rpx; right: 14rpx; border-top: 2rpx dashed var(--mrc-border-strong); opacity: .45; }
.gnf__card-img { width: 100%; height: 102rpx; overflow: hidden; border: 4rpx solid #fff; border-radius: 16rpx; background: var(--mrc-surface-2); box-shadow: 0 4rpx 10rpx rgba(88, 48, 31, 0.14); }
.gnf__card-img image { width: 100%; height: 100%; }
.gnf__card-img--blank { display: flex; align-items: center; justify-content: center; background: var(--mrc-surface-peach); color: var(--mrc-accent); font-size: 32rpx; font-weight: 800; }
.gnf__card-name { max-width: 100%; margin-top: 10rpx; padding: 0 6rpx; color: var(--mrc-text-deep); font-size: 21rpx; font-weight: 800; text-align: center; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; box-sizing: border-box; }

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
  .gnf__center.is-dealing, .gnf__panel, .gnf__result, .gnf__confetti-piece { animation: none; }
  .gnf__confetti-piece { opacity: 0; }
}
</style>
