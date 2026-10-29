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
import GuozaiButton from './GuozaiButton.vue'
import { saveOnboarding } from '@/api/preferences'
import { haptic } from '@/utils/haptic'
import { funnel } from '@/utils/funnel'

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  /** completed=false 表示跳过/关闭，父页面据此放行去选餐别 */
  (e: 'done', result: { completed: boolean }): void
}>()

const props = withDefaults(defineProps<{
  modelValue?: boolean
  /** 所在页面带原生 tabBar：弹层底部要额外让出 tabBar 高度，否则底部按钮会被 tabBar 盖住 */
  withTabbar?: boolean
}>(), { modelValue: false, withTabbar: false })

const show = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const STEPS: Array<{ key: 'time' | 'spice' | 'cuisine' | 'avoid' | 'people' | 'body', title: string, hint: string, required: boolean }> = [
  { key: 'time', title: '平时做一顿饭，想花多久？', hint: '锅仔只推你做得完的菜', required: true },
  { key: 'spice', title: '口味上，你能吃多辣？', hint: '按你的度来，不放纵也不敷衍', required: true },
  { key: 'cuisine', title: '喜欢哪些菜系？', hint: '可多选，最多 3 个', required: false },
  { key: 'avoid', title: '有什么不吃或过敏的吗？', hint: '过敏的东西锅仔会永远帮你避开', required: false },
  { key: 'people', title: '家里几口人一起吃？', hint: '份量按人头算，不浪费', required: false },
  { key: 'body', title: '想算得更准的话，说下身体情况？', hint: '可选。用来估算你每天需要的能量与蛋白质（NASEM 2023）', required: false },
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

// [2026-10-28] 身体数据步骤（可选）：EER 目标计算输入
const GENDER_OPTIONS: GuozaiChoiceOption[] = [
  { value: 'FEMALE', label: '女' },
  { value: 'MALE', label: '男' },
]
const ACTIVITY_OPTIONS: GuozaiChoiceOption[] = [
  { value: 'INACTIVE', label: '久坐少动' },
  { value: 'LOW_ACTIVE', label: '每周动几次' },
  { value: 'ACTIVE', label: '几乎每天动' },
  { value: 'VERY_ACTIVE', label: '高强度训练' },
]
const MEAL_TIMING_OPTIONS: GuozaiChoiceOption[] = [
  { value: 'REGULAR', label: '三餐规律' },
  { value: 'NO_BREAKFAST', label: '常不吃早餐' },
  { value: 'LATE_NIGHT', label: '夜宵比较多' },
  { value: 'INTERMITTENT', label: '轻断食' },
]

const stepIndex = ref(0)
const step = computed(() => STEPS[stepIndex.value])
const isLast = computed(() => stepIndex.value === STEPS.length - 1)
const submitting = ref(false)
/** 防止「提交完成」与「下滑关闭」双触发 done */
const finished = ref(false)

// [2026-10-28] 第 6 步漏斗：可选步骤是否造成流失。
// 注意必须放在 stepIndex 声明之后：watch 的 getter 会立即执行一次取初始值，
// 声明前引用在小程序编译产物里表现为 undefined.value（2026-10-29 首页 TypeError 修复）。
watch(() => stepIndex.value, (idx) => {
  if (STEPS[idx]?.key === 'body') funnel('onboarding_body_reached')
})

const selections = reactive<Record<string, string[]>>({
  time: [], spice: [], cuisine: [], avoid: [], allergen: [], people: [], activity: [], mealTiming: [],
})

/** [2026-10-28] 身体数据输入（可选步骤）：性别用 chips，三项数字用输入框 */
const body = reactive({ gender: '', age: '', heightCm: '', weightKg: '' })

function pickBodyGender(value: string) {
  haptic('light')
  body.gender = body.gender === value ? '' : value
}
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
      // [2026-10-28] 身体数据（可选）：只传有效值，空着就不影响目标计算
      gender: body.gender ? (body.gender as 'MALE' | 'FEMALE') : undefined,
      age: Number(body.age) > 0 ? Number(body.age) : undefined,
      heightCm: Number(body.heightCm) > 0 ? Number(body.heightCm) : undefined,
      weightKg: Number(body.weightKg) > 0 ? Number(body.weightKg) : undefined,
      activityLevel: selections.activity[0] as never,
      mealTiming: selections.mealTiming[0] as never,
    })
    funnel(body.gender || Number(body.age) > 0 || Number(body.heightCm) > 0 || Number(body.weightKg) > 0
      || selections.activity[0] || selections.mealTiming[0]
      ? 'onboarding_body_filled' : 'onboarding_body_skipped')
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
  <wd-popup v-model="show" position="bottom" :safe-area-inset-bottom="false" custom-style="border-radius: 40rpx 40rpx 0 0; overflow: hidden; background: var(--mrc-surface);">
    <view class="onboard" :style="{ '--onboard-tabbar': withTabbar ? '120rpx' : '0rpx' }">
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

        <template v-else-if="step.key === 'body'">
          <text class="onboard__group-label">性别</text>
          <GuozaiChoiceChips
            :model-value="body.gender ? [body.gender] : []"
            :options="GENDER_OPTIONS"
            aria-label="性别，单选"
            @select="pickBodyGender"
          />
          <view class="onboard__metrics">
            <view class="onboard__metric">
              <text class="onboard__metric-label">年龄</text>
              <input v-model="body.age" class="onboard__metric-input" type="number" placeholder="30" :maxlength="3" />
            </view>
            <view class="onboard__metric">
              <text class="onboard__metric-label">身高cm</text>
              <input v-model="body.heightCm" class="onboard__metric-input" type="digit" placeholder="165" :maxlength="5" />
            </view>
            <view class="onboard__metric">
              <text class="onboard__metric-label">体重kg</text>
              <input v-model="body.weightKg" class="onboard__metric-input" type="digit" placeholder="55" :maxlength="5" />
            </view>
          </view>
          <text class="onboard__group-label onboard__group-label--gap">平时活动量</text>
          <GuozaiChoiceChips
            :model-value="selections.activity"
            :options="ACTIVITY_OPTIONS"
            aria-label="平时活动量，单选"
            @select="value => pickSingle('activity', value)"
          />
          <text class="onboard__group-label onboard__group-label--gap">进食节奏</text>
          <GuozaiChoiceChips
            :model-value="selections.mealTiming"
            :options="MEAL_TIMING_OPTIONS"
            aria-label="进食节奏，单选"
            @select="value => pickSingle('mealTiming', value)"
          />
        </template>

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
        <GuozaiButton variant="ghost" block aria-label="跳过问卷" :disabled="submitting" @click="skip">跳过，之后再聊</GuozaiButton>
        <view class="onboard__actions">
          <view v-if="stepIndex > 0" class="onboard__back-wrap">
            <GuozaiButton variant="secondary" block :disabled="submitting" @click="back">上一步</GuozaiButton>
          </view>
          <view class="onboard__next-wrap">
            <GuozaiButton variant="primary" block :disabled="!canGoNext" :loading="submitting" aria-label="下一步" @click="next">
              {{ submitting ? '正在记下…' : isLast ? '就这些，开饭！' : '下一步' }}
            </GuozaiButton>
          </view>
        </view>
      </view>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.onboard { padding: 32rpx 28rpx calc(32rpx + env(safe-area-inset-bottom) + var(--onboard-tabbar, 0rpx)); }
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
.onboard__metrics { display: flex; gap: 12rpx; }
.onboard__metric { flex: 1; display: flex; flex-direction: column; gap: 6rpx; background: var(--mrc-surface-2); border: 2rpx solid var(--mrc-border-light); border-radius: var(--mrc-radius-lg); padding: 12rpx 16rpx; }
.onboard__metric-label { color: var(--mrc-text-sub); font-size: 20rpx; font-weight: var(--mrc-fw-semibold); }
.onboard__metric-input { color: var(--mrc-text-strong); font-size: 27rpx; font-weight: var(--mrc-fw-bold); min-height: 44rpx; }

.onboard__foot { display: flex; flex-direction: column; gap: 16rpx; padding-top: 24rpx; }
.onboard__actions { display: flex; gap: 16rpx; }
.onboard__back-wrap { flex: 1; }
.onboard__next-wrap { flex: 2; }
</style>
