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

/** 建议词语气 → 语义色（少吃/控制=暖红警示，多吃=薄荷正向，其余中性） */
function adviceTone(advice: string) {
  if (!advice) return 'neutral'
  if (advice.includes('多吃')) return 'good'
  if (advice.includes('少吃') || advice.includes('控制')) return 'warn'
  return 'neutral'
}
</script>

<template>
  <view class="nutri-advice" role="note" aria-label="营养建议">
    <view class="nutri-advice__badge">营养建议</view>

    <!-- advice 异步到达，未到达时绝不渲染正文——裸读 advice.groups 曾导致整页白屏（微信体验评分报告） -->
    <view v-if="!advice" class="na-loading">
      <text>营养分析生成中…</text>
    </view>
    <template v-else>

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
          <text class="na-group__advice" :class="`advice--${adviceTone(group.advice)}`">{{ group.advice }}</text>
        </view>
      </view>
    </view>

    <!-- 营养明细：标签式行列表，数值右对齐纵向可扫读（原伪金字塔宽度不编码信息、视线锯齿跳动，已废） -->
    <view class="na-pyramid-head">
      <text class="na-pyramid-head__title">菜谱营养分析</text>
      <text class="na-pyramid-head__sub">（按食材估算）</text>
    </view>
    <view class="na-list">
      <view v-for="layer in advice.pyramid" :key="layer.key" class="na-item">
        <text class="na-item__key">{{ layer.key }}</text>
        <view class="na-item__advice">
          <text class="na-item__tag" :class="`tag--${adviceTone(layer.advice)}`">{{ layer.advice }}</text>
          <text v-if="layer.group" class="na-item__group">{{ layer.group }}</text>
        </view>
        <text class="na-item__value">{{ layer.value }}</text>
      </view>
    </view>

    <!-- 要点总结 -->
    <view v-if="advice.bullets.length" class="na-bullets">
      <view v-for="(bullet, index) in advice.bullets" :key="index" class="na-bullet">
        <text class="na-bullet__dot" /><text class="na-bullet__text">{{ bullet }}</text>
      </view>
    </view>

    <text v-if="estimated !== false" class="na-estimate">AI 估算值，仅供参考，不能替代专业营养建议</text>
    </template>
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

/* 加载占位 */
.na-loading { padding: 30rpx 0 10rpx; color: var(--mrc-text-light); font-size: 23rpx; text-align: center; }

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
.na-group__advice { font-size: 20rpx; font-weight: 700; color: var(--mrc-text-sub); }
.advice--warn { color: #C24A2E; }
.advice--good { color: var(--mrc-color-success); }

/* 营养明细行列表：名称 | 建议+人群 | 数值右对齐，发丝线分行 */
.na-pyramid-head { display: flex; align-items: baseline; gap: 8rpx; margin-top: 26rpx; }
.na-pyramid-head__title { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 800; }
.na-pyramid-head__sub { color: var(--mrc-text-light); font-size: 20rpx; }
.na-list { display: flex; flex-direction: column; margin-top: 8rpx; }
.na-item {
  display: grid;
  grid-template-columns: 140rpx minmax(0, 1fr) auto;
  align-items: center;
  gap: 12rpx;
  padding: 20rpx 0;
  border-bottom: 1rpx solid var(--mrc-border-light);
}
.na-item:last-child { border-bottom: none; padding-bottom: 4rpx; }
.na-item__key {
  color: var(--mrc-text-strong);
  font-size: 25rpx;
  font-weight: 800;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.na-item__advice { display: flex; align-items: center; gap: 10rpx; min-width: 0; }
.na-item__tag {
  flex: 0 0 auto;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  font-size: 19rpx;
  font-weight: 700;
  background: var(--mrc-surface-2);
  color: var(--mrc-text-sub);
}
.tag--warn { background: rgba(217, 72, 65, 0.13); color: #C24A2E; }
.tag--good { background: rgba(40, 194, 160, 0.16); color: var(--mrc-color-success); }
.na-item__group {
  flex: 1;
  min-width: 0;
  color: var(--mrc-text-light);
  font-size: 20rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.na-item__value { color: var(--mrc-accent); font-size: 28rpx; font-weight: 800; white-space: nowrap; }

/* 要点列表 */
.na-bullets { display: flex; flex-direction: column; gap: 10rpx; margin-top: 22rpx; }
.na-bullet { display: flex; align-items: flex-start; gap: 12rpx; }
.na-bullet__dot { width: 10rpx; height: 10rpx; flex: 0 0 auto; margin-top: 12rpx; border-radius: 50%; background: var(--mrc-accent); }
.na-bullet__text { color: var(--mrc-text); font-size: 23rpx; line-height: 1.55; }
.na-estimate { display: block; margin-top: 14rpx; color: var(--mrc-text-light); font-size: 19rpx; text-align: center; }
</style>
