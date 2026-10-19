<script setup lang="ts">
/**
 * 锅仔转盘：全屏抽奖式换菜弹层（图片扇区版）。
 *
 * 诚实转盘原则：扇区只是舞台，结果永远来自父组件真实调用
 * 「换一道」接口返回的那道菜——先请求、后定格（land() 把结果
 * 连名带图写进随机扇区并旋转到指针处），绝不假随机欺骗用户。
 *
 * 扇区图源：全库 AI 推荐菜（所有用户的记录）随机抽样，由父组件传入。
 *
 * 交互节拍：waiting（锅仔蓄力晃动）→ spinning（5 圈减速）→ landed（回弹定格）。
 */
import { computed, ref, watch } from 'vue'
import { STATIC_BASE_URL } from '@/utils/assets'

interface WheelSlot {
  name: string
  image?: string
}

const props = withDefaults(defineProps<{
  visible: boolean
  /** 扇区池：全库 AI 菜随机抽样（带图），最多 8 个，不足用彩蛋词补位 */
  items?: WheelSlot[]
}>(), {
  visible: false,
  items: () => [],
})

const emit = defineEmits<{
  (e: 'close'): void
  /** 用户拍下中心锅仔，父组件开始请求新菜 */
  (e: 'spin'): void
  /** 旋转结束，携带中奖菜名 */
  (e: 'result', name: string): void
}>()

const SEG = 8
const SEG_DEG = 360 / SEG
/** 补位彩蛋词（无图装饰扇区，永远不会成为结果） */
const DECOR: WheelSlot[] = [
  { name: '干饭大吉' }, { name: '香迷糊' }, { name: '锅气满满' }, { name: '好胃口' },
  { name: '慢慢炖' }, { name: '暖暖的' }, { name: '吃饱饱' }, { name: '锅仔拿手' },
]

const slots = ref<WheelSlot[]>(Array.from({ length: SEG }, (_, i) => ({ ...DECOR[i % DECOR.length] })))
const rotation = ref(0)
const phase = ref<'idle' | 'waiting' | 'spinning' | 'landed'>('idle')
const resultName = ref('')
const resultImage = ref('')
const celebrating = ref(false)
let celebrateTimer: ReturnType<typeof setTimeout> | undefined

watch(() => props.visible, (v) => {
  if (v) {
    phase.value = 'idle'
    resultName.value = ''
    celebrating.value = false
    if (celebrateTimer) clearTimeout(celebrateTimer)
    buildSegments()
  }
  else {
    // 关闭时清掉旋转兜底定时器，避免隐藏组件里幽灵定格并误 emit('result')
    if (landTimer) clearTimeout(landTimer)
    if (celebrateTimer) clearTimeout(celebrateTimer)
  }
})

/** 撒花粒子：从盘心向四周炸开，18 片、6 色、错峰出发 */
const CONFETTI_COLORS = ['#FF8A5C', '#FFD93D', '#7BC47F', '#6FB7FF', '#F472B6', '#FFB86C']
const confetti = computed(() => Array.from({ length: 18 }, (_, i) => {
  const angle = (i / 18) * Math.PI * 2 + (i % 3) * 0.22
  const dist = 190 + (i % 5) * 52
  return {
    x: Math.round(Math.cos(angle) * dist),
    y: Math.round(Math.sin(angle) * dist),
    rot: (i * 47) % 360,
    delay: (i % 6) * 0.05,
    color: CONFETTI_COLORS[i % CONFETTI_COLORS.length],
    size: i % 3 === 0 ? 16 : i % 3 === 1 ? 12 : 14,
  }
}))

/** 落地庆祝：撒花 2.2s + 轻震动，让「转到什么」有惊喜感 */
function celebrate() {
  celebrating.value = true
  if (celebrateTimer) clearTimeout(celebrateTimer)
  celebrateTimer = setTimeout(() => { celebrating.value = false }, 2200)
  try { uni.vibrateShort({ type: 'medium' }) } catch {}
}

function buildSegments() {
  const pool = props.items.filter(item => item?.name).slice(0, SEG)
  slots.value = Array.from({ length: SEG }, (_, i) => pool[i] || { ...DECOR[i % DECOR.length] })
}

/** 菜池异步到达时重建扇区：父组件先开弹层后请求，visible 的 watch 只会拿到空池 */
watch(() => props.items, () => {
  if (phase.value === 'idle')
    buildSegments()
})

const discBg = computed(() => {
  // 极浅暖色阶交替 + 扇区边界 1° 白分隔带：三层以内的装饰语言，不再堆叠
  const stops: string[] = []
  for (let i = 0; i < SEG; i++) {
    const color = i % 2 ? 'var(--mrc-surface-sun)' : 'var(--mrc-surface)'
    stops.push(`${color} ${SEG_DEG * i}deg ${SEG_DEG * (i + 1) - 1}deg`)
    stops.push(`#fff ${SEG_DEG * (i + 1) - 1}deg ${SEG_DEG * (i + 1)}deg`)
  }
  return `conic-gradient(${stops.join(', ')})`
})

const hint = computed(() => {
  if (phase.value === 'waiting') return '锅仔正在锅里翻找…'
  if (phase.value === 'spinning') return '转起来了——'
  if (phase.value === 'landed') return resultName.value ? `就吃「${resultName.value}」！` : '就吃它！'
  return '拍一下锅仔，转一道今天的菜'
})

const hubText = computed(() => (phase.value === 'landed' ? '再转' : '转'))

function segAngle(i: number) {
  return SEG_DEG * i + SEG_DEG / 2
}

/**
 * 菜名换行：扇区文字沿切线排布，越靠盘心可用弧长越短（8 等分、半径 140rpx
 * 处相邻扇区中心间距仅约 107rpx），单行 6 字以上必然压到隔壁扇区。
 * 平衡切分（6 字→3+3）比交给浏览器硬折行更不容易出现「孤字」；
 * 每行最多 5 字，超过 10 字截断加省略号。
 */
const MAX_CHARS_PER_LINE = 5
function nameLines(name: string): string[] {
  const chars = Array.from(name || '')
  if (chars.length <= MAX_CHARS_PER_LINE)
    return [name || '']
  const per = Math.min(MAX_CHARS_PER_LINE, Math.ceil(chars.length / 2))
  const first = chars.slice(0, per).join('')
  let second = chars.slice(per, per * 2).join('')
  if (chars.length > per * 2)
    second = `${second.slice(0, per - 1)}…`
  return [first, second]
}

/** 扇区图挂了就地清空：退化为纯文字扇区，绝不留空框破图 */
function onSegImgError(i: number) {
  const next = [...slots.value]
  next[i] = { name: next[i].name }
  slots.value = next
}

function tapHub() {
  if (phase.value !== 'idle' && phase.value !== 'landed')
    return
  phase.value = 'waiting'
  emit('spin')
}

/** 父组件拿到真实结果后调用：结果连名带图写进随机扇区，旋转 5 圈减速定格。 */
function land(name: string, image?: string) {
  if (phase.value !== 'waiting')
    return
  resultName.value = name
  resultImage.value = image || ''
  const slot = Math.floor(Math.random() * SEG)
  const next = [...slots.value]
  next[slot] = { name, image: image || next[slot].image }
  slots.value = next
  phase.value = 'spinning'
  const jitter = Math.random() * (SEG_DEG * 0.7) - (SEG_DEG * 0.35)
  const base = Math.ceil(rotation.value / 360) * 360
  rotation.value = base + 360 * 5 + (360 - segAngle(slot)) + jitter
  // transitionend 为主结束信号（onSpinEnd），定时器仅兜底防丢事件
  if (landTimer)
    clearTimeout(landTimer)
  landTimer = setTimeout(() => { if (phase.value === 'spinning') finish() }, 4800)
}

let landTimer: ReturnType<typeof setTimeout> | undefined

/** 旋转过渡结束：进入中奖定格态（NutUI-Bingo turntable 的 transitionend 方案）。 */
function onSpinEnd() {
  if (phase.value !== 'spinning')
    return
  if (landTimer)
    clearTimeout(landTimer)
  finish()
}

function finish() {
  phase.value = 'landed'
  celebrate()
  emit('result', resultName.value)
}

/** 接口失败（没有别的菜可换等）时回到待机。 */
function fail() {
  if (phase.value === 'waiting')
    phase.value = 'idle'
}

function onMask() {
  if (phase.value === 'idle' || phase.value === 'landed')
    emit('close')
}

defineExpose({ land, fail })
</script>

<template>
  <view v-if="visible" class="gw" aria-label="锅仔转盘">
    <view class="gw__mask" @click="onMask" />
    <view class="gw__panel">
      <view class="gw__close" role="button" aria-label="关闭转盘" @click="onMask">✕</view>
      <view class="gw__head">
        <text class="gw__eyebrow">GUOZAI WHEEL</text>
        <text class="gw__title">锅仔转一转</text>
        <text class="gw__sub">{{ hint }}</text>
      </view>
      <view class="gw__stage" :class="{ 'is-spinning': phase === 'spinning' || phase === 'waiting' }">
        <view class="gw__lights" aria-hidden="true">
          <view v-for="i in 12" :key="i" class="gw__light" :class="{ 'is-even': i % 2 === 0 }" :style="{ transform: `rotate(${(i - 1) * 30}deg) translateY(-316rpx)` }" />
        </view>
        <view class="gw__pointer" aria-hidden="true">
          <view class="gw__pointer-pin" />
        </view>
        <view class="gw__disc" :class="{ 'is-landed': phase === 'landed' }" :style="{ background: discBg, transform: `rotate(${rotation}deg)` }" @transitionend="onSpinEnd">
          <view v-for="(slot, i) in slots" :key="i" class="gw__seg" :style="{ transform: `rotate(${segAngle(i)}deg)` }">
            <view class="gw__seg-item" :class="{ 'is-text-only': !slot.image }">
              <view v-if="slot.image" class="gw__seg-img">
                <image :src="slot.image" mode="aspectFill" @error="onSegImgError(i)" />
              </view>
              <view class="gw__seg-text">
                <text v-for="(line, li) in nameLines(slot.name)" :key="li" class="gw__seg-line">{{ line }}</text>
              </view>
            </view>
          </view>
        </view>
        <view class="gw__hub" :class="{ 'is-waiting': phase === 'waiting' }" role="button" :aria-label="phase === 'landed' ? '再转一次' : '转一转换道菜'" @click="tapHub">
          <image class="gw__hub-img" :src="STATIC_BASE_URL + '/static/guozai/action_01_bowl.png'" mode="aspectFit" />
          <text class="gw__hub-text">{{ hubText }}</text>
        </view>
        <view v-if="celebrating" class="gw__confetti" aria-hidden="true">
          <view v-for="(p, i) in confetti" :key="i" class="gw__confetti-piece" :style="{ background: p.color, width: `${p.size}rpx`, height: `${Math.round(p.size * 0.45)}rpx`, '--cx': `${p.x}rpx`, '--cy': `${p.y}rpx`, '--cr': `${p.rot}deg`, animationDelay: `${p.delay}s` }" />
        </view>
      </view>
      <view v-if="phase === 'landed' && resultName" class="gw__result" role="status">
        <image v-if="resultImage" class="gw__result-img" :src="resultImage" mode="aspectFill" @error="resultImage = ''" />
        <view class="gw__result-copy">
          <text class="gw__result-label">🎉 撒花！转到了</text>
          <text class="gw__result-name">{{ resultName }}</text>
        </view>
        <view class="gw__result-btn" role="button" aria-label="就吃这道菜" @click="emit('close')">就吃它</view>
      </view>
      <view class="gw__foot">
        <text class="gw__foot-text">转出来的就是今天吃的，锅仔说话算数</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.gw { position: fixed; inset: 0; z-index: 1200; display: flex; align-items: center; justify-content: center; }
.gw__mask { position: absolute; inset: 0; background: rgba(43, 24, 14, 0.55); }
.gw__panel { position: relative; z-index: 1; display: flex; flex-direction: column; align-items: center; width: 690rpx; padding: 44rpx 30rpx calc(40rpx + env(safe-area-inset-bottom)); border: 2rpx solid var(--mrc-border-light); border-radius: 44rpx; background: var(--mrc-surface); box-shadow: 0 30rpx 70rpx rgba(60, 30, 14, 0.35); animation: gw-in 0.32s cubic-bezier(0.22, 0.9, 0.3, 1.2) both; }
@keyframes gw-in { from { opacity: 0; transform: scale(0.86) translateY(40rpx); } to { opacity: 1; transform: scale(1) translateY(0); } }
.gw__close { position: absolute; top: 24rpx; right: 28rpx; z-index: 5; display: flex; align-items: center; justify-content: center; width: 56rpx; height: 56rpx; border-radius: 50%; background: var(--mrc-surface-2); color: var(--mrc-text-sub); font-size: 26rpx; }
.gw__head { display: flex; flex-direction: column; align-items: center; gap: 8rpx; margin-bottom: 30rpx; }
.gw__eyebrow { color: var(--mrc-accent); font-size: 18rpx; font-weight: 800; letter-spacing: 3rpx; }
.gw__title { color: var(--mrc-text-strong); font-size: 40rpx; font-weight: 800; }
.gw__sub { min-height: 34rpx; color: var(--mrc-text-sub); font-size: 22rpx; }

.gw__stage { position: relative; width: 600rpx; height: 600rpx; }
/* 指针：单元素泪滴定位针（border-radius pin 旋转 45°，尖朝下）——
   一个元素天然对称，不再有圆头+三角双元素在真机上错位脱节的问题 */
.gw__pointer { position: absolute; z-index: 7; top: -26rpx; left: 50%; width: 64rpx; height: 64rpx; margin-left: -32rpx; pointer-events: none; }
.gw__pointer-pin { width: 64rpx; height: 64rpx; box-sizing: border-box; border: 6rpx solid #fff; border-radius: 50% 50% 50% 0; background: var(--mrc-primary-grad); transform: rotate(-45deg); box-shadow: 0 6rpx 16rpx rgba(120, 50, 20, 0.32); }
.gw__disc { position: absolute; inset: 0; border: 10rpx solid #fff; border-radius: 50%; box-shadow: 0 16rpx 40rpx rgba(88, 48, 31, 0.18), var(--mrc-gloss); transition: transform 4.2s cubic-bezier(0.16, 0.84, 0.24, 1); will-change: transform; }
.gw__disc.is-landed { animation: gw-land 0.5s ease; }
@keyframes gw-land { 0% { scale: 1; } 40% { scale: 1.04; } 100% { scale: 1; } }
/* 扇区 item：图上文下（NutUI 布局）；无图退化为纯文字，绝不留空框。
   定位关键：中心 hub 半径 96rpx（176rpx 盘 + 8rpx 白边），文字底端必须落在
   104rpx 之外，否则菜名会被 hub 盖住。
   外移 24rpx（margin-top 80→56）：文字所在半径变大，切线方向的可用弧长随之
   变长（8 等分下可用宽度≈0.765×半径），这是「菜名不再挤在一起」的主因；
   剩余空间由两行文字承担（20rpx×2 行×1.2 = 48rpx，底端落在半径 118rpx）。 */
.gw__seg { position: absolute; inset: 0; display: flex; justify-content: center; align-items: flex-start; }
.gw__seg-item { display: flex; flex-direction: column; align-items: center; gap: 6rpx; margin-top: 56rpx; width: 150rpx; }
.gw__seg-item.is-text-only { margin-top: 164rpx; }
.gw__seg-img { width: 72rpx; height: 72rpx; overflow: hidden; border-radius: 16rpx; box-shadow: 0 4rpx 10rpx rgba(88, 48, 31, 0.14); }
.gw__seg-img image { width: 100%; height: 100%; }
.gw__seg-text { display: block; max-width: 104rpx; color: var(--mrc-text-deep); font-size: 20rpx; font-weight: 700; line-height: 1.2; text-align: center; }
.gw__seg-line { display: block; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.gw__hub { position: absolute; z-index: 4; top: 50%; left: 50%; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; gap: 2rpx; width: 176rpx; height: 176rpx; padding-bottom: 16rpx; border: 8rpx solid #fff; border-radius: 50%; background: var(--mrc-primary-grad); box-shadow: 0 10rpx 26rpx rgba(200, 70, 30, 0.35); transform: translate(-50%, -50%); }
.gw__hub.is-waiting { animation: gw-shake 0.5s ease infinite; }
@keyframes gw-shake { 0%, 100% { transform: translate(-50%, -50%) rotate(0); } 30% { transform: translate(-50%, -50%) rotate(-7deg); } 70% { transform: translate(-50%, -50%) rotate(7deg); } }
.gw__hub-img { width: 72rpx; height: 72rpx; }
.gw__hub-text { color: #fff; font-size: 22rpx; font-weight: 800; }
/* 外圈灯带：12 颗暖点，待机/旋转时跑马交替闪（NutUI turntable 的灯带节奏） */
.gw__lights { position: absolute; inset: 0; z-index: 2; pointer-events: none; }
.gw__light { position: absolute; top: 50%; left: 50%; width: 12rpx; height: 12rpx; margin: -6rpx 0 0 -6rpx; border-radius: 50%; background: #FFD93D; }
.gw__light.is-even { background: #FFE9A8; }
.gw__stage.is-spinning .gw__light { animation: gw-blink 0.45s ease-in-out infinite; }
.gw__stage.is-spinning .gw__light.is-even { animation-delay: 0.22s; }
@keyframes gw-blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.25; } }

/* 撒花：从盘心向四周炸开的彩纸屑（落地瞬间一次性动画，2.2s 后整体移除） */
.gw__confetti { position: absolute; z-index: 8; top: 50%; left: 50%; pointer-events: none; }
.gw__confetti-piece { position: absolute; border-radius: 4rpx; opacity: 0; animation: gw-burst 1.5s cubic-bezier(0.16, 0.84, 0.3, 1) forwards; }
@keyframes gw-burst {
  0% { opacity: 0; transform: translate(0, 0) rotate(0deg) scale(0.6); }
  12% { opacity: 1; }
  70% { opacity: 1; }
  100% { opacity: 0; transform: translate(var(--cx), var(--cy)) rotate(var(--cr)) scale(1); }
}

/* 中奖结果条：图 + 名 + 确认 CTA */
.gw__result { display: flex; align-items: center; gap: 20rpx; width: 100%; margin-top: 30rpx; padding: 18rpx 22rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; background: var(--mrc-surface-sun); box-sizing: border-box; animation: gw-pop 0.5s cubic-bezier(0.22, 0.9, 0.3, 1.35) both; }
@keyframes gw-pop { 0% { opacity: 0; transform: translateY(24rpx) scale(0.9); } 60% { opacity: 1; transform: translateY(-4rpx) scale(1.03); } 100% { opacity: 1; transform: translateY(0) scale(1); } }
.gw__result-img { width: 84rpx; height: 84rpx; flex: 0 0 84rpx; overflow: hidden; border-radius: 20rpx; background: var(--mrc-surface-2); }
.gw__result-copy { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 4rpx; }
.gw__result-label { color: var(--mrc-text-sub); font-size: 19rpx; }
.gw__result-name { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 800; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.gw__result-btn { flex: 0 0 auto; padding: 14rpx 30rpx; border-radius: 999rpx; background: var(--mrc-primary-grad); color: #fff; font-size: 24rpx; font-weight: 800; box-shadow: var(--mrc-shadow-coral); }
.gw__result-btn:active { transform: scale(0.95); }

.gw__foot { margin-top: 30rpx; }
.gw__foot-text { color: var(--mrc-text-light); font-size: 20rpx; }

@media (prefers-reduced-motion: reduce) {
  .gw__disc { transition-duration: 0.6s; }
  .gw__hub.is-waiting, .gw__disc.is-landed, .gw__panel, .gw__result, .gw__stage.is-spinning .gw__light, .gw__confetti-piece { animation: none; }
  .gw__confetti-piece { opacity: 0; }
}
</style>
