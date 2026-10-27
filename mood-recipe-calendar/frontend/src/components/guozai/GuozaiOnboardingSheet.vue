<script setup lang="ts">
/**
 * [2026-10-27] 冷启动画像问卷（主循环 P1：新用户首日就"像用了一年的样子"）。
 *
 * 只弹一次的契约由父页面保证：展示时立刻写 `mrc_onboarding_asked`，
 * 完成/跳过/下滑关闭都绝不再次自动弹出（见 mood 页 onConfirm）。
 *
 * 5 步渐进披露，每题都对应一个真实打分因子（对应 docs/product-loop-evolution-2026-10-26.md）：
 * 1. 做饭时长 → maxMinutes（时长打分 + 快手推荐）
 * 2. 辣度 → spiceLevel（preferenceScore）
 * 3. 喜欢的菜系 → favoriteCuisines（菜系 +8）
 * 4. 不吃/过敏 → 安全键硬过滤（EXPLICIT 才允许写，问卷正是显式来源）
 * 5. 家里几口人 → KEY_PEOPLE（为周计划人数缩放埋点）
 *
 * 提交走双轨：POST /preferences/onboarding 同时写显式偏好表 + 记忆库（EXPLICIT/0.9）。
 */
import { computed, reactive, ref, watch } from 'vue'
import GuozaiChoiceChips, { type GuozaiChoiceOption } from './GuozaiChoiceChips.vue'
import { saveOnboarding } from '@/api/preferences'
import { haptic } from '@/utils/haptic'

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  /** completed=false 表示跳过/关闭，父页面据此放行去选餐别 */
  (e: 'done', result: { completed: boolean }): void
}>()

const props = withDefaults(defineProps<{
  modelValue?: boolean
}>(), { modelValue: false })

const show = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const STEPS: Array<{ key: 'time' | 'spice' | 'cuisine' | 'avoid' | 'people', title: string, hint: string, required: boolean }> = [
  { key: 'time', title: '平时做一顿饭，想花多久？', hint: '锅仔只推你做得完的菜', required: true },
  { key: 'spice', title: '口味上，你能吃多辣？', hint: '按你的度来，不放纵也不敷衍', required: true },
  { key: 'cuisine', title: '喜欢哪些菜系？', hint: '可多选，最多 3 个', required: false },
  { key: 'avoid', title: '有什么不吃或过敏的吗？', hint: '过敏的东西锅仔会永远帮你避开', required: false },
  { key: 'people', title: '家里几口人一起吃？', hint: '份量按人头算，不浪费', required: false },
]

const TIME_OPTIONS: GuozaiChoiceOption[] = [
  { value: '15', label: '15分钟内' },
  { value: '20', label: '20分钟左右' },
  { value: '30', label: '30分钟左右' },
  { value: '60', label: '不着急，慢慢做' },
]

const SPICE_OPTIONS: GuozaiChoiceOption[] = [
  { value: 'NONE', label: '不吃辣' },
  { value: 'MILD', label: '微辣' },
  { value: 'NORMAL', label: '中辣' },
  { value: 'HOT', label: '无辣不欢' },
]

const CUISINE_OPTIONS: GuozaiChoiceOption[] = [
  { value: '家常菜', label: '家常菜' },
  { value: '川菜', label: '川菜' },
  { value: '粤菜', label: '粤菜' },
  { value: '江浙菜', label: '江浙菜' },
  { value: '东北菜', label: '东北菜' },
  { value: '西北面食', label: '西北面食' },
  { value: '日韩料理', label: '日韩料理' },
  { value: '西式简餐', label: '西式简餐' },
]

const AVOID_OPTIONS: GuozaiChoiceOption[] = [
  { value: '香菜', label: '香菜' },
  { value: '内脏', label: '内脏' },
  { value: '肥肉', label: '肥肉' },
  { value: '芹菜', label: '芹菜' },
  { value: '胡萝卜', label: '胡萝卜' },
  { value: '姜蒜味重', label: '姜蒜味重' },
]

const ALLERGEN_OPTIONS: GuozaiChoiceOption[] = [
  { value: '海鲜', label: '海鲜/虾蟹' },
  { value: '花生', label: '花生' },
  { value: '鸡蛋', label: '鸡蛋' },
  { value: '牛奶', label: '牛奶' },
  { value: '芒果', label: '芒果' },
]

const PEOPLE_OPTIONS: GuozaiChoiceOption[] = [
  { value: '1', label: '就我自己' },
  { value: '2', label: '两个人' },
  { value: '3', label: '三口之家' },
  { value: '4', label: '四口及以上' },
]

const stepIndex = ref(0)
const step = computed(() => STEPS[stepIndex.value])
const isLast = computed(() => stepIndex.value === STEPS.length - 1)
const submitting = ref(false)
/** 防止「提交完成」与「下滑关闭」双触发 done */
const finished = ref(false)

const selections = reactive<Record<string, string[]>>({
  time: [], spice: [], cuisine: [], avoid: [], allergen: [], people: [],
})

const canGoNext = computed(() => {
  if (!step.value.required) return true
  return selections[step.value.key].length > 0
})

/** 单选题：点选替换、再点取消（GuozaiChoiceChips 是多选语义，在外面包装） */
function pickSingle(key: string, value: string) {
  haptic('light')
  selections[key] = selections[key][0] === value ? [] : [value]
}

function toggleCuisine(value: string) {
  haptic('light')
  const current = selections.cuisine
  if (current.includes(value)) selections.cuisine = current.filter(item => item !== value)
  else if (current.length < 3) selections.cuisine = [...current, value]
}

function next() {
  if (!canGoNext.value || submitting.value) return
  haptic('light')
  if (isLast.value) void submit()
  else stepIndex.value += 1
}

function back() {
  if (stepIndex.value === 0 || submitting.value) return
  haptic('light')
  stepIndex.value -= 1
}

function skip() {
  if (submitting.value) return
  haptic('light')
  finished.value = true
  show.value = false
  emit('done', { completed: false })
}

async function submit() {
  if (submitting.value) return
  submitting.value = true
  try {
    await saveOnboarding({
      maxMinutes: selections.time[0] ? Number(selections.time[0]) : undefined,
      spiceLevel: selections.spice[0] as never,
      cuisines: selections.cuisine.length ? selections.cuisine : undefined,
      avoidIngredients: selections.avoid.length ? selections.avoid : undefined,
      allergens: selections.allergen.length ? selections.allergen : undefined,
      people: selections.people[0] ? Number(selections.people[0]) : undefined,
    })
    finished.value = true
    show.value = false
    emit('done', { completed: true })
  }
  catch {
    uni.showToast({ title: '没存上，再试一次或跳过', icon: 'none' })
  }
  finally {
    submitting.value = false
  }
}

/** 下滑/点遮罩关闭视为跳过（只触发一次） */
watch(show, (value) => {
  if (!value && !finished.value) {
    finished.value = true
    emit('done', { completed: false })
  }
})
</script>

<template>
  <wd-popup v-model="show" position="bottom" :safe-area-inset-bottom="true" custom-style="border-radius: 40rpx 40rpx 0 0; overflow: hidden; background: var(--mrc-surface);">
    <view class="onboard">
      <view class="onboard__head">
        <text class="onboard__eyebrow">锅仔想先认识你一下 · {{ stepIndex + 1 }}/{{ STEPS.length }}</text>
        <text class="onboard__title">{{ step.title }}</text>
        <text class="onboard__hint">{{ step.hint }}</text>
        <view class="onboard__dots" aria-hidden="true">
          <view
            v-for="(item, index) in STEPS"
            :key="item.key"
            class="onboard__dot"
            :class="{ 'is-done': index < stepIndex, 'is-current': index === stepIndex }"
          />
        </view>
      </view>

      <view class="onboard__body">
        <GuozaiChoiceChips
          v-if="step.key === 'time' || step.key === 'spice' || step.key === 'people'"
          :model-value="selections[step.key]"
          :options="step.key === 'time' ? TIME_OPTIONS : step.key === 'spice' ? SPICE_OPTIONS : PEOPLE_OPTIONS"
          :aria-label="step.title"
          @select="value => pickSingle(step.key, value)"
        />

        <GuozaiChoiceChips
          v-else-if="step.key === 'cuisine'"
          :model-value="selections.cuisine"
          :options="CUISINE_OPTIONS"
          aria-label="喜欢的菜系，可多选"
          @select="toggleCuisine"
        />

        <template v-else>
          <text class="onboard__group-label">不吃的</text>
          <GuozaiChoiceChips
            :model-value="selections.avoid"
            :options="AVOID_OPTIONS"
            aria-label="不吃的食材，可多选"
            @select="value => selections.avoid = selections.avoid.includes(value) ? selections.avoid.filter(item => item !== value) : [...selections.avoid, value]"
          />
          <text class="onboard__group-label onboard__group-label--gap">过敏的（锅仔会硬性避开）</text>
          <GuozaiChoiceChips
            :model-value="selections.allergen"
            :options="ALLERGEN_OPTIONS"
            aria-label="过敏原，可多选"
            @select="value => selections.allergen = selections.allergen.includes(value) ? selections.allergen.filter(item => item !== value) : [...selections.allergen, value]"
          />
        </template>
      </view>

      <view class="onboard__foot">
        <button class="onboard__skip" :disabled="submitting" @click="skip">跳过，之后再聊</button>
        <view class="onboard__actions">
          <button v-if="stepIndex > 0" class="onboard__back" :disabled="submitting" @click="back">上一步</button>
          <button
            class="onboard__next"
            :class="{ 'is-disabled': !canGoNext }"
            :disabled="submitting"
            @click="next"
          >
            {{ submitting ? '正在记下…' : isLast ? '就这些，开饭！' : '下一步' }}
          </button>
        </view>
      </view>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.onboard { padding: 32rpx 28rpx calc(32rpx + env(safe-area-inset-bottom)); }
.onboard__head { display: flex; flex-direction: column; gap: 8rpx; padding: 0 4rpx 24rpx; }
.onboard__eyebrow { color: var(--mrc-text-sub); font-size: 21rpx; font-weight: var(--mrc-fw-semibold); }
.onboard__title { color: var(--mrc-text-strong); font-size: 32rpx; font-weight: var(--mrc-fw-heavy); }
.onboard__hint { color: var(--mrc-text-sub); font-size: 22rpx; }
.onboard__dots { display: flex; gap: 10rpx; margin-top: 12rpx; }
.onboard__dot { width: 28rpx; height: 8rpx; border-radius: var(--mrc-radius-pill); background: var(--mrc-border); transition: background var(--mrc-motion-fast); }
.onboard__dot.is-done { background: var(--mrc-primary); }
.onboard__dot.is-current { background: var(--mrc-primary-deep); }

.onboard__body { display: flex; flex-direction: column; gap: var(--mrc-space-sm); padding: 8rpx 4rpx; min-height: 220rpx; }
.onboard__group-label { color: var(--mrc-text-sub); font-size: 22rpx; font-weight: var(--mrc-fw-semibold); }
.onboard__group-label--gap { margin-top: var(--mrc-space-sm); }

.onboard__foot { display: flex; flex-direction: column; gap: 16rpx; padding-top: 24rpx; }
.onboard__skip { width: 100%; min-height: 72rpx; border-radius: var(--mrc-radius-lg); color: var(--mrc-text-sub); background: transparent; font-size: 23rpx; font-weight: var(--mrc-fw-semibold); }
.onboard__actions { display: flex; gap: 16rpx; }
.onboard__back { flex: 1; min-height: 92rpx; border: 2rpx solid var(--mrc-border-light); border-radius: var(--mrc-radius-lg); color: var(--mrc-text-deep); background: var(--mrc-surface-2); font-size: 27rpx; font-weight: var(--mrc-fw-bold); }
.onboard__next { flex: 2; min-height: 92rpx; border-radius: var(--mrc-radius-lg); color: var(--mrc-on-primary); background: var(--mrc-primary); font-size: 28rpx; font-weight: var(--mrc-fw-bold); }
.onboard__next.is-disabled { opacity: 0.55; }
.onboard__back[disabled], .onboard__next[disabled], .onboard__skip[disabled] { opacity: 0.55; }
</style>
