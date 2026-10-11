<script setup lang="ts">
/**
 * 锅仔九宫格：3×3 翻牌抽奖弹层（复刻 NutUI-Bingo squarenine 机制）。
 *
 * 诚实原则：跑马灯最终停在的格子，就是父组件从真实 AI 菜池里抽出的那道菜
 * ——先由父组件定结果，再驱动灯带减速定格到该格，绝不假随机。
 *
 * 交互节拍：idle（四角灯慢闪）→ running（灯带加速跑圈）→ 减速 → landed（中奖格脉冲）。
 * 中奖格确认「就做这道」由父组件接手（emit('cook')，携带格子下标）。
 */
import { computed, ref, watch } from 'vue'
import { STATIC_BASE_URL } from '@/utils/assets'

interface NineSlot {
  name: string
  image?: string
}

const props = withDefaults(defineProps<{
  visible: boolean
  /** 奖格池：真实 AI 菜抽样，最多 8 个（外圈 8 格），不足用彩蛋词补位 */
  items?: NineSlot[]
}>(), {
  visible: false,
  items: () => [],
})

const emit = defineEmits<{
  (e: 'close'): void
  /** 用户按下中心锅仔，父组件定结果后调 land(index) */
  (e: 'start'): void
  /** 灯带定格完成，携带中奖格下标（0..7，对应 items） */
  (e: 'result', index: number): void
  /** 用户确认「就做这道」 */
  (e: 'cook', index: number): void
}>()

/** 外圈格子按顺时针的跑灯顺序（3×3 网格中，4 号位是中心按钮） */
const ORDER = [0, 1, 2, 5, 8, 7, 6, 3]
const DECOR = ['干饭大吉', '香迷糊', '锅气满满', '好胃口', '慢慢炖', '暖暖的', '吃饱饱', '锅仔拿手']

const cells = ref<NineSlot[]>(Array.from({ length: 8 }, (_, i) => ({ name: DECOR[i] })))
const activePos = ref(-1)
const phase = ref<'idle' | 'running' | 'landed'>('idle')
const resultIndex = ref(-1)
let runTimer: ReturnType<typeof setTimeout> | undefined

watch(() => props.visible, (v) => {
  if (v) {
    phase.value = 'idle'
    resultIndex.value = -1
    activePos.value = -1
    buildCells()
  }
})

function buildCells() {
  const pool = props.items.filter(item => item?.name).slice(0, 8)
  cells.value = Array.from({ length: 8 }, (_, i) => pool[i] || { name: DECOR[i] })
}

const centerText = computed(() => (phase.value === 'landed' ? '再来' : '开转'))
const hint = computed(() => {
  if (phase.value === 'running') return '锅仔在九宫格里翻找…'
  if (phase.value === 'landed' && resultIndex.value >= 0) return `就吃「${cells.value[resultIndex.value]?.name || ''}」！`
  return '按下锅仔，翻出一道今天的菜'
})

/** 判断 3×3 网格位置 p（0..8）此刻是否被点亮：跑灯经过或中奖定格 */
function isLit(p: number): 'run' | 'win' | null {
  const orderIdx = ORDER.indexOf(p)
  if (orderIdx < 0) return null
  if (phase.value === 'landed' && resultIndex.value === orderIdx) return 'win'
  if (phase.value === 'running' && activePos.value === orderIdx) return 'run'
  return null
}

function pressCenter() {
  if (phase.value !== 'idle' && phase.value !== 'landed')
    return
  phase.value = 'running'
  emit('start')
}

/** 父组件定好中奖格后调用：灯带跑 3 圈再减速滑向目标格。 */
function land(target: number) {
  if (phase.value !== 'running')
    return
  const start = Math.max(0, activePos.value)
  const delta = (target - start + 8) % 8
  const total = 24 + delta
  let step = 0
  const tick = () => {
    activePos.value = (start + step) % 8
    step += 1
    if (step > total) {
      phase.value = 'landed'
      resultIndex.value = target
      emit('result', target)
      return
    }
    // 前 2/3 匀速快跑，末段逐步减速（NutUI squarenine 同款节奏）
    const slowdown = Math.max(0, step - (total - 12))
    const delay = slowdown > 0 ? 75 + (slowdown / 12) * 330 : 72
    runTimer = setTimeout(tick, delay)
  }
  tick()
}

function onMask() {
  if (phase.value !== 'running')
    emit('close')
}

function cook() {
  if (resultIndex.value >= 0)
    emit('cook', resultIndex.value)
}

defineExpose({ land })
</script>

<template>
  <view v-if="visible" class="gn" aria-label="锅仔九宫格">
    <view class="gn__mask" @click="onMask" />
    <view class="gn__panel">
      <view class="gn__close" role="button" aria-label="关闭九宫格" @click="onMask">✕</view>
      <view class="gn__head">
        <text class="gn__eyebrow">GUOZAI SQUARENINE</text>
        <text class="gn__title">锅仔翻一翻</text>
        <text class="gn__sub">{{ hint }}</text>
      </view>
      <view class="gn__grid" aria-label="菜品九宫格">
        <template v-for="p in 9" :key="p">
          <view v-if="p - 1 === 4" class="gn__center" :class="{ 'is-running': phase === 'running' }" role="button" :aria-label="centerText" @click="pressCenter">
            <image class="gn__center-img" :src="STATIC_BASE_URL + '/static/guozai/action_16_chopsticks.png'" mode="aspectFit" />
            <text class="gn__center-text">{{ centerText }}</text>
          </view>
          <view
            v-else
            class="gn__cell"
            :class="{ 'is-run': isLit(p - 1) === 'run', 'is-win': isLit(p - 1) === 'win', 'is-dim': phase === 'running' && !isLit(p - 1) }"
            :aria-label="cells[ORDER.indexOf(p - 1)]?.name"
          >
            <view v-if="cells[ORDER.indexOf(p - 1)]?.image" class="gn__cell-img">
              <image :src="cells[ORDER.indexOf(p - 1)]!.image" mode="aspectFill" />
            </view>
            <view v-else class="gn__cell-img gn__cell-img--blank"><text>锅</text></view>
            <text class="gn__cell-name">{{ cells[ORDER.indexOf(p - 1)]?.name }}</text>
          </view>
        </template>
      </view>
      <view v-if="phase === 'landed' && resultIndex >= 0" class="gn__result" role="status">
        <image v-if="cells[resultIndex]?.image" class="gn__result-img" :src="cells[resultIndex]!.image" mode="aspectFill" />
        <view class="gn__result-copy">
          <text class="gn__result-label">翻到了</text>
          <text class="gn__result-name">{{ cells[resultIndex]?.name }}</text>
        </view>
        <view class="gn__result-btn" role="button" aria-label="就做这道菜" @click="cook">就做这道</view>
      </view>
      <view class="gn__foot">
        <text class="gn__foot-text">翻到哪道做哪道，锅仔说话算数</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.gn { position: fixed; inset: 0; z-index: 1200; display: flex; align-items: center; justify-content: center; }
.gn__mask { position: absolute; inset: 0; background: rgba(43, 24, 14, 0.55); }
.gn__panel { position: relative; z-index: 1; display: flex; flex-direction: column; align-items: center; width: 640rpx; padding: 44rpx 30rpx calc(40rpx + env(safe-area-inset-bottom)); border: 2rpx solid var(--mrc-border-light); border-radius: 44rpx; background: var(--mrc-surface); box-shadow: 0 30rpx 70rpx rgba(60, 30, 14, 0.35); animation: gn-in 0.32s cubic-bezier(0.22, 0.9, 0.3, 1.2) both; }
@keyframes gn-in { from { opacity: 0; transform: scale(0.86) translateY(40rpx); } to { opacity: 1; transform: scale(1) translateY(0); } }
.gn__close { position: absolute; top: 24rpx; right: 28rpx; z-index: 5; display: flex; align-items: center; justify-content: center; width: 56rpx; height: 56rpx; border-radius: 50%; background: var(--mrc-surface-2); color: var(--mrc-text-sub); font-size: 26rpx; }
.gn__head { display: flex; flex-direction: column; align-items: center; gap: 8rpx; margin-bottom: 30rpx; }
.gn__eyebrow { color: var(--mrc-accent); font-size: 18rpx; font-weight: 800; letter-spacing: 3rpx; }
.gn__title { color: var(--mrc-text-strong); font-size: 40rpx; font-weight: 800; }
.gn__sub { min-height: 34rpx; color: var(--mrc-text-sub); font-size: 22rpx; }

.gn__grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14rpx; width: 100%; padding: 22rpx; border: 4rpx solid #FFD93D; border-radius: 32rpx; background: var(--mrc-primary-grad); box-shadow: var(--mrc-shadow-coral); box-sizing: border-box; }
.gn__cell { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8rpx; min-height: 170rpx; border: 2rpx solid rgba(255, 255, 255, 0.7); border-radius: 22rpx; background: #FFF9F0; box-sizing: border-box; transition: transform 0.12s ease, box-shadow 0.12s ease, filter 0.12s ease; }
.gn__cell.is-dim { filter: saturate(0.6) brightness(0.96); }
.gn__cell.is-run { transform: scale(1.05); border-color: #FFD93D; box-shadow: 0 0 18rpx rgba(255, 200, 60, 0.85); }
.gn__cell.is-win { transform: scale(1.08); border-color: #F2542D; background: #FFF0E4; box-shadow: 0 0 24rpx rgba(242, 84, 45, 0.45); animation: gn-win 0.55s ease; }
@keyframes gn-win { 0% { transform: scale(1.2); } 60% { transform: scale(1.02); } 100% { transform: scale(1.08); } }
.gn__cell-img { width: 92rpx; height: 92rpx; overflow: hidden; border-radius: 18rpx; background: var(--mrc-surface-2); }
.gn__cell-img image { width: 100%; height: 100%; }
.gn__cell-img--blank { display: flex; align-items: center; justify-content: center; background: var(--mrc-surface-peach); color: var(--mrc-accent); font-size: 34rpx; font-weight: 800; }
.gn__cell-name { max-width: 100%; padding: 0 8rpx; color: var(--mrc-text-deep); font-size: 20rpx; font-weight: 700; text-align: center; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; box-sizing: border-box; }

.gn__center { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4rpx; border-radius: 22rpx; background: var(--mrc-primary-grad); box-shadow: 0 6rpx 18rpx rgba(200, 70, 30, 0.4); min-height: 170rpx; }
.gn__center.is-running { animation: gn-shake 0.5s ease infinite; }
@keyframes gn-shake { 0%, 100% { transform: rotate(0); } 30% { transform: rotate(-4deg); } 70% { transform: rotate(4deg); } }
.gn__center-img { width: 92rpx; height: 92rpx; filter: drop-shadow(0 4rpx 8rpx rgba(0, 0, 0, 0.15)); }
.gn__center-text { color: #fff; font-size: 22rpx; font-weight: 800; }

.gn__result { display: flex; align-items: center; gap: 20rpx; width: 100%; margin-top: 30rpx; padding: 18rpx 22rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; background: var(--mrc-surface-sun); box-sizing: border-box; animation: gn-pop 0.4s cubic-bezier(0.22, 0.9, 0.3, 1.3) both; }
@keyframes gn-pop { from { opacity: 0; transform: translateY(24rpx) scale(0.94); } to { opacity: 1; transform: translateY(0) scale(1); } }
.gn__result-img { width: 84rpx; height: 84rpx; flex: 0 0 84rpx; overflow: hidden; border-radius: 20rpx; background: var(--mrc-surface-2); }
.gn__result-copy { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 4rpx; }
.gn__result-label { color: var(--mrc-text-sub); font-size: 19rpx; }
.gn__result-name { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 800; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.gn__result-btn { flex: 0 0 auto; padding: 14rpx 30rpx; border-radius: 999rpx; background: var(--mrc-primary-grad); color: #fff; font-size: 24rpx; font-weight: 800; box-shadow: var(--mrc-shadow-coral); }
.gn__result-btn:active { transform: scale(0.95); }

.gn__foot { margin-top: 30rpx; }
.gn__foot-text { color: var(--mrc-text-light); font-size: 20rpx; }

@media (prefers-reduced-motion: reduce) {
  .gn__center.is-running, .gn__cell.is-win, .gn__panel, .gn__result { animation: none; }
}
</style>
