<template>
  <view
    class="gz"
    :class="['gz--' + moodClass]"
    :style="{ width: size + 'rpx', height: size + 'rpx' }"
  >
    <!-- 头顶装饰：蒸汽 / 星星 / 泪 / 问号 / Zzz -->
    <view v-if="deco" class="gz-deco">
      <text class="gz-deco-text">{{ deco }}</text>
    </view>

    <!-- 锅盖 -->
    <view class="gz-lid">
      <view class="gz-lid-knob" />
    </view>

    <!-- 锅身 -->
    <view class="gz-body">
      <!-- 心形标记（随心情变色） -->
      <view class="gz-heart" />

      <!-- 眼睛 -->
      <view class="gz-eye gz-eye--l" />
      <view class="gz-eye gz-eye--r" />

      <!-- 腮红 -->
      <view class="gz-cheek gz-cheek--l" />
      <view class="gz-cheek gz-cheek--r" />

      <!-- 嘴 -->
      <view class="gz-mouth" />

      <!-- 泪 / 口水 / 汗 等小装饰 -->
      <view v-if="mood === '难过'" class="gz-tear gz-tear--l" />
      <view v-if="mood === '难过'" class="gz-tear gz-tear--r" />
    </view>

    <!-- 小手 -->
    <view class="gz-hand gz-hand--l" />
    <view class="gz-hand gz-hand--r" />

    <!-- 气泡文案 -->
    <view v-if="bubble" class="gz-bubble">
      <text>{{ bubble }}</text>
      <view class="gz-bubble-tail" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 心情：开心/平静/疲惫/焦虑/难过/嘴馋/低落/想家 */
    mood?: string
    /** 尺寸 rpx */
    size?: number
    /** 气泡文案（为空则不显示） */
    bubble?: string
  }>(),
  { mood: '开心', size: 240, bubble: '' },
)

const MOOD_DECO: Record<string, string> = {
  开心: '✨',
  平静: '',
  疲惫: '💨',
  焦虑: '💢',
  难过: '',
  嘴馋: '',
  低落: '',
  想家: '💛',
}

const deco = computed(() => MOOD_DECO[props.mood] ?? '')

/** 心情中文 -> 英文类名后缀（WXSS 不支持中文选择器） */
const MOOD_CLASS: Record<string, string> = {
  开心: 'happy',
  平静: 'calm',
  疲惫: 'tired',
  焦虑: 'anxious',
  难过: 'sad',
  嘴馋: 'hungry',
  低落: 'low',
  想家: 'homesick',
}
const moodClass = computed(() => MOOD_CLASS[props.mood] ?? props.mood)
</script>

<style lang="scss" scoped>
.gz {
  position: relative;
  display: inline-block;
}

/* ---- 锅身 ---- */
.gz-body {
  position: absolute;
  left: 12%;
  top: 22%;
  width: 76%;
  height: 66%;
  background: #ffffff;
  border: 4rpx solid #ff8b6a;
  border-radius: 50% 50% 46% 46% / 42% 42% 52% 52%;
  box-shadow: 0 6rpx 0 rgba(233, 137, 91, 0.25);
  overflow: hidden;
  box-sizing: border-box;
}

/* 锅沿红色描边加重（底部红边） */
.gz-body::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 14%;
  background: #ff8b6a;
  border-radius: 0 0 46% 46% / 0 0 52% 52%;
  opacity: 0.9;
}

/* ---- 锅盖 ---- */
.gz-lid {
  position: absolute;
  left: 12%;
  top: 8%;
  width: 76%;
  height: 22%;
  background: #fff6ef;
  border: 4rpx solid #ff8b6a;
  border-radius: 50%;
  box-sizing: border-box;
  z-index: 2;
}
.gz-lid-knob {
  position: absolute;
  left: 50%;
  top: -40%;
  transform: translateX(-50%);
  width: 18%;
  height: 40%;
  background: #ff8b6a;
  border-radius: 50%;
}

/* ---- 心形标记 ---- */
.gz-heart {
  position: absolute;
  left: 50%;
  bottom: 16%;
  transform: translateX(-50%);
  width: 16%;
  height: 14%;
  background: #ff5a5a;
  transform-origin: center;
  clip-path: polygon(50% 100%, 0 40%, 0 30%, 20% 12%, 50% 28%, 80% 12%, 100% 30%, 100% 40%);
  transition: all 0.3s;
}

/* ---- 眼睛 ---- */
.gz-eye {
  position: absolute;
  top: 34%;
  width: 9%;
  height: 14%;
  background: #5a3e2b;
  border-radius: 50%;
}
.gz-eye--l {
  left: 26%;
}
.gz-eye--r {
  right: 26%;
}

/* ---- 腮红 ---- */
.gz-cheek {
  position: absolute;
  top: 56%;
  width: 16%;
  height: 9%;
  background: #ffc4d0;
  border-radius: 50%;
  opacity: 0.85;
}
.gz-cheek--l {
  left: 8%;
}
.gz-cheek--r {
  right: 8%;
}

/* ---- 嘴 ---- */
.gz-mouth {
  position: absolute;
  left: 50%;
  bottom: 34%;
  transform: translateX(-50%);
  width: 18%;
  height: 10%;
  border-bottom: 4rpx solid #5a3e2b;
  border-radius: 0 0 50% 50%;
}

/* ---- 小手 ---- */
.gz-hand {
  position: absolute;
  top: 52%;
  width: 16%;
  height: 16%;
  background: #ffffff;
  border: 4rpx solid #ff8b6a;
  border-radius: 50%;
  z-index: 3;
}
.gz-hand--l {
  left: -2%;
}
.gz-hand--r {
  right: -2%;
}

/* ---- 气泡 ---- */
.gz-bubble {
  position: absolute;
  top: -38%;
  left: 50%;
  transform: translateX(-50%);
  background: #ffffff;
  border: 3rpx solid var(--mrc-border, #edd4c0);
  border-radius: 24rpx;
  padding: 10rpx 22rpx;
  font-size: 22rpx;
  color: var(--mrc-text, #5a3e2b);
  white-space: nowrap;
  box-shadow: 0 4rpx 12rpx rgba(232, 131, 107, 0.12);
  z-index: 5;
}
.gz-bubble-tail {
  position: absolute;
  bottom: -12rpx;
  left: 50%;
  transform: translateX(-50%);
  width: 0;
  height: 0;
  border-left: 12rpx solid transparent;
  border-right: 12rpx solid transparent;
  border-top: 12rpx solid #ffffff;
}

/* ---- 头顶装饰 ---- */
.gz-deco {
  position: absolute;
  top: -6%;
  right: 2%;
  z-index: 4;
  font-size: 30rpx;
  animation: gz-float 2s ease-in-out infinite;
}
.gz-deco-text {
  display: block;
}

@keyframes gz-float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8rpx);
  }
}

/* ================= 8 种心情差异 ================= */

/* 开心：月牙眼 + 大笑 */
.gz--happy .gz-eye {
  height: 8%;
  border-radius: 0 0 100% 100%;
}
.gz--happy .gz-mouth {
  width: 24%;
  height: 12%;
  border-bottom: 5rpx solid #5a3e2b;
  border-radius: 0 0 60% 60%;
}
.gz--happy .gz-cheek {
  background: #ff9eb8;
  opacity: 1;
}

/* 平静：圆眼微笑 */
.gz--calm .gz-eye {
  width: 8%;
  height: 12%;
}
.gz--calm .gz-mouth {
  width: 14%;
  height: 8%;
}

/* 疲惫：半闭眼 + 淡腮红 */
.gz--tired .gz-eye {
  height: 4%;
  border-radius: 4rpx;
  background: #8b6b55;
}
.gz--tired .gz-cheek {
  opacity: 0.35;
}
.gz--tired .gz-mouth {
  width: 12%;
  height: 6%;
  border-bottom: 3rpx solid #8b6b55;
}

/* 焦虑：瞪大眼 + 波浪嘴 */
.gz--anxious .gz-eye {
  width: 12%;
  height: 18%;
  background: #3f2c1e;
}
.gz--anxious .gz-mouth {
  width: 22%;
  height: 8%;
  border: none;
  border-bottom: 3rpx dashed #5a3e2b;
  border-radius: 0;
}

/* 难过：下垂眼 + 下弯嘴 */
.gz--sad .gz-eye {
  height: 6%;
  border-radius: 50%;
  background: #6b5645;
}
.gz--sad .gz-eye--l {
  transform: rotate(18deg);
}
.gz--sad .gz-eye--r {
  transform: rotate(-18deg);
}
.gz--sad .gz-mouth {
  width: 16%;
  height: 10%;
  border: none;
  border-top: 4rpx solid #5a3e2b;
  border-radius: 50% 50% 0 0;
}
.gz--sad .gz-cheek {
  opacity: 0.3;
}
.gz-tear {
  position: absolute;
  top: 42%;
  width: 5%;
  height: 12%;
  background: #9cc6e8;
  border-radius: 50%;
  animation: gz-tear-fall 1.6s ease-in infinite;
}
.gz-tear--l {
  left: 14%;
}
.gz-tear--r {
  right: 14%;
}
@keyframes gz-tear-fall {
  0% {
    transform: translateY(0);
    opacity: 1;
  }
  80% {
    transform: translateY(140%);
    opacity: 0.6;
  }
  100% {
    opacity: 0;
  }
}

/* 嘴馋：亮眼 + 流口水 */
.gz--hungry .gz-eye {
  background: #ffd93d;
  border: 2rpx solid #5a3e2b;
}
.gz--hungry .gz-mouth {
  width: 20%;
  height: 10%;
  border-bottom: 5rpx solid #ff8b6a;
}
.gz--hungry .gz-cheek {
  background: #ff9eb8;
  opacity: 1;
}

/* 低落：耷拉眼 + 平嘴 */
.gz--low .gz-eye {
  height: 5%;
  border-radius: 4rpx;
  background: #9aa0ad;
}
.gz--low .gz-eye--l {
  transform: rotate(12deg);
}
.gz--low .gz-eye--r {
  transform: rotate(-12deg);
}
.gz--low .gz-mouth {
  width: 14%;
  height: 4%;
  border: none;
  border-bottom: 3rpx solid #9aa0ad;
  border-radius: 0;
}
.gz--low .gz-cheek {
  opacity: 0.2;
}

/* 想家：心形发暖光 + 温柔眼 */
.gz--homesick .gz-eye {
  width: 8%;
  height: 12%;
  background: #4a3628;
}
.gz--homesick .gz-heart {
  background: #ffb366;
  box-shadow: 0 0 12rpx 4rpx rgba(255, 179, 102, 0.6);
}
.gz--homesick .gz-mouth {
  width: 14%;
  height: 7%;
}
</style>
