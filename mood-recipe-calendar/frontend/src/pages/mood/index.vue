<script setup lang="ts">
import { ref } from 'vue'
import AppNav from '../../components/common/AppNav.vue'
import MoodPicker from '../../components/guozai/MoodPicker.vue'

definePage({
  name: 'mood',
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '选一个心情吧',
  },
})

const router = useRouter()
const selected = ref('')

function onConfirm(m: { key: string }) {
  router.push({ name: 'recipe', query: { mood: m.key } })
}
</script>

<template>
  <view class="mood-page">
    <AppNav title="选一个心情吧" show-back />

    <MoodPicker
      v-model="selected"
      :hero-height="360"
      title="此刻，你的心情是？"
      confirm-text="就选它，开始推荐"
      @confirm="onConfirm"
    />
  </view>
</template>

<style lang="scss" scoped>
.mood-page {
  min-height: 100vh;
  box-sizing: border-box;
  background: var(--mrc-bg);
  padding: 0 32rpx;
  padding-bottom: calc(160rpx + env(safe-area-inset-bottom));
}
</style>
