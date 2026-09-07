<script setup lang="ts">
import type { SpiceLevel } from '../../api/preferences'
import { computed, ref } from 'vue'
import { navBack } from '@/composables/useNavBar'
import { clearFoodPreference, fetchFoodPreference, saveFoodPreference } from '../../api/preferences'
import { ensureLogin } from '../../utils/login'

definePage({
  name: 'preferences',
  layout: 'default',
  style: { navigationStyle: 'custom', navigationBarTitleText: '锅仔记忆' },
})

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const saving = ref(false)
const favoriteTags = ref<string[]>([])
const favoriteCuisines = ref<string[]>([])
const favoriteDishes = ref('')
const eatScallion = ref<boolean | null>(null)
const eatCilantro = ref<boolean | null>(null)
const spiceLevel = ref<SpiceLevel>('NORMAL')
const avoidIngredients = ref('')
const allergens = ref('')

const FAVORITES = ['家常菜', '汤粥', '面食', '米饭', '清淡', '香辣', '肉食', '海鲜']
const CUISINES = ['川菜', '湘菜', '粤菜', '江浙菜', '东北菜', '西北菜', '云贵菜', '日韩料理']
const SPICE_LEVELS: { value: SpiceLevel, label: string }[] = [
  { value: 'NONE', label: '不吃辣' },
  { value: 'MILD', label: '微辣' },
  { value: 'NORMAL', label: '正常辣' },
  { value: 'HOT', label: '很能吃辣' },
]
const onboarding = computed(() => route.query.from === 'onboarding')

onLoad(async () => {
  try {
    await ensureLogin()
    const data = await fetchFoodPreference()
    favoriteTags.value = data.favoriteTags ? data.favoriteTags.split(',').filter(Boolean) : []
    favoriteCuisines.value = data.favoriteCuisines ? data.favoriteCuisines.split(',').filter(Boolean) : []
    favoriteDishes.value = data.favoriteDishes || ''
    eatScallion.value = data.eatScallion
    eatCilantro.value = data.eatCilantro
    spiceLevel.value = data.spiceLevel || 'NORMAL'
    avoidIngredients.value = data.avoidIngredients || ''
    allergens.value = data.allergens || ''
  }
  catch (e: any) {
    uni.showToast({ title: e.message || '记忆加载失败，请重试', icon: 'none' })
  }
  finally {
    loading.value = false
  }
})

function toggleFavorite(tag: string) {
  favoriteTags.value = favoriteTags.value.includes(tag)
    ? favoriteTags.value.filter(item => item !== tag)
    : [...favoriteTags.value, tag]
}

function toggleCuisine(cuisine: string) {
  favoriteCuisines.value = favoriteCuisines.value.includes(cuisine)
    ? favoriteCuisines.value.filter(item => item !== cuisine)
    : [...favoriteCuisines.value, cuisine]
}

async function save() {
  if (saving.value)
    return
  saving.value = true
  try {
    await saveFoodPreference({
      favoriteTags: favoriteTags.value.join(','),
      favoriteCuisines: favoriteCuisines.value.join(','),
      favoriteDishes: favoriteDishes.value.trim(),
      avoidIngredients: avoidIngredients.value.trim(),
      allergens: allergens.value.trim(),
      eatScallion: eatScallion.value,
      eatCilantro: eatCilantro.value,
      spiceLevel: spiceLevel.value,
    })
    uni.removeStorageSync('mrc_companion_message')
    uni.setStorageSync('mrc_preference_onboarded', '1')
    uni.showToast({ title: '锅仔记住啦', icon: 'success' })
    setTimeout(finish, 450)
  }
  catch (e: any) {
    uni.showToast({ title: e.message || '保存失败，请重试', icon: 'none' })
  }
  finally {
    saving.value = false
  }
}

function finish() {
  const mood = String(route.query.mood || '')
  if (onboarding.value && mood)
    router.replace({ name: 'recipe', query: { mood } })
  else navBack()
}

async function skip() {
  favoriteTags.value = []
  favoriteCuisines.value = []
  favoriteDishes.value = ''
  eatScallion.value = null
  eatCilantro.value = null
  spiceLevel.value = 'NORMAL'
  avoidIngredients.value = ''
  allergens.value = ''
  await save()
}

function clearMemory() {
  uni.showModal({
    title: '清除锅仔的口味记忆？',
    content: '会清除口味、忌口和推荐反馈，不影响做菜记录。你以后可以重新告诉锅仔。',
    confirmText: '确认清除',
    confirmColor: '#C84B3A',
    success: async (result) => {
      if (!result.confirm)
        return
      try {
        await clearFoodPreference()
        uni.removeStorageSync('mrc_companion_message')
        uni.removeStorageSync('mrc_preference_onboarded')
        favoriteTags.value = []
        favoriteCuisines.value = []
        favoriteDishes.value = ''
        eatScallion.value = null
        eatCilantro.value = null
        spiceLevel.value = 'NORMAL'
        avoidIngredients.value = ''
        allergens.value = ''
        uni.showToast({ title: '口味记忆已清除', icon: 'none' })
      }
      catch (e: any) {
        uni.showToast({ title: e.message || '清除失败，请重试', icon: 'none' })
      }
    },
  })
}
</script>

<template>
  <view class="memory-page">
    <wd-navbar title="锅仔记忆" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <view v-if="loading" class="memory-loading">
      锅仔正在翻开记忆本…
    </view>
    <template v-else>
      <view class="memory-hero">
        <image class="memory-hero__image" src="/static/guozai/action_10_thinking.png" mode="aspectFit" />
        <view class="memory-hero__copy">
          <text class="memory-hero__eyebrow">
            只记你愿意告诉我的
          </text>
          <text class="memory-hero__title">
            以后每一餐，都更懂你一点
          </text>
          <text class="memory-hero__body">
            这些记忆只用于推荐，你可以随时修改或清除。
          </text>
        </view>
      </view>

      <view class="memory-section">
        <text class="memory-section__title">
          你平时喜欢吃什么？
        </text>
        <text class="memory-section__hint">
          可以多选，之后还能慢慢调整
        </text>
        <view class="choice-grid">
          <view
            v-for="tag in FAVORITES"
            :key="tag"
            class="choice-chip"
            :class="{ 'choice-chip--selected': favoriteTags.includes(tag) }"
            role="checkbox"
            :aria-checked="favoriteTags.includes(tag)"
            @click="toggleFavorite(tag)"
          >
            {{ tag }}
          </view>
        </view>
        <text class="memory-section__hint memory-section__hint--input">
          也可以直接告诉锅仔你爱吃的菜
        </text>
        <input v-model="favoriteDishes" class="memory-input" :maxlength="500" placeholder="例如：番茄炒蛋、红烧肉">
      </view>

      <view class="memory-section">
        <text class="memory-section__title">
          你偏爱哪些菜系？
        </text>
        <text class="memory-section__hint">
          可以多选，锅仔会在合适的时候优先想到它们
        </text>
        <view class="choice-grid">
          <view
            v-for="cuisine in CUISINES"
            :key="cuisine"
            class="choice-chip choice-chip--cuisine"
            :class="{ 'choice-chip--selected': favoriteCuisines.includes(cuisine) }"
            role="checkbox"
            :aria-checked="favoriteCuisines.includes(cuisine)"
            @click="toggleCuisine(cuisine)"
          >
            <view v-if="favoriteCuisines.includes(cuisine)" class="choice-chip__mark" />
            {{ cuisine }}
          </view>
        </view>
      </view>

      <view class="memory-section">
        <text class="memory-section__title">
          两件很重要的小事
        </text>
        <view class="binary-row">
          <text class="binary-row__label">
            吃葱吗？
          </text>
          <view class="binary-row__actions">
            <view class="binary-button" :class="{ 'binary-button--selected': eatScallion === true }" @click="eatScallion = true">
              可以
            </view>
            <view class="binary-button" :class="{ 'binary-button--selected': eatScallion === false }" @click="eatScallion = false">
              不要
            </view>
          </view>
        </view>
        <view class="binary-row">
          <text class="binary-row__label">
            吃香菜吗？
          </text>
          <view class="binary-row__actions">
            <view class="binary-button" :class="{ 'binary-button--selected': eatCilantro === true }" @click="eatCilantro = true">
              可以
            </view>
            <view class="binary-button" :class="{ 'binary-button--selected': eatCilantro === false }" @click="eatCilantro = false">
              不要
            </view>
          </view>
        </view>
      </view>

      <view class="memory-section">
        <text class="memory-section__title">
          能吃多辣？
        </text>
        <view class="choice-grid choice-grid--two">
          <view
            v-for="item in SPICE_LEVELS"
            :key="item.value"
            class="choice-chip"
            :class="{ 'choice-chip--selected': spiceLevel === item.value }"
            role="radio"
            :aria-checked="spiceLevel === item.value"
            @click="spiceLevel = item.value"
          >
            {{ item.label }}
          </view>
        </view>
      </view>

      <view class="memory-section">
        <text class="memory-section__title">
          还有什么不吃？
        </text>
        <text class="memory-section__hint">
          用逗号隔开，例如：芹菜、花生、动物内脏
        </text>
        <input v-model="avoidIngredients" class="memory-input" :maxlength="500" placeholder="没有可以留空">
      </view>

      <view class="memory-section memory-section--warning">
        <text class="memory-section__title">
          食物过敏
        </text>
        <text class="memory-section__hint">
          例如：花生、虾、乳制品。推荐仅作辅助，食用前仍需核对全部食材。
        </text>
        <input v-model="allergens" class="memory-input" :maxlength="500" placeholder="没有可以留空">
      </view>

      <view class="memory-actions">
        <button class="memory-save" :disabled="saving" @click="save">
          {{ saving ? '正在记住…' : '让锅仔记住' }}
        </button>
        <button v-if="onboarding" class="memory-skip" :disabled="saving" @click="skip">
          暂时跳过
        </button>
        <button v-else class="memory-clear" :disabled="saving" @click="clearMemory">
          清除全部口味记忆
        </button>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.memory-page { min-height: 100vh; box-sizing: border-box; padding: 0 32rpx calc(56rpx + env(safe-area-inset-bottom)); background: var(--mrc-bg); }
.memory-loading { padding: 160rpx 0; text-align: center; color: var(--mrc-text-sub); font-size: 28rpx; }
.memory-hero { display: flex; align-items: center; gap: 20rpx; padding: 28rpx; margin-bottom: 24rpx; border: 2rpx solid var(--mrc-border); border-radius: 32rpx; background: linear-gradient(135deg, var(--mrc-surface-sun), var(--mrc-surface-peach)); box-shadow: var(--mrc-shadow-soft); }
.memory-hero__image { width: 150rpx; height: 150rpx; flex-shrink: 0; }
.memory-hero__copy { flex: 1; min-width: 0; }
.memory-hero__eyebrow, .memory-hero__title, .memory-hero__body { display: block; }
.memory-hero__eyebrow { color: var(--mrc-accent); font-size: 21rpx; font-weight: 800; letter-spacing: 2rpx; }
.memory-hero__title { margin-top: 8rpx; color: var(--mrc-text-deep); font-size: 34rpx; font-weight: 900; line-height: 1.35; }
.memory-hero__body { margin-top: 10rpx; color: var(--mrc-text-sub); font-size: 23rpx; line-height: 1.55; }
.memory-section { padding: 28rpx; margin-bottom: 20rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.memory-section--warning { border-color: var(--mrc-border); }
.memory-section__title, .memory-section__hint { display: block; }
.memory-section__title { color: var(--mrc-text-deep); font-size: 31rpx; font-weight: 800; }
.memory-section__hint { margin-top: 8rpx; color: var(--mrc-text-sub); font-size: 23rpx; line-height: 1.55; }
.memory-section__hint--input { margin-top: 24rpx; }
.choice-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14rpx; margin-top: 22rpx; }
.choice-grid--two { grid-template-columns: repeat(2, 1fr); }
.choice-chip, .binary-button { min-height: 88rpx; display: flex; align-items: center; justify-content: center; box-sizing: border-box; padding: 0 12rpx; border: 2rpx solid var(--mrc-border); border-radius: 44rpx; background: var(--mrc-bg); color: var(--mrc-text-deep); font-size: 24rpx; font-weight: 700; transition: transform .15s ease, background-color .2s ease; }
.choice-chip--selected, .binary-button--selected { border-color: var(--mrc-primary); background: var(--mrc-surface-peach); color: var(--mrc-accent); }
.choice-chip--cuisine { gap: 8rpx; }
.choice-chip__mark { width: 10rpx; height: 10rpx; flex-shrink: 0; border-radius: 50%; background: var(--mrc-primary); }
.choice-chip:active, .binary-button:active { transform: scale(.96); }
.binary-row { min-height: 96rpx; display: flex; align-items: center; justify-content: space-between; gap: 20rpx; border-bottom: 2rpx solid var(--mrc-border-light); }
.binary-row:last-child { border-bottom: 0; }
.binary-row__label { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 700; }
.binary-row__actions { display: flex; gap: 12rpx; }
.binary-button { min-width: 110rpx; min-height: 68rpx; border-radius: 40rpx; }
.memory-input { height: 88rpx; box-sizing: border-box; margin-top: 20rpx; padding: 0 24rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 20rpx; background: var(--mrc-bg); color: var(--mrc-text-deep); font-size: 27rpx; }
.memory-actions { display: flex; flex-direction: column; gap: 14rpx; padding-top: 12rpx; }
.memory-save, .memory-skip, .memory-clear { width: 100%; min-height: 96rpx; display: flex; align-items: center; justify-content: center; margin: 0; border: 0; border-radius: 48rpx; font-size: 29rpx; font-weight: 800; }
.memory-save { background: var(--mrc-primary-grad); color: #fff; box-shadow: var(--mrc-shadow-coral); }
.memory-skip { background: var(--mrc-surface); color: var(--mrc-text-sub); }
.memory-clear { background: transparent; color: var(--mrc-danger, #a54235); font-weight: 600; }
.memory-save[disabled], .memory-skip[disabled], .memory-clear[disabled] { opacity: .45; }
.memory-save::after, .memory-skip::after, .memory-clear::after { border: 0; }
</style>
