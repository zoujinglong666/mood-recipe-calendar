<script setup lang="ts">
import { ref, watch } from 'vue'
import { fetchRecipeNutrition, analyzeNutrition, type NutritionAdvice } from '@/api/nutrition'

const props = defineProps<{
  /** 二选一：菜谱 id（详情页）或 菜名+食材（三餐页） */
  recipeId?: number
  name?: string
  ingredients?: string[]
  /** 是否标注估算口径（默认标注，符合诚实展示原则） */
  estimated?: boolean
}>()

const advice = ref<NutritionAdvice>()
const loadedKey = ref('')
/** 页面内缓存：同一道菜换 tab/折叠展开不重复请求（后端另有全局缓存） */
const cache = new Map<string, NutritionAdvice>()

watch(
  () => `${props.recipeId || ''}|${props.name || ''}|${(props.ingredients || []).join('、')}`,
  async (key) => {
    if (!props.recipeId && !props.name)
      return
    if (key === loadedKey.value)
      return
    loadedKey.value = key
    if (cache.has(key)) {
      advice.value = cache.get(key)
      return
    }
    advice.value = undefined
    const result = props.recipeId
      ? await fetchRecipeNutrition(props.recipeId)
      : await analyzeNutrition(props.name || '', (props.ingredients || []).join('、'))
    if (key !== loadedKey.value) return // 等待期间菜谱已切换，丢弃
    if (result) {
      cache.set(key, result)
      advice.value = result
    }
  },
  { immediate: true },
)

/** 整体结论胶囊的语气 → 配色（全部走主题令牌） */
const ADVICE_CLS: Record<string, string> = {
  控制少吃: 'pill--warn',
  适量: 'pill--neutral',
  建议多吃: 'pill--good',
}

/** 金字塔层宽：糖/热量/脂肪自上而下递增，还原参考图的三层梯形观感 */
function layerWidth(index: number) {
  const widths = ['44%', '72%', '100%']
  return widths[index] || '100%'
}
/** 金字塔层配色：自上而下由浅到深（黄 → 浅绿 → 薄荷绿） */
function layerCls(index: number) {
  const cls = ['layer--top', 'layer--mid', 'layer--bottom']
  return cls[index] || 'layer--bottom'
}
</script>

<template>
  <view class="nutri-advice" role="note" aria-label="营养建议">
    <view class="nutri-advice__badge">营养建议</view>

    <!-- 结论区：关键营养素 + 整体结论 + 人群建议网格 -->
    <view class="na-top">
      <view class="na-verdict">
        <text v-if="advice.keyNutrient" class="na-verdict__label">{{ advice.keyNutrient }}</text>
        <text v-if="advice.keyValue" class="na-verdict__value">{{ advice.keyValue }}</text>
        <view class="na-verdict__head">
          <text class="na-verdict__headline">{{ advice.headline }}</text>
          <text class="na-verdict__pill" :class="ADVICE_CLS[advice.headlineAdvice] || 'pill--neutral'">
            {{ advice.headlineAdvice }}
          </text>
        </view>
      </view>
      <view class="na-groups">
        <view v-for="group in advice.groups" :key="group.name" class="na-group">
          <text class="na-group__name">{{ group.name }}</text>
          <text class="na-group__advice">{{ group.advice }}</text>
        </view>
      </view>
    </view>

    <!-- 营养金字塔：三层宽度递增堆叠，左右交替标注数值与人群 -->
    <view class="na-pyramid-head">
      <text class="na-pyramid-head__title">菜谱营养分析</text>
      <text class="na-pyramid-head__sub">（按食材估算）</text>
    </view>
    <view class="na-pyramid">
      <view
        v-for="(layer, index) in advice.pyramid"
        :key="layer.key"
        class="na-row"
        :class="{ 'na-row--flip': index % 2 === 1 }"
      >
        <view class="na-row__side">
          <text class="na-row__value">{{ layer.value }}</text>
          <text class="na-row__desc">{{ layer.advice }} {{ layer.group }}</text>
        </view>
        <view class="na-row__center">
          <view class="na-layer" :class="layerCls(index)" :style="{ width: layerWidth(index) }">
            <text class="na-layer__key">{{ layer.key }}</text>
          </view>
        </view>
        <view class="na-row__side na-row__side--ghost" />
      </view>
    </view>

    <!-- 要点总结 -->
    <view v-if="advice.bullets.length" class="na-bullets">
      <view v-for="(bullet, index) in advice.bullets" :key="index" class="na-bullet">
        <text class="na-bullet__dot" /><text class="na-bullet__text">{{ bullet }}</text>
      </view>
    </view>

    <text v-if="estimated !== false" class="na-estimate">AI 估算值，仅供参考，不能替代专业营养建议</text>
  </view>
</template>

<style lang="scss" scoped>
.nutri-advice {
  margin-top: 24rpx;
  padding: 24rpx 24rpx 20rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 28rpx;
  background: var(--mrc-surface);
  box-shadow: var(--mrc-shadow-soft);
}
/* 标题徽章：参考图的黄底标签 → 主题高光面 */
.nutri-advice__badge {
  display: inline-flex;
  align-self: flex-start;
  padding: 10rpx 26rpx;
  border-radius: 18rpx;
  background: var(--mrc-surface-sun);
  border: 2rpx solid var(--mrc-border);
  color: var(--mrc-text-deep);
  font-size: 28rpx;
  font-weight: 800;
}

/* 结论区 */
.na-top { display: flex; gap: 20rpx; margin-top: 20rpx; }
.na-verdict {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  padding: 20rpx 12rpx;
  border-radius: 20rpx;
  background: var(--mrc-surface-sun);
}
.na-verdict__label { color: var(--mrc-text-sub); font-size: 22rpx; }
.na-verdict__value { color: var(--mrc-accent); font-size: 44rpx; font-weight: 800; }
.na-verdict__head { display: flex; align-items: center; gap: 12rpx; }
.na-verdict__headline { color: var(--mrc-text-strong); font-size: 30rpx; font-weight: 800; }
.na-verdict__pill { padding: 6rpx 18rpx; border-radius: 999rpx; color: #fff; font-size: 20rpx; font-weight: 700; }
.pill--warn { background: var(--mrc-primary-grad); }
.pill--neutral { background: var(--mrc-text-sub); }
.pill--good { background: var(--mrc-mint); }

/* 人群网格 2×2 */
.na-groups { flex: 1; display: grid; grid-template-columns: 1fr 1fr; gap: 12rpx; }
.na-group {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  padding: 14rpx 16rpx;
  border-radius: 16rpx;
  background: var(--mrc-surface-2);
}
.na-group__name { color: var(--mrc-text-deep); font-size: 24rpx; font-weight: 800; }
.na-group__advice { color: var(--mrc-color-success); font-size: 20rpx; font-weight: 700; }

/* 金字塔 */
.na-pyramid-head { display: flex; align-items: baseline; gap: 8rpx; margin-top: 26rpx; }
.na-pyramid-head__title { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 800; }
.na-pyramid-head__sub { color: var(--mrc-text-light); font-size: 20rpx; }
.na-pyramid { display: flex; flex-direction: column; gap: 14rpx; margin-top: 18rpx; }
.na-row { display: flex; align-items: center; gap: 12rpx; }
.na-row__side { width: 210rpx; display: flex; flex-direction: column; gap: 2rpx; }
.na-row--flip .na-row__side { order: 3; align-items: flex-end; text-align: right; }
.na-row__side--ghost { flex: 1; }
.na-row__center { flex: 1; display: flex; justify-content: center; }
.na-row__value { color: var(--mrc-accent); font-size: 30rpx; font-weight: 800; }
.na-row__desc { color: var(--mrc-text-sub); font-size: 19rpx; line-height: 1.4; }

/* 三层塔身：宽度递增 + 由浅到深，圆角块堆叠还原梯形观感 */
.na-layer {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 74rpx;
  border-radius: 14rpx;
}
.layer--top { background: var(--mrc-surface-sun); border: 2rpx solid var(--mrc-border); }
.layer--mid { background: var(--mrc-accent-soft); }
.layer--bottom { background: rgba(40, 194, 160, 0.18); }
.na-layer__key { color: var(--mrc-text-deep); font-size: 25rpx; font-weight: 800; }

/* 要点列表 */
.na-bullets { display: flex; flex-direction: column; gap: 10rpx; margin-top: 22rpx; }
.na-bullet { display: flex; align-items: flex-start; gap: 12rpx; }
.na-bullet__dot { width: 10rpx; height: 10rpx; flex: 0 0 auto; margin-top: 12rpx; border-radius: 50%; background: var(--mrc-accent); }
.na-bullet__text { color: var(--mrc-text); font-size: 23rpx; line-height: 1.55; }
.na-estimate { display: block; margin-top: 14rpx; color: var(--mrc-text-light); font-size: 19rpx; text-align: center; }
</style>
