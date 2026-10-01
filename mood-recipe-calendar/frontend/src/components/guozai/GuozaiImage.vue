<template>
  <image
    v-if="displaySrc"
    class="guozai-image"
    :src="displaySrc"
    :mode="mode"
    :aria-label="ariaLabel"
    role="img"
    @error="onError"
    @load="onLoad"
    @click="emit('click', $event)"
  />
  <view
    v-else
    class="guozai-image guozai-image--placeholder"
    :class="placeholderClass"
    :aria-label="ariaLabel"
    role="img"
    @click="emit('click', $event)"
  >
    <text class="guozai-image__emoji">{{ placeholderText }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

/**
 * 统一图片组件：多级回退，专治裂图。
 *
 * 回退链：src → fallbacks[0] → … → 占位块（纯 CSS，不依赖任何图片资源，永不错位）。
 * 适用场景：后端返回的可变图片（菜谱图 / 分享卡图 / 日历轮播图）加载失败时，
 * 依次尝试备用地址，全部失败则展示暖色占位块，避免空白裂图破坏版面。
 *
 * 对标 FridgeApp 的 imageResolver 5 级回退，这里用「组件内置回退」实现，
 * 调用方只需把 <image :src> 换成 <guozai-image :src>，无需改动业务逻辑。
 */
defineOptions({ inheritAttrs: false })

const props = withDefaults(defineProps<{
  /** 主图地址，可为空或加载失败 */
  src?: string
  /** 回退地址链：主图失败依次尝试，仍失败则显示占位块 */
  fallbacks?: string[]
  /** 图片裁剪模式，默认 aspectFill */
  mode?: 'scaleToFill' | 'aspectFit' | 'aspectFill' | 'widthFix' | 'heightFix' | 'top' | 'bottom' | 'center' | 'left' | 'right'
  /** 占位块内 emoji/文字，不依赖任何图片资源，永不错位 */
  placeholderText?: string
  /** 占位块额外样式类（用于继承外部尺寸/圆角） */
  placeholderClass?: string
  /** 无障碍标签 */
  ariaLabel?: string
}>(), {
  src: '',
  fallbacks: () => [],
  mode: 'aspectFill',
  placeholderText: '🍲',
  placeholderClass: '',
  ariaLabel: '',
})

const emit = defineEmits<{ click: [event: any] }>()

const index = ref(0)

const chain = computed(() =>
  [props.src, ...props.fallbacks].filter((s): s is string => !!s && s.trim().length > 0),
)

const displaySrc = computed(() =>
  index.value < chain.value.length ? chain.value[index.value] : '',
)

function onError() {
  if (index.value < chain.value.length - 1) {
    index.value += 1
  }
  else {
    index.value = chain.value.length // 越界 → 展示占位块
  }
}

function onLoad() {
  // 加载成功，无需处理
}

watch(() => props.src, () => {
  index.value = 0
})
</script>

<style scoped>
.guozai-image {
  width: 100%;
  height: 100%;
}
.guozai-image--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--mrc-surface, #F4ECE2);
  border-radius: 16rpx;
}
.guozai-image__emoji {
  font-size: 56rpx;
  opacity: 0.5;
}
</style>
