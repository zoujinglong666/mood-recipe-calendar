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
    <!-- 卡头：标题进卡内（替代原来浮在卡外的黄色贴纸），估算口径收在右侧 -->
    <view class="na-head">
      <view class="na-head__dot" />
      <text class="na-head__title">营养建议</text>
      <text v-if="estimated !== false" class="na-head__est">按食材估算</text>
    </view>

    <!-- advice 异步到达，未到达时绝不渲染正文——裸读 advice.groups 曾导致整页白屏（微信体验评分报告） -->
    <view v-if="!advice" class="na-loading">
      <text>营养分析生成中…</text>
    </view>
    <template v-else>

    <!-- 结论带：一条暖色横带，左大数值 + 右结论胶囊（替代原来两个不等高的盒子） -->
    <view class="na-verdict">
      <view class="na-verdict__metric">
        <text v-if="advice.keyNutrient" class="na-verdict__label">{{ advice.keyNutrient }}</text>
        <text v-if="advice.keyValue" class="na-verdict__value">{{ advice.keyValue }}</text>
      </view>
      <view class="na-verdict__concl">
        <text class="na-verdict__headline">{{ advice.headline }}</text>
        <text class="na-verdict__pill" :class="ADVICE_CLS[advice.headlineAdvice] || 'pill--neutral'">
          {{ advice.headlineAdvice }}
        </text>
      </view>
    </view>

    <!-- 人群建议：语义色 chips 流式排布，有几个排几个（替代僵硬 2×2 灰盒子） -->
    <view v-if="advice.groups.length" class="na-groups">
      <view v-for="group in advice.groups" :key="group.name" class="na-chip" :class="`na-chip--${adviceTone(group.advice)}`">
        <text class="na-chip__name">{{ group.name }}</text>
        <text class="na-chip__advice">{{ group.advice }}</text>
      </view>
    </view>

    <!-- 营养明细：标签式行列表，数值右对齐纵向可扫读 -->
    <view class="na-section-head">
      <text class="na-section-head__title">菜谱营养分析</text>
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
  padding: 26rpx 26rpx 22rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 28rpx;
  background: var(--mrc-surface);
  box-shadow: var(--mrc-shadow-soft);
}
/* 卡头：珊瑚点 + 标题 + 右侧估算口径，替代原来浮在卡外的黄贴纸 */
.na-head { display: flex; align-items: center; gap: 12rpx; }
.na-head__dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: var(--mrc-accent);
  box-shadow: 0 0 0 6rpx var(--mrc-accent-soft);
}
.na-head__title { color: var(--mrc-text-strong); font-size: 28rpx; font-weight: 900; letter-spacing: 1rpx; }
.na-head__est { margin-left: auto; color: var(--mrc-text-light); font-size: 19rpx; font-weight: 600; }

/* 加载占位 */
.na-loading { padding: 36rpx 0 12rpx; color: var(--mrc-text-light); font-size: 23rpx; text-align: center; }

/* 结论带：一条暖色横带收口——左大数值、右结论与胶囊，替代原来两个不等高的盒子 */
.na-verdict {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-top: 20rpx;
  padding: 22rpx 26rpx;
  border-radius: 20rpx;
  background: var(--mrc-surface-sun);
  border: 2rpx solid var(--mrc-border-light);
}
.na-verdict__metric { display: flex; flex-direction: column; gap: 6rpx; flex-shrink: 0; }
.na-verdict__label { color: var(--mrc-text-sub); font-size: 21rpx; font-weight: 700; }
.na-verdict__value { color: var(--mrc-accent); font-size: 46rpx; font-weight: 900; line-height: 1.05; }
.na-verdict__concl { flex: 1; min-width: 0; display: flex; flex-direction: column; align-items: flex-end; gap: 10rpx; }
.na-verdict__headline { color: var(--mrc-text-strong); font-size: 28rpx; font-weight: 800; text-align: right; }
.na-verdict__pill { padding: 7rpx 20rpx; border-radius: 999rpx; color: #fff; font-size: 20rpx; font-weight: 800; letter-spacing: 1rpx; }
/* WXSS 对 var() 存渐变支持不稳，渐变写死 */
.pill--warn { background: linear-gradient(135deg, #FF9D82, #FF6B5B); }
.pill--neutral { background: var(--mrc-text-sub); }
.pill--good { background: var(--mrc-mint); }

/* 人群 chips：语义色流式排布 */
.na-groups { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 16rpx; }
.na-chip {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 9rpx 20rpx;
  border-radius: 999rpx;
  background: var(--mrc-surface-2);
  border: 2rpx solid var(--mrc-border-light);
}
.na-chip__name { color: var(--mrc-text-deep); font-size: 21rpx; font-weight: 800; }
.na-chip__advice { font-size: 19rpx; font-weight: 700; color: var(--mrc-text-sub); }
.na-chip--good { background: rgba(40, 194, 160, .1); border-color: rgba(40, 194, 160, .26); }
.na-chip--good .na-chip__advice { color: var(--mrc-color-success); }
.na-chip--warn { background: rgba(217, 72, 65, .08); border-color: rgba(217, 72, 65, .2); }
.na-chip--warn .na-chip__advice { color: #C24A2E; }

/* 营养明细行列表：名称 | 建议+人群 | 数值右对齐，发丝线分行 */
.na-section-head { display: flex; align-items: baseline; gap: 10rpx; margin-top: 26rpx; }
.na-section-head__title { color: var(--mrc-text-deep); font-size: 26rpx; font-weight: 900; letter-spacing: 1rpx; }
.na-list { display: flex; flex-direction: column; margin-top: 6rpx; }
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
  padding: 5rpx 16rpx;
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

/* 要点总结：浅米色块收拢，与上面的行列表拉开 */
.na-bullets {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-top: 22rpx;
  padding: 20rpx 22rpx;
  border-radius: 18rpx;
  background: var(--mrc-surface-2);
}
.na-bullet { display: flex; align-items: flex-start; gap: 12rpx; }
.na-bullet__dot { width: 10rpx; height: 10rpx; flex: 0 0 auto; margin-top: 12rpx; border-radius: 50%; background: var(--mrc-accent); }
.na-bullet__text { color: var(--mrc-text); font-size: 23rpx; line-height: 1.55; }
.na-estimate { display: block; margin-top: 16rpx; color: var(--mrc-text-light); font-size: 19rpx; text-align: center; }
</style>
