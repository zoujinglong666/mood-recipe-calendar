<script setup lang="ts">
import { ref } from 'vue'
import AppNav from '../../components/common/AppNav.vue'

definePage({
  name: 'mood',
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '选一个心情吧',
  },
})

const router = useRouter()

const MOODS = [
  { key: '开心', img: '/static/guozai/mood_01_happy.png' },
  { key: '平静', img: '/static/guozai/mood_02_calm.png' },
  { key: '疲惫', img: '/static/guozai/mood_03_tired.png' },
  { key: '焦虑', img: '/static/guozai/mood_04_anxious.png' },
  { key: '难过', img: '/static/guozai/mood_05_sad.png' },
  { key: '嘴馋', img: '/static/guozai/mood_06_hungry.png' },
  { key: '低落', img: '/static/guozai/mood_07_low.png' },
  { key: '想家', img: '/static/guozai/mood_08_homesick.png' },
]

const selected = ref('')
const heroImg = ref('/static/guozai/mood_01_happy.png')

function pickMood(key: string) {
  selected.value = key
  // 大锅仔同步变换表情
  const mood = MOODS.find(m => m.key === key)
  if (mood) heroImg.value = mood.img
  // 震动反馈
  try { uni.vibrateShort({ type: 'light' }) } catch {}
  // 短暂展示选中态后跳转
  setTimeout(() => {
    router.push({ name: 'recipe', query: { mood: key } })
  }, 350)
}
</script>

<template>
  <view class="mood-page">
    <AppNav title="选一个心情吧" show-back />

    <!-- 大锅仔 -->
    <view class="mood-hero">
      <image class="mood-hero__img guozai-breathe" :src="heroImg" mode="aspectFit" />
    </view>

    <!-- 副标题 -->
    <text class="mood-subtitle">选一个心情吧，我懂你。</text>

    <!-- 8 心情卡片 2×4 -->
    <view class="mood-grid">
      <view
        v-for="m in MOODS"
        :key="m.key"
        class="mood-card"
        :class="{ 'mood-card--active': selected === m.key }"
        @click="pickMood(m.key)"
      >
        <image class="mood-card__icon" :src="m.img" mode="aspectFit" />
        <text class="mood-card__label">{{ m.key }}</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.mood-page {
  min-height: 100vh;
  box-sizing: border-box;
  background: var(--mrc-bg);
  padding: 0 32rpx 60rpx;
}

/* 大锅仔 */
.mood-hero {
  position: relative;
  display: flex;
  justify-content: center;
  height: 294rpx;
  margin: 12rpx 0 16rpx;
  overflow: hidden;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 36rpx;
  background: radial-gradient(circle at 75% 20%, rgba(255, 197, 61, 0.28), transparent 24%), var(--mrc-surface-peach);
  box-shadow: var(--mrc-shadow-soft);
}
.mood-hero__img {
  width: 330rpx;
  height: 330rpx;
  margin-top: 12rpx;
}

/* 副标题 */
.mood-subtitle {
  display: block;
  text-align: center;
  font-size: 27rpx;
  color: var(--mrc-text-sub);
  margin-bottom: 32rpx;
  letter-spacing: 1rpx;
}

/* 心情网格 */
.mood-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16rpx;
}

/* 心情卡片 */
.mood-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  min-height: 154rpx;
  padding: 18rpx 0;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 24rpx;
  box-shadow: var(--mrc-shadow-sm);
  transition: transform 0.2s ease, background 0.2s ease;
}
.mood-card:active {
  transform: scale(0.94);
}
.mood-card--active {
  background: var(--mrc-primary-grad);
  border-color: transparent;
  transform: scale(1.05);
  box-shadow: var(--mrc-shadow-coral);
}
.mood-card__icon {
  width: 82rpx;
  height: 82rpx;
}
.mood-card__label {
  font-size: 28rpx;
  color: var(--mrc-text-deep);
  font-weight: 500;
  letter-spacing: 1rpx;
}
.mood-card--active .mood-card__label {
  color: #fff;
  font-weight: 700;
}
</style>
