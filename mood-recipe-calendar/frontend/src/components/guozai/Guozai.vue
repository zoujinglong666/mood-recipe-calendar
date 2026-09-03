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
/* 落地柔影：让锅仔"站"在页面上 */
.gz::after {
  content: '';
  position: absolute;
  left: 18%;
  right: 18%;
  bottom: 1%;
  height: 5%;
  background: rgba(120, 70, 45, 0.18);
  border-radius: 50%;
  z-index: 0;
}

/* ---- 锅身（带体积渐变 + 珊瑚描边 + 底部红边） ---- */
.gz-body {
  position: absolute;
  left: 11%;
  top: 21%;
  width: 78%;
  height: 67%;
  background: linear-gradient(165deg, #ffffff 0%, #fff1e6 100%);
  border: 5rpx solid #ff7a5c;
  border-radius: 50% 50% 46% 46% / 42% 42% 54% 54%;
  box-shadow: 0 8rpx 0 rgba(255, 122, 92, 0.18), var(--mrc-shadow-soft);
  overflow: hidden;
  box-sizing: border-box;
  z-index: 1;
}
.gz-body::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 15%;
  background: #ff7a5c;
  border-radius: 0 0 46% 46% / 0 0 54% 54%;
  opacity: 0.92;
}

/* ---- 锅盖（带高光） ---- */
.gz-lid {
  position: absolute;
  left: 11%;
  top: 7%;
  width: 78%;
  height: 23%;
  background: linear-gradient(165deg, #fff7f0, #ffe7d6);
  border: 5rpx solid #ff7a5c;
  border-radius: 50%;
  box-sizing: border-box;
  z-index: 2;
}
.gz-lid::after {
  content: '';
  position: absolute;
  left: 16%;
  top: 14%;
  width: 42%;
  height: 32%;
  background: rgba(255, 255, 255, 0.85);
  border-radius: 50%;
}
.gz-lid-knob {
  position: absolute;
  left: 50%;
  top: -42%;
  transform: translateX(-50%);
  width: 20%;
  height: 42%;
  background: #ff7a5c;
  border-radius: 50%;
}

/* ---- 心形标记 ---- */
.gz-heart {
  position: absolute;
  left: 50%;
  bottom: 17%;
  transform: translateX(-50%);
  width: 17%;
  height: 15%;
  background: var(--mrc-accent, #ef5a3c);
  clip-path: polygon(50% 100%, 0 40%, 0 30%, 20% 12%, 50% 28%, 80% 12%, 100% 30%, 100% 40%);
  transition: all 0.3s;
}

/* ---- 眼睛（大而亮，带高光点） ---- */
.gz-eye {
  position: absolute;
  top: 33%;
  width: 13%;
  height: 19%;
  background: #3a2418;
  border-radius: 50%;
  z-index: 2;
}
.gz-eye::after {
  content: '';
  position: absolute;
  top: 16%;
  left: 22%;
  width: 40%;
  height: 40%;
  background: #fff;
  border-radius: 50%;
}
.gz-eye--l {
  left: 25%;
}
.gz-eye--r {
  right: 25%;
}

/* ---- 腮红 ---- */
.gz-cheek {
  position: absolute;
  top: 53%;
  width: 17%;
  height: 11%;
  background: #ff9db6;
  border-radius: 50%;
  opacity: 0.9;
  z-index: 2;
}
.gz-cheek--l {
  left: 7%;
}
.gz-cheek--r {
  right: 7%;
}

/* ---- 嘴（默认：张开的小笑嘴） ---- */
.gz-mouth {
  position: absolute;
  left: 50%;
  bottom: 31%;
  transform: translateX(-50%);
  width: 15%;
  height: 9%;
  background: #ff7a5c;
  border-radius: 0 0 60% 60%;
  z-index: 2;
}

/* ---- 小手 ---- */
.gz-hand {
  position: absolute;
  top: 52%;
  width: 15%;
  height: 15%;
  background: #fffcf7;
  border: 5rpx solid #ff7a5c;
  border-radius: 50%;
  z-index: 3;
}
.gz-hand--l {
  left: -1%;
}
.gz-hand--r {
  right: -1%;
}

/* ---- 气泡 ---- */
.gz-bubble {
  position: absolute;
  top: -36%;
  left: 50%;
  transform: translateX(-50%);
  background: #fffcf7;
  border: 3rpx solid var(--mrc-border, #ead2b6);
  border-radius: 24rpx;
  padding: 10rpx 22rpx;
  font-size: 22rpx;
  color: var(--mrc-text-deep, #3d2519);
  white-space: nowrap;
  box-shadow: var(--mrc-shadow-sm);
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
  border-top: 12rpx solid #fffcf7;
}

/* ---- 头顶装饰 ---- */
.gz-deco {
  position: absolute;
  top: -4%;
  right: 0;
  z-index: 4;
  font-size: 34rpx;
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

/* 开心：月牙眼 + 大笑 + 亮腮红 */
.gz--happy .gz-eye {
  height: 9%;
  top: 38%;
  border-radius: 0 0 100% 100% / 0 0 100% 100%;
}
.gz--happy .gz-eye::after {
  display: none;
}
.gz--happy .gz-mouth {
  width: 21%;
  height: 11%;
  background: #ff7a5c;
  border-radius: 0 0 60% 60%;
}
.gz--happy .gz-cheek {
  background: #ff8fb0;
  opacity: 1;
}

/* 平静：圆眼微笑 */
.gz--calm .gz-eye {
  width: 12%;
  height: 16%;
}
.gz--calm .gz-mouth {
  width: 13%;
  height: 8%;
}

/* 疲惫：半闭眼 + 淡腮红 + 小嘴 */
.gz--tired .gz-eye {
  height: 5%;
  top: 40%;
  border-radius: 6rpx;
  background: #6b4a37;
}
.gz--tired .gz-eye::after {
  display: none;
}
.gz--tired .gz-cheek {
  opacity: 0.4;
}
.gz--tired .gz-mouth {
  width: 11%;
  height: 6%;
  background: #ff7a5c;
  border-radius: 0 0 50% 50%;
}

/* 焦虑：瞪大眼 + 波浪嘴 */
.gz--anxious .gz-eye {
  width: 15%;
  height: 22%;
  background: #2c1810;
}
.gz--anxious .gz-mouth {
  width: 17%;
  height: 7%;
  background: transparent;
  border-bottom: 4rpx solid #5a3e2b;
  border-radius: 0;
}

/* 难过：下垂眼 + 下弯嘴 + 泪 */
.gz--sad .gz-eye {
  height: 9%;
  background: #5a3e2b;
}
.gz--sad .gz-eye--l {
  transform: rotate(16deg);
}
.gz--sad .gz-eye--r {
  transform: rotate(-16deg);
}
.gz--sad .gz-eye::after {
  opacity: 0.5;
}
.gz--sad .gz-mouth {
  width: 15%;
  height: 9%;
  background: transparent;
  border-top: 5rpx solid #5a3e2b;
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

/* 嘴馋：亮黄眼 + 大笑 + 亮腮红 */
.gz--hungry .gz-eye {
  background: #ffc53d;
  border: 2rpx solid #3a2418;
}
.gz--hungry .gz-eye::after {
  background: #fff;
}
.gz--hungry .gz-mouth {
  width: 20%;
  height: 12%;
  background: #ff7a5c;
  border-radius: 0 0 60% 60%;
}
.gz--hungry .gz-cheek {
  background: #ff8fb0;
  opacity: 1;
}

/* 低落：耷拉灰眼 + 平嘴 */
.gz--low .gz-eye {
  height: 6%;
  top: 40%;
  border-radius: 6rpx;
  background: #9aa0ad;
}
.gz--low .gz-eye::after {
  display: none;
}
.gz--low .gz-mouth {
  width: 13%;
  height: 4%;
  background: transparent;
  border-bottom: 4rpx solid #9aa0ad;
  border-radius: 0;
}
.gz--low .gz-cheek {
  opacity: 0.2;
}

/* 想家：心形发暖光 + 温柔眼 */
.gz--homesick .gz-eye {
  width: 12%;
  height: 16%;
  background: #4a3628;
}
.gz--homesick .gz-heart {
  background: #ffb366;
  box-shadow: 0 0 14rpx 5rpx rgba(255, 179, 102, 0.6);
}
.gz--homesick .gz-mouth {
  width: 13%;
  height: 8%;
}
</style>
