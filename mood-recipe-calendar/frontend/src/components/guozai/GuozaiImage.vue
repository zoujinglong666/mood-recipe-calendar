<script setup lang="ts">
interface Props {
  /** 锅仔图片名，如 action_01_bowl / mood_01_happy */
  name: string
  /** 动画类型 */
  anim?: 'breathe' | 'bounce' | 'spin' | 'none'
  /** 宽度 rpx */
  width?: number
  /** 高度 rpx，默认等于 width */
  height?: number
}

const props = withDefaults(defineProps<Props>(), {
  anim: 'breathe',
  width: 200,
  height: 0,
})

const src = `/static/guozai/${props.name}.png`
const animClass = props.anim === 'none' ? '' : `guozai-${props.anim}`
const h = props.height || props.width
</script>

<template>
  <image
    class="guozai-img"
    :class="animClass"
    :src="src"
    mode="aspectFit"
    :style="{ width: width + 'rpx', height: h + 'rpx' }"
  />
</template>

<style lang="scss" scoped>
.guozai-img {
  display: block;
  pointer-events: none;
}
</style>
