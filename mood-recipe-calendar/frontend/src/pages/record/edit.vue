<script setup lang="ts">
import type { RecordItem } from '@/api/records'
import { ref } from 'vue'
import RecordEditorForm from '@/components/record/RecordEditorForm.vue'
import { navBack } from '@/composables/useNavBar'

definePage({ name: 'record-edit', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '编辑这顿饭' } })

import { onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'

// 直接本页写生命周期，比 useShare 组合式更可靠
onShareAppMessage(() => {
  const pages = getCurrentPages()
  const route = (pages[pages.length - 1] as any)?.route || ''
  return { title: '锅仔 · 按心情帮你决定今天吃什么', path: `/${route}` }
})
onShareTimeline(() => ({ title: '锅仔 · 按心情帮你决定今天吃什么' }))

const router = useRouter()
const route = useRoute()
// 同 record/detail：@wot-ui/router 的 route.query 不可靠（name 跳转会丢弃 query 字段，
// 且 query 是靠 page.$page.fullPath 反解的），改用 uni-app 原生 onLoad 的 options 取 id。
const recordId = ref(0)
onLoad((options: any) => {
  const raw = options?.id ?? route.query?.id ?? (route as any).params?.id
  recordId.value = Number(raw)
})
const saved = ref(false)

function cancel() {
  if (saved.value)
    return
  router.back()
}

function onSaved(_record: RecordItem) {
  saved.value = true
  router.replace({ name: 'timeline' })
}
</script>

<template>
  <view class="record-edit-page mrc-hero">
    <wd-navbar title="编辑这顿饭" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />
    <RecordEditorForm v-if="recordId" :record-id="recordId" @saved="onSaved" @cancel="cancel" />
    <view v-else class="record-edit-page__empty">
      <text>找不到这条记录</text>
      <view class="record-edit-page__back pressable" role="button" aria-label="返回菜谱时光机" @click="router.replace({ name: 'timeline' })">
        回到时光机
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.record-edit-page { min-height: 100vh; }.record-edit-page__empty { display: flex; min-height: 70vh; flex-direction: column; align-items: center; justify-content: center; gap: 28rpx; color: var(--mrc-text-sub); font-size: 26rpx; }.record-edit-page__back { display: flex; min-height: 82rpx; align-items: center; padding: 0 32rpx; border: 2rpx solid var(--mrc-border); border-radius: 42rpx; color: var(--mrc-text-deep); background: var(--mrc-surface); font-weight: 750; }
</style>
