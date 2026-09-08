<script setup lang="ts">
/**
 * MoodPicker 心情选择器（公共组件）
 * - 12 个锅仔 IP 情绪表情（大图展示，可看清细节）
 * - 顶部大锅仔联动：随选中/悬停实时切换表情
 * - 卡片选中弹跳 + 光晕动画 + 震动反馈
 * - confirmText 提供时：选中后弹出底部确认条，点击确认后 emit confirm
 */
import { computed, ref } from 'vue'

interface MoodOption {
  key: string
  img: string
  tip: string
}

const MOOD_OPTIONS: MoodOption[] = [
  { key: '开心', img: '/static/guozai/mood_01_happy.png', tip: '今天也要闪闪发光呀！' },
  { key: '平静', img: '/static/guozai/mood_02_calm.png', tip: '稳稳的，就是幸福。' },
  { key: '疲惫', img: '/static/guozai/mood_03_tired.png', tip: '累了就歇会儿，我陪你。' },
  { key: '焦虑', img: '/static/guozai/mood_04_anxious.png', tip: '慢慢来，锅仔在呢。' },
  { key: '难过', img: '/static/guozai/mood_05_sad.png', tip: '别难过，有我在。' },
  { key: '嘴馋', img: '/static/guozai/mood_06_hungry.png', tip: '走！咱去吃点好的。' },
  { key: '低落', img: '/static/guozai/mood_07_low.png', tip: '抱抱你，天会亮的。' },
  { key: '想家', img: '/static/guozai/mood_08_homesick.png', tip: '家的味道，最暖。' },
  { key: '期待', img: '/static/guozai/mood_09_excited.png', tip: '前方有好事发生！' },
  { key: '满足', img: '/static/guozai/mood_10_content.png', tip: '这样刚刚好。' },
  { key: '得意', img: '/static/guozai/mood_11_proud.png', tip: '我可太厉害了！' },
  { key: '害羞', img: '/static/guozai/mood_12_shy.png', tip: '被你发现啦～' },
]

const props = withDefaults(
  defineProps<{
    /** v-model：当前选中心情 key */
    modelValue?: string
    /** 是否显示顶部大锅仔联动区（默认 true） */
    showHero?: boolean
    /** 确认按钮文案；提供后显示底部确认条 */
    confirmText?: string
    /** hero 高度（rpx），默认 300 */
    heroHeight?: number
    /** 标题（hero 上方），默认「今天的心情」 */
    title?: string
  }>(),
  { modelValue: '', showHero: true, confirmText: '', heroHeight: 300, title: '今天的心情' },
)

const emit = defineEmits<{
  (e: 'update:modelValue', key: string): void
  (e: 'confirm', mood: MoodOption): void
}>()

const selected = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

// 悬停/选中联动大锅仔
const preview = ref('')
const shownMood = computed(() => {
  const key = preview.value || selected.value
  return MOOD_OPTIONS.find((m) => m.key === key)
})

const heroImg = computed(() => shownMood.value?.img || MOOD_OPTIONS[0].img)
const heroTip = computed(() => shownMood.value?.tip || MOOD_OPTIONS[0].tip)

function pick(key: string) {
  selected.value = key
  preview.value = key
  try { uni.vibrateShort({ type: 'light' }) } catch {}
}

function onCardTap(key: string) {
  pick(key)
  // 无确认条时点击即完成选择
  if (!props.confirmText) return
  // 有确认条：保持选中态，等待用户点确认
}

function onConfirm() {
  const mood = MOOD_OPTIONS.find((m) => m.key === selected.value)
  if (mood) emit('confirm', mood)
}

function heroTap() {
  // 点击大锅仔：若已选中则触发确认（仅确认模式）
  if (props.confirmText && selected.value) onConfirm()
}
</script>

<template>
  <view class="mood-picker">
    <view class="mood-picker__heading">
      <view class="mood-picker__heading-copy">
        <text class="mood-picker__eyebrow">锅仔听着呢</text>
        <text class="mood-picker__title">{{ title }}</text>
      </view>
      <view class="mood-picker__current" :class="{ 'mood-picker__current--active': selected }">
        {{ selected || '未选择' }}
      </view>
    </view>

    <!-- 顶部大锅仔联动区 -->
    <view v-if="showHero" class="mood-picker__hero" :style="{ height: heroHeight + 'rpx' }" @click="heroTap">
      <image
        class="mood-picker__hero-img guozai-breathe"
        :src="heroImg"
        mode="aspectFit"
        :style="{ height: heroHeight * 0.78 + 'rpx', width: heroHeight * 0.78 + 'rpx' }"
      />
      <view class="mood-picker__hero-bubble">
        <text class="mood-picker__hero-mood">{{ shownMood?.key || '开心' }}</text>
        <text class="mood-picker__hero-tip">{{ heroTip }}</text>
      </view>
    </view>

    <!-- 12 心情大卡片 3×4 -->
    <view class="mood-picker__grid">
      <view
        v-for="m in MOOD_OPTIONS"
        :key="m.key"
        class="mood-picker__card"
        :class="{
          'mood-picker__card--active': selected === m.key,
          'mood-picker__card--pop': selected === m.key,
        }"
        hover-class="mood-picker__card--hover"
        :hover-stay-time="80"
        role="button"
        :aria-label="`选择心情：${m.key}${selected === m.key ? '，已选择' : ''}`"
        @click="onCardTap(m.key)"
      >
        <image class="mood-picker__card-icon" :src="m.img" mode="aspectFit" />
        <text class="mood-picker__card-label">{{ m.key }}</text>
        <view v-if="selected === m.key" class="mood-picker__card-check">✓</view>
      </view>
    </view>

    <!-- 底部确认条（confirmText 提供时显示） -->
    <view v-if="confirmText && selected" class="mood-picker__bar">
      <image v-if="shownMood" class="mood-picker__bar-img" :src="shownMood.img" mode="aspectFit" />
      <text class="mood-picker__bar-txt">{{ shownMood?.key }} · {{ heroTip }}</text>
      <view class="mood-picker__bar-btn" role="button" :aria-label="`${confirmText}：${selected}`" @click="onConfirm">{{ confirmText }}</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.mood-picker {
  width: 100%;
}

/* 标题 */
.mood-picker__heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24rpx;
  margin-bottom: 20rpx;
}
.mood-picker__heading-copy {
  min-width: 0;
}
.mood-picker__eyebrow {
  display: block;
  margin-bottom: 6rpx;
  color: var(--mrc-accent);
  font-size: 20rpx;
  font-weight: 800;
  letter-spacing: 2rpx;
}
.mood-picker__title {
  display: block;
  color: var(--mrc-text-strong);
  font-size: 36rpx;
  font-weight: var(--mrc-fw-heavy);
  line-height: 1.25;
}
.mood-picker__current {
  min-width: 104rpx;
  padding: 12rpx 18rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 28rpx;
  background: var(--mrc-surface-2);
  color: var(--mrc-text-sub);
  font-size: 22rpx;
  font-weight: 700;
  text-align: center;
}
.mood-picker__current--active {
  border-color: var(--mrc-primary);
  background: var(--mrc-accent-soft);
  color: var(--mrc-accent);
}

/* 大锅仔联动区 */
.mood-picker__hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 40rpx;
  background: radial-gradient(circle at 82% 8%, rgba(255, 197, 61, 0.28), transparent 30%), linear-gradient(145deg, var(--mrc-surface), var(--mrc-surface-peach));
  box-shadow: var(--mrc-shadow-lift), var(--mrc-gloss);
  margin-bottom: 28rpx;
  overflow: hidden;
}
.mood-picker__hero-img {
  margin-right: 24rpx;
}
.mood-picker__hero-bubble {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  max-width: 44%;
  padding: 24rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 26rpx 26rpx 26rpx 8rpx;
  background: var(--mrc-surface);
  box-shadow: var(--mrc-shadow-sm);
}
.mood-picker__hero-mood {
  font-size: 40rpx;
  font-weight: 800;
  color: var(--mrc-text-deep);
}
.mood-picker__hero-tip {
  font-size: 24rpx;
  color: var(--mrc-text-sub);
  line-height: 1.5;
}

/* 12 心情网格 4×3，缩短选择路径 */
.mood-picker__grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14rpx;
}
.mood-picker__card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  min-height: 156rpx;
  padding: 12rpx 4rpx;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 24rpx;
  box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss);
  transition: transform 0.18s ease, background 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}
.mood-picker__card--hover {
  transform: scale(0.96);
}
.mood-picker__card--active {
  background: var(--mrc-surface-peach);
  border-color: var(--mrc-primary-deep);
  box-shadow: 0 8rpx 20rpx rgba(239, 90, 60, 0.18), inset 0 0 0 2rpx var(--mrc-primary);
}
/* 选中弹跳 */
@keyframes mood-pop {
  0% { transform: scale(0.88); }
  50% { transform: scale(1.1); }
  100% { transform: scale(1); }
}
.mood-picker__card--pop {
  animation: mood-pop 0.35s ease;
}
.mood-picker__card-icon {
  width: 84rpx;
  height: 84rpx;
}
.mood-picker__card-label {
  font-size: 24rpx;
  color: var(--mrc-text-deep);
  font-weight: 600;
  letter-spacing: 1rpx;
}
.mood-picker__card--active .mood-picker__card-label {
  color: var(--mrc-accent);
  font-weight: 800;
}
.mood-picker__card-check {
  position: absolute;
  top: 10rpx;
  right: 12rpx;
  width: 34rpx;
  height: 34rpx;
  border-radius: 50%;
  background: var(--mrc-primary-deep);
  color: #fff;
  font-size: 22rpx;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 底部确认条 */
.mood-picker__bar {
  position: fixed;
  left: 32rpx;
  right: 32rpx;
  bottom: calc(24rpx + env(safe-area-inset-bottom));
  z-index: 50;
  display: flex;
  align-items: center;
  gap: 16rpx;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 40rpx;
  padding: 16rpx 20rpx;
  box-shadow: 0 8rpx 28rpx rgba(90, 62, 43, 0.16);
  animation: mood-bar-in 0.28s ease;
}
@keyframes mood-bar-in {
  from { transform: translateY(40rpx); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
.mood-picker__bar-img {
  width: 88rpx;
  height: 88rpx;
}
.mood-picker__bar-txt {
  flex: 1;
  font-size: 28rpx;
  color: var(--mrc-text-deep);
  font-weight: 600;
}
.mood-picker__bar-btn {
  min-height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--mrc-primary-grad);
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
  padding: 20rpx 36rpx;
  border-radius: 40rpx;
  box-shadow: 0 6rpx 16rpx rgba(253, 145, 132, 0.35);
}
.mood-picker__bar-btn:active {
  transform: scale(0.95);
}

@media (prefers-reduced-motion: reduce) {
  .mood-picker__card,
  .mood-picker__card--pop,
  .mood-picker__bar { animation: none; transition: none; }
}
</style>
